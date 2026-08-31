# Agent 工具链与复用说明

> 本目录存放项目级 **Agent 指令、可复用 Skills、MCP 配置**，是所有 AI 协作者（DSH Agent / Claude / ZCode / VS Code Copilot）的统一入口。
> 全局约束以仓库根 [AGENTS.md](../AGENTS.md) 为准；本文档是**工具层**的操作说明。

---

## 1. 目录结构

```
agent/
├── README.md                 # 本文件（工具链总入口）
├── skills/                   # 可复用 Skills（SKILL.md，带 YAML frontmatter）
│   ├── module-dev/SKILL.md          # 通用模块开发指南
│   ├── workflow-dev/SKILL.md        # 工作流模块专用开发指南
│   ├── db-query/SKILL.md            # MCP 数据库只读查询指南
│   └── release/SKILL.md             # 发布/回滚/运维指南
└── mcp/
    ├── mcp.json              # MCP Server 配置（mysql-ry / mysql-flowable）
    └── README.md             # MCP 启动、使用、复用说明
```

---

## 2. Skills 使用规则

- **系统/平台级 skill**（module-dev、release）在**任何涉及对应动作**时加载。
- **领域级 skill**（workflow-dev、db-query）在**匹配其 description 的场景**时加载。
- Skill 命名规则：`<domain>-dev`，description 必须标明"修改或新增 X 相关代码时自动加载"。
- 新增 skill 时：在 `agent/skills/<name>/SKILL.md` 添加 frontmatter（`name`、`description`），并在此 README 的 skills 清单登记。

| Skill | 适用场景 | 加载时机 |
|-------|----------|----------|
| `module-dev` | 新增/修改任何业务模块 | 编辑 `ruoyi-modules/**`、`ruoyi-common/**` 时 |
| `workflow-dev` | 工作流（审批/驳回/加签/抄送） | 编辑 `ruoyi-modules/ruoyi-workflow/**` 时 |
| `db-query` | 查询数据库（业务/工作流） | 需要查 `ry-cloud`/`ry-flowable` 数据时 |
| `release` | 发布、灰度、回滚、运维命令 | 进入 `04-release` / `05-ops` 阶段时 |

---

## 3. MCP 使用规则

- **优先**使用 `agent/mcp/mcp.json` 中的连接，**不要**进 Docker 或终端直连数据库。
- 只读查询；**禁用** MCP 做任何写库操作。
- 连接信息与环境变量见 [mcp/README.md](mcp/README.md) 与 `mcp.json`。

| Server | 目标库 | 用途 | 备注 |
|--------|--------|------|------|
| `mysql-ry` | `ry-cloud` | 业务/系统数据 | 库名反引号 `\`ry-config\``（若查Nacos配置表） |
| `mysql-flowable` | `ry-flowable` | 工作流 `act_*` | 只读 |

---

## 4. 给 AI 协作者的调用次序（重要）

在仓库中处理任务时，按此顺序决定加载什么：

```
1. 判阶段 → 读 docs/process/0X-*.md（设计/开发/测试/发布/运维）
2. 编辑模块 → 读 docs/modules/<module>.md + skills/<domain>-dev/SKILL.md
3. 查数据   → 读 agent/mcp/README.md + 用 mysql-ry / mysql-flowable（只读）
4. 改配置   → 读 config/<service>-<profile>.yml 模板 + local-env.yml 约定
5. 发布运维 → 读 docs/process/04-release.md / 05-ops.md + bin/ + .github/workflows/
6. 每次改动 → 严格按 [AGENTS.md](../AGENTS.md) §1 的「原因+验证+编译/构建」流程执行并汇报
7. 每次提交 → 追加当天需求与改动到 docs/changelog/<yyyy-MM-dd>.md（见 [AGENTS.md](../AGENTS.md) §2.4）
```

---

## 5. 复用与扩展

- **复用**：新增第三方依赖/中间件时，优先在 `ruoyi-common-*` 沉淀为可复用组件，并写对应模块文档与 skill。
- **扩展 skill**：把高频、可复刻的操作固化为 skill，避免每次从头解释。
- **扩展 mcp**：新增数据源时，在 `mcp.json` 注册新 server，并更新本 README 与 db-query skill 的库清单。

---

## 6. 与本仓库文档的一致性

本文档与以下文件保持一致，修改时需同步：

- [../AGENTS.md](../AGENTS.md)（全局要求）
- [../docs/process/00-overview.md](../docs/process/00-overview.md)（流程总览）
- [../docs/architecture/00-architecture-overview.md](../docs/architecture/00-architecture-overview.md)（架构）
