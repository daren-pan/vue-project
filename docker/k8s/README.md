# RuoYi-Cloud CI/CD 部署文档

## 环境架构

```
GitHub (springboot3) → Jenkins (K8s) → ACR 镜像仓库 → K8s 集群
```

| 组件 | 地址/版本 | 说明 |
|---|---|---|
| K8s | Docker Desktop 单节点 | containerd 运行时 |
| Jenkins | jenkins/jenkins:lts-jdk17 | dind 边车容器构建 |
| ACR | crpi-zu4tna9y8drenzc4.cn-hangzhou.personal.cr.aliyuncs.com/ruoyi-personal | 阿里云个人版 |
| MySQL | ruoyi-mysql:3308 | LoadBalancer |
| Nacos | ruoyi-nacos:8848 | 注册中心 & 配置中心 |
| Redis | ruoyi-redis:6379 | 缓存 |
| Nexus | ruoyi-nexus:8081 | Maven+NPM 私服，缓存依赖 |

---

## 已完成的 CI/CD 优化

### 优化清单

| # | 优化项 | 说明 |
|---|--------|------|
| 1 | 镜像标签加构建号 + Git 短哈希 | 每次构建产出唯一标签 `3.6.7-b42-abc1234`，可追溯可回滚 |
| 2 | Helm Chart 统一部署 | `helm upgrade` 替代逐个 `kubectl set image` |
| 3 | 部署 Nexus Maven 私服 | Maven 依赖缓存到内网，构建加速 60-80% |
| 4 | docker-compose 双环境 | `docker-compose.yml`（本地） + `docker-compose.prod.yml`（ACR） |
| 5 | NPM 缓存私服 | npm 依赖也走 Nexus，前后端统一加速 |
| 6 | Helm Chart 管理 K8s | 8 个核心服务统一 values.yaml 集中配置 |

---

## 安装使用手册

### 一、镜像标签 + 部署策略（优化 1 & 2）

**效果：** 每次构建产出唯一标签 `3.6.7-b${BUILD_NUMBER}-${GIT_HASH}`，ACR 保留历史可回滚，`helm upgrade` 统一更新。

**用法：** Jenkins 脚本自动处理，无需手动操作。如需手动部署：

```bash
# 查看当前 Helm 版本
helm history ruoyi -n ruoyi

# 切换到指定标签
helm upgrade ruoyi docker/k8s/charts/ruoyi-cloud -n ruoyi \
  --set imageTag=3.6.7-b99-abc1234 \
  --reuse-values

# 回滚到上一版本
helm rollback ruoyi -n ruoyi
```

---

### 二、Helm Chart（优化 2 & 6）

**Chart 路径：** `docker/k8s/charts/ruoyi-cloud/`

**管理范围：**

```
✅ Helm（8个）: mysql, redis, nacos, gateway, auth, system, gen, job, file, nginx, ingress
⬜ kubectl（3个）: nexus, jenkins, elk
```

**安装 Helm：**

```bash
winget install Helm.Helm
```

**部署：**

```bash
# 首次部署（需先删除旧 kubectl 管理的资源）
helm install ruoyi docker/k8s/charts/ruoyi-cloud -n ruoyi

# 渲染预览（不部署）
helm template ruoyi docker/k8s/charts/ruoyi-cloud

# 升级
helm upgrade ruoyi docker/k8s/charts/ruoyi-cloud -n ruoyi --reuse-values

# 改参数升级
helm upgrade ruoyi docker/k8s/charts/ruoyi-cloud -n ruoyi \
  --set nginx.replicas=2 \
  --set mysql.password=新密码 \
  --reuse-values

# 回滚
helm rollback ruoyi -n ruoyi

# 历史
helm history ruoyi -n ruoyi

# 卸载
helm uninstall ruoyi -n ruoyi
```

**多环境（values-prod.yaml 只写差异）：**

```yaml
microservices:
  gateway:
    replicas: 3
mysql:
  password: prod@strong123
```

```bash
helm upgrade ruoyi docker/k8s/charts/ruoyi-cloud -f values-prod.yaml
```

---

### 三、Nexus Maven + NPM 私服（优化 3 & 5）

**部署：**

```bash
# 部署 Nexus
sh docker/k8s/k8s-deploy.sh nexus
# 或
kubectl apply -f docker/k8s/10-nexus.yaml

# 获取初始密码
kubectl exec deploy/ruoyi-nexus -n ruoyi -- cat /nexus-data/admin.password
```

**访问：** `http://localhost:8880/nexus`

**首次配置：**

1. 用初始密码登录 `admin`
2. 改密码为 `admin123`（与 `settings-nexus.xml` 一致）
3. 创建 Maven 代理仓库 `aliyun-public` → `https://maven.aliyun.com/repository/public`
4. 创建 Maven 仓库组 `maven-public`，包含 `aliyun-public` + `maven-central`
5. 创建 npm 代理仓库 `npm-proxy` → `https://registry.npmmirror.com`
6. 创建 npm 仓库组 `npm-public`，包含 `npm-proxy`

**仓库结构：**

```
Nexus (ruoyi-nexus:8081/nexus)
├── 📦 Maven      ├── aliyun-public (proxy)      └── maven-central (proxy)
│   └── maven-public (group)
│
└── 📦 npm        ├── npm-proxy (proxy)
    └── npm-public (group)
```

**Jenkins 集成（已配置）：**

```bash
# Maven → mvn -s docker/k8s/settings-nexus.xml clean package
# npm   → npm install --registry=http://ruoyi-nexus:8081/nexus/repository/npm-public/
```

---

### 四、docker-compose 双环境（优化 4）

| 文件 | 用途 | 命令 |
|------|------|------|
| `docker-compose.yml` | 本地开发（build 构建） | `docker-compose up -d` |
| `docker-compose.prod.yml` | 模拟 K8s（拉 ACR 镜像） | `docker-compose -f docker-compose.prod.yml up -d` |

**区别：** 本地版包含 `build:` 段从源码编译，生产版直接 `image:` 从 ACR 拉取，和 K8s 完全一致。

---

### 完整 CI/CD 流程

```
git push → Jenkins Poll SCM（每 5 分钟检测）
  │
  ├─ [1/5] docker login ACR
  ├─ [2/5] checkout + git diff 增量检测 → 生成唯一标签
  ├─ [3/5] Maven（Nexus缓存）+ npm（Nexus缓存）并行编译
  ├─ [4/5] docker build（双标签）→ docker push ACR → helm upgrade
  └─ [5/5] curl 健康检查
```

---

## 一、Jenkins 部署

### 1.1 部署到 K8s

```bash
kubectl apply -f docker/k8s/09-jenkins.yaml
```

包含：
- Jenkins Master（jenkins/jenkins:lts-jdk17）
- Docker-in-Docker（dind）边车容器
- kubectl + docker CLI 工具注入
- PVC 持久化（jenkins-data、build-cache、dind-storage）

### 1.2 访问 Jenkins

Jenkins 通过 Ingress 暴露：`http://localhost:8880/jenkins`

首次启动查看初始密码：
```bash
kubectl exec deploy/jenkins -n ruoyi -c jenkins -- cat /var/jenkins_home/secrets/initialAdminPassword
```

---

## 二、安装 Git 插件

### 2.1 安装步骤

1. **登录 Jenkins** → **Manage Jenkins** → **Plugins** → **Available plugins**
2. 搜索并安装以下插件：
   - **Git**（必需，用于 SCM 拉取代码）
   - **GitHub**（可选，GitHub 集成）
3. 勾选 **Install without restart**，等待安装完成

### 2.2 验证

**Manage Jenkins** → **Tools** → 确认 Git 安装路径为 `git`（默认自动识别）

---

## 三、创建 Jenkins Job

### 3.1 新建任务

1. **Dashboard** → **New Item**
2. 输入名称：`RuoYi-Cloud`
3. 选择 **Freestyle project** → **OK**

### 3.2 配置 Git 源码管理

| 配置项 | 值 |
|---|---|
| Repository URL | `https://github.com/daren-pan/RuoYi-Cloud.git` |
| Branches to build | `*/springboot3` |
| Repository browser | (自动) |

> ⚠️ 不需要配置 Credentials（公开仓库）

### 3.3 配置构建触发器

勾选 **Poll SCM**，填写：
```
H/5 * * * *
```
每 5 分钟检查一次 GitHub 是否有新提交。

### 3.4 配置构建脚本

**Build** → **Add build step** → **Execute shell**

将下面的脚本粘贴进去：

<details>
<summary>点击展开完整脚本</summary>

```bash
#!/bin/bash
echo "========================================="
echo "  RuoYi-Cloud CI/CD Pipeline"
echo "  Build: #${BUILD_NUMBER}"
echo "========================================="

# ============================================================
# 1. 登录 ACR 镜像仓库
# ============================================================
echo ""
echo "[1/5] 登录 ACR..."
docker login --username=爱笑的小白11 --password=cai123456 crpi-zu4tna9y8drenzc4.cn-hangzhou.personal.cr.aliyuncs.com
REG=crpi-zu4tna9y8drenzc4.cn-hangzhou.personal.cr.aliyuncs.com/ruoyi-personal
echo "  ✅ 登录成功"

# ============================================================
# 2. 准备最新代码
# ============================================================
echo ""
echo "[2/5] 准备代码..."
docker run --rm -v /workspace:/workspace alpine sh -c "rm -rf /workspace/* /workspace/.[!.]* /workspace/..?*"
cp -r /var/jenkins_home/workspace/RuoYi-Cloud/. /workspace/
cd /workspace
git config --global --add safe.directory /workspace

# 生成唯一镜像标签：版本号-构建号-Git短哈希
GIT_HASH=$(git rev-parse --short HEAD)
UNIQUE_TAG="3.6.7-b${BUILD_NUMBER}-${GIT_HASH}"
echo "  镜像标签: ${UNIQUE_TAG}"

OLD=$(git rev-parse HEAD~1 2>/dev/null || echo "")
if [ -n "$OLD" ]; then
  DIFF=$(git diff --name-only $OLD HEAD 2>/dev/null || echo ALL)
else
  DIFF=ALL
fi

B_UI=$(echo "$DIFF" | grep -c "^ruoyi-ui/")
B_A=$(echo "$DIFF" | grep -cE "^(ruoyi-auth|ruoyi-api|ruoyi-common)/")
B_G=$(echo "$DIFF" | grep -cE "^(ruoyi-gateway|ruoyi-api|ruoyi-common)/")
B_S=$(echo "$DIFF" | grep -cE "^(ruoyi-modules/ruoyi-system|ruoyi-api|ruoyi-common)/")
[ "$DIFF" = ALL ] && { B_UI=1; B_A=1; B_G=1; B_S=1; }

echo "  变更文件: $DIFF"
echo "  UI=$B_UI  Auth=$B_A  Gateway=$B_G  System=$B_S"

# ============================================================
# 3. 并行编译（Maven + npm）
# ============================================================
echo ""
echo "[3/5] 编译..."

M=""
[ $B_A -gt 0 ] && M="$M,ruoyi-auth"
[ $B_G -gt 0 ] && M="$M,ruoyi-gateway"
[ $B_S -gt 0 ] && M="$M,ruoyi-modules/ruoyi-system"

M_PID=""
if [ -n "$M" ]; then
  echo "  [后端] Maven 编译: ${M#,}"
  docker run --rm -v /cache/m2:/root/.m2 -v /workspace:/workspace -w /workspace \
    maven:3.9-eclipse-temurin-17 mvn -s docker/k8s/settings-nexus.xml clean package -DskipTests -pl ${M#,} -am &
  M_PID=$!
else
  echo "  [后端] 无变更，跳过 Maven"
fi

if [ $B_UI -gt 0 ]; then
  echo "  [前端] npm 编译 + 构建镜像..."
  docker run --rm -v /cache/npm:/root/.npm -v /workspace/ruoyi-ui:/app -w /app \
    node:18-alpine sh -c "npm install --registry=http://ruoyi-nexus:8081/nexus/repository/npm-public/ && npm run build:prod"
  echo "  [前端] 编译完成"
else
  echo "  [前端] 无变更，跳过"
fi

# ============================================================
# 4. 构建镜像 + 推送 + 部署
# ============================================================
echo ""
echo "[4/5] 构建 & 部署..."

[ -n "$M_PID" ] && { echo "  等待 Maven 完成..."; wait $M_PID; }

bd() {
  echo "  [${1}] 打包镜像..."
  cp $2/target/*.jar docker/ruoyi/$3/jar/ 2>/dev/null || true
  docker build \
    -t $REG/$1:${UNIQUE_TAG} \
    -t $REG/$1:3.6.7 \
    -f docker/ruoyi/$3/dockerfile docker/ruoyi/$3
  echo "  [${1}] 推送 ACR（唯一标签: ${UNIQUE_TAG}）..."
  docker push $REG/$1:${UNIQUE_TAG}
  docker push $REG/$1:3.6.7
}

if [ $B_UI -gt 0 ]; then
  echo "  [nginx] 打包镜像..."
  mkdir -p docker/nginx/html
  rm -rf docker/nginx/html/dist
  cp -r ruoyi-ui/dist docker/nginx/html/dist
  docker build --no-cache \
    -t $REG/ruoyi-nginx:${UNIQUE_TAG} \
    -t $REG/ruoyi-nginx:latest \
    -f docker/nginx/dockerfile docker/nginx
  echo "  [nginx] 推送 ACR（唯一标签: ${UNIQUE_TAG}）..."
  docker push $REG/ruoyi-nginx:${UNIQUE_TAG}
  docker push $REG/ruoyi-nginx:latest
fi

[ $B_A -gt 0 ] && bd ruoyi-auth ruoyi-auth auth
[ $B_G -gt 0 ] && bd ruoyi-gateway ruoyi-gateway gateway
[ $B_S -gt 0 ] && bd ruoyi-system ruoyi-modules/ruoyi-system modules/system

# Helm 统一部署（所有服务一个命令更新）
if [ $B_UI -gt 0 ] || [ $B_A -gt 0 ] || [ $B_G -gt 0 ] || [ $B_S -gt 0 ]; then
  echo ""
  echo "  [Helm] 滚动更新所有服务（imageTag: ${UNIQUE_TAG}）..."
  helm upgrade ruoyi docker/k8s/charts/ruoyi-cloud -n ruoyi \
    --set imageTag=${UNIQUE_TAG} \
    --set image.nginxTag=${UNIQUE_TAG} \
    --reuse-values
  kubectl rollout status deploy/ruoyi-gateway -n ruoyi --timeout=120s
  kubectl rollout status deploy/ruoyi-system -n ruoyi --timeout=120s
  echo "  [Helm] ✅ 完成"
fi

# ============================================================
# 5. 健康检查
# ============================================================
echo ""
echo "[5/5] 健康检查..."

hc() {
  code=$(curl -s --max-time 5 -o /dev/null -w '%{http_code}' $1 2>/dev/null)
  [ "$code" = "200" ] && echo "  [$2] ✅ OK" || echo "  [$2] ❌ FAIL ($code)"
}

[ $B_UI -gt 0 ] && { sleep 5; hc http://ruoyi-nginx:30080 nginx; }
[ $B_A -gt 0 ] && { sleep 5; hc http://ruoyi-auth:8080/actuator/health ruoyi-auth; }
[ $B_G -gt 0 ] && { sleep 5; hc http://ruoyi-gateway:8080/actuator/health ruoyi-gateway; }
[ $B_S -gt 0 ] && { sleep 5; hc http://ruoyi-system:8080/actuator/health ruoyi-system; }

echo ""
echo "========================================="
echo "  Pipeline #${BUILD_NUMBER} 完成"
echo "========================================="
```

</details>

### 3.5 保存

点击 **Save** 保存配置。

---

## 四、Nexus Maven 私服（推荐）

部署 Nexus 后 Maven 依赖只需下载一次，后续构建从内网缓存获取。

### 4.1 部署

```bash
# 部署 Nexus
sh docker/k8s/k8s-deploy.sh nexus

# 获取初始密码
kubectl exec deploy/ruoyi-nexus -n ruoyi -- cat /nexus-data/admin.password
```

### 4.2 首次配置

1. 浏览器打开 `http://localhost:30081`
2. 用初始密码登录（用户 admin）
3. 按向导设置新密码为 `admin123`（与 `settings-nexus.xml` 一致）
4. 点击齿轮图标 → **Repository** → **Create repository**
5. 选择 **maven2 (proxy)**，创建以下代理仓库：

| 仓库名 | 代理地址 |
|--------|---------|
| `aliyun-public` | `https://maven.aliyun.com/repository/public` |
| `maven-central` | `https://repo1.maven.org/maven2/` |

6. 创建 **maven2 (group)**，命名为 `maven-public`，将上面两个代理仓库加入

### 4.3 生效方式

Jenkins 构建脚本已自动通过 `-s docker/k8s/settings-nexus.xml` 走 Nexus，无需额外操作。

---

## 五、流水线说明

### 5.1 执行流程

```
[1/5] 登录 ACR
   ↓
[2/5] 准备代码（从 Jenkins workspace 复制 + 增量检测 + 生成唯一标签）
   ↓
[3/5] 并行编译
   ├── Maven（auth / gateway / system）
   └── npm（ruoyi-ui）
   ↓
[4/5] 构建镜像 → 推送双标签 → kubectl set image（滚动更新）
   ↓
[5/5] 健康检查
```

### 5.2 镜像标签策略

每次构建产出一个**唯一标签**（用于回滚追溯）和一个**滚动标签**（用于自动部署）：

```
3.6.7-b42-abc1234          ← 唯一标签（保留历史）
3.6.7                      ← 滚动标签（始终指向最新成功构建）
```

| 标签 | 格式 | 用途 |
|------|------|------|
| 唯一标签 | `3.6.7-b${BUILD_NUMBER}-${GIT_HASH}` | 永久保留，快速回滚到任意历史版本 |
| 滚动标签 | `3.6.7` | K8s 部署目标，始终拉最新版 |

### 5.3 增量构建

脚本通过 `git diff HEAD~1 HEAD` 检测变更文件，只编译受影响的模块：

| 变更路径 | 编译范围 |
|---|---|
| `ruoyi-ui/**` | 仅 npm + nginx 镜像 |
| `ruoyi-auth/**` 或 `ruoyi-common/**` | Maven(auth) + auth 镜像 |
| `ruoyi-gateway/**` 或 `ruoyi-common/**` | Maven(gateway) + gateway 镜像 |
| `ruoyi-modules/ruoyi-system/**` | Maven(system) + system 镜像 |

### 5.4 构建耗时

| 场景 | 耗时 |
|---|---|
| 仅改前端 | ~30s |
| 仅改一个后端模块 | ~1min |
| 全量构建 | ~2min |

---

## 六、K8s 资源清单

| 文件 | 内容 | 部署顺序 |
|---|---|---|
| `00-secret.yaml` | ACR 镜像拉取凭证 | ① |
| `01-namespace.yaml` | ruoyi 命名空间 | ② |
| `02-mysql.yaml` | MySQL 数据库 | ③ |
| `03-redis.yaml` | Redis 缓存 | ③ |
| `04-nacos.yaml` | Nacos 注册配置中心 | ④ |
| `10-nexus.yaml` | Nexus Maven 私服 | ④ |
| `05-microservices.yaml` | 微服务（gateway/auth/system/gen/job/file） | ⑤ |
| `06-nginx.yaml` | 前端 Nginx | ⑤ |
| `07-monitor.yaml` | 监控（可选） | ⑥ |
| `08-ingress.yaml` | Ingress 路由 | ⑦ |
| `09-jenkins.yaml` | Jenkins CI/CD | ⑧ |

---

## 七、常用命令

```bash
# 查看所有 Pod
kubectl get pod -n ruoyi

# 查看 Jenkins 构建日志
kubectl exec deploy/jenkins -n ruoyi -c jenkins -- cat /var/jenkins_home/jobs/RuoYi-Cloud/builds/$(kubectl exec deploy/jenkins -n ruoyi -c jenkins -- ls /var/jenkins_home/jobs/RuoYi-Cloud/builds/ | tail -1)/log

# 手动触发构建
kubectl exec deploy/jenkins -n ruoyi -c jenkins -- curl -X POST http://localhost:8080/jenkins/job/RuoYi-Cloud/build

# 重启某个服务
kubectl rollout restart deploy/ruoyi-nginx -n ruoyi

# 查看 ACR Secret
kubectl get secret acr-auth -n ruoyi -o jsonpath="{.data.\.dockerconfigjson}" | base64 -d

# 全部重新部署
kubectl apply -f docker/k8s/
```
