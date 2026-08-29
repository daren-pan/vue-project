---
name: workflow-dev
description: RuoYi-Cloud 工作流模块开发指南，覆盖审批、驳回、加签、抄送、部署、Flowable 集成等全流程。编辑 ruoyi-modules/ruoyi-workflow 相关代码时自动加载。
---

# 工作流模块开发指南（workflow-dev）

## 1. 项目结构

```
ruoyi-modules/ruoyi-workflow/
├── controller/          → 只注入 Service 接口，不做业务逻辑
│   ├── TaskController.java            → 注入 IWorkflowTaskService
│   ├── ProcessInstanceController.java → 注入 IWorkflowInstanceService
│   └── ProcessDefinitionController.java → 注入 IWorkflowDefinitionService
├── service/
│   ├── IWorkflowTaskService.java
│   ├── IWorkflowInstanceService.java
│   ├── IWorkflowDefinitionService.java
│   ├── FlowableService.java          → Flowable API 封装
│   └── ProcessBuilder.java           → Java DSL 构建 BPMN
├── service/impl/
│   ├── WorkflowTaskServiceImpl.java
│   ├── WorkflowInstanceServiceImpl.java
│   └── WorkflowDefinitionServiceImpl.java
├── domain/vo/
│   ├── TaskResult.java / TaskVO.java / ProcessDefinitionVO.java / ProcessInstanceVO.java
│   ├── TrackNodeVO.java / InstanceStatusVO.java / ProcessConfigDTO.java
└── config/
    └── FlowableEngineConfig.java
```

## 2. 核心编码规范

- 接口流程：`Controller → Service 接口 → Service 实现 → 编译验证`。
- Controller 只校验+调用+返回；**禁止** BPMN 解析、流程变量拼装、审批链判断。
- VO 用标准 getter/setter；每个字段带注释；返回强类型。

### Controller 模板

```java
@RestController
@RequestMapping("/xxx")
public class XxxController extends BaseController {
    @Autowired
    private IXxxService xxxService;   // 注接口，不注实现

    @PostMapping("/action")
    public R<TaskResult> action(@RequestParam String param) {
        return R.ok(xxxService.doAction(param));
    }
}
```

### VO 模板

```java
public class XxxVO {
    /** 字段注释 */
    private String fieldName;
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
}
```

## 3. 关键业务逻辑位置

| 功能 | 位置 |
|------|------|
| 审批通过（含加签） | `WorkflowTaskServiceImpl.approve()` |
| 驳回到上一节点 | `WorkflowTaskServiceImpl.rollback()` |
| 加签 + 计数管理 | `WorkflowTaskServiceImpl.addSign()` |
| 流程结束时抄送 | `WorkflowTaskServiceImpl.createCcTasks()` |
| 审批轨迹构建 | `WorkflowInstanceServiceImpl.buildTrack()` |
| 按业务单据查询完整流程 | `WorkflowInstanceServiceImpl.listFlowByBusinessKey()` + `FlowableService.getFlowByBusinessKey()` |
| 发起流程（含会签注入） | `WorkflowInstanceServiceImpl.startProcess()` |
| BPMN 解析提取 | `WorkflowDefinitionServiceImpl.extractConfig()` |

## 4. 数据库

- 业务库：`ry-cloud`（sys_user 等）
- 工作流库：`ry-flowable`（act_re_procdef、act_hi_taskinst、act_hi_procinst、act_ru_* 等）
- MCP 可只读查询：`mysql-flowable`（见 `agent/mcp/mcp.json`）。

## 5. 配置项（application.yml）

- `spring.flowable.database-schema-update=true`（开发环境）
- `ruoyi.workflow.*` 业务参数（见 `config/ruoyi-workflow-*.yml`）

## 6. 编译命令

```bash
mvn compile -pl ruoyi-modules/ruoyi-workflow -am -q 2>&1
```

## 7. 发布与回滚注意

- 流程定义（BPMN/流程XML）变更属于**结构变更**，需评估存量实例，必要时停机维护或版本化流程 key。
- 新增流程变量/节点需同步更新 `ProcessConfigDTO` 与对应活动表单。
