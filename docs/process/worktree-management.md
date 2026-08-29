# 工作树（Git Worktree）管理

> 本文档介绍如何用 `git worktree` 在同一仓库内并行开发多个任务并保持工作树隔离，是本项目并行开发的标准姿势，与 [workflow-management.md](workflow-management.md) 的分支模型配合使用。
> 配套：[00-overview.md](00-overview.md) §3.2 · [workflow-management.md](workflow-management.md) · [AGENTS.md](../../AGENTS.md) §5

---

## 1. 为什么用 git worktree

默认情况下一个仓库目录只有一个工作树：切分支要 `stash`/提交、重建依赖、重启 IDE，非常低效。`git worktree` 允许**同一个仓库同时检出多个分支到不同目录**，各自独立编译、运行、联调：

| 痛点 | 用 worktree 解决 |
|------|------------------|
| 并行开发：任务 A 进行中，来了紧急任务 B | B 开新 worktree，互不干扰，无需 stash |
| 多环境切换：同时看 develop 与 release/v1.2.0 | 每个环境一个目录，各自启动服务 |
| 联调依赖：两个 feature 分支互相配合 | 两个目录各起服务，配置指向对方 |
| 频繁切分支重建前端 node_modules / 后端 target | 各目录独立构建产物，不互相覆盖 |
| 本地验证他人 PR | 直接在 PR 分支的新 worktree 里跑，不动当前工作 |

> 适用前提：**Git 2.15+**（`git worktree add` 已是成熟特性）。注意：**一个分支只能被一个 worktree 检出**，这是隔离的代价也是保护。

---

## 2. 常用命令速查

```bash
git worktree add <路径> [分支]     # 新增工作树（可指定 -b 新建分支）
git worktree list                  # 列出所有工作树（路径、分支、commit）
git worktree remove <路径>         # 移除工作树（工作区须干净）
git worktree prune                 # 清理已删除目录的失效记录
git worktree lock <路径>           # 锁定（防止误删，如正在联调/挂载）
git worktree unlock <路径>
```

常用组合：

```bash
# 从 develop 新建一个 feature 分支并挂到新目录
git worktree add ../dsh-feature-123 -b feature/123-user-login develop

# 查看每个 worktree 对应的分支
git worktree list --porcelain
```

---

## 3. 与 feature 分支 / 多任务绑定

**一条 Worktree = 一个分支 = 一个任务**（与 [workflow-management.md](workflow-management.md) §5 的"一个 Issue 一个分支"对齐）：

| 场景 | 做法 |
|------|------|
| 新功能开发 | `git worktree add ../dsh-f123 -b feature/123-xxx develop` |
| 缺陷修复 | `git worktree add ../dsh-b126 -b bugfix/126-xxx develop` |
| 发布准备 | `git worktree add ../dsh-rel -b release/v1.2.0 main`（或检出已有 release 分支） |
| 线上热修 | `git worktree add ../dsh-hot -b hotfix/v1.2.1 main` |
| 验证他人 PR | `git worktree add ../dsh-pr456 feature/456-xxx`（检出已有分支，勿再 checkout 到别处） |

> 命名建议：目录用 `dsh-<任务简写>`，与主目录 `dsh` 区分；worktree 目录**放在仓库外**（如上级目录），避免嵌套进主工作树。

---

## 4. 多目录联调策略

并行分支需要互相配合时（如 workflow 与 system 同时改动）：

1. **分别启动**：在每个 worktree 目录内独立启动服务：

```bash
# worktree A（feature/123：改 system）
cd D:\RuoyiProject\demo\dsh-f123
mvn -pl ruoyi-system -am spring-boot:run -Dspring-boot.run.profiles=dev

# worktree B（feature/125：改 workflow）
cd D:\RuoyiProject\demo\dsh-f125
mvn -pl ruoyi-workflow -am spring-boot:run -Dspring-boot.run.profiles=dev
```

2. **端口/配置隔离**：同机并行启动同模块时，用 `local-env.yml` 或 `--server.port` 覆盖端口（如 system 9201/9211），Nacos 注册名相同但实例不同，网关按需路由。
3. **依赖对齐**：若 B 依赖 A 的公共 API 改动，先在 A 执行 `mvn -pl ruoyi-common/ruoyi-common-core,ruoyi-api -am install -DskipTests`（见 [AGENTS.md](../../AGENTS.md) §1.1），再在 B 编译。
4. **前端并行**：每个 worktree 各自 `npm ci && npm run dev`，Vite 端口用 `--port` 区分，代理指向对应后端端口。
5. **联调完成**：A、B 各自 PR 合并回 `develop` 后，删除 worktree（见 §6）。

> 若多目录需共享同一套基础设施（Nacos/MySQL/MinIO），直接用 `docker compose` 拉起的公共依赖即可，各目录只跑自己的服务。

---

## 5. 注意事项与坑

| 坑 | 说明与对策 |
|----|-----------|
| 分支唯一占用 | 分支被某 worktree 检出后，其他 worktree 无法再 checkout 该分支；用 `git worktree list` 确认占用，避免误以为"分支丢了" |
| 误删目录 | worktree 目录是普通目录，删除后 git 记录仍在；用 `git worktree prune` 清理，分支本身不丢 |
| remove 失败 | 工作区有未提交改动或 untracked 文件时 `git worktree remove` 会拒绝；先 `git status` 处理，或加 `--force`（慎用，会丢改动） |
| 磁盘与构建开销 | 每个 worktree 都有独立 `.git` 链接与 target/node_modules，N 个任务 ≈ N 份构建产物；注意磁盘空间，用完即清 |
| IDE 多窗口 | VS Code/IDEA 打开多个 worktree 目录时，各自独立索引；避免两个窗口对同一文件同时编辑 |
| 相对路径/脚本 | 脚本中的相对路径按各目录解析；发布/CI 脚本基于仓库根路径，worktree 中注意 `pwd` |
| 子模块/嵌套 | 本项目无 submodule；若有，worktree 内子模块需重新 `git submodule update` |
| 主目录污染 | 把 worktree 加在仓库内部会形成嵌套仓库，**一律加在仓库外**（`../dsh-xxx`） |

---

## 6. 清理流程

分支合并回 `develop`/`main` 后，及时清理，避免 worktree 越堆越多：

```bash
# 1) 确认工作区干净（有改动先提交或按需保留）
cd D:\RuoyiProject\demo\dsh-f123 && git status

# 2) 删除 worktree（工作区干净时直接删）
git worktree remove D:\RuoyiProject\demo\dsh-f123

# 3) 删除已合并的分支（feature 合并后）
git branch -d feature/123-user-login      # 未合并会拒绝，确认无误再用 -D

# 4) 清理失效记录
git worktree prune

# 5) 确认现状
git worktree list && git branch -a
```

清理节奏：**每个 PR 合并后顺手清**；每周 `git worktree prune` + 检查 `git worktree list` 一次。

---

## 7. 从零到并行的完整命令示例

```bash
# 0) 克隆仓库（主工作树在 main/develop 上）
git clone https://github.com/org/dsh.git D:\RuoyiProject\demo\dsh
cd D:\RuoyiProject\demo\dsh

# 1) 主工作树留在 develop，做日常集成
git checkout develop && git pull

# 2) 任务 A：新功能 #123 → 独立 worktree
git worktree add D:\RuoyiProject\demo\dsh-f123 -b feature/123-user-login develop
#   在 dsh-f123 中开发、编译、自测、push、建 PR（流程见 workflow-management.md）

# 3) 任务 B：修复 #126 → 另一个 worktree，互不干扰
git worktree add D:\RuoyiProject\demo\dsh-b126 -b bugfix/126-batch-import-slow develop

# 4) 紧急热修（任务 A 进行中也不受影响）
git worktree add D:\RuoyiProject\demo\dsh-hot -b hotfix/v1.2.1 main

# 5) 多环境并行查看
git worktree add D:\RuoyiProject\demo\dsh-rel release/v1.2.0   # 检出已有 release 分支

# 6) 联调：A 与 B 的目录各自启动服务、共享公共依赖（见 §4）

# 7) A/B 的 PR 合并后清理
git worktree remove D:\RuoyiProject\demo\dsh-f123
git worktree remove D:\RuoyiProject\demo\dsh-b126
git branch -d feature/123-user-login bugfix/126-batch-import-slow
git worktree prune

# 8) 最终确认
git worktree list
```

---

## 8. 与相邻文档的关系

| 文档 | 与本文档的关系 |
|------|----------------|
| [workflow-management.md](workflow-management.md) | 分支模型与 PR 流程（worktree 只是并行载体） |
| [00-overview.md](00-overview.md) | §3.2 对 worktree 并行开发的总体约定 |
| [02-development.md](02-development.md) | 开发阶段的编译/自测验证规则（每个 worktree 内同样适用） |
| [04-release.md](04-release.md) | release/hotfix 分支在独立 worktree 中准备与验证 |
| [AGENTS.md](../../AGENTS.md) | §5 分支命名约定；§1 编译验证规则（各 worktree 独立执行） |
