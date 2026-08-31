# 工作流管理（Git / 分支 / 提交 / CI-CD）

> 本文档是仓库级 Git 协作规范与 CI/CD 流水线设计。**§0 为 Agent 必须遵守的硬性规则（必定执行）**，与 [AGENTS.md](../../AGENTS.md) §2.5 一致；其余章节为落地细则。
> 配套：[00-overview.md](00-overview.md) · [worktree-management.md](worktree-management.md) · [04-release.md](04-release.md) · [05-ops.md](05-ops.md)

---

## 0. Agent 强制遵守 · 必定执行（硬性 · 最高优先）

> 本节作用对象为**所有 AI 协作者（DSH / Claude / ZCode / VS Code Copilot）**，优先级高于本文件任何"建议/可选"表述；冲突时以本节为准。**以下为"必须（MUST）"，不可省略。**

### 0.1 先建分支再改代码（必须）

- 禁止在 `main` / `develop`（含本项目开发型分支如 `springboot3`）上直接改代码。
- 功能/缺陷从 `develop` 切 `feature/<ticket>-<desc>`、`bugfix/<ticket>-<desc>`；发布/热修从 `main` 切 `release/<ver>`、`hotfix/<ver>`。
- 一个分支只做一个 issue；命名 kebab-case。
- **并行任务必须用 `git worktree`**（见 [worktree-management.md](worktree-management.md)），禁止在主工作树反复切分支。
- **建立分支后必须立即向用户显示当前分支**：执行 `git branch --show-current`（worktree 用 `git worktree list`）确认，并在回复中明确贴出当前分支名（附关联 issue/task）。示例：`✔ 已在分支 feature/123-user-login 上，关联 #123`。让用户一眼看到改动落在哪个分支，避免在错误分支上提交。

### 0.2 提交规范（必须）

- 必须 Conventional Commits：`<type>(<scope>): <subject>`；一条提交只做一件事；subject 清晰，禁 `update`/`fix bug`。
- 必要时 `Closes #123`、`BREAKING CHANGE:`。
- 提交前：`git status`/`git diff` 自查（无多余文件、无敏感信息）；**必须先追加 `docs/changelog/<yyyy-MM-dd>.md>` 留痕**。

### 0.3 提交后必走 PR/MR 评审（必须）

- 禁止直接向 `main`/`develop` 推送；必须 `push` 到自己的 feature 分支并创建 PR/MR。
- 合并门禁（全满足）：CI 全绿 + ≥1 维护者 Approve（核心/跨服务 ≥2）+ 描述与改动一致 + 无未解决 conversation。
- 默认 Squash merge；合并后删除分支及对应 worktree。

### 0.4 校验前置（必须）

- 改动先按 [AGENTS.md](../../AGENTS.md) §1 编译 / lint / 测试通过，CI 全绿才可合并。

---

## 1. 分支模型（GitFlow 简化版）

```
main ──────────────●────────────────●──────────►  (仅合并 release/hotfix，保持可发布)
  │                │                │
  ├─ release/v1.2.0 ●───●───────────┘            (发布分支：冻结、修 bug、打 tag)
  │                │
develop ───────────●───●────●───────►            (集成分支：合并 feature/bugfix)
  │                │    │    │
  ├─ feature/123-login   │    └─ feature/125-cc  (特性分支：一个 issue 一个分支)
  └─ bugfix/126-import   │                        (缺陷修复分支)
                        hotfix/v1.2.1 ──────────►  (线上热修：从 main 切出，修复后合回 main+develop)
```

| 分支 | 用途 | 谁可以合入 | 生命周期 |
|------|------|-----------|----------|
| `main` | 生产可发布版本，只接受 PR 合并 | 维护者 | 常驻 |
| `develop` | 日常集成，feature 的汇聚点 | 维护者 | 常驻 |
| `feature/<ticket>-<desc>` | 新功能开发 | 开发者自建 | 合并后删除 |
| `bugfix/<ticket>-<desc>` | 非紧急缺陷修复 | 开发者自建 | 合并后删除 |
| `release/<ver>` | 发布准备：回归、修 bug、定版 | 发布负责人 | tag 后合并回 main/develop 并删除 |
| `hotfix/<ver>` | 线上紧急修复 | 值班/维护者 | 合并回 main/develop 后删除 |

> **铁律：禁止直接向 `main` / `develop` 推送**，一切通过 PR/MR 评审合并，CI 全绿才可合并（见 §4）。

---

## 2. 分支命名规范

```
feature/<issue编号>-<简短描述>    示例：feature/123-user-login
bugfix/<issue编号>-<简短描述>     示例：bugfix/126-batch-import-slow
release/<版本号>                 示例：release/v1.2.0
hotfix/<版本号>                  示例：hotfix/v1.2.1
```

- 描述用 kebab-case 英文（或拼音），简短清晰；一个分支**只做一个 issue** 的工作。
- 分支从正确基线切出：`feature`/`bugfix` 从 `develop` 切；`release`/`hotfix` 从 `main` 切。

---

## 3. 提交规范（Conventional Commits）

```
<type>(<scope>): <subject>
```

| type | 含义 | 示例 |
|------|------|------|
| feat | 新功能 | `feat(workflow): 审批支持加签/转办` |
| fix | 缺陷修复 | `fix(system): 修复用户批量导入慢 SQL` |
| docs | 文档 | `docs(process): 补充发布回滚章节` |
| style | 格式（不影响逻辑） | `style(common): 统一 import 顺序` |
| refactor | 重构（不改变行为） | `refactor(auth): 提取令牌校验公共方法` |
| perf | 性能优化 | `perf(system): 用户列表分页索引优化` |
| test | 测试相关 | `test(workflow): 补充抄送单测` |
| build / ci / chore | 构建 / CI / 杂项 | `ci: 流水线增加镜像签名步骤` |

- subject 中文/英文均可，但必须清晰表达"做了什么"；`scope` 用模块名（gateway/auth/system/workflow/ui…）。
- 一个提交只做一件事；提交信息与内容一致，禁止 `update`、`fix bug` 这类无意义信息。
- 关闭 issue 用 `Closes #123`；涉及破坏性变更加 `BREAKING CHANGE:` 说明。
- 提交前自查：`git status` / `git diff` 确认无多余文件、无敏感信息（见 [AGENTS.md](../../AGENTS.md) §2.5）。

---

## 4. PR/MR 评审与合并门禁

### 4.1 门禁（全部满足才可合并）

- [ ] CI 全绿：lint + 单测 + 后端 `mvn verify` + 前端 `npm run build`（见 §6）
- [ ] 至少 1 名维护者 Approve（核心/跨服务变更至少 2 人）
- [ ] 变更内容与 PR 描述一致，含验证说明（测试结果/截图）
- [ ] 无未解决 conversation；代码符合 [../modules/coding-standard.md](../modules/coding-standard.md) 编码规范
- [ ] 不直接合入 `main`（`develop` 验证通过后再走 `release` 流程）

### 4.2 合并策略

- 默认 **Squash merge**：一个 PR 一个提交，保持 `develop` 历史线性；描述引用 PR 号。
- 分支合并后**立即删除**，避免残留（配合 worktree 清理，见 [worktree-management.md](worktree-management.md)）。

### 4.3 代码审查清单

- [ ] 功能：实现与需求/issue 一致，边界与异常分支已处理
- [ ] 分层：Controller 只做校验/调用/返回；Service 承载业务；无散落 Map、无裸 `RuntimeException`（用 `ServiceException`）
- [ ] 安全：写操作有 `@PreAuthorize`/`@Log`；入参 `@Validated` 校验；敏感字段脱敏；无硬编码凭证
- [ ] 健壮性：幂等、乐观锁、Feign 超时与降级、分布式事务边界（Seata）正确
- [ ] 测试：新增逻辑有单测（JUnit5 + Mockito）；关键路径有集成测试
- [ ] 文档：接口/模块文档同步（见 [AGENTS.md](../../AGENTS.md) §2.4）；SQL 迁移脚本符合向前兼容

---

## 5. Issue / 里程碑与分支绑定

- **一个 Issue = 一个分支 = 一个 PR**：Issue 描述需求/缺陷，编号用于分支命名与提交引用。
- **里程碑（Milestone） = 一个可发布版本**：里程碑内的 issue 全部关闭且测试通过后，才创建 `release/<ver>`。
- 分支命名即绑定：`feature/123-xxx` → 合并时 `Closes #123`，可自动关闭 issue。
- 里程碑推进：dev 验证 → 测试回归 → 发布（见 [04-release.md](04-release.md)）。

---

## 6. CI/CD 流水线设计（.github/workflows）

### 6.1 流水线矩阵

| 流水线 | 触发 | 阶段 | 产物 |
|--------|------|------|------|
| `ci.yml` | PR / push 到任意分支 | lint → 单测 → 后端 `mvn verify` → 前端 `npm run build` | 测试报告 |
| `build-and-deploy.yml` | push 到 `develop` / `main` | 构建镜像 → 推送私有仓库 → 部署 dev（main 部署 staging） | 镜像 + 部署 |
| `release.yml` | push tag `v*` | 构建镜像 → 推送 → 部署 staging → 人工确认 → 部署 prod（灰度） | 生产发布 |

### 6.2 关键 job 示意（ci.yml 骨架）

```yaml
name: CI
on:
  pull_request:
  push:
    branches: [develop, main]
jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - name: Build & Test
        run: mvn -B verify -DskipTests=false   # 单测+集成测试，CI 全绿门禁
      - name: Upload reports
        uses: actions/upload-artifact@v4
        with: { name: surefire-reports, path: '**/target/surefire-reports' }
  frontend:
    runs-on: ubuntu-latest
    defaults: { run: { working-directory: ruoyi-ui } }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '18', cache: npm, cache-dependency-path: ruoyi-ui/package-lock.json }
      - run: npm ci
      - run: npm run lint
      - run: npm run build
  docker:
    needs: [backend, frontend]
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    steps:
      - name: Build & Push images
        run: docker buildx bake --push   # docker/ 下多服务镜像，tag 用 git sha
```

### 6.3 release 打 tag 触发部署

```bash
# 发布负责人操作（release 分支验证通过后）
git checkout main && git pull
git merge --no-ff release/v1.2.0 -m "Merge release/v1.2.0"
git tag -a v1.2.0 -m "Release v1.2.0"
git push origin main --tags          # 触发 release.yml：staging → 人工确认 → prod 灰度
```

> 镜像 tag 与版本一致（`registry.example.com/ruoyi-system:v1.2.0`），保证可追溯、可回滚（见 [04-release.md](04-release.md) §7）。

---

## 7. Git 常用命令示例

```bash
# 1) 新功能：从 develop 切分支
git checkout develop && git pull
git checkout -b feature/123-user-login

# 2) 规范提交
git add ruoyi-modules/ruoyi-system/src
git commit -m "feat(system): 用户登录增加验证码开关 (Closes #123)"

# 3) 推送并创建 PR（默认 Squash merge）
git push -u origin feature/123-user-login

# 4) 更新分支（develop 有进展时 rebase 保持线性）
git fetch origin && git rebase origin/develop && git push --force-with-lease

# 5) 发布/热修流程（见 §6.3）；查看历史
git log --oneline --graph --decorate --all
git tag -l 'v*'
```

---

## 8. 与相邻文档的关系

| 文档 | 与本文档的关系 |
|------|----------------|
| [00-overview.md](00-overview.md) | 里程碑/CI/CD 在流程总览 §3 的定义 |
| [worktree-management.md](worktree-management.md) | 并行开发时每条 worktree 绑定一个 `feature/<ticket>` 分支 |
| [04-release.md](04-release.md) | `release/<ver>` 分支、tag 触发部署、回滚版本 |
| [05-ops.md](05-ops.md) | 变更记录与值班（生产变更须关联 PR/工单） |
| [AGENTS.md](../../AGENTS.md) | §2.5 Git 工作流、§2.2 测试阶段（CI 依据） |
