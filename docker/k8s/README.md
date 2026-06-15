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
| Nexus | ruoyi-nexus:8081 | Maven 私服，缓存依赖 |

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
  echo "  [${1}] 滚动更新（set image → ${UNIQUE_TAG}）..."
  kubectl set image deploy/$1 ${1#ruoyi-}=$REG/$1:${UNIQUE_TAG} -n ruoyi
  kubectl rollout status deploy/$1 -n ruoyi --timeout=120s
  echo "  [${1}] ✅ 完成"
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
  echo "  [nginx] 滚动更新（set image → ${UNIQUE_TAG}）..."
  kubectl set image deploy/ruoyi-nginx nginx=$REG/ruoyi-nginx:${UNIQUE_TAG} -n ruoyi
  kubectl rollout status deploy/ruoyi-nginx -n ruoyi --timeout=120s
  echo "  [nginx] ✅ 完成"
fi

[ $B_A -gt 0 ] && bd ruoyi-auth ruoyi-auth auth
[ $B_G -gt 0 ] && bd ruoyi-gateway ruoyi-gateway gateway
[ $B_S -gt 0 ] && bd ruoyi-system ruoyi-modules/ruoyi-system modules/system

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
