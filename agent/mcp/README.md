# MCP 配置与复用说明

> 本目录定义本项目使用的 MCP（Model Context Protocol）Server，用于让 AI 协作者**只读**查询数据库，避免进入 Docker/终端敲命令直连。

---

## 1. 配置文件

- `mcp.json`：定义两个 MySQL Server：`mysql-ry`（业务库）与 `mysql-flowable`（工作流库）。

| Server | 目标库 | 默认主机端口 | 环境变量覆盖 | 说明 |
|--------|--------|--------------|--------------|------|
| `mysql-ry` | `ry-cloud` | localhost:3306 | `MCP_MYSQL_HOST/PORT/USER/PASSWORD` | 系统/业务数据 |
| `mysql-flowable` | `ry-flowable` | localhost:3306 | 同上 | 工作流 `act_*` 数据 |

> 连接参数支持 `${VAR:-default}` 语法，可由环境注入生产/测试不同的主机与口令；**禁止**把口令硬编码提交到 git。

---

## 2. 如何加载

### VS Code / Claude / Copilot

将 `mcp.json` 内容合并到 `.vscode/mcp.json`（或 IDE 的 MCP Server 配置），启用后控制台输出 `Discovered N tools` 即成功。

### DSH Agent

在会话中对数据库的查询，直接说明"使用 mysql-ry / mysql-flowable 查询"。DSH 通过客户端插件把 `N` 个 MCP 工具暴露给模型。

### 兜底

若 MCP 未配置/连不上/权限不足，才允许改用：

```bash
docker exec -it <mysql-container> mysql -uroot -p
```

---

## 3. 使用示例（自然语言）

- 「查一下 `sys_user` 表有多少用户」
- 「最近 5 条工作流审批记录」
- 「`act_hi_taskinst` 表结构」
- 「查 Nacos 配置表 `ry-config.config_info`」

> 注意：Nacos 配置表库名带连字符，需用反引号包裹：**\`ry-config\`**。

---

## 4. 只读与安全准则

1. **只读**：MCP 工具仅用于 `SELECT`，任何写库（INSERT/UPDATE/DELETE/DDL）都必须走项目脚本或人工审批。
2. **最小化**：只查需要的列与表，禁止 `SELECT *` 大表全量。
3. **脱敏**：涉及手机号/身份证/银行卡等敏感信息，查询结果按 `ruoyi-common-sensitive` 规则脱敏后再用于分析。
4. **不落库**：临时分析数据不得写入生产库。

---

## 5. 新增数据源

在 `mcp.json` 的 `servers` 新增条目（如 `mysql-seata`、`redis`），并在本项目 `agent/README.md` 的 MCP 清单与 `skills/db-query/SKILL.md` 中登记。

---

## 6. 一致性

本目录与 `./mcp.json`、[../README.md](../README.md)、[../../AGENTS.md](../../AGENTS.md) 保持一致；修改需同步。
