# ruoyi-workflow 模块文档

> 工作流模块，基于 Flowable 7 提供流程定义/实例/任务的审批流引擎能力。
> 修改前必读本文件；通用编码规范见 [coding-standard.md](coding-standard.md)。

---

## 1. 职责

- 流程定义管理：BPMN 流程部署、模型、版本。
- 流程实例：发起、审批（同意/驳回）、加签、转办、抄送、作废。
- 任务与待办：待办/已办、任务处理、委托。
- 与业务表集成：通过 `businessKey` 关联业务单，提供 `act_*` 流程数据。

## 2. 包与分层

```
com.ruoyi.workflow
├── controller/   REST 接口
├── service/      IFlowDefinitionService、IFlowInstanceService、IFlowTaskService 等
├── service/impl/
├── domain/       实体 + Flowable 相关模型
├── domain/vo/    强类型 VO/DTO
├── domain/bo/    强类型入参
├── mapper/       MyBatis Mapper
└── config/       Flowable / 流程配置
```

## 3. 依赖

- 公共：`common-core`、`common-security`、`common-redis`、`common-datasource`。
- 跨服务契约：`api-workflow`。
- 引擎：`flowable` 7.0.1（BPMN）。

## 4. 关键能力 / 接口

| 类型 | 说明 | 示例 |
|------|------|------|
| 流程定义 | 部署、列表、删除、激活 | `/workflow/definition/**` |
| 流程实例 | 发起、详情、作废 | `/workflow/instance/**` |
| 任务 | 待办、处理（同意/驳回/加签/转办/抄送） | `/workflow/task/**` |
| 关联业务 | 按 `businessKey` 拉取流程状态 | `/workflow/instance/business/{businessKey}` |

## 5. 数据与配置

- 库：`ry-flowable`（独立库，`act_*` 表）；业务表在 `ry-cloud`。
- `application.yml`：服务名 `ruoyi-workflow`、端口 `9210`。
- Nacos 模板：`config/ruoyi-workflow-<profile>.yml`。

## 6. 编译 / 启动

```bash
mvn compile -pl ruoyi-modules/ruoyi-workflow -am -q 2>&1
```

> 专项开发指南：`agent/skills/workflow-dev/SKILL.md`。

---

## 变更记录

| 日期 | 内容 | 验证 |
|------|------|------|
| — | 初始建立 | — |
