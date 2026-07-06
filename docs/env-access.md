# 环境访问指南

## 访问地址

| 环境 | 前端 | Nacos |
|------|------|-------|
| dev | http://47.99.220.14 | http://47.99.220.14/nacos/ |
| test | NodePort（待启用 LoadBalancer） | 同上 |

> Nacos 地址末尾 `/` 不可省略

## 账号

| 系统 | 用户 | 密码 |
|------|------|------|
| 前端 | admin | admin123 |
| Nacos | nacos | nacos |

## 部署

```bash
kubectl apply -k docker/k8s/overlays/dev
kubectl apply -k docker/k8s/overlays/test
```
