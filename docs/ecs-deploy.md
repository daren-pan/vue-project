# ECS Docker Compose 部署指南

## 概述

使用 Docker Compose 一键部署全套微服务（MySQL + Redis + Nacos + Gateway + Auth + System + Nginx）。
适合 ECS 开发/测试环境，与 K8s 生产环境隔离。

## 环境要求

- ECS 2C4G+ Ubuntu 22.04
- JDK 21 / Maven 3.x / Git / Docker

```bash
apt update && apt install -y openjdk-21-jdk maven git docker.io
```

## 1. 拉代码

```bash
git clone <repo-url> ~/RuoYi-Cloud
cd ~/RuoYi-Cloud/docker
```

## 2. 编译 & 复制 jar

```bash
# 回到项目根目录编译
cd ~/RuoYi-Cloud
mvn clean package -DskipTests

# 复制 jar 到 docker 部署目录
cd docker && sh copy.sh
```

## 3. 构建镜像 & 启动

```bash
# 构建所有服务镜像
docker compose build

# 启动所有服务
docker compose up -d
```

## 4. 启动后配置 Nacos

Nacos 启动后，需要配置各模块的数据源 URL，使用占位符适配 Docker 容器网络：

1. 浏览器打开 `http://<ECS公网IP>:8848/nacos/`，登录（nacos/nacos）
2. 配置管理 → 配置列表 → 编辑 `ruoyi-system-dev.yml`，数据源 URL 改为：

```yaml
datasource:
  master:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/ry-cloud?...
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:password}
```

3. 同理编辑 `ruoyi-gen-dev.yml`、`ruoyi-job-dev.yml`、`ruoyi-file-dev.yml`、`ruoyi-visual-monitor-dev.yml`

> docker-compose.yml 中已设置 `DB_HOST=ruoyi-mysql`、`DB_USER=root`、`DB_PASSWORD=你的密码`，Nacos 占位符会自动注入。

## 5. 验证

```bash
docker compose ps                                    # 所有服务应 Up
curl -s http://localhost:8080/auth/login             # 预期 {"code":401}
curl -s http://localhost:81                         # 前端页面

# 查看日志
docker compose logs -f ruoyi-modules-system          # system 日志
docker compose logs --tail 50 ruoyi-nacos            # Nacos 日志
```

## 常用命令

```bash
# 在 docker/ 目录下操作
cd ~/RuoYi-Cloud/docker

# 查看状态
docker compose ps

# 重启某个服务
docker compose restart ruoyi-modules-system

# 重新编译部署某个服务
cd ~/RuoYi-Cloud && mvn package -DskipTests -pl ruoyi-modules/ruoyi-system -am
cd docker && sh copy.sh
docker compose up -d --build ruoyi-modules-system

# 停止/删除
docker compose stop
docker compose down
```

## 访问地址

| 服务 | 地址 |
|------|------|
| 前端 | `http://<ECS公网IP>:81` |
| Nacos | `http://<ECS公网IP>:8848/nacos/` |
| Sentinel | `http://<ECS公网IP>:8718` |
| Gateway | `http://<ECS公网IP>:8080` |
| Auth | `http://<ECS公网IP>:9200` |
| System | `http://<ECS公网IP>:9301` |
| Jenkins | `http://<ECS公网IP>:8081` |
| SkyWalking | `http://<ECS公网IP>:8090` |
| Kibana | `http://<ECS公网IP>:5601` |

> 需要安全组开放对应端口

## 配置原理

```
Docker Compose:
  docker-compose.yml → env vars 注入（DB_HOST, REDIS_HOST 等）
    ↓
  bootstrap.yml → 连 Nacos → 拉 ruoyi-*-dev.yml
    ↓
  Nacos 配置中 ${DB_HOST} 占位符 → Docker 环境变量自动填充
```

## 常见问题

| 问题 | 解决 |
|------|------|
| Nacos JWT 密钥缺失 | `docker-compose.yml` 已预设 `NACOS_AUTH_TOKEN` |
| MySQL Access Denied | `docker exec ruoyi-mysql mysql -uroot -p -e "GRANT ALL ON *.* TO 'root'@'%' IDENTIFIED BY '密码';"` |
| 容器名冲突 | `docker compose down && docker compose up -d` |
| 端口冲突 | 修改 `docker-compose.yml` 中的 `ports` 映射 |
