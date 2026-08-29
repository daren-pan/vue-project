# RuoYi-Cloud 项目指令

> 本文件为 ZCode 工作区级指令文件（每次会话自动加载）。
> 内容与 `CLAUDE.md`、`.github/copilot-instructions.md` 保持一致，修改时请同步更新。

## 编译验证规则

每次使用 Edit / Write 等工具修改 `.java` 文件后，**必须立即**执行以下操作：

1. 找到该文件所属的 Maven 模块（向上查找最近的 `pom.xml`）
2. 运行：`mvn compile -pl <模块路径> -am -q 2>&1`
3. 如果编译失败：
   - 分析错误消息
   - 自动修复代码
   - 再次编译直到通过
4. 如果编译通过，简短输出 "✅ 编译通过"

## Vue/JS 文件

修改 `.vue` / `.js` / `.ts` 文件后，通过 lint / 构建检查错误，有错误立即修复。

## 原则

- 这不是可选的建议，是**必须执行**的步骤
- 编译失败不要等用户提醒，主动修复
- 最终目标：用户不需要手动跑任何验证
- **批量修改代码必须逐个文件使用 Edit 工具**，禁止使用 Bash 执行 PowerShell 批量替换命令（如 `Get-Content | ForEach-Object | Set-Content`），防止中文注释和字符乱码

## 文件操作说明

每次对文件执行**新增、修改、删除**操作时，必须：

1. **解释原因**：说明本次操作的目的（解决什么问题 / 实现什么功能 / 为什么需要删除）
2. **提供验证方法**：给出如何验证改动正确的方式（编译命令、测试命令、检查点等），并执行验证、报告结果

## 数据查询规范

需要查询数据库数据（如业务表、Nacos 配置表 `ry-config.config_info` 等）时：

1. **优先使用 MCP 工具查询**（`mysql-ry` / `mysql-flowable`），不用进 Docker 或终端敲命令
2. 只有 MCP 工具不可用（未配置、连不上、权限不足）时，才改用 `docker exec` 或终端命令等替代方式
3. 查询 Nacos 配置表时注意：库名是反引号包裹的 `ry-config`（带连字符），表为 `ry-config.config_info`

## 项目模块编码规范（参照 ruoyi-system，所有新增模块必须遵守）

### 包结构

```
controller/        — REST 接口，只注入 Service 接口，不注入实现
service/           — Service 接口（I 前缀，如 ISysUserService）
service/impl/      — Service 实现（如 SysUserServiceImpl）
domain/            — 实体类（如 SysUser、SysConfig）
domain/vo/         — VO/DTO 实体类（如 MetaVo、RouterVo）
mapper/            — MyBatis Mapper 接口
config/            — 配置类
```

### Service 分层

- **接口**放在 `service/`，命名 `IXxxService`
- **实现**放在 `service/impl/`，命名 `XxxServiceImpl`
- Controller 只注入接口：`@Autowired private IXxxService xxxService;`

### DTO/VO 风格

- **必须使用标准 getter/setter**，与 `SysUser`、`SysAuditLog` 一致
- **禁止** builder 链式模式（如 `XxxDTO.ok().field(x).field2(y)`）
- **每个字段必须有 `/** 注释 */`**
- 正确写法：
  ```java
  XxxVO vo = new XxxVO();
  vo.setField1(x);
  vo.setField2(y);
  return vo;
  ```

### Controller 职责

- **仅做**：参数校验、调用 Service、返回结果
- **禁止**：任何业务逻辑、BPMN 解析、流式 Map 拼装

### 异常处理

- 模块专用异常放在 `ruoyi-common-core` 的 `exception/` 包
- 在 `GlobalExceptionHandler` 添加对应的 `@ExceptionHandler`
- 业务异常**禁止**使用裸 `RuntimeException`

### 实体类返回

- 查询接口优先返回强类型 VO/DTO（如 `R<List<XxxVO>>`），禁止散落 `Map<String, Object>`
- 泛型推断失败时使用显式类型：`R.<TaskResult>ok(...)`

---

## 工作流模块（ruoyi-workflow）专项规范

以下为工作流模块特有的规范，通用规范见上方。

### 编译命令

```
mvn compile -pl ruoyi-modules/ruoyi-workflow -am -q 2>&1
```

---

## Agent 全流程规范（本次追加 · 文档驱动）

> 本段为追加的「Agent 遵循的全流程规范」，供 DSH / Claude / ZCode / VS Code Copilot 统一遵循。
> 不影响上方既有规则；两者冲突时以更严格者为准。

### 0. 阶段与模块文档**强制先读** + 上下文可视化（硬性 · 最高优先）

> 本小节为「Agent 全流程规范」的**第一条强制要求**，优先级高于本文件中任何"建议/参考/可选"描述；两者冲突时以本条为准。核心目的：**防止丢失上下文**，保证每轮都基于最新且完整的文档工作。

#### 0.1 修改模块 / 进入阶段前必须先读对应文档（强制）

1. **修改任何模块前**（无论改动大小），必须先 `read` 该模块文档 `docs/modules/<module>.md`。若该文件不存在，应阅读 `docs/architecture/00-architecture-overview.md` 中该模块的职责矩阵与依赖，并在回复中指出现有模块文档缺失。
2. **进入任意生命周期阶段前**，必须先 `read` 对应阶段文档：
   - 总览：`docs/process/00-overview.md`
   - 设计：`docs/process/01-design.md`（Gate G1）
   - 开发：`docs/process/02-development.md`（Gate G2）
   - 测试：`docs/process/03-testing.md`（Gate G3）
   - 生产验收/发布：`docs/process/04-release.md`（Gate G4）
   - 运维：`docs/process/05-ops.md`（Gate G5）
3. 涉及具体改动时还应一并纳入：相关技能 `agent/skills/<domain>-dev/SKILL.md`；跨服务契约 `ruoyi-api`；服务配置 `application.yml`；依赖 `pom.xml`。
4. **未完成上述读取前，禁止开始编码或修改任何文件。**

#### 0.2 上下文加载文件打印（强制 · 便于人工核对）

- 每轮回复开始时（或用户要求核对时），必须把**当前已进入上下文**的 **Markdown 文档（`.md`）**按**加载顺序**逐条列出到回复窗口，供用户核对是否完整、正确。
- **仅列出 `.md` 文档**；代码（`.java`）、配置（`.yml`/`.properties`）、`pom.xml`、目录列举（glob / grep / pwsh）等非 `.md` 内容**一律不列入**清单，避免冗长。
- 每条注明：**文件路径**（用 Markdown 内联代码）+ **来源**（自动注入 / read）。
- 目的：**防止上下文丢失**。用户若发现某 `.md` 文档不在清单内，视为未加载，Agent 应立即补读后再继续。

### 1. 生命周期五阶段（设计 → 开发 → 测试 → 生产验收 → 运维）

任意任务先判定所处阶段并读取 `docs/process/` 对应文档（含 Gate 闸门）：

- 总览：`docs/process/00-overview.md`
- 设计：`docs/process/01-design.md`（Gate G1 评审冻结）
- 开发：`docs/process/02-development.md`（Gate G2 编译/单测/自测）
- 测试：`docs/process/03-testing.md`（Gate G3 测试达标）
- 生产验收 / 发布：`docs/process/04-release.md`（Gate G4 验收 + 回滚）
- 运维：`docs/process/05-ops.md`（Gate G5 SLA/告警）
- 工作流管理：`docs/process/workflow-management.md`（Git 分支/提交/PR/CI-CD）
- 工作树管理：`docs/process/worktree-management.md`（`git worktree` 并行开发）

### 2. Agent 工具链（技能 / MCP）

- 入口：`agent/README.md`（工具层说明与调用次序）。
- 技能：`agent/skills/`（`module-dev` / `workflow-dev` / `db-query` / `release`）。
- MCP：`agent/mcp/mcp.json` 与 `.vscode/mcp.json` 一致（`mysql-ry`→`ry-cloud`、`mysql-flowable`→`ry-flowable`，端口 3307），**只读**；仅在 MCP 不可用时才走 `docker exec`。

### 3. 提交留痕（日修改清单）

**每次 commit/push 前，必须把当天完成的需求与改动追加到 `docs/changelog/<yyyy-MM-dd>.md`**（以日期为名；同一天多次提交追加同一文件，跨天次日补记）。约定见 `docs/changelog/README.md`。记录：需求、改动清单（模块/文件/接口/配置/SQL/文档）、验证结果、commit sha。

### 4. 架构与运维参考

- 总体架构与安全/健壮性/扩展性/数据设计：`docs/architecture/`。
- 运维与发布说明模板：`docs/ops/ops-handbook.md`、`docs/ops/release-notes-template.md`。
