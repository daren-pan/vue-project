---
name: ruoyi-workflow-dev
description: RuoYi-Cloud 工作流模块开发指南，覆盖审批、驳回、加签、抄送、部署等全流程
---

# RuoYi-Cloud 工作流模块开发指南

## 项目结构

```
ruoyi-modules/ruoyi-workflow/
├── controller/          → 只注入 Service 接口，不做业务逻辑
│   ├── TaskController.java              → 注入 IWorkflowTaskService
│   ├── ProcessInstanceController.java   → 注入 IWorkflowInstanceService
│   └── ProcessDefinitionController.java → 注入 IWorkflowDefinitionService
├── service/             → 接口层
│   ├── IWorkflowTaskService.java
│   ├── IWorkflowInstanceService.java
│   ├── IWorkflowDefinitionService.java
│   ├── FlowableService.java            → 底层 Flowable API 封装
│   └── ProcessBuilder.java             → Java DSL 构建 BPMN
├── service/impl/        → 实现层
│   ├── WorkflowTaskServiceImpl.java
│   ├── WorkflowInstanceServiceImpl.java
│   └── WorkflowDefinitionServiceImpl.java
├── domain/vo/           → 响应实体（标准 getter/setter）
│   ├── TaskResult.java
│   ├── TaskVO.java
│   ├── ProcessDefinitionVO.java
│   ├── ProcessInstanceVO.java
│   ├── TrackNodeVO.java
│   ├── InstanceStatusVO.java
│   └── ProcessConfigDTO.java
└── config/
    └── FlowableEngineConfig.java
```

## 核心编码规范

### 1. 新增接口流程

```
Controller → Service 接口 → Service 实现 → 编译验证
```

### 2. Controller 模板

```java
@RestController
@RequestMapping("/xxx")
public class XxxController extends BaseController {

    @Autowired
    private IXxxService xxxService;  // 注接口，不注实现

    @PostMapping("/action")
    public R<TaskResult> action(@RequestParam String param) {
        return R.ok(xxxService.doAction(param));
    }
}
```

### 3. VO 模板

```java
public class XxxVO {
    /** 字段注释 */
    private String fieldName;

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
}
```

### 4. 禁止事项

- ❌ Controller 里写业务逻辑（BPMN 解析、流程变量拼装、审批链判断）
- ❌ 返回 `R<Map<String, Object>>`
- ❌ DTO 用 builder 链式模式
- ❌ 用原始 `RuntimeException`

## 关键业务逻辑位置

| 功能 | 位置 |
|------|------|
| 审批通过（含加签逻辑） | `WorkflowTaskServiceImpl.approve()` |
| 驳回到上一节点 | `WorkflowTaskServiceImpl.rollback()` |
| 加签 + 计数管理 | `WorkflowTaskServiceImpl.addSign()` |
| 流程结束时抄送 | `WorkflowTaskServiceImpl.createCcTasks()` |
| 审批轨迹构建 | `WorkflowInstanceServiceImpl.buildTrack()` |
| 发起流程（含会签注入） | `WorkflowInstanceServiceImpl.startProcess()` |
| BPMN 解析提取 | `WorkflowDefinitionServiceImpl.extractConfig()` |

## 数据库

- 业务库：`ry-cloud`（sys_user 等）
- 工作流库：`ry-flowable`（act_hi_taskinst、act_re_procdef 等）
- MCP 可直查（只读），命令：`mvn compile -pl ruoyi-modules/ruoyi-workflow -am -q`

## 编译命令

```bash
mvn compile -pl ruoyi-modules/ruoyi-workflow -am -q 2>&1
```
