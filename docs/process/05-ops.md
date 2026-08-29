# 运维阶段（Operations & Maintenance）

> 本文档定义「运维（Ops）」阶段的持续活动：**监控体系、日志规范与采集、告警规则与响应、故障等级与升级、备份与恢复、容量与安全巡检、SLA、日常变更与值班、持续优化闭环**，并附**运维检查清单**。
> 上一阶段：[04-release.md](04-release.md) ｜ 配套：[00-overview.md](00-overview.md) · [../ops/ops-handbook.md](../ops/ops-handbook.md) · [../architecture/00-architecture-overview.md](../architecture/00-architecture-overview.md)

---

## 1. 阶段目标与出口条件（Gate G5）

运维阶段的出口条件是**持续**的，不是一次性事件：

| # | 验收项 | 判定 |
|---|--------|------|
| G5-1 | 监控看板覆盖全部服务（健康/资源/错误率/业务指标） | 每周核对 |
| G5-2 | 告警规则有效，P0/P1 均能在时限内响应（见 §4） | 每月核对 |
| G5-3 | 备份任务按计划执行，月度恢复演练通过 | 每月 |
| G5-4 | 无未闭环的 P0/P1 故障；故障复盘均有 action 落地 | 持续 |
| G5-5 | SLA 达标（见 §7） | 月度统计 |

---

## 2. 监控体系

### 2.1 分层监控

| 层次 | 关注点 | 工具/手段 |
|------|--------|-----------|
| 基础设施 | 主机 CPU/内存/磁盘/网络、MySQL/Redis/Nacos/MinIO 实例状态 | Prometheus node_exporter + 中间件 exporter |
| 应用 | 健康、JVM（堆/GC/线程）、HTTP 指标、Feign/Sentinel/Seata | Spring Boot Actuator + Micrometer |
| 业务 | 登录成功率、审批单量、文件上传量、接口 P95 | 业务指标埋点（Micrometer 自定义指标） |
| 用户体验 | 前端错误率、首屏耗时 | 前端埋点 + 日志 |

### 2.2 Actuator 约定

所有服务默认开启 actuator（见 [../architecture/00-architecture-overview.md](../architecture/00-architecture-overview.md)）：

```yaml
# 每个服务 application.yml 公共部分
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always        # 含 db/redis/nacos 组件健康
  metrics:
    tags:
      application: ${spring.application.name}
```

检查点：`curl -fsS http://localhost:9201/actuator/health` 返回 `"status":"UP"`。

### 2.3 监控平台思路

- **Spring Boot Admin（ruoyi-monitor [9100]）**：面向开发者的服务健康聚合面板，快速定位"哪个服务挂了"。
- **Prometheus + Grafana（生产标配）**：`/actuator/prometheus` 暴露指标 → Prometheus 抓取 → Grafana 看板与告警。
- 推荐看板指标集：服务可用性、JVM 堆/GC 次数、HTTP 错误率与 P95、数据库连接池、Redis 命中率、Sentinel 被限流 QPS。

```yaml
# prometheus.yml 抓取示例
scrape_configs:
  - job_name: ruoyi-services
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: [gateway:8080, auth:9200, system:9201, gen:9202, job:9203, workflow:9210, file:9300]
```

---

## 3. 日志规范与采集

### 3.1 日志规范（logback）

- 每行 JSON 结构：`时间戳 | traceId | 服务名 | 级别 | 线程 | 类 | 消息`。
- **traceId 贯穿全链路**：网关生成 `X-Trace-Id`，经 Feign/日志 MDC 透传，故障时按 traceId 串联调用链。
- 禁止打印敏感信息（密码/令牌/身份证等，见 AGENTS.md §8）；脱敏走 `ruoyi-common-sensitive`。

```xml
<!-- logback-spring.xml 片段 -->
<pattern>{"ts":"%d{yyyy-MM-dd HH:mm:ss.SSS}","traceId":"%X{traceId}","app":"%{APP_NAME}","level":"%level","thread":"%thread","logger":"%logger{36}","msg":"%msg"}%n</pattern>
```

### 3.2 日志采集与存储

- 采集：Filebeat（或 Fluent Bit）→ Kafka（可选缓冲）→ **ELK / Loki**，统一检索。
- 保留策略：生产全量日志 30 天，错误日志 90 天，审计日志 180 天（合规要求另定）。
- 关键查询：按 `traceId`、按服务+级别、按错误关键字（`Exception|ERROR|Seata|Timeout`）。

---

## 4. 告警规则与响应

### 4.1 告警规则示例

| 规则 | 表达式（PromQL 示例） | 级别 | 说明 |
|------|------------------------|------|------|
| 服务不可用 | `up{job="ruoyi-services"} == 0` 持续 1m | P0 | 立即响应 |
| 错误率突增 | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m])) > 0.05` | P1 | 5% 错误率 |
| P95 劣化 | `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 1.5` | P1 | >1.5s |
| JVM 堆高 | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85` | P2 | 持续 10m |
| 磁盘不足 | `node_filesystem_avail_bytes / node_filesystem_size_bytes < 0.1` | P1 | <10% |
| 慢 SQL | 慢查询日志条数 > 阈值 | P2 | DB 巡检 |

### 4.2 告警通道与收敛

- 通道：钉钉/企微机器人 → 值班群；P0 追加电话/短信。
- 收敛：同一规则 30 分钟内不重复轰炸（静默/聚合），恢复后自动 `resolve`。
- 告警必带：服务名、实例、指标值、时间、traceId 示例、值班人。

---

## 5. 故障等级与升级

| 等级 | 定义 | 响应时限 | 升级路径 |
|------|------|----------|----------|
| P0 | 全部/核心服务不可用、数据丢失、安全事件 | 15 min 响应，1 h 恢复 | 立即拉群 → 值班负责人 → CTO/运维负责人 |
| P1 | 核心功能部分不可用、错误率 >5% | 30 min 响应，4 h 恢复 | 值班负责人 → 相关服务 Owner |
| P2 | 非核心功能受损、性能劣化 | 4 h 响应，24 h 处理 | 服务 Owner 排期 |
| P3 | 一般问题、体验优化 | 下一迭代 | 记录进 issue |

升级原则：**不隐藏、不等待**——超时未解决必须升级，由更高层协调资源；故障处理全程记录时间线（见 [04-release.md](04-release.md) §9 复盘模板）。

---

## 6. 备份与恢复

| 对象 | 备份方式 | 频率 | 保留 | 恢复演练 |
|------|----------|------|------|----------|
| MySQL（ry-cloud / ry-flowable） | mysqldump 全量 + binlog 增量（或 xtrabackup） | 全量每日、增量实时 | 全量 14 天、增量 7 天 | 每月一次恢复到演练库 |
| Nacos 配置 | 配置导出/快照（含历史版本） | 每次变更后 | 永久 | 随发布演练 |
| MinIO 对象存储 | 版本化 + 异地/跨桶复制 | 实时 | 按桶策略 | 每季度 |
| Redis | RDB/AOF 持久化 + 定期备份 | 每日 | 7 天 | 每季度 |
| 镜像/制品 | 私有仓库 + tag 保留策略 | 每次发布 | 保留最近 20 个版本 | — |

```bash
# MySQL 全量备份示例（生产窗口外执行）
mysqldump -h mysql -uroot -p"${MYSQL_PASSWORD}" \
  --single-transaction --routines --triggers ry-cloud \
  | gzip > /backup/ry-cloud-$(date +%F-%H%M).sql.gz

# 恢复演练（到演练库，绝不直接覆盖生产）
mysql -h restore-host -uroot -p"${MYSQL_PASSWORD}" ry-cloud-restore < ry-cloud-YYYY-MM-DD.sql
```

> 备份原则：**3-2-1**（3 份副本、2 种介质、1 份异地）。恢复演练必须有记录，演练失败视为 P1。

---

## 7. 容量与安全巡检

### 7.1 容量巡检

- 每两周：磁盘水位、连接池使用率、Redis 内存、Nacos 压力、QPS 峰值与容量余量。
- 扩缩容依据：指标趋势 + 活动日历（大促/月底结算），提前 1 周规划。

### 7.2 安全巡检

- 依赖漏洞：`mvn dependency-check` / Dependabot 每周扫描。
- 凭证轮换：数据库密码、JWT 密钥、MinIO 密钥每季度轮换（环境变量注入，禁止入库）。
- 访问审计：网关访问日志、操作日志（`@Log`）抽查；异常登录/越权行为监控。
- 基线检查：中间件默认端口/弱口令、Nacos 控制台访问控制、容器镜像扫描。

---

## 8. SLA 定义

| 指标 | 定义 | 目标 |
|------|------|------|
| 可用性 | （总分钟 − 故障分钟）/ 总分钟 × 100%（按服务、按月） | ≥ 99.9% |
| 平均恢复时间 MTTR | 故障从发现到恢复的平均时长 | P0 ≤ 1 h |
| 故障间隔 MTBF | 两次故障间隔 | 持续提升 |
| 告警响应率 | 时限内响应的告警占比 | 100% |

月度统计在 [../ops/ops-handbook.md](../ops/ops-handbook.md) 中登记，未达标项进入改进闭环。

---

## 9. 日常变更与值班

- **变更窗口**：常规变更固定在发布窗口；窗口外变更需紧急审批（见 [04-release.md](04-release.md) §5）。
- **值班**：每日轮值（On-call），值班人持有：告警群权限、回滚手册、备份账号、应急通讯录。
- **值班交接**：未闭环故障、待观察变更、容量风险逐项交接并留痕。
- 每次生产变更必须关联发布单/工单，变更记录可追溯。

---

## 10. 持续优化闭环（PDCA）

```
监控发现 (P) → 定位根因 (D) → 修复/优化并验证 (C) → 沉淀为规则/文档/自动化 (A)
```

- 每次故障、每次容量告警、每次安全扫描发现，都必须产出 action 并跟踪闭环。
- 优化方向：告警误报率下降、MTTR 下降、巡检自动化（脚本/流水线代替手工）、容量水位预判。

---

## 11. 运维检查清单

### 11.1 每日（值班）

- [ ] 告警群无未处理 P0/P1；P2 已登记跟进
- [ ] 核心服务健康检查全绿（网关/认证/系统/工作流）
- [ ] 备份任务执行成功（MySQL/Nacos/MinIO）

### 11.2 每周

- [ ] 磁盘/内存/连接池水位巡检，异常项登记
- [ ] 慢 SQL 与错误日志抽查（Top N）
- [ ] 变更记录核对：本周发布与发布单一一对应

### 11.3 每月

- [ ] 恢复演练（MySQL 至少）通过并留档
- [ ] SLA 统计与复盘；未达标项制定改进 action
- [ ] 凭证轮换计划执行；依赖漏洞扫描结果处理
- [ ] 告警规则有效性复核（清理无效/重复规则）

### 11.4 每季度

- [ ] MinIO 异地恢复演练；Redis 恢复演练
- [ ] 安全基线复查（Nacos/网关/镜像）
- [ ] 容量规划评审（下季度资源预算）

---

## 12. 与相邻文档的关系

| 文档 | 与本阶段的关系 |
|------|----------------|
| [00-overview.md](00-overview.md) | Gate G5 定义与阶段闸门总览 |
| [04-release.md](04-release.md) | 发布/回滚操作进入运维日常；故障复盘联动 |
| [../ops/ops-handbook.md](../ops/ops-handbook.md) | 运维操作手册：命令、脚本、应急 SOP（本文件的执行细节） |
| [../architecture/00-architecture-overview.md](../architecture/00-architecture-overview.md) | 服务端口、健康检查、依赖关系（监控对象） |
| [../architecture/architecture-robustness.md](../architecture/architecture-robustness.md) | 容错/降级设计（故障时的保护手段） |
