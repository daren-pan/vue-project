# RuoYi-Cloud K8s 16GB 宿主机精简配置

> 适用于 16GB 内存笔记本电脑，Docker Desktop WSL2 分配 8GB。

## 📊 内存分配策略

| 公式 | 说明 |
|------|------|
| `Container Limit = Xmx × 2` | JVM 堆外还需 Metaspace/NIO/线程栈 |
| `Requests < Limits` | Requests 保证调度，Limits 防 OOM |

## 🎯 服务体系

### ✅ 保留（核心业务）

| 服务 | 副本 | Limits | Requests | Xmx | 倍率 |
|------|------|--------|----------|-----|------|
| Gateway | 1 | 1 Gi | 384 Mi | 512m | 2:1 |
| Auth | 1 | 768 Mi | 384 Mi | 384m | 2:1 |
| System | **1** (原2) | 768 Mi | 384 Mi | 384m | 2:1 |
| Gen | 1 | 512 Mi | 256 Mi | 256m | 2:1 |
| Job | 1 | 512 Mi | 256 Mi | 256m | 2:1 |
| Nginx | 1 | 256 Mi | 128 Mi | — | — |
| MySQL | 1 | 1 Gi | 512 Mi | — | — |
| Redis | 1 | 256 Mi | 128 Mi | — | — |
| Nacos | 1 | 1 Gi | 512 Mi | — | — |

### ⚠️ 按需启动

| 服务 | 策略 |
|------|------|
| Jenkins | 用 CI/CD 时 `kubectl scale deploy jenkins -n ruoyi --replicas=1`，用完缩到 0 |
| File | 需要文件上传时手动开 |
| Monitor | 需要监控时手动开 |

### ❌ 已关闭（省 ~2.5 Gi）

| 服务 | 原因 |
|------|------|
| ELK 全家 (ES/Logstash/Kibana/Filebeat) | 用文件日志替代 |
| System 第 2 副本 | 单节点无需高可用 |

## 📦 资源汇总

| | 内存 | 占 8Gi WSL2 |
|------|------|------|
| Requests 总和 | **≈ 3.4 Gi** | 42% ✅ |
| Limits 总和 | **≈ 6.5 Gi** | 81% ✅ |

## 🚀 部署步骤

### 1. 部署基础设施（一次性）

```powershell
cd docker\k8s

# 基础环境
kubectl apply -f 01-namespace.yaml
kubectl apply -f 02-mysql.yaml
kubectl apply -f 03-redis.yaml
kubectl apply -f 04-nacos.yaml

# 入口
kubectl apply -f 06-nginx.yaml
kubectl apply -f 08-ingress.yaml
```

### 2. 部署微服务（16G 优化版）

```powershell
# 使用 16G 精简配置
kubectl apply -f ..\k8s-16g\05-microservices.yaml
```

### 3. 确认状态

```powershell
kubectl get pod -n ruoyi
# 应该看到: gateway, auth, system, gen, job, nginx, mysql, redis, nacos 全部 Running
```

### 4. （可选）按需启动 Jenkins

```powershell
# 启动 Jenkins
kubectl apply -f 09-jenkins.yaml
kubectl scale deploy jenkins -n ruoyi --replicas=1

# 用完后关闭
kubectl scale deploy jenkins -n ruoyi --replicas=0
```

## 🔄 恢复完整配置

切回 32GB 机器时：

```powershell
# 用原版配置覆盖
kubectl apply -f docker\k8s\05-microservices.yaml
kubectl apply -f docker\k8s\07-elk.yaml
kubectl apply -f docker\k8s\09-jenkins.yaml
```

## ⚙️ 关键改动说明

1. **去掉 LOGSTASH_HOST** — 无 ELK 时避免连接报错
2. **System 副本 2→1** — 省 768Mi Request
3. **所有 JVM Xmx 减半** — 适配降低的 Limits，始终保持 2:1 安全倍率
4. **移除 ELK 全家桶** — 日志仍写入容器内文件，`kubectl logs` 可查看
