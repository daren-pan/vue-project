# 工作流模块 (ruoyi-workflow)

独立微服务，端口 **9208**，负责流程定义管理、流程发起、任务审批等全部工作流功能。

---

## API 接口

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
| POST | `/start` | processKey, applicant, 业务参数 | **通用发起流程**，自动查审批链 |
| GET | `/running` | - | 所有运行中实例 |
| GET | `/{processInstanceId}` | 路径参数 | 实例状态（运行中/已结束） |
| GET | `/{processInstanceId}/track` | 路径参数 | 审批轨迹 |

### 任务管理 `/task`

| 方法 | 路径 | 入参 | 功能 |
|------|------|------|------|
| GET | `/todo` | assignee | 某人待办任务 |
| GET | `/history` | assignee | 某人已办历史 |
| POST | `/approve` | taskId, comment | 审批通过 |
| POST | `/reject` | taskId, reason | 审批驳回 |

---

## 核心设计

### 流程变量

- `${deptLeader}` — 部门经理（自动从组织架构查）
- `${parentDeptLeader}` — 上级领导（自动从组织架构查）
- 业务变量由前端表单传入，后端自动合并

### 通用发起接口

```
POST /instance/start?processKey=leave&applicant=admin&days=3
→ 自动查 admin 的审批链 → deptLeader=李四, parentDeptLeader=王五
→ 合并流程变量 → startProcess("leave", {applicant:"admin", days:3, deptLeader:"李四", parentDeptLeader:"王五"})
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

1. `流程配置` 页面填节点+连线 → 部署
2. `apply/forms/` 下创建表单 `.vue` 文件
3. `formRegistry.js` 注册一行映射
