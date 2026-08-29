# 设计阶段（Design）

> 本文档定义 **「需求分析 → 接口/数据库设计 → 模块划分 → 设计评审（Gate G1）」** 阶段的输入、产出、规范与检查点，是 [02-development](02-development.md)（编码）与 [03-testing](03-testing.md)（测试）的输入源头。
> 关联文档：[流程总览](00-overview.md) · [总体架构](../architecture/00-architecture-overview.md) · [数据与存储设计](../architecture/architecture-data.md) · [AGENTS.md](../../AGENTS.md)

---

## 1. 阶段目标与输入输出

| 项 | 说明 |
|----|------|
| 输入 | 需求工单/Issue、原型、业务约束、既有系统现状 |
| 产出 | 设计文档（`docs/design/<feature>/`）、OpenAPI 契约、数据模型 DDL、模块划分说明、评审记录 |
| 出口条件（Gate G1） | 设计评审通过，**接口与数据模型冻结**（变更须走变更评审） |

> 设计阶段的"冻结"是为了让开发与测试有稳定基线；冻结不是不允许改，而是任何修改都要走 §7 的变更流程留痕。

---

## 2. 需求分析与拆解

### 2.1 用户故事（User Story）

统一使用「角色—功能—价值」模板，一条故事只描述一个可验收的增量：

> 作为 **<角色>**（如审批人），我希望 **<功能>**（如在工作台查看待办任务），以便 **<价值>**（如及时处理审批、不遗漏）。

### 2.2 验收标准（Acceptance Criteria）

每条故事必须带 1 条以上可执行验收标准，建议 Given-When-Then 或编号清单：

```
GIVEN 用户已登录且拥有"流程-任务-办理"权限
WHEN  用户打开工作台待办列表
THEN  展示其全部待办任务，按创建时间倒序，支持分页
AND   点击任务可进入办理页
```

### 2.3 需求拆解检查清单

- [ ] 每个需求条目都有唯一编号（对应 Issue/工单号，如 `#123`）
- [ ] 明确了角色、权限边界（谁可以用、谁能看哪些数据）
- [ ] 明确了异常场景（超时、重复提交、无权限、数据不存在）
- [ ] 明确了数据量级与性能预期（用于索引/分页/缓存设计）
- [ ] 拆解结果被评审方确认，避免开发中需求蔓延

---

## 3. 接口设计

### 3.1 OpenAPI / springdoc

- 统一使用 `ruoyi-common-swagger`（springdoc-openapi 2.6.0），运行时通过网关聚合各服务 `/v3/api-docs`。
- 必须使用注解声明契约：`@Tag`（模块）、`@Operation`（接口语义）、`@Schema`（DTO/VO 字段说明）。
- 设计阶段先产出 **openapi.yaml 契约**（可人工编写或原型生成），评审通过后再落地代码注解，保证"契约先行"。

### 3.2 路径与方法规范

- 前端统一经网关 `http://<gateway>:8080/prod-api` 转发，网关按服务名路由并 `StripPrefix`。
- 服务内路径以模块前缀开头（与 Nacos 服务名一致），RESTful 资源命名，一律小写、单词间用 `-`。

| 场景 | 方法 | 路径示例 | 说明 |
|------|------|----------|------|
| 分页/列表查询 | GET | `/system/user/list` | 参数 `pageNum`/`pageSize` + 查询条件 |
| 详情 | GET | `/system/user/{userId}` | 路径参数取资源 ID |
| 新增 | POST | `/system/user` | 请求体为强类型 BO/DTO |
| 修改 | PUT | `/system/user` | 全量/受控更新，body 携带 id |
| 删除 | DELETE | `/system/user/{ids}` | 支持逗号分隔批量，如 `1,2,3` |
| 状态流转 | PUT | `/workflow/task/complete` | 动词化子资源，语义清晰 |

### 3.3 参数规范

- 查询条件用 `GET` 查询参数；复杂查询（多条件/嵌套）用 `POST` + BO。
- 入参必须强类型 DTO/BO，禁止散落 `Map<String, Object>`；字段用 `@NotNull/@NotBlank/@Size` 等声明校验。
- 分页参数统一 `pageNum`（从 1 起）、`pageSize`（上限 100），返回 `TableDataInfo`。

### 3.4 错误码规范

统一返回体 `R<T> { code, msg, data }`；传输层语义走 HTTP 状态码，业务错误用 `code` 区分：

| 区间 | 含义 | 示例 |
|------|------|------|
| 200 | 成功 | `200 操作成功` |
| 401 | 未认证/令牌失效 | 网关拦截，前端跳登录 |
| 403 | 无权限 | `@PreAuthorize` 拒绝 |
| 404 | 资源不存在 | 路由/资源缺失 |
| 500 | 系统异常 | 未捕获异常，由 `GlobalExceptionHandler` 兜底 |
| 10xxx | system 模块业务码 | `10404 用户不存在`、`10405 用户已禁用` |
| 20xxx | workflow 模块业务码 | `20401 流程不存在`、`20402 任务已被处理` |
| 30xxx | file 模块业务码 | `30401 文件不存在` |
| 40xxx | job 模块业务码 | `40401 任务已被占用` |

> 规则：业务码在设计中登记到模块文档的「错误码表」，禁止重复、禁止裸 `RuntimeException`（用 `ServiceException`，见 [AGENTS.md](../../AGENTS.md) §4.5）。

---

## 4. 数据库设计

### 4.1 表与字段

| 维度 | 规范 | 示例 |
|------|------|------|
| 表名 | 小写下划线，`<模块前缀>_<业务名>`，单数 | `sys_user`、`flow_process_instance` |
| 字段 | snake_case，语义自明 | `user_name`、`create_time` |
| 主键 | `id BIGINT` 自增，或业务表用唯一键 | `id` |
| 通用字段 | `create_by/create_time/update_by/update_time/remark`，软删表加 `del_flag` | 参照 `sys_user` |
| 金额/状态 | 无符号整型或 decimal，状态流转加 `version` 乐观锁 | `amount DECIMAL(18,2)`、`status TINYINT` |

### 4.2 索引与约束

- 索引命名：普通索引 `idx_<表>_<列>`，唯一索引 `uk_<表>_<列>`。
- 必建索引：主键、外键关联列、常用查询/排序/分页列；联合索引遵循最左前缀。
- 约束：`NOT NULL` + `DEFAULT` 兜底；字符串长度明确（如 `varchar(64)`）；枚举状态用 `TINYINT` + 字典表或注释说明。
- 字符集统一 `utf8mb4`、引擎 `InnoDB`；**禁止跨库外键**——微服务数据独立，一致性由业务层/Seata 保证（见 [总体架构](../architecture/00-architecture-overview.md) §1.2 原则 8）。

### 4.3 交付物

- DDL 与增量迁移：`sql/<version>__<desc>.sql`（如 `sql/20260615__add_sys_approval.sql`），只允许向前迁移。
- 设计评审时用 MCP（`mysql-ry` / `mysql-flowable`，只读）核对既有表结构与字典，避免重复建表/命名冲突。

---

## 5. 模块划分与依赖

### 5.1 何时新建 module

满足以下任一条件才新建 Maven 模块，否则先并入现有模块（简单 CRUD 不拆服务）：

| 条件 | 说明 |
|------|------|
| 业务域独立 | 有清晰单一职责，可独立演进与部署 |
| 数据独立 | 拥有自己的库/表组，符合"服务只能访问自己的库" |
| 伸缩差异 | 流量/并发与既有模块差异大，需独立扩缩容 |
| 团队/节奏 | 独立团队或独立发布节奏 |
| 跨服务复用 | 能力被多个服务消费 → 放 `ruoyi-api`（Feign 契约）或 `ruoyi-common-*` |

### 5.2 新建模块登记清单

- [ ] 端口未占用（见 [README](../../README.md) §2 端口表：system 9201 / workflow 9210 / file 9300 …）
- [ ] Nacos 服务名 = `spring.application.name`，与 `config/<service>-<profile>.yml` 一致
- [ ] 依赖方向合规：业务模块只依赖 `common-*` 与 `api-*`，禁止反向依赖其他业务模块实现
- [ ] 计划输出 `docs/modules/<module>.md` 与架构图更新

---

## 6. 设计文档模板

每功能/模块在 `docs/design/<feature>/` 下建目录，文件结构如下：

```
docs/design/<feature>/
├── README.md              # 设计文档主文件（章节见下表）
├── api/
│   └── openapi.yaml       # OpenAPI 3.0 契约（评审后冻结）
├── db/
│   ├── schema.sql         # 目标表结构 DDL
│   └── migration.sql      # 增量迁移（最终落入 sql/<version>__<desc>.sql）
├── flow/
│   └── diagrams.md        # 时序图/状态图（Mermaid）
└── review/
    └── gate-g1.md         # G1 评审记录：结论、问题、冻结清单、变更登记
```

**README.md 章节模板**：

| 章节 | 内容 |
|------|------|
| 1. 背景与目标 | 需求来源、要解决的问题、范围 |
| 2. 需求分析 | 用户故事 + 验收标准（§2） |
| 3. 接口设计 | 路径/方法/参数/错误码，附 openapi.yaml 链接 |
| 4. 数据模型 | 表/字段/索引/约束，附 schema.sql 链接 |
| 5. 模块与依赖 | 所属模块、依赖、是否新建 module |
| 6. 时序与状态 | 关键链路时序图、状态流转 |
| 7. 安全与健壮性 | 权限、脱敏、幂等、限流、异常预案 |
| 8. 评审记录 | G1 结论与冻结清单 |

---

## 7. 设计评审（Gate G1）

### 7.1 评审检查清单

- [ ] 需求条目与验收标准齐全、可测
- [ ] 接口路径/方法/参数/错误码符合 §3 规范，openapi.yaml 可被工具解析
- [ ] 数据模型评审通过：表/字段/索引/约束无冲突，命名合规
- [ ] 模块划分与依赖无循环，符合"只依赖 common-* / api-*"原则
- [ ] 安全设计已纳入：权限、脱敏、幂等、操作日志
- [ ] 性能预估完成：索引、分页上限、缓存与限流策略
- [ ] 文档同步计划：`docs/modules/`、`docs/architecture/` 更新责任人明确

### 7.2 冻结与变更

- 评审通过后，**接口契约与数据模型冻结**，作为开发与测试的基线。
- 冻结后任何变更：先评估影响（接口调用方/表结构迁移/测试用例），在 `review/gate-g1.md` 登记，重走受影响部分评审；**禁止静默改契约**。

---

## 8. skill / MCP 的使用时机

| 工具 | 时机 | 用途 |
|------|------|------|
| 业务域 skill（`agent/skills/<domain>-dev/SKILL.md`） | 进入某业务域设计前 | 了解领域约束、既有实现与编码惯例，保证设计与现状一致 |
| MCP `mysql-ry` / `mysql-flowable` | 数据库设计时 | **只读**核对既有表结构、字典、索引，避免设计冲突 |
| springdoc / OpenAPI 工具 | 契约产出时 | 校验 openapi.yaml 语法与一致性 |

> 详见 [agent/README.md](../../agent/README.md) 与 [agent/mcp/README.md](../../agent/mcp/README.md)；数据访问一律只读（[AGENTS.md](../../AGENTS.md) §3）。

---

## 9. 进入开发阶段的前置

- [ ] G1 评审通过，`review/gate-g1.md` 结论为"通过"
- [ ] 设计文档、openapi.yaml、schema.sql 已入库
- [ ] 需求拆解为可排期的开发任务（对应 `feature/<ticket>-<desc>` 分支，见 [workflow-management](workflow-management.md)）

满足以上条件后进入 [02-development.md](02-development.md)。
