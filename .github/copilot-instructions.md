# RuoYi-Cloud 项目指令（Agent 全流程规范 · 大纲）

> Contents mirror [AGENTS.md](../AGENTS.md). This file is the outline + full-process spec only; details live in stage / module / git docs.
> 细则按来源分列维护：
> - 阶段文档：`docs/process/0X-*.md`（设计/开发/测试/生产验收/运维，含 Gate 闸门）
> - 模块文档：`docs/modules/<module>.md`（开发阶段每个模块一份）
> - Git 工作流：`docs/process/workflow-management.md`（分支/提交/PR/CI-CD）
> - 工具链：`agent/README.md`、`agent/skills/<domain>-dev/SKILL.md`、`agent/mcp/mcp.json`

---

## 1. 通用硬性规则（所有阶段都必须遵守）

1. **修改 `.java` 后必须立即编译验证**：找到所属 Maven 模块 → `mvn compile -pl <模块路径> -am -q 2>&1`，失败即修直到通过，通过后输出 `✅ 编译通过`。修改 `.vue`/`.js`/`.ts` 后跑 lint / build，有错即修。
2. **文件操作说明**：每次新增/修改/删除文件，先**解释原因**，再给出**验证方法**并执行、报告结果。
3. **批量改代码必须用 Edit 工具逐个文件**，禁止用 Bash/PowerShell 批量替换命令（如 `Get-Content | ForEach-Object | Set-Content`），防止中文注释乱码。
4. **查数据库优先用 MCP**（`mysql-ry`→`ry-cloud`、`mysql-flowable`→`ry-flowable`，只读）；MCP 不可用时才 `docker exec`。查 Nacos 配置表注意库名反引号 `ry-config.config_info`。

---

## 2. Agent 全流程规范（严格遵循）

### 2.1 文档驱动（强约束）

- **进入任一生阶段前**，先 `read` 对应阶段文档 `docs/process/<阶段>.md`（未读禁止开始工作）。
- **开发阶段修改任一模块前**，先 `read` 该模块文档 `docs/modules/<module>.md`；若缺档，读 `docs/architecture/00-architecture-overview.md` 的职责矩阵并在回复中指出。
- `agent/skills/<domain>-dev/SKILL.md` 在匹配场景时一并加载。

### 2.2 生命周期五阶段

| 阶段 | 文档 | 闸门 |
|------|------|------|
| 设计 | `docs/process/01-design.md` | G1 评审冻结 |
| 开发 | `docs/process/02-development.md` | G2 编译/单测/自测 |
| 测试 | `docs/process/03-testing.md` | G3 测试达标 |
| 生产验收/发布 | `docs/process/04-release.md` | G4 验收+回滚 |
| 运维 | `docs/process/05-ops.md` | G5 SLA/告警 |

总览与里程碑：`docs/process/00-overview.md`。

### 2.3 工具链

- 入口：`agent/README.md`；技能：`agent/skills/`（`module-dev`/`workflow-dev`/`db-query`/`release`）。
- MCP：`agent/mcp/mcp.json`（`mysql-ry`/`mysql-flowable`，只读）。

### 2.4 提交留痕

**每次 commit/push 前**，必须把当天需求与改动追加到 `docs/changelog/<yyyy-MM-dd>.md`（约定见 `docs/changelog/README.md`）。

### 2.5 Git 工作流（硬性）

1. **先建分支再改代码**：禁止在 `main`/`develop`/开发型分支（如 `springboot3`）上直接改；功能/缺陷从 `develop` 切 `feature/<ticket>-<desc>`、`bugfix/<ticket>-<desc>`，发布/热修从 `main` 切 `release/<ver>`、`hotfix/<ver>`；一个分支一个 issue。
2. **并行开发必须用 `git worktree`**：每个任务一条 worktree。
3. **建立分支后必须立即向用户显示当前分支**：用 `git branch --show-current`（worktree 用 `git worktree list`）确认当前分支名，并在回复中明确展示「当前分支 + 关联 issue/task」，防止改动/提交落到错误分支。
4. **Conventional Commits**：`<type>(<scope>): <subject>`，一条提交只做一件事。
5. **必须走 PR/MR 评审**：禁止直接向 `main`/`develop` 推送；CI 全绿 + Approve → Squash 合并 → 删分支/删 worktree。
6. **校验前置**：改动先编译 / lint / 测试通过，CI 全绿才可合并。

> 详细版见 [`docs/process/workflow-management.md`](docs/process/workflow-management.md) 与 [`docs/process/worktree-management.md`](docs/process/worktree-management.md)，冲突时以本节为准。
