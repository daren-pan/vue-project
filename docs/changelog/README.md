# 修改清单（Daily Change Log）

> 本目录以**日期为文件名**记录每天的需求与改动，作为「提交即留痕」的日常变更账本。
> 规则补充在 [AGENTS.md](../../AGENTS.md) §2.4「提交留痕」中：**每次提交（commit/push）前，
> 必须把当天完成的需求与改动追加到对应日期的文件**。

---

## 1. 目录结构与命名

```
docs/changelog/
├── README.md            # 本文件（约定 + 模板）
├── 2026-08-29.md        # 以日期命名：yyyy-MM-dd.md
├── 2026-08-30.md
└── ...
```

- 文件名：`yyyy-MM-dd.md`（当天的日期）。
- 一天一个文件；同一天多次提交**追加**到同一文件，不新建。
- 全量描述以 `ls docs/changelog/` 可快速纵览某历史日期的改动。

---

## 2. 何时写入（触发点）

| 时机 | 动作 |
|------|------|
| 每次**需求变更/代码提交**前 | 在当天文件中追加一段「需求 + 改动」记录 |
| 每个**功能/接口/配置/SQL**改动 | 记录模块、接口、文件、涉及点 |
| 每次**发布/回滚** | 在当天文件记录，并另在 `docs/ops/release-notes/` 登记（二选一都留痕） |
| **跨天持续开发** | 在完成当天的节点、或次日开头补记前一天 |

> 目的：任何时候回溯某天做了什么、为什么做、改动了哪些文件，无需翻 git log。

---

## 3. 单条记录格式（推荐模板）

```markdown
### [HH:MM] 需求 / 主题（可写 Ticket/单号 / 需求来源）

- **需求**：一句话描述业务诉求与目标。
- **改动**：
  - 模块/文件：改动清单（含新增/修改/删除），如 `ruoyi-modules/ruoyi-workflow/...`
  - 接口：`GET /workflow/instance/business/{businessKey}`
  - 配置/SQL：如 `config/ruoyi-workflow-dev.yml`、`sql/V2__xx.sql`
  - 文档：如 `docs/modules/ruoyi-workflow.md`
- **验证**：编译/测试/检查结果（如 `mvn compile -pl ... -am` 通过、健康检查）。
- **提交**：`<commit sha>` feat(workflow): ...（可选，提交后回填）。
```

---

## 4. 与 release-notes 的分工

| 文档 | 粒度 | 使用 |
|------|------|------|
| `docs/changelog/<date>.md` | **每日/每次改动**（细） | 开发期留痕 |
| `docs/ops/release-notes/<ver>.md` | **每次发布版本**（粗，变更聚合） | 上线归档 |

---

## 5. 相关文档

- 全局规范：[../../AGENTS.md](../../AGENTS.md)
- 流程总览：[../../docs/process/00-overview.md](../../docs/process/00-overview.md)
- 发布说明模板：[../../docs/ops/release-notes-template.md](../../docs/ops/release-notes-template.md)
