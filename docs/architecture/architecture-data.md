# 数据与存储设计（Data & Storage Architecture）

> 本文档定义数据层的全部规范：数据库拆分与库表规范、主从与分库分表演进、Redis 使用与缓存一致性、MinIO 对象存储、事务边界、备份迁移与慢 SQL 治理。
> 配套文档：[总体架构](00-architecture-overview.md) · [安全架构设计](architecture-security.md) · [健壮性设计](architecture-robustness.md) · [扩展性设计](architecture-scalability.md) · [研发全流程](../process/00-overview.md)。

---

## 1. 目标与原则

- **数据独立**：每个服务独享数据库，禁止跨服务直连他人库；跨库一致性用 Seata/消息（见 [健壮性设计](architecture-robustness.md)）。
- **规范先行**：表结构、命名、索引、脚本统一规范，可评审、可审计、可迁移。
- **性能可预期**：索引设计纳入评审，慢 SQL 有治理闭环。
- **数据可恢复**：备份、迁移、回滚策略明确，RPO/RTO 达标。

---

## 2. 数据库拆分与库表规范

### 2.1 一服务一库

| 服务 | 数据库 | 说明 |
|------|--------|------|
| ruoyi-auth / system / gen / job | `ry-cloud` | 系统公共库（用户/角色/菜单/字典/日志） |
| ruoyi-workflow | `ry-flowable` | Flowable 引擎独立库 |
| ruoyi-file | `ry-cloud`（元数据） + MinIO | 文件内容在对象存储 |
| 新增业务模块 | `ry-<module>` | 独立库，见 [扩展性设计](architecture-scalability.md) §11 |

> 库名、连接信息以 [agent/mcp/mcp.json](../../agent/mcp/mcp.json) 为准（`mysql-ry` / `mysql-flowable`，查询一律只读，见 [AGENTS.md](../../AGENTS.md) 第 3 节）。

### 2.2 拆库原则

- 先按业务域拆库，再考虑库内分表；拆库成本高于拆表，谨慎决策。
- 跨库关联禁止 JOIN，改为：冗余字段 / 应用层聚合 / 接口聚合（网关编排）。
- 只读聚合报表可引入**只读从库**或数仓层，不侵入业务主库。

---

## 3. 表设计规范

### 3.1 必备字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `bigint unsigned` | 主键，雪花/号段生成，**不用自增主键做业务主键** |
| `create_by` / `create_time` | `varchar(64)` / `datetime` | 创建人/创建时间 |
| `update_by` / `update_time` | 同左 | 更新人/更新时间 |
| `del_flag` | `char(1)` 默认 `'0'` | **逻辑删除**（`0` 正常 / `1` 删除），统一由框架处理 |
| `version` | `int` 默认 `0` | **乐观锁**版本号（状态流转/金钱相关必加） |
| `remark` | `varchar(500)` | 备注（可选） |

### 3.2 命名与类型规范

- 表名 `sys_`/`biz_` 前缀 + 小写下划线；字段全小写下划线，禁止驼峰入库。
- 字符集统一 `utf8mb4`，排序规则 `utf8mb4_general_ci`（表情/生僻字安全）。
- 金额用 `decimal(18,2)`（或 `decimal(18,4)` 中间计算），**禁止 float/double**；数量用 `int unsigned`。
- 时间统一 `datetime`（UTC+8），禁止字符串存时间；状态用 `tinyint/char` + 注释或字典，禁止魔法数字。
- 每张表必须有 `COMMENT` 注释；字段必须有注释（与 DTO 注释对应，见 [AGENTS.md](../../AGENTS.md) 4.3）。
- 逻辑删除与唯一索引冲突处理：唯一索引冗余删除标记列（如 `del_flag` + 删除时间戳）或唯一键含 `del_flag`。

### 3.3 索引规范

- 命名：`idx_<表>_<列>`（普通）、`uk_<表>_<列>`（唯一）。
- 每张表唯一键 ≤ 3 个；单表索引总数 ≤ 8，避免冗余索引。
- 复合索引遵循**最左前缀**，区分度高的列在前；禁止对长文本/大字段建索引（用前缀索引或改为冗余短列）。

---

## 4. 主从与分库分表演进

### 4.1 演进路径

```
单库单表 ──► 读写分离(1主N从) ──► 垂直拆分(按业务域分库) ──► 水平分片(分表/分库分表)
```

| 阶段 | 触发信号 | 方案 |
|------|----------|------|
| 读写分离 | 读 QPS 高、写压力可控 | `ruoyi-common-datasource`（dynamic-datasource）主从路由，写走主、读走从 |
| 垂直拆分 | 业务域膨胀、单库连接吃紧 | 按服务拆库（§2），跨库交互走接口/消息 |
| 水平分片 | 单表数据量 > 千万级 | ShardingSphere/MyCat，按业务键（用户 ID/订单号）分片 |

### 4.2 注意事项

- 读写分离有**主从延迟**：刚写入即读的场景（登录后取用户信息）强制走主库或缓存。
- 分片键一旦选定不可随意更改；跨片事务与聚合查询成本高，提前设计。
- 分片演进必须与 [扩展性设计](architecture-scalability.md) §7 容量估算联动，避免过早分片增加复杂度。

---

## 5. Redis 使用规范与 Key 设计

### 5.1 Key 命名

```
格式: <服务>:<业务域>:<对象>:<标识>[:子键]
示例: system:user:info:1001
      auth:captcha:uuid:xxxx
      idempotent:order:pay:SO20240601xxxx
      rate:login:user:1001
```

- 一律小写、冒号分段、语义清晰；禁止无前缀裸 key 与超长 key（> 128 字符）。
- 统一通过 `ruoyi-common-redis` 的 `RedisService` 访问，禁止各模块自行 new 连接。
- 所有 key 必须设置 TTL（会话/验证码/幂等键按业务窗口），防止内存泄漏。

### 5.2 数据类型选择

| 场景 | 类型 |
|------|------|
| 会话/Token、验证码 | String（短 TTL） |
| 用户权限集合、去重 | Set |
| 排行榜/时间线 | ZSet |
| 队列/延迟任务 | List / Stream |
| 计数 | INCR/DECR + 过期 |

### 5.3 防滥用

- 禁止用 Redis 存大对象（> 1MB）与长列表（> 万级）——换 DB/MinIO。
- 禁止 `KEYS *` 全量扫描（用 SCAN）；批量操作用 Pipeline/Lua 减少 RTT。
- 热点 key 加随机后缀分散（防雪崩，见 [健壮性设计](architecture-robustness.md) §9）。

---

## 6. 缓存与 DB 一致性

### 6.1 更新策略（推荐：Cache Aside + 删缓存）

```
写: 更新 DB → 删除缓存（或延迟双删）
读: 先查缓存 → 未命中查 DB → 回填缓存（TTL + 随机抖动）
```

### 6.2 一致性保障

- **删缓存而非更新缓存**：避免并发写导致缓存脏值。
- **延迟双删**（高并发场景）：更新 DB → 删缓存 → 延迟 500ms 再删一次，兜住读回填竞态。
- **先写 DB 后删缓存**，删除失败走重试/监听 binlog 补偿（如 Canal 同步）。
- 一致性要求高（金额、状态机）的数据**不缓存或只做短 TTL 缓存**，以 DB 为准。
- 缓存与 DB 不一致的修复：短 TTL 自愈 + 定期对账任务。

---

## 7. MinIO 对象存储目录与命名

### 7.1 桶与目录

```
bucket: ruoyi
  avatar/     头像
  file/       通用文件
  export/     导出文件（限时清理）
  import/     导入模板
  workflow/   流程附件
```

- 目录按业务域划分，禁止根目录散落文件。
- 桶策略：私有桶 + 预签名 URL（限时访问）；公开桶仅限无敏感静态资源。

### 7.2 对象命名

```
格式: <业务域>/<yyyyMM>/<yyyyMMddHHmmss>-<随机串>.<ext>
示例: export/202406/20240601103000-8f3a1c2d.xlsx
```

- 对象名**禁止使用业务主键/用户敏感信息**（防枚举泄露），一律时间 + 随机串。
- 文件元数据（原名、大小、MD5、归属、上传人）落库（`sys_file_info` 类表），MinIO 只存内容。
- 上传分片、临时文件按 TTL 清理；导出文件定期归档/删除（定时任务）。

---

## 8. 事务边界

- **本地事务**：单库操作用 `@Transactional`；事务内**禁止**远程调用（Feign/HTTP）、外部等待、大循环写——避免长事务锁表与连接占用。
- **只读操作不加事务**；查询走 `@Transactional(readOnly = true)` 仅用于多查询一致性场景。
- 跨服务强一致走 Seata AT（`ruoyi-common-seata`），弱一致走「本地事务 + 消息/定时补偿」（见 [健壮性设计](architecture-robustness.md) §5）。
- 事务超时显式配置（默认 30s 内），事务方法与自调用问题（自调用不走代理）通过拆分 Service 规避。
- 涉及金钱/库存的并发更新使用乐观锁或 `FOR UPDATE`（短事务），并校验影响行数。

---

## 9. 数据备份与迁移

| 项 | 策略 |
|----|------|
| 备份频率 | 生产库每日全量（凌晨）+ 每 5 分钟 binlog 增量；MinIO 每日快照 |
| 保留周期 | 全量 30 天，增量 7 天（按合规调整） |
| 恢复演练 | 每季度演练一次恢复，验证 RPO ≤ 5 分钟、RTO ≤ 30 分钟 |
| 迁移 | 结构变更走 `sql/` 迁移脚本（向前兼容）；数据迁移写一次性脚本 + 校验对账 |
| 回滚 | 发布前快照，回滚用「反向脚本」或「恢复快照」，禁止手动改生产数据 |

> 备份与告警落地见 `docs/ops/` 运维文档；MCP 查询遵守只读红线（[AGENTS.md](../../AGENTS.md) 第 3 节）。

---

## 10. sql/ 迁移脚本规范

- 脚本命名：`<version>__<desc>.sql`，如 `20240601__add_biz_order.sql`；只允许**向前迁移**（Flyway 语义），禁止回退删除。
- 每个脚本包含：目的说明（文件头注释）、变更内容、影响面；DDL 与 DML 分开文件（或同一脚本分段）。
- 幂等写法：`CREATE TABLE IF NOT EXISTS`、`INSERT ... ON DUPLICATE KEY`、先查后改。
- 新增字段**不允许** `NOT NULL` 无默认值（存量数据报错）；先加可空字段 + 回填 + 再收紧。
- 脚本提交前必须经过 MySQL 语法校验（`yq`/客户端），并记录到发布单（见 [研发全流程](../process/00-overview.md)）。

---

## 11. 索引与慢 SQL 治理

### 11.1 治理闭环

```
慢SQL日志(阈值 500ms) ──► 采集分析 ──► 定位(EXPLAIN) ──► 优化(索引/改写/缓存) ──► 复测 ──► 回归纳入监控
```

- 开启慢查询日志（`long_query_time = 0.5`），每日巡检 TOP N 慢 SQL。
- 优化三板斧：`EXPLAIN` 看 `type`（至少 `ref`，避免 `ALL` 全表扫描）→ 补索引 → 改查询（避免 `SELECT *`、函数包裹索引列、隐式类型转换）。
- 大表分页 `LIMIT offset` 深翻页优化：游标/子查询定位（`WHERE id > ? ORDER BY id LIMIT n`）。
- 联表超过 3 张、子查询嵌套过深的重构为应用层聚合；高频只读查询用缓存承接（§5/§6）。
- 治理结果纳入模块文档与发布单，指标（慢 SQL 数、平均耗时）上监控看板。

### 11.2 检查清单（数据层）

- [ ] 每个服务独享库，无跨库 JOIN 与跨服务直连
- [ ] 新表含规范字段（id/时间/逻辑删除/乐观锁），命名与注释完整
- [ ] 金额 decimal、时间 datetime、字符集 utf8mb4，索引命名规范且不冗余
- [ ] Redis key 规范 + TTL 全覆盖，无大 key/无 `KEYS *`
- [ ] 缓存更新采用「删缓存 + 延迟双删」，对账任务在跑
- [ ] MinIO 目录/对象命名规范，私有桶 + 预签名，元数据落库
- [ ] sql/ 迁移脚本版本化、幂等、向前兼容，已语法校验
- [ ] 慢 SQL 治理闭环运行，TOP N 有记录有整改

---

## 12. 相关文档

| 文档 | 主题 |
|------|------|
| [00-architecture-overview.md](00-architecture-overview.md) | 总体架构、基础设施 |
| [architecture-security.md](architecture-security.md) | 数据加密/脱敏/备份安全 |
| [architecture-robustness.md](architecture-robustness.md) | 事务/缓存三防/幂等 |
| [architecture-scalability.md](architecture-scalability.md) | 分库分表演进/容量估算 |
| [../process/00-overview.md](../process/00-overview.md) | 研发全流程（SQL 管理在 02/04 阶段） |
