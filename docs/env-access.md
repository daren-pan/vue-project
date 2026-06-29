# 环境访问指南

## 访问地址

| 环境 | 前端 | Nacos |
|------|------|-------|
| dev | http://121.199.13.17 | http://121.199.13.17/nacos/ |
| test | http://121.40.29.76 | http://121.40.29.76/nacos/ |

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
