# 研发全流程总览（Lifecycle Overview）

> 本文档定义 **「设计 → 开发 → 测试 → 生产验收 → 运维」** 五阶段的通用流程，是重构成立后所有工程实践的统一骨架。
> 每个阶段有独立文档：`01-design`、`02-development`、`03-testing`、`04-release`、`05-ops`；`workflow-management` 与 `worktree-management` 是全流程的横向支撑。

---

## 1. 为什么需要这套流程

- **可预期**：任何需求从提起到上线都有明确节点、产出物与负责人。
- **可复用**：流程与交付物模板化，新模块/新项目直接套用。
- **可校验**：每个阶段有「出口条件（Exit Criteria）」，不满足不得进入下一阶段，形成质量闸门。
- **可追溯**：文档、代码、测试、发布、监控一一对应，发生问题可回溯。
- **AI 友好**：Agent 依据本流程给出阶段判断并加载对应 skill/mcp，行为可预测。

---

## 2. 阶段总览与闸门

```
 ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
 │  设计     │  │  开发     │  │  测试     │  │ 生产验收  │  │  运维     │
 │  Design  │→ │ Develop  │→ │  Test   │→ │ Release  │→ │   Ops    │
 └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘
      │ G1          │ G2          │ G3          │ G4          │ G5(持续)
      │ 设计评审通过 │ 编译/自测通过│ 测试全通过   │ 验收+灰度通过│ 监控/告警/迭代
```

| 阶段 | 关键产出 | 出口条件（Gate） | 文档 |
|------|----------|------------------|------|
| 设计 | 需求分析、架构/接口设计、数据库设计、原型 | G1：设计评审通过，接口与数据模型冻结 | [01-design](01-design.md) |
| 开发 | 代码、单测、Nacos 配置、SQL 迁移 | G2：编译通过、单测通过、自测通过 | [02-development](02-development.md) |
| 测试 | 测试计划、用例、缺陷报告、回归报告 | G3：测试通过、缺陷清零（或达成上线阈值） | [03-testing](03-testing.md) |
| 生产验收 | 发布单、验收报告、回滚方案 | G4：用户验收通过、灰度/蓝绿验证通过 | [04-release](04-release.md) |
| 运维 | 监控看板、告警、备份、值班、变更记录 | G5：SLA 达标、无 P0/P1 未闭环 | [05-ops](05-ops.md) |

---

## 3. 横向支撑（贯穿全程）

### 3.1 里程碑与迭代

- 采用 **GitHub Flow + 里程碑（Milestone）** 管理模式，一个里程碑对应一个可发布版本。
- 每个里程碑拆分任务（Issue/工单），遵循 `feat|fix|docs|...` 规范，一个 Issue 对应一个分支（见 [workflow-management](workflow-management.md)）。

### 3.2 Git Worktree（并行开发）

- 同仓库多任务并行时，使用 `git worktree add` 隔离工作树，避免相互覆盖。
- 每条 Worktree 绑定一个 `feature/<ticket>` 分支；开发完成后移除（见 [worktree-management](worktree-management.md)）。

### 3.3 CI/CD 流水线

- 提交触发：`lint` + 单测 + `mvn verify`（后端）+ `npm run build`（前端）。
- 主分支合并触发：构建镜像、推送镜像、部署到 `dev` 环境。
- 打 tag 触发：构建镜像、部署到 `staging` → `prod`（灰度/蓝绿）。
- 见 `.github/workflows/` 与 `docker/`。

### 3.4 配置与 SQL 管理

- 配置：`config/` 模板 → Nacos；应用 `<service>-<profile>.yml`。
- SQL：`sql/` 使用「迁移脚本 + 版本号」，生产只允许向前迁移（Flyway 语义），禁止回退数据。

### 3.5 环境说明

| 环境 | 用途 | 特点 |
|------|------|------|
| local | 开发者本机 | 凭 local-env.yml 覆盖，可缺省依赖 |
| dev | 集成环境 | 自动部署，数据可重置 |
| test | 测试环境 | 数据固定，供 QA 回归 |
| staging | 预生产 | 与生产同构，灰度演练 |
| prod | 生产 | 白名单/灰度，变更需评审 |

---

## 4. 通用交付物清单（每个功能/模块通用）

1. **设计文档**：`docs/design/<feature>/`：需求、接口（OpenAPI）、数据模型、时序、事故预案。
2. **模块文档**：`docs/modules/<module>.md`：职责、依赖、接口清单、运维要点。
3. **配置**：`config/<service>-<profile>.yml`（Nacos 模板）与 `local-env.yml` 覆盖项。
4. **SQL**：`sql/<version>__<desc>.sql`（向后兼容迁移）。
5. **代码**：遵循 `AGENTS.md` 编码规范（分层/命名/注释/单元测试）。
6. **测试**：单测（JUnit5）、集成测试、契约测试、端到端（可选）。
7. **发布**：`docs/ops/release-notes/<ver>.md` + 回滚脚本。
8. **监控**：actuator 指标 + 日志规范 + 告警规则。

---

## 5. Agent 如何遵循本流程

- **阶段判断**：进入仓库后，依据任务性质定位到 01–05 之一，读取对应文档。
- **闸门校验**：代码改动必须过 G2（编译/测试）；发布相关改动必须过 G4（验收/回滚）。
- **工具选择**：
  - 涉及数据库 → 加载 `agent/mcp/mcp.json`（mysql-ry / mysql-flowable，只读）。
  - 涉及某业务域编码 → 加载 `agent/skills/<domain>-dev/SKILL.md`。
  - 涉及发布/部署 → 参考 `bin/` 与 `.github/workflows/`。
- **产出留痕**：每个改动输出「原因 + 验证」，并在模块文档/发布说明登记。

---

## 6. 文档导航

| 阶段文档 | 说明 |
|----------|------|
| [01-design.md](01-design.md) | 需求→设计→评审 |
| [02-development.md](02-development.md) | 编码→自测→MR |
| [03-testing.md](03-testing.md) | 测试策略→执行→回归 |
| [04-release.md](04-release.md) | 发布→灰度→验收→回滚 |
| [05-ops.md](05-ops.md) | 监控→告警→备份→持续优化 |
| [workflow-management.md](workflow-management.md) | Git/分支/提交/评审/CI/CD |
| [worktree-management.md](worktree-management.md) | Git Worktree 并行开发 |
