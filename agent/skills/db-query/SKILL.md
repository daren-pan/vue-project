---
name: db-query
description: 数据库只读查询指南，说明如何使用 mysql-ry / mysql-flowable 两个 MCP Server 查询业务库与工作流库。需要查询 ry-cloud 或 ry-flowable 数据时自动加载。
---

# MCP 数据查询指南（db-query）

## 1. 连接概览

| Server | 目标库 | 用途 |
|--------|--------|------|
| `mysql-ry` | `ry-cloud` | 系统/业务数据 |
| `mysql-flowable` | `ry-flowable` | 工作流 `act_*` |

配置见 `agent/mcp/mcp.json`；启动/环境变量见 `agent/mcp/README.md`。

## 2. 使用顺序（严格）

1. 优先用 MCP 工具（只读 SELECT）。
2. 仅在不可用时用 `docker exec` 兜底。
3. **禁止**用 MCP 做写库。

## 3. 常用查询

```sql
-- 用户数
SELECT COUNT(*) FROM sys_user;
-- 最近工作流审批
SELECT * FROM act_hi_taskinst ORDER BY END_TIME_ DESC LIMIT 5;
-- 表结构
SHOW CREATE TABLE act_hi_taskinst;
-- Nacos 配置表（库名带连字符，用反引号）
SELECT * FROM `ry-config`.config_info;
```

## 4. 安全约定

- 只查所需列，禁 `SELECT *` 大表全量。
- 敏感字段（手机号/身份证/银行卡）按 common-sensitive 规则脱敏后再分析。
- 临时分析不落生产库。

## 5. 新增库

在 `agent/mcp/mcp.json` 加 server，并在 `agent/README.md` MCP 清单与本文件列表登记。
