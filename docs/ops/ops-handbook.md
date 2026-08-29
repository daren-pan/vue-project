# 运维手册（Ops Handbook）

> 适用对象：值班工程师、运维负责人、研发（自救排查）。
> 适用范围：RuoYi-Cloud 重构版微服务（Spring Boot 3 + Spring Cloud Alibaba + Java 17 + Vue3 + Docker Compose）。
> 本手册是运维阶段的落地文档，与流程文档 [../process/00-overview.md](../process/00-overview.md)、[../process/05-ops.md](../process/05-ops.md) 保持一致；发布相关操作请使用[发布说明模板](release-notes-template.md)。

**相关文档**：

- 流程：[../process/00-overview.md](../process/00-overview.md)（总览）· [../process/04-release.md](../process/04-release.md)（生产验收/发布）· [../process/05-ops.md](../process/05-ops.md)（运维阶段）
- 架构：[../architecture/00-architecture-overview.md](../architecture/00-architecture-overview.md)（总体架构）· [../architecture/architecture-security.md](../architecture/architecture-security.md)（安全架构）
- 模块（按服务细分）：`docs/modules/` 规划目录（`ruoyi-system` · `ruoyi-workflow` · `ruoyi-file` · `ruoyi-monitor`）
- 发布：[release-notes-template.md](release-notes-template.md)（发布说明模板）

---

## 1. 环境拓扑

### 1.1 总体拓扑

```
   用户 / 浏览器
        │  HTTPS
        ▼
 ┌─────────────────────┐
 │  ruoyi-ui（Vue3）     │  前端静态资源（Nginx / 静态托管），代理 /prod-api → 网关
 └──────────┬──────────┘
            │  HTTP/JSON
            ▼
 ┌─────────────────────┐
 │ ruoyi-gateway [8080] │  唯一入口：路由 / 全局鉴权 / Sentinel 限流 / 审计 / 跨域
 └──────┬──────┬──────┬─┘
        │      │      │
   ┌────▼──┐ ┌▼─────┐ ┌▼────────┐
   │ auth  │ │system│ │gen      │  业务服务（经 Nacos 注册发现，服务间 OpenFeign 调用）
   │ 9200  │ │ 9201 │ │ 9202    │
   └───────┘ └──────┘ └─────────┘
   ┌───────┐ ┌───────┐ ┌────────┐ ┌────────┐
   │ job   │ │work-  │ │ file   │ │monitor │
   │ 9203  │ │flow   │ │ 9300   │ │ 9100   │
   │       │ │ 9210  │ │(MinIO) │ │(Admin) │
   └───────┘ └───────┘ └────────┘ └────────┘
        │
        ▼
 ┌─────────────────────────────────────────────┐
 │ 基础设施：Nacos 8848 · Redis 6379 · MySQL 3306 │
 │           MinIO 9000 · Seata 8091 · Sentinel 8858 │
 └─────────────────────────────────────────────┘
```

- 所有外部流量只进网关，业务服务端口不对外暴露（防火墙 / 安全组放行 8080 及必要的运维端口即可）。
- 服务间调用禁止绕过网关直连，统一走 Nacos 服务发现 + OpenFeign。

### 1.2 服务端口一览

| 服务 | 端口 | Nacos 服务名 | 数据库 | 关键依赖 |
|------|------|--------------|--------|----------|
| ruoyi-gateway | 8080 | ruoyi-gateway | — | Nacos、Sentinel |
| ruoyi-auth | 9200 | ruoyi-auth | ry-cloud | Redis（token/验证码） |
| ruoyi-system | 9201 | ruoyi-system | ry-cloud | MySQL、Redis |
| ruoyi-gen | 9202 | ruoyi-gen | ry-cloud | MySQL |
| ruoyi-job | 9203 | ruoyi-job | ry-cloud | MySQL、Redis |
| ruoyi-workflow | 9210 | ruoyi-workflow | ry-flowable | MySQL、Flowable 7 |
| ruoyi-file | 9300 | ruoyi-file | ry-cloud | MinIO |
| ruoyi-monitor | 9100 | ruoyi-monitor | ry-cloud | 各服务 actuator |

> 所有服务默认开启 `actuator`，健康检查端点统一为 `http://<host>:<port>/actuator/health`。

### 1.3 中间件与端口

| 中间件 | 端口 | 用途 | 默认控制台 |
|--------|------|------|-----------|
| Nacos | 8848（HTTP）/ 9848（gRPC） | 注册中心 + 配置中心 | http://\<host\>:8848/nacos |
| Redis | 6379 | 缓存 / token / 验证码 / 幂等 | redis-cli |
| MySQL | 3306 | 业务数据（ry-cloud、ry-flowable） | mysql 客户端 |
| MinIO | 9000（API）/ 9001（Console） | 对象存储 | http://\<host\>:9001 |
| Seata | 8091 | 分布式事务（AT 模式） | — |
| Sentinel | 8858 | 限流 / 降级 / 熔断 | http://\<host\>:8858 |

### 1.4 数据与存储

| 数据 | 位置 | 说明 |
|------|------|------|
| 业务库 | MySQL `ry-cloud` | 用户/角色/菜单/字典/参数/日志等 |
| 工作流库 | MySQL `ry-flowable` | Flowable 引擎表（ACT_*） |
| Nacos 配置 | Nacos 存储（或 MySQL `ry-config`） | 配置模板源在 `config/`（git） |
| 缓存 | Redis | 登录 token（`login_tokens:*`）、验证码（`captcha_codes:*`）等 |
| 文件 | MinIO bucket | 上传文件，元数据在 ry-cloud |

### 1.5 环境划分

| 环境 | 用途 | 数据策略 | 变更策略 |
|------|------|----------|----------|
| local | 开发者本机 | 本地库，可重置 | 自由 |
| dev | 集成环境 | 自动部署，数据可重置 | 合并即部署 |
| test | 测试环境 | 数据固定，供 QA 回归 | 评审后部署 |
| staging | 预生产 | 与生产同构，灰度演练 | 发布评审 |
| prod | 生产 | 白名单 + 灰度 | 变更评审 + 回滚预案 |

---

## 2. 启动与停止

### 2.1 启动顺序

注册中心（Nacos）使各服务可乱序启动，但推荐按「基础设施 → 业务服务 → 网关 → 前端」执行，便于探活与路由就绪。

**第 1 步：拉起基础设施（Docker Compose）**

```bash
docker compose -f docker/docker-compose.yml up -d nacos mysql redis minio sentinel seata
# 等待中间件就绪（见 §3 健康检查），重点确认 Nacos 与 MySQL 可用
```

**第 2 步：启动业务服务（JAR / 镜像方式）**

```bash
# 认证与系统服务先行（登录链路依赖）
mvn -pl ruoyi-auth,ruoyi-system -am spring-boot:run
# 其余业务服务
mvn -pl ruoyi-gen,ruoyi-job,ruoyi-workflow,ruoyi-file,ruoyi-monitor -am spring-boot:run
```

**第 3 步：启动网关（最后）**

```bash
mvn -pl ruoyi-gateway -am spring-boot:run
```

**第 4 步：启动前端**

```bash
cd ruoyi-ui && npm install && npm run dev        # 开发模式
npm run build:prod                               # 生产构建，产物部署到 Nginx/静态托管
```

> 生产多实例部署：先滚动拉起所有实例并确认注册成功，再让网关 / 负载均衡放量。

### 2.2 停止顺序

与启动相反：**前端 → 网关 → 业务服务 → 中间件**。

```bash
# 业务服务 / 网关：优先优雅停机（等待处理中请求完成）
kill -TERM <pid>          # 或 docker stop <container>
# 最后停止中间件（除非整机维护，否则建议保持中间件常驻）
docker compose -f docker/docker-compose.yml stop
```

> 禁止直接 `kill -9` 业务服务；`docker compose down` 会移除容器（数据卷 volume 默认保留），`down -v` 会**删除数据卷（数据不可恢复）**，仅允许在明确的重置场景使用。

### 2.3 一键脚本

```bash
./bin/dev-up.sh          # Windows PowerShell： .\bin\dev-up.ps1
# 脚本内部依次执行：依赖拉起 → 建库/迁移 → 业务服务启动 → 网关启动 → 前端启动
```

---

## 3. 健康检查

### 3.1 服务健康检查

所有服务健康端点：`http://<host>:<port>/actuator/health`，返回 `{"status":"UP"}`（HTTP 200）为健康，`DOWN`（HTTP 503）为异常。

| 服务 | 健康检查 URL |
|------|--------------|
| ruoyi-gateway | http://127.0.0.1:8080/actuator/health |
| ruoyi-auth | http://127.0.0.1:9200/actuator/health |
| ruoyi-system | http://127.0.0.1:9201/actuator/health |
| ruoyi-gen | http://127.0.0.1:9202/actuator/health |
| ruoyi-job | http://127.0.0.1:9203/actuator/health |
| ruoyi-workflow | http://127.0.0.1:9210/actuator/health |
| ruoyi-file | http://127.0.0.1:9300/actuator/health |
| ruoyi-monitor | http://127.0.0.1:9100/actuator/health |

一键批量检查：

```bash
for p in 8080 9200 9201 9202 9203 9210 9300 9100; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${p}/actuator/health")
  echo "port ${p} -> ${code}"
done
```

### 3.2 中间件健康检查

| 中间件 | 检查命令 | 期望结果 |
|--------|----------|----------|
| Nacos | `curl -s http://127.0.0.1:8848/nacos/v1/ns/operator/metrics \| jq` | 返回 JSON，服务数正常 |
| Redis | `docker exec -it redis redis-cli -a "\${REDIS_PASSWORD}" ping` | `PONG` |
| MySQL | `docker exec -it mysql mysqladmin -uroot -p"\${MYSQL_ROOT_PASSWORD}" ping` | `mysqld is alive` |
| MinIO | `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:9000/minio/health/live` | `200` |
| Seata | `docker logs seata-server --tail 50`（或开启 actuator 后 `curl :8091/actuator/health`） | 日志出现 `Server started` |
| Sentinel | `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8858` | `200`（登录页） |

### 3.3 注册中心检查（服务是否注册成功）

```bash
# 服务列表
curl -s "http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100" | jq
# 指定服务的实例（健康实例数应 ≥ 预期副本数）
curl -s "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=ruoyi-system&groupName=DEFAULT_GROUP" | jq
```

> 常见坑：服务启动成功但 `instance.list` 为空 → 检查 Nacos 地址配置、`spring.cloud.nacos.discovery.enabled`、防火墙 9848（gRPC）端口是否放行。

---

## 4. 常用日志路径

### 4.1 应用日志

| 部署方式 | 日志位置 | 说明 |
|----------|----------|------|
| Docker | `docker logs -f <container>` | 标准输出，编排平台可采集 |
| JAR / 宿主机 | `logs/<app-name>/`（如 `logs/ruoyi-system/`） | logback 配置落盘，含 `info.log` / `error.log` 等 |

查看某个服务的最近日志：

```bash
docker logs --tail 200 -f ruoyi-system
docker logs --tail 200 ruoyi-system 2>&1 | grep -i "error"
```

### 4.2 中间件日志

| 中间件 | 日志路径 / 获取方式 |
|--------|---------------------|
| Nacos | 容器内 `nacos/logs/nacos.log`、`start.out`（`docker logs nacos`） |
| MySQL | `docker logs mysql`，或 `SHOW VARIABLES LIKE 'log_error';` |
| Redis | `docker logs redis`（默认 stdout） |
| MinIO | `docker logs minio` |
| Seata | 容器内 `seata/logs/seata-server.log`（`docker logs seata-server`） |

### 4.3 日志规范（排查与审计要求）

- 应用日志要求包含 `traceId`（链路追踪标识），跨服务排查时按 `traceId` 串联。
- 敏感字段（手机号/身份证/银行卡）必须脱敏后入日志（见 [../architecture/architecture-security.md](../architecture/architecture-security.md)）。
- 禁止将数据库密码、JWT 密钥等凭证打印到日志。

---

## 5. 常用诊断命令

### 5.1 容器与编排

```bash
docker ps -a                              # 容器状态（STATUS 含 Exited/Restarting 需关注）
docker compose -f docker/docker-compose.yml ps   # compose 服务状态
docker stats                               # 容器 CPU/内存实时占用
docker inspect <container> | jq '.State'   # 容器详细状态与退出码
```

### 5.2 端口与网络

```bash
# Linux
ss -lntp | grep -E '8080|9201'            # 端口监听
lsof -i :9201                             # 占用进程
# Windows（PowerShell）
netstat -ano | findstr :9201
# 连通性
curl -v http://127.0.0.1:9201/actuator/health
telnet 127.0.0.1 8848                     # 端口可达性
```

### 5.3 服务状态与进程

```bash
jps -l                                    # JVM 进程列表（JAR 部署）
jstack <pid>                              # 线程栈（配合高 CPU/卡死排查）
jmap -heap <pid>                          # 堆内存概况
jstat -gcutil <pid> 1000 10               # GC 情况（FGC 持续增长 → 排查泄漏）
# 生产建议使用 Arthas 在线诊断：java -jar arthas-boot.jar
```

### 5.4 中间件诊断

```bash
# Redis：内存与键统计
docker exec redis redis-cli -a "${REDIS_PASSWORD}" info memory | grep -E 'used_memory_human|maxmemory'
docker exec redis redis-cli -a "${REDIS_PASSWORD}" --scan --pattern 'login_tokens:*' | head

# MySQL：慢查询 / 连接数 / 当前会话
docker exec mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "SHOW PROCESSLIST;"
docker exec mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "SHOW VARIABLES LIKE 'slow_query_log%';"
docker exec mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "SELECT COUNT(*) FROM information_schema.processlist;"

# MinIO：容量与桶
mc alias set local http://127.0.0.1:9000 "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}"
mc admin info local
```

### 5.5 网关 / 链路排查

```bash
# 网关路由断言：确认服务路由规则存在
curl -s http://127.0.0.1:8080/actuator/gateway/routes | jq
# 全链路（带 token 模拟请求，观察是否 401/404/502）
curl -s -H "Authorization: Bearer <token>" http://127.0.0.1:8080/system/user/list -o /dev/null -w '%{http_code}\n'
```

---

## 6. 数据库备份与恢复

### 6.1 备份策略

| 项 | 策略 |
|----|------|
| 频率 | 每日全量（低峰 02:00），生产建议追加 binlog 增量 |
| 保留 | 本地保留 30 天，异地（对象存储/备份机）保留 ≥ 90 天 |
| 范围 | `ry-cloud`、`ry-flowable` 两个库全量（含存储过程/触发器/事件） |
| 校验 | 每周恢复演练一次；每日检查 dump 文件大小与表数量 |
| 补充 | MinIO 桶、Nacos 配置（`config/` 模板 + 控制台导出）一并备份 |

### 6.2 手动备份

```bash
mkdir -p backup
docker exec mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --events --set-gtid-purged=OFF ry-cloud' > "backup/ry-cloud_$(date +%F_%H%M).sql"
docker exec mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --events --set-gtid-purged=OFF ry-flowable' > "backup/ry-flowable_$(date +%F_%H%M).sql"
```

> `--single-transaction` 保证 InnoDB 一致性快照，避免锁表；密码通过容器内环境变量注入，禁止写入脚本明文。

### 6.3 定时备份

```bash
# crontab -e（每日 02:00，输出到日志）
0 2 * * * /opt/scripts/backup-mysql.sh >> /var/log/backup-mysql.log 2>&1
# 备份脚本要点：mysqldump → gzip 压缩 → 校验文件大小 → 清理超过保留期的旧备份 → 同步到异地
```

### 6.4 恢复流程

```bash
# 1. 确认恢复目标（库必须存在，或先建库）
docker exec -i mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS ry-cloud DEFAULT CHARACTER SET utf8mb4;"'
# 2. 恢复（恢复前务必先对当前库做一次快照备份）
docker exec -i mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" ry-cloud' < backup/ry-cloud_20260620_0200.sql
# 3. 校验：关键表行数、最新数据时间点、登录冒烟
```

**恢复注意**：

- 恢复是破坏性操作，必须走变更审批，恢复前先备份「当前损坏状态」。
- 只恢复到**指定时间点**的备份，超出备份点的数据需人工核对补录（有 binlog 时用 `mysqlbinlog` 前滚）。
- 恢复后重启依赖该库的服务，并观察错误日志。

### 6.5 对象存储与配置备份

```bash
# MinIO 桶镜像同步到备份端
mc alias set backup http://<backup-host>:9000 "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}"
mc mirror --overwrite local/ry-bucket backup/ry-bucket

# Nacos 配置：config/（git 模板）为源；控制台「配置管理 → 导出」定期导出全量配置留档
```

### 6.6 Redis 备份说明

- Redis 在本项目是**缓存（可重建）**，非权威数据源，默认无需备份；登录 token 丢失会导致用户重新登录。
- 如需恢复现场，可 `SAVE`/`BGSAVE` 生成 RDB 或开启 AOF；恢复后需对热点数据预热。

---

## 7. 常见故障排查（P0–P3）

### 7.1 故障级别定义

| 级别 | 定义 | 响应时限 | 示例 |
|------|------|----------|------|
| P0 | 全站不可用 / 核心链路（登录、支付类）完全中断 | 立即响应，持续处置 | 网关全挂、数据库宕机 |
| P1 | 主要功能受损，有绕过方案 | 15 分钟内响应，1 小时内缓解 | 单服务不可用、上传失败 |
| P2 | 局部功能异常，影响有限 | 4 小时内响应 | 定时任务漏执行、个别页面报错 |
| P3 | 体验/隐患类问题 | 24 小时内响应，排期修复 | 日志噪音、慢 SQL、告警误报 |

> 处置原则：先恢复服务（降级/回滚/重启），再定位根因；所有操作留痕，事后补故障报告。

### 7.2 P0 案例

**P0-1 全站 502 / 网关无法访问**

| 项 | 内容 |
|----|------|
| 现象 | 所有请求 502，或网关端口无响应 |
| 可能原因 | 网关进程退出、Nacos 不可用导致路由/注册异常、下游全部掉线 |
| 排查 | `docker ps` / `jps -l` 确认网关存活；`curl :8848` 确认 Nacos；`curl :8080/actuator/gateway/routes` 看路由；`docker logs ruoyi-gateway --tail 200` |
| 处理 | 先重启网关；Nacos 异常则先恢复 Nacos；下游服务批量掉线时按 §2.1 顺序整体拉起 |

**P0-2 数据库宕机 / 连接池耗尽**

| 项 | 内容 |
|----|------|
| 现象 | 服务大量报连接异常、接口超时；`SHOW PROCESSLIST` 连接数打满 |
| 可能原因 | MySQL 进程退出、慢 SQL 堆积、连接未释放、磁盘满 |
| 排查 | `docker ps` 看 mysql 容器；`docker logs mysql`；`df -h` 看磁盘；`SHOW PROCESSLIST` 找长事务/慢查询 |
| 处理 | 磁盘满先扩容/清理；慢 SQL 先 kill 会话止损；恢复后检查连接池参数（druid 配置）与索引 |

**P0-3 Redis 故障导致登录不可用**

| 项 | 内容 |
|----|------|
| 现象 | 验证码报错、登录后 token 校验失败、大量 401 |
| 可能原因 | Redis 宕机、内存打满（maxmemory 策略驱逐）、网络分区 |
| 排查 | `docker ps`、`redis-cli ping`、`info memory`、`info clients` |
| 处理 | 重启/拉起 Redis；内存打满则调大 `maxmemory` 或清理过期键（`--scan` + `TTL` 核查）；恢复后确认各服务重连（lettuce 会自动重连） |

### 7.3 P1 案例

**P1-1 服务启动失败（Nacos 连接超时 / 端口占用）**

| 项 | 内容 |
|----|------|
| 现象 | 启动日志 `Connect to server failed` 或 `Port already in use`，进程退出 |
| 排查 | `ss -lntp` 查端口占用；`telnet <nacos> 8848`；检查 `config/<service>-<profile>.yml` 的 Nacos 地址与账号 |
| 处理 | 释放端口或换端口；确认 Nacos 可达后重启；确认 9848（gRPC）端口放行 |

**P1-2 服务反复重启 / 健康检查失败被摘除**

| 现象 | 容器一直 `Restarting`，或实例从 Nacos 下线 |
| 可能原因 | OOM（堆内存不足）、启动依赖（MySQL/Redis）未就绪、配置错误 |
| 排查 | `docker logs <svc> --tail 100`；`jmap -heap` 看堆；检查启动期依赖 |
| 处理 | 调大 `-Xmx`/容器内存上限；按依赖顺序重启；修正配置 |

**P1-3 登录后偶发 401（token 校验失败）**

| 现象 | 部分请求 401，重新登录恢复 |
| 可能原因 | Redis 中 token 被驱逐（内存策略）、多实例时钟不同步导致 JWT 过期判断偏差、网关与 auth 密钥不一致 |
| 排查 | `redis-cli TTL login_tokens:<uuid>`；比对多机 `date`；核对 `config/` 中 JWT 密钥配置 |
| 处理 | 统一 NTP 时钟同步；调大 Redis `maxmemory`；确保密钥一致（环境变量注入） |

**P1-4 Seata 分布式事务回滚异常**

| 现象 | 业务日志 `Global transaction rollback failed`，跨服务数据不一致 |
| 排查 | `docker logs seata-server --tail 100`；确认 seata 服务端与客户端 `tx-service-group`、registry 配置一致；`seata/logs/seata-server.log` 中查找 xid |
| 处理 | 手工核对并修正不一致数据（评审后执行补偿 SQL）；恢复 Seata 后重试事务；确认 AT 模式需要 undo_log 表存在 |

**P1-5 MinIO 上传失败 / 磁盘满**

| 现象 | 文件服务报上传异常，MinIO 控制台容量告警 |
| 排查 | `mc admin info local`；`docker logs minio`；检查 bucket 权限（policy）与 AK/SK |
| 处理 | 扩容/清理旧文件（对象生命周期策略）；修正 bucket 策略与访问密钥后重试 |

### 7.4 P2 案例

**P2-1 Nacos 配置修改后不生效**

| 现象 | 改了配置，服务行为未变化 |
| 可能原因 | 未点「发布」、dataId 与服务名不一致、配置未 `@RefreshScope`、客户端缓存 |
| 排查 | 控制台确认发布成功；核对 `NACOS_DATA_ID` 与 `spring.application.name`；`curl :8848/nacos/v1/cs/configs?dataId=...&group=...` 看实际内容 |
| 处理 | 重新发布；给需要热更新的 Bean 加 `@RefreshScope`；必要时重启服务 |

**P2-2 Sentinel 限流误伤**

| 现象 | 正常流量被 `Blocked by Sentinel` 拒绝 |
| 排查 | Sentinel 控制台（:8858）查看实时监控与规则；检查阈值/热点参数配置；看网关与业务侧是否双重限流 |
| 处理 | 调高阈值或删除误配规则；按压测数据重新评估 QPS 基线 |

**P2-3 定时任务不执行 / 重复执行**

| 现象 | job 服务正常但任务未跑，或同一任务多实例同时执行 |
| 排查 | `docker logs ruoyi-job --tail 100`；确认任务开关与 cron 表达式；多实例部署时检查 Quartz 集群锁表（`QRTZ_LOCKS`）是否异常 |
| 处理 | 修正 cron/开关；任务必须幂等（支持重复触发）；必要时单实例部署 job 服务 |

**P2-4 工作流实例卡住**

| 现象 | 审批流程停在某节点不再推进 |
| 排查 | Flowable 异步执行器日志（`ruoyi-workflow`）；检查 ACT_RU_JOB 表是否有遗留 job；确认任务节点的办理人/组存在 |
| 处理 | 手动推进或重置任务需评审；确认 Flowable 7 异步执行器配置（`async-executor-activate`）正常 |

**P2-5 文件上传成功但预览/下载失败**

| 现象 | 上传 OK，访问 URL 403 或 Content-Type 错误 |
| 排查 | bucket 匿名/签名策略；MinIO 控制台看对象 metadata；检查文件服务返回的 URL 域名是否正确 |
| 处理 | 调整 bucket policy 或改为预签名 URL；修正返回地址 |

### 7.5 P3 案例

**P3-1 慢 SQL 与索引缺失**

```bash
# 打开慢查询日志，阈值 1s
docker exec mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "SET GLOBAL slow_query_log=ON; SET GLOBAL long_query_time=1;"
# 查看执行计划
docker exec mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" ry-cloud -e "EXPLAIN SELECT ...;"
```

**P3-2 日志噪音 / 告警误报**：收敛重复告警、按级别分级通知、定期校准告警阈值（见 §8）。

---

## 8. 值班与告警

### 8.1 值班职责

- 监控看板巡检（服务健康、注册实例数、中间件状态、磁盘水位）。
- 告警响应与分级处置（§7.1），记录处理过程与结论。
- 交接班：填写值班记录（时间、事件、处理、遗留事项、知识沉淀）。

### 8.2 告警接入

| 渠道 | 用途 | 说明 |
|------|------|------|
| 钉钉/企微群机器人 Webhook | 实时告警 | P0/P1 必达 |
| 邮件（SMTP） | 汇总告警 | 日报/周报、P2/P3 |
| 监控面板（Spring Boot Admin :9100 / Prometheus+Grafana，按需） | 趋势与巡检 | 对接 actuator 指标 |

### 8.3 建议告警规则

| 指标 | 阈值 | 级别 |
|------|------|------|
| 服务健康检查连续失败 | ≥ 3 次 | P0 |
| 服务实例数 < 预期副本数 | 立即 | P1 |
| CPU / 内存使用率 | > 85% 持续 5 分钟 | P1 |
| 磁盘使用率 | > 80% 预警，> 90% 告警 | P1 |
| Sentinel 拒绝率 | 突增（与基线对比） | P2 |
| 慢 SQL（> 1s）数量 | 突增 | P3 |
| 备份失败 / 备份文件缺失 | 立即 | P1 |

### 8.4 事件升级路径

```
值班工程师 →（15 分钟未缓解）→ 技术负责人 →（1 小时未缓解）→ 部门负责人 / 管理层
```

升级时同步：故障级别、影响面、已做处理、需要的资源（数据库账号、云控制台权限等）。

---

## 9. 安全巡检

### 9.1 巡检清单

| 频率 | 检查项 | 要求 |
|------|--------|------|
| 每日 | 备份任务执行与文件大小 | 备份成功且非空 |
| 每日 | 中间件与业务服务健康 | 全绿 |
| 每周 | 弱口令/默认口令 | 禁止 `nacos/nacos`、`minioadmin/minioadmin`、空密码 |
| 每周 | 端口暴露面 | 仅 8080 及必要运维端口对外，中间件端口（8848/6379/3306/9000/8091/8858）不暴露公网 |
| 每周 | 日志脱敏抽查 | 手机号/身份证/银行卡无明文 |
| 每月 | 依赖漏洞扫描（trivy 等） | 高危 CVE 限期修复 |
| 每月 | 备份恢复演练 | 可完整恢复 |
| 每季 | 证书有效期 | TLS 证书、MinIO/Sentinel 等自签证书 |

### 9.2 配置与凭证红线

- 数据库密码、Redis 密码、JWT 密钥、Nacos 账号一律用环境变量/`local-env.yml`（已 gitignore），**禁止硬编码进 `config/` 或代码提交**。
- Nacos 配置中的敏感项用占位符 `${...}`，由环境注入。
- 巡检时检查 `git log`/代码扫描，防止凭证被误提交。

---

## 10. 升级与回滚

### 10.1 升级前准备（Checklist）

- [ ] 发布说明（按[发布说明模板](release-notes-template.md)填写）评审通过。
- [ ] 数据库备份完成（§6），SQL 迁移脚本在 staging 演练过。
- [ ] Nacos 配置变更项导出留档。
- [ ] 当前版本镜像/JAR 留存（回滚用）。
- [ ] 灰度窗口与回滚决策人确认。

### 10.2 标准升级流程

1. **构建**：`mvn -pl <module> -am clean package -DskipTests`（或 CI 出镜像，打 `release/<ver>` tag）。
2. **SQL 迁移**：按顺序执行 `sql/` 迁移脚本（只前向，版本递增）。
3. **配置更新**：Nacos 发布新配置（或更新环境变量）。
4. **灰度**：先升级 1 个实例，冒烟（登录、核心链路）通过后全量滚动。
5. **全量**：逐实例滚动重启，每批确认健康检查与注册成功后继续。
6. **验证**：按发布说明「验证清单」逐项确认。

> 多实例升级注意滚动窗口：`docker compose up -d --no-deps <svc>` 会重建并滚动重启该服务实例。

### 10.3 回滚策略

| 回滚对象 | 方式 | 注意 |
|----------|------|------|
| 代码/镜像 | 回退到上一版本镜像/JAR，滚动重启 | 最常用、最安全 |
| 配置 | 恢复 Nacos 上一版本配置（发布记录留档） | 确认 `@RefreshScope` 热更新或需重启 |
| 数据 | **只前向，禁止回滚 SQL** | 数据回退需人工补偿脚本 + 评审，禁止直接执行反向 DDL |
| 混合 | 代码回退 + 数据兼容 | 新代码引入的数据结构需保证旧代码可读（向后兼容） |

**回滚决策点**：灰度验证未过 / 全量后 P0-P1 故障且 30 分钟内无法修复 → 启动回滚，回滚后按 §3 健康检查与 §7 排查根因。

---

## 11. Docker Compose 常用指令

> 项目 Compose 文件位于 `docker/docker-compose.yml`，以下命令均以 `-f docker/docker-compose.yml` 指定；Compose v2 使用 `docker compose`（空格），旧版为 `docker-compose`（连字符）。

| 操作 | 命令 | 说明 |
|------|------|------|
| 启动全部 | `docker compose -f docker/docker-compose.yml up -d` | 后台拉起，按依赖顺序 |
| 启动指定服务 | `docker compose -f docker/docker-compose.yml up -d nacos mysql redis` | 常用作中间件 |
| 查看状态 | `docker compose -f docker/docker-compose.yml ps` | STATUS 异常需关注 |
| 查看日志 | `docker compose -f docker/docker-compose.yml logs -f --tail 100 ruoyi-system` | 实时跟踪 |
| 进入容器 | `docker compose -f docker/docker-compose.yml exec mysql bash` | 调试 |
| 重启服务 | `docker compose -f docker/docker-compose.yml restart ruoyi-system` | 应用配置 |
| 停止服务 | `docker compose -f docker/docker-compose.yml stop ruoyi-system` | 保留容器 |
| 移除容器 | `docker compose -f docker/docker-compose.yml down` | 保留数据卷 |
| **危险**：移除容器+数据卷 | `docker compose -f docker/docker-compose.yml down -v` | **数据不可恢复，禁止用于生产** |
| 拉取新镜像 | `docker compose -f docker/docker-compose.yml pull` | 升级前置 |
| 重新构建 | `docker compose -f docker/docker-compose.yml build <svc>` | 本地镜像变更 |
| 校验配置 | `docker compose -f docker/docker-compose.yml config` | 语法/合并检查 |
| 扩缩容 | `docker compose -f docker/docker-compose.yml up -d --scale ruoyi-system=2` | 多实例（需服务支持） |
| 资源查看 | `docker stats` | CPU/内存实时监控 |

---

## 附：快速定位索引

| 遇到问题 | 先看这里 |
|----------|----------|
| 服务起不来 | §5.1、§7.3 P1-1 |
| 请求 502/401 | §7.2 P0-1、§7.3 P1-3 |
| 数据丢了/要恢复 | §6 |
| 要发版/回滚 | §10 + [发布说明模板](release-notes-template.md) |
| 不知道端口/服务名 | §1.2、§1.3 |
