# 工作流模块 (ruoyi-workflow)

独立微服务，端口 **9208**，负责流程定义管理、流程发起、任务审批等全部工作流功能。

---

## 后端 API

### 流程定义 `/definition`

| 方法 | 路径 | 入参 | 功能 |
|------|------|------|------|
| GET | `/list` | - | 所有流程定义列表 |
| POST | `/deploy-table` | ProcessConfigDTO JSON | 表格配置部署新流程 |
| DELETE | `/{deploymentId}` | 路径参数 | 删除指定版本（级联） |
| POST | `/{deploymentId}/apply` | 路径参数 | 克隆该版本为最新版本 |
| GET | `/{deploymentId}/config` | 路径参数 | 提取节点+连线（修改用） |
| GET | `/history/{processKey}` | 路径参数 | 指定 key 的所有历史版本 |

### 流程实例 `/instance`

| 方法 | 路径 | 入参 | 功能 |
|------|------|------|------|
| POST | `/start` | JSON: { processKey, applicant, ...业务变量 } | **通用发起流程**，自动查审批链 |
| GET | `/running` | - | 所有运行中实例 |
| GET | `/{processInstanceId}` | 路径参数 | 实例状态（运行中/已结束） |
| GET | `/{processInstanceId}/track` | 路径参数 | 审批轨迹（含加签） |
| POST | `/{processInstanceId}/withdraw` | 路径参数 | **撤回**：仅申请人可撤回未审批的流程 |

### 任务管理 `/task`

| 方法 | 路径 | 入参 | 功能 |
|------|------|------|------|
| GET | `/todo` | assignee | 某人待办任务（含加签） |
| GET | `/history` | assignee | 某人已办历史 |
| POST | `/approve` | taskId, comment | 审批通过（支持并行加签） |
| POST | `/reject` | taskId, reason | 审批驳回（删除流程实例） |
| POST | `/rollback` | taskId, reason | **驳回上一节点**：回退到上一个审批人 |
| POST | `/addSign` | taskId, assignee | **加签**：并行新增审批人 |

---

## 前端 API (`@/api/workflow/flowable`)

所有方法已封装，用户身份自动注入，调用方只传业务数据：

### 流程定义

```js
listDefinitions()                              // 查所有最新版本
deployTable(config)                            // 表格配置部署
deleteDefinition(deploymentId)                 // 级联删除
applyDefinition(deploymentId)                  // 克隆为新版本
getDefinitionConfig(deploymentId)              // 反解析 JSON 配置
getHistoryVersions(processKey)                 // 查看历史版本
```

### 流程实例

```js
startProcess('leave', { days: 3 })             // 发起流程，applicant 自动注入
listRunningProcesses()                         // 运行中的流程
getProcessStatus(processInstanceId)            // 流程状态
getProcessTrack(processInstanceId)             // 审批轨迹
withdrawProcess(processInstanceId)             // 撤回流程（仅申请人）
```

### 任务管理

```js
listTodoTasks()                                // 待办，默认当前用户
listTodoTasks('zhangsan')                      // 查别人待办
approveTask(taskId)                            // 通过，comment 默认 "同意"
approveTask(taskId, '没问题')
rejectTask(taskId)                             // 驳回（删除流程），reason 默认 "不同意"
rejectTask(taskId, '材料不完整')
rollbackTask(taskId)                           // 驳回上一节点，reason 默认 "需修改"
rollbackTask(taskId, '请补充材料')
addSign(taskId, 'wangwu')                      // 加签并行审批人
listHistoryTasks()                             // 已办，默认当前用户
listHistoryTasks('lisi')                       // 查别人已办
```

---

## 核心设计

### 流程变量

- `${deptLeader}` — 部门经理（自动从 `sys_dept` 查）
- `${parentDeptLeader}` — 上级领导（自动从 `sys_dept` 查）
- `${applicant}` — 申请人（前端自动注入当前用户）
- 业务变量由前端表单传入，后端合并到流程变量

### 通用发起接口

```json
POST /instance/start
Content-Type: application/json

{
  "processKey": "leave",
  "applicant": "admin",
  "days": 3
}
→ 自动查 admin 的审批链 → deptLeader=李四, parentDeptLeader=王五
→ 合并流程变量 → startProcess("leave", {applicant:"admin", days:3, deptLeader:"李四", parentDeptLeader:"王五"})
```

### 并行加签

```
审批人点击加签 → 创建独立任务（标记 parentTaskId）
                 _signCount 计数器 +1
                 主审批人通过 → 暂不完成（等加签人）
                 加签人通过 → _signCount -1
                 _signCount≤1 且主审批已通过 → 完成主任务 → 流程推进
```

### 驳回到上一节点

```
审批人选择"驳回上一步" → 记录驳回原因
                       → 查询最近完成的历史任务作为目标节点
                       → 删除当前活跃任务（含独立加签任务）
                       → ChangeActivityStateBuilder 回退流程到目标活动
                       → 重新分配给上一步的审批人
```

### 撤回

```
申请人点击"撤回" → 校验是否为流程申请人
                → 校验是否有任务已被审批（无则允许撤回）
                → deleteProcessInstance → 流程结束
```

### 后端目录结构

```
ruoyi-modules/ruoyi-workflow/src/main/java/com/ruoyi/workflow/
├── RuoYiWorkflowApplication.java     # 启动类 (端口9208)
├── config/
│   └── FlowableEngineConfig.java     # 数据源 + 引擎配置
├── controller/
│   ├── ProcessDefinitionController.java  # 流程定义接口
│   ├── ProcessInstanceController.java    # 流程实例接口
│   └── TaskController.java              # 任务管理接口
├── core/
│   └── ProcessBuilder.java           # Java DSL 构建 + 部署引擎
├── model/
│   └── ProcessConfigDTO.java         # 前端配置 JSON ↔ 对象
└── service/
    └── FlowableService.java          # 封装 Runtime/Task/History Service
```

### 前端目录结构

```
ruoyi-ui/src/views/workflow/
├── apply/                    # 流程申请
│   ├── index.vue             #   流程列表
│   ├── formRegistry.js       #   表单注册表
│   └── forms/
│       ├── leave.vue         #   请假表单
│       └── cost.vue          #   报销表单
├── config/                   # 流程配置（表格化）
│   └── index.vue
├── definition/               # 流程定义管理
│   └── index.vue
└── task/                     # 我的待办
    └── index.vue
```

### 新增流程步骤

1. `流程配置` 页面填节点 + 连线 → 部署
2. `apply/forms/` 下创建表单 `.vue` 文件（只需业务字段，不需要 applicant）
3. `formRegistry.js` 注册一行映射
