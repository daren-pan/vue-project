# 环境访问指南

## 访问地址

| 环境 | 前端 | Nacos | Sentinel |
|------|------|-------|----------|
| dev | http://101.37.254.163 | http://101.37.254.163/nacos/ | http://101.37.254.163:8718 |
| test | http://47.99.220.14:30080 | http://47.99.220.14/nacos/ | — |

> Nacos 地址末尾 `/` 不可省略
> Sentinel 初始账号: sentinel/sentinel

## 账号

| 系统 | 用户 | 密码 |
|------|------|------|
| 前端 | admin | admin123 |
| Nacos | nacos | nacos |
| Sentinel | sentinel | sentinel |

## 架构说明

- **dev (ACS K8s)**: 弹性容器集群，Nginx 通过 EIP 直绑 Pod 对外服务
- **test (ECS Compose)**: 单机 Docker Compose，完整微服务栈 + Jenkins CI/CD

## 部署

```bash
kubectl apply -k docker/k8s/overlays/dev
kubectl apply -k docker/k8s/overlays/test
```
