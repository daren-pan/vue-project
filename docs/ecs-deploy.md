# ECS 自包含开发环境部署指南

## 概述

在 ECS 上用 Docker 自建中间件（MySQL + Redis + Nacos），直接 `mvn spring-boot:run` 启动微服务。
适合本地开发调试，与 K8s 生产环境隔离。

## 环境要求

- ECS 2C4G+ Ubuntu 22.04
- JDK 21 / Maven 3.x / Git / Docker

```bash
apt update && apt install -y openjdk-21-jdk maven git docker.io
```

## 1. 启动中间件

### MySQL

```bash
docker run -d --name mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=Cai@123456 \
  mysql:8.0
```

### Redis

```bash
docker run -d --name redis -p 6379:6379 \
  redis:7 --requirepass Cai@123456
```

### Nacos

```bash
# 先生成 JWT 密钥
SECRET_KEY=$(openssl rand -base64 32)

docker run -d --name nacos \
  --link mysql:mysql \
  -p 8848:8848 -p 9848:9848 \
  -e MODE=standalone \
  -e SPRING_DATASOURCE_PLATFORM=mysql \
  -e MYSQL_SERVICE_HOST=mysql \
  -e MYSQL_SERVICE_PORT=3306 \
  -e MYSQL_SERVICE_DB_NAME=ry-config \
  -e MYSQL_SERVICE_USER=root \
  -e MYSQL_SERVICE_PASSWORD=Cai@123456 \
  -e MYSQL_SERVICE_DB_PARAM="allowPublicKeyRetrieval=true&useSSL=false" \
  -e NACOS_AUTH_ENABLE=true \
  -e NACOS_AUTH_TOKEN_EXPIRE_SECONDS=18000 \
  -e NACOS_AUTH_TOKEN="${SECRET_KEY}" \
  nacos/nacos-server:v2.5.1
```

## 2. 初始化数据库

```bash
# Nacos 配置库
docker exec -i mysql mysql -uroot -pCai@123456 < docker/mysql/db/ry_config_20250902.sql

# 业务库
docker exec -i mysql mysql -uroot -pCai@123456 -e "CREATE DATABASE IF NOT EXISTS \`ry-cloud\` DEFAULT CHARACTER SET utf8mb4;"
docker exec -i mysql mysql -uroot -pCai@123456 ry-cloud < sql/ry_20250523.sql
docker exec -i mysql mysql -uroot -pCai@123456 ry-cloud < sql/quartz.sql
docker exec -i mysql mysql -uroot -pCai@123456 ry-cloud < sql/migrations/wf_tables.sql

# MySQL root 远程权限（Docker 网桥连接需要）
docker exec -i mysql mysql -uroot -pCai@123456 -e \
  "ALTER USER 'root'@'%' IDENTIFIED WITH mysql_native_password BY 'Cai@123456'; FLUSH PRIVILEGES;"
```

## 3. 拉代码 + 配置

```bash
git clone <repo-url> ~/RuoYi-Cloud
cd ~/RuoYi-Cloud

# 3 个模块都要从模板复制配置
for module in ruoyi-auth ruoyi-gateway; do
  cp $module/src/main/resources/bootstrap-dev.example.yml \
     $module/src/main/resources/bootstrap-dev.yml
  cp $module/src/main/resources/application-dev.example.yml \
     $module/src/main/resources/application-dev.yml
done

cp ruoyi-modules/ruoyi-system/src/main/resources/bootstrap-dev.example.yml \
   ruoyi-modules/ruoyi-system/src/main/resources/bootstrap-dev.yml
cp ruoyi-modules/ruoyi-system/src/main/resources/application-dev.example.yml \
   ruoyi-modules/ruoyi-system/src/main/resources/application-dev.yml

# 修改密码（模板里是 <你的密码>）
find ~/RuoYi-Cloud -name "application-dev.yml" -exec sed -i 's/<你的密码>/Cai@123456/g' {} \;
```

## 4. 编译 & 启动

```bash
# 全量编译
cd ~/RuoYi-Cloud && mvn clean install -DskipTests

# 后台启动（按顺序：auth → gateway → system）
nohup mvn spring-boot:run -pl ruoyi-auth -Dspring-boot.run.profiles=dev > ~/ruoyi-auth.log 2>&1 &
sleep 20
nohup mvn spring-boot:run -pl ruoyi-gateway -Dspring-boot.run.profiles=dev > ~/ruoyi-gateway.log 2>&1 &
nohup mvn spring-boot:run -pl ruoyi-modules/ruoyi-system -Dspring-boot.run.profiles=dev > ~/ruoyi-system.log 2>&1 &
```

## 5. 验证

```bash
# 端口检查（应看到 9200, 8080, 9201）
ss -tlnp | grep java

# 接口检查
curl -s http://localhost:8080/auth/login
# 预期: {"code":401,"msg":"令牌不能为空"}

curl -s http://localhost:9201/system/user/list | head -3
```

## 常见问题

| 问题 | 解决 |
|------|------|
| Nacos JWT 密钥缺失 | 启动时设置 `NACOS_AUTH_TOKEN`，长度 ≥32 字节 base64 |
| MySQL Public Key Retrieval | 连接串加 `allowPublicKeyRetrieval=true`，Nacos 通过 `MYSQL_SERVICE_DB_PARAM` 追加 |
| 编译 target=17 失败 | 安装 JDK 21：`apt install openjdk-21-jdk` |
| `root@172.17.0.1` Access Denied | Docker 网桥需要 `root@'%'` 授权 |
| Nacos 共享配置覆盖本地数据源 | 用 `bootstrap-dev.yml` 设置 `config.enabled: false` |

## 配置原理

```
🍀 ECS/本地开发:  bootstrap.yml → bootstrap-dev.yml(关Nacos config) → application-dev.yml(localhost)
☸️ K8s 生产:     bootstrap.yml → Nacos 配置中心 → env vars 注入
```
