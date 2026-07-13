# 环境访问指南

## 访问地址

| 环境 | 前端 | Nacos | 访问方式 |
|------|------|-------|----------|
| dev | http://localhost:8070 | http://localhost:8070/nacos/ | `kubectl port-forward -n ruoyi-dev svc/ruoyi-nginx 8070:80` |
| test | http://47.99.220.14:30080 | http://47.99.220.14/nacos/ | 公网直达 |

> Nacos 地址末尾 `/` 不可省略
> Sentinel 初始账号: sentinel/sentinel

## 账号

| 系统 | 用户 | 密码 |
|------|------|------|
| 前端 | admin | admin123 |
| Nacos | nacos | nacos |
| Sentinel | sentinel | sentinel |

## 架构说明

- **dev (ACS K8s)**: 弹性容器集群，通过 kubectl port-forward 本地访问
- **test (ECS Compose)**: 单机 Docker Compose，完整微服务栈 + Jenkins CI/CD

## 部署

```bash
kubectl apply -k docker/k8s/overlays/dev
kubectl apply -k docker/k8s/overlays/test
```
