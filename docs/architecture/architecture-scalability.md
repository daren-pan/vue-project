# 扩展性 / 可伸缩设计（Scalability & Extensibility）

> 本文档定义系统如何**演进**（新增模块/拆服务）、如何**横扩**（无状态化、水平扩展、缓存分层、限流配额）以及代码与工程层面的**可扩展原则**，并给出新增业务模块的完整步骤清单。
> 配套文档：[总体架构](00-architecture-overview.md) · [安全架构设计](architecture-security.md) · [健壮性设计](architecture-robustness.md) · [数据与存储设计](architecture-data.md) · [研发全流程](../process/00-overview.md)。

---

## 1. 目标与原则

- **演进式架构**：微服务边界可按业务域演进，不推翻重来。
- **无状态优先**：任何实例可被替换/重启/扩缩容，状态只存在于 Redis/DB。
- **扩展点显式化**：通过接口/策略/SPI 扩展，而非修改核心代码（开闭原则）。
- **容量可预估**：每个服务/接口有容量基线，扩展有依据、有配额。

---

## 2. 微服务划分与演进

### 2.1 划分原则（高内聚、低耦合）

| 维度 | 判定 |
|------|------|
| 业务域 | 单一职责：一个服务只承载一个业务域（用户/订单/审批…） |
| 数据独立 | 一个服务独享一个库（见 [数据与存储设计](architecture-data.md)） |
| 调用频率 | 高频调用域独立成服务，避免牵一发动全身 |
| 团队边界 | 一个服务一个团队可独立发布（对应一个 Git 仓库/目录） |
| 变更频率 | 变更频繁的模块独立，降低回归面 |

### 2.2 从「模块」演进为「服务」

1. 先在 `ruoyi-modules` 下按**模块**落地（共享基础设施与部署），验证业务边界；
2. 模块稳定后，若出现独立扩缩容/独立发布/独立团队诉求，再抽取为**独立服务**（新 Nacos 服务名 + 新库 + 新端口）。
3. 演进时接口契约（`ruoyi-api` 的 Feign 接口）先行冻结，调用方先依赖契约，再迁移实现。

### 2.3 拆分新模块的文档要求

- 新增/拆分必须产出：设计文档（接口契约、数据模型、时序）、模块文档（`docs/modules/<module>.md`）、Nacos 配置模板、SQL 迁移脚本（见 [研发全流程](../process/00-overview.md) 通用交付物清单）。
- 同步更新 `README.md` 模块清单、`docs/architecture/00-architecture-overview.md` 的模块职责矩阵。

---

## 3. 无状态服务化

- **会话外置**：登录态/JWT 会话放 Redis，实例重启不丢登录。
- **本地无状态**：禁止将业务数据写入实例本地磁盘/内存（临时文件用 MinIO，任务状态用 DB）。
- **配置外置**：配置走 Nacos，环境差异走 profile + 环境变量（见 [总体架构](00-architecture-overview.md) 第 6 节）。
- **可水平替换**：任一实例被杀掉不影响整体（配合健康检查 `actuator/health` 与滚动更新）。

---

## 4. 水平扩展

### 4.1 分层扩展

| 层 | 扩展手段 | 要点 |
|----|----------|------|
| 网关 | 多实例 + LB | 无状态，可多副本；限流配额按实例聚合（见 §6） |
| 业务服务 | 多实例水平扩容 | 无状态保证扩容安全；基于 Nacos 负载均衡自动分发 |
| 定时任务 | 分布式锁（Redis） | 多实例下同一任务仅一个实例执行（`ruoyi-job`） |
| 数据库 | 读写分离 → 分库分表 | 读多写少先上从库；单表超千万行再分片（见 [数据与存储设计](architecture-data.md)） |
| Redis | 哨兵 → 集群（Cluster） | 容量/吞吐不足时集群化，key 设计预留 slot 分布 |
| 对象存储 | MinIO 多节点纠删码 | 容量与可用性随节点线性扩展 |

### 4.2 扩展的前提检查

- 服务已无状态化、配置外置、依赖（DB 连接池、线程池）有容量余量。
- 数据库为瓶颈时，先做索引与慢 SQL 治理，再谈读写分离（见 [数据与存储设计](architecture-data.md) 第 11 节）。
- 扩容后必须验证：注册中心实例数、流量均衡、错误率无上升。

---

## 5. 缓存分层

```
L1 本地缓存(Caffeine) ──► L2 Redis ──► DB
  TTL 短(秒级)           TTL 分钟级     兜底
```

| 层 | 适用 | 注意事项 |
|----|------|----------|
| L1 Caffeine | 字典、参数、权限等**读多写极少**数据 | 每实例独立，写后需失效（MQ/版本号广播） |
| L2 Redis | 会话、验证码、热点数据、幂等键 | key 规范见 [数据与存储设计](architecture-data.md) |
| DB | 最终一致性兜底 | 一致性策略见 [数据与存储设计](architecture-data.md) 第 6 节 |

> 缓存不是越多越好：L1 只放**一致性要求低**的数据；一致性要求高的数据只走 Redis 或直查 DB。

---

## 6. 限流配额

- 对外接口：Sentinel 按 QPS 限流（网关 + 服务两级），规则按 `resource + 用户/来源` 细分。
- 配额设计：
  - 接口级配额：按业务重要性分档（核心 > 一般 > 批量导出）。
  - 用户级配额：防单用户刷量（登录、验证码、导出）。
  - 服务级配额：保护下游（DB 连接、第三方接口）不过载。
- 配额不足时的行为：返回 `E6xxx` 限流错误码 + `Retry-After` 头，前端展示友好提示（见 [健壮性设计](architecture-robustness.md)）。
- 配额指标（命中次数/被限流请求）纳入监控看板，作为容量规划的输入。

---

## 7. 容量估算

### 7.1 估算公式（单接口）

```
单实例 QPS = min( 线程数/平均RT , 连接池/DB 平均RT , 网络/IO 上限 )
所需实例数 = 目标QPS / 单实例QPS × 冗余系数(1.5~2)
```

### 7.2 示例

> 目标 1000 QPS，接口平均 RT 100ms，单实例 200 线程 → 单实例约 2000 QPS（理论），考虑 GC/网络取 60% 即 1200 QPS → **2 实例**即可，预留冗余取 3~4 实例。

- 数据库容量：连接池总数 = 实例数 × 单实例连接数，需低于 DB `max_connections` 的 70%。
- Redis 容量：按 key 数量 × 平均大小估算内存，预留 30% 余量。
- 容量估算结果记录在模块文档/发布单，作为扩缩容依据（不拍脑袋）。

---

## 8. 灰度 / 蓝绿发布对扩展性的支撑

- **灰度发布**：新版本实例先承接小比例流量（网关按权重/用户标签路由），验证稳定后再全量——让「扩容」与「验证」解耦，可随时回滚。
- **蓝绿发布**：两套环境切换，回滚成本低，适合核心服务。
- 对扩展性的意义：
  - 新模块/新实例上线可灰度验证，降低水平扩容的风险；
  - 容量不足时先灰度扩容试点，确认指标后再批量扩；
  - 结合自动扩缩容（K8s HPA/云厂商），按 CPU/RT/队列深度弹性伸缩。
- 发布流程与闸门见 [研发全流程](../process/00-overview.md) 生产验收阶段。

---

## 9. 前端分包与构建

- **路由懒加载**：Vue Router 按页面 `import()` 动态导入，按需分包。
- **三方库分离**：Vite `manualChunks` 将 Element Plus / 图表库等独立 chunk，利用浏览器缓存。
- **构建产物**：`npm run build` 产出带 hash 的静态资源，上传 CDN/对象存储；网关/静态服务器配置长缓存。
- **环境注入**：接口地址、网关域名通过构建环境变量注入，避免硬编码。
- 前端微前端化（Module Federation）仅在团队规模/页面量达到阈值时引入，避免过度设计。

---

## 10. 代码可扩展原则

### 10.1 设计原则

- **开闭原则**：扩展通过新增类/策略实现，不修改已有核心类。
- **策略模式**：业务规则（如计费、校验、通知渠道）定义策略接口 + 实现注册表，按类型路由。
- **SPI / 注解扫描**：可插拔能力通过 Spring 机制（`@Component` + 接口集合注入）自动装配，新实现即插即用。
- **依赖倒置**：业务模块依赖 `ruoyi-api` 契约，不依赖其他业务模块实现（见 [总体架构](00-architecture-overview.md) 第 7 节）。

### 10.2 反模式

- ❌ 在核心 Service 里 `if/else` 枚举业务类型堆逻辑 → 改为策略注册。
- ❌ 跨模块 `import` 其他业务模块的 Mapper/实体 → 走 `ruoyi-api` 契约。
- ❌ 常量散落 → 收敛到 `ruoyi-common-core` 常量/枚举。

---

## 11. 模块可插拔（新增模块清单）

### 11.1 在 `ruoyi-common` 下新增公共模块

> 适用：多个业务模块共用的能力（如新的脱敏规则、通用工具）。

- [ ] 目录：`ruoyi-common/ruoyi-common-<name>/`，父 POM 为 `ruoyi-common`。
- [ ] `pom.xml`：依赖最小化（只依赖 `ruoyi-common-core`），不反向依赖业务模块。
- [ ] 在根 `pom.xml` 的 `<modules>` 注册新模块。
- [ ] 提供 `spring.factories`/自动配置类（如需 Bean 自动装配）。
- [ ] 文档：`docs/modules/` 说明职责与使用方式；更新 `README.md` 模块树。

### 11.2 在 `ruoyi-modules` 下新增业务模块

> 适用：新业务域（如订单、库存）。

- [ ] 目录：`ruoyi-modules/ruoyi-<name>/`，包结构 `controller/service/domain/mapper/config`（见 [../modules/coding-standard.md](../modules/coding-standard.md) §1）。
- [ ] `pom.xml` 依赖 `ruoyi-common-core/-security/-redis/-datasource/-datascope/-log/-sensitive` + 所需 `ruoyi-api`；根 POM 注册。
- [ ] Nacos 配置模板 `config/ruoyi-<name>-<profile>.yml`，`spring.application.name` 与 `NACOS_DATA_ID` 一致。
- [ ] 独立数据库（`ry-<name>`）与 SQL 迁移脚本（规范见 [数据与存储设计](architecture-data.md) 第 10 节）。
- [ ] 端口分配（避开既有端口表）与健康检查开启。
- [ ] 接口契约：若需跨服务调用，先定义 `ruoyi-api` 的 Feign 接口。
- [ ] 文档三件套：设计文档、`docs/modules/ruoyi-<name>.md`、发布/运维要点；同步更新 `README.md` 与架构总览。

---

## 12. 新增一个业务模块的完整步骤清单（Checklist）

1. **设计**：明确业务域边界与数据边界，输出接口契约 + 数据模型，过 G1 评审（见 [研发全流程](../process/00-overview.md)）。
2. **骨架**：按 §11.2 创建模块目录与 `pom.xml`，注册到父 POM。
3. **配置**：编写 Nacos 配置模板 + `local-env.yml` 覆盖项；确认服务名与配置 DataId 一致。
4. **数据库**：创建独立库，编写版本化迁移脚本并校验语法（`sql/` 规范）。
5. **编码**：按 [../modules/coding-standard.md](../modules/coding-standard.md) 分层规范实现 controller/service/mapper；入参校验、权限注解、`@Log`、幂等默认开启。
6. **契约**：需要跨服务的接口进 `ruoyi-api`，Feign 配置超时/重试/降级（见 [健壮性设计](architecture-robustness.md)）。
7. **自测**：`mvn compile -pl ruoyi-modules/ruoyi-<name> -am -q` 编译通过；补单测（JUnit5 + Mockito）。
8. **文档**：模块文档 + 设计文档 + 更新 README/架构总览索引。
9. **联调**：`./bin/dev-up.sh` 起依赖与服务，前端走网关联调关键链路。
10. **发布**：测试 → 验收 → 灰度发布（见 [研发全流程](../process/00-overview.md) 04/05 阶段）。

---

## 13. 扩展性检查清单

- [ ] 新模块已按 §11 清单落地（目录/配置/库/文档齐全）
- [ ] 服务无状态化：会话在 Redis、配置在 Nacos、无本地磁盘依赖
- [ ] 关键接口有容量基线（目标 QPS/RT）与限流配额
- [ ] 缓存分层合理：L1 只放低一致性要求数据
- [ ] 扩展点走接口/策略，无散落 if/else 与跨模块依赖
- [ ] 灰度/蓝绿发布方案可用，扩容可回滚

---

## 14. 相关文档

| 文档 | 主题 |
|------|------|
| [00-architecture-overview.md](00-architecture-overview.md) | 总体架构、模块职责矩阵 |
| [architecture-security.md](architecture-security.md) | 安全（鉴权/限流防刷） |
| [architecture-robustness.md](architecture-robustness.md) | 熔断/降级/错误码 |
| [architecture-data.md](architecture-data.md) | 分库分表/缓存/迁移 |
| [../process/00-overview.md](../process/00-overview.md) | 研发全流程（设计→发布闸门） |
