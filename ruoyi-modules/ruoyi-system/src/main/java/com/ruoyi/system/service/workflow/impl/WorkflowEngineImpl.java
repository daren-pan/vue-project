package com.ruoyi.system.service.workflow.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.system.domain.workflow.*;
import com.ruoyi.system.mapper.workflow.*;
import com.ruoyi.system.service.workflow.IWorkflowEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流引擎实现类
 * 
 * 核心职责：
 * 1. 启动流程：创建流程实例，自动流转到第一个审批节点
 * 2. 审批操作：处理同意/驳回，判断联审是否完成，自动进入下一节点
 * 3. 自动流转：根据节点类型（开始/条件/审批/结束）驱动流程前进
 * 4. 审批人解析：根据节点配置动态解析具体审批人
 * 5. 条件判断：根据流程变量判断走哪条连线
 */
@Service
public class WorkflowEngineImpl implements IWorkflowEngine {

    @Autowired
    private WfProcessDefinitionMapper definitionMapper;
    @Autowired
    private WfNodeDefinitionMapper nodeMapper;
    @Autowired
    private WfLineDefinitionMapper lineMapper;
    @Autowired
    private WfProcessInstanceMapper instanceMapper;
    @Autowired
    private WfVariableMapper variableMapper;
    @Autowired
    private WfAuditRecordMapper auditRecordMapper;
    @Autowired
    private WfTaskMapper taskMapper;

    /**
     * 审批人解析器 —— 根据节点配置的审批人类型，解析出具体的审批人列表
     * 
     * 支持类型：
     * - dept_leader：发起人的部门负责人（查SysDeptMapper）
     * - role：指定角色下的所有用户（查SysRoleMapper + SysUserMapper）
     * - user：直接指定用户ID
     * - self_choose：发起人在提交表单时自选的审批人（从变量表读取）
     * - apply_self：发起人自己审批
     *
     * @param node     当前节点定义
     * @param instance 当前流程实例
     * @return 审批人列表（联审时返回多人，串审时只有一人）
     */
    private List<String> resolveAssignees(WfNodeDefinition node, WfProcessInstance instance) {
        String type = node.getAssigneeType();
        String value = node.getAssigneeValue();
        if (type == null) return Collections.emptyList();

        switch (type) {
            case "dept_leader":
                // 模拟：查发起人的部门负责人，实际应注入 SysDeptMapper 查询
                return Collections.singletonList("dept_leader_of_" + instance.getApplicant());
            case "role":
                // 模拟：按角色查用户，实际应注入 SysRoleMapper + SysUserMapper 查询
                return Collections.singletonList("user_of_role_" + value);
            case "user":
                // 直接返回配置的指定用户
                return Collections.singletonList(value);
            case "self_choose": {
                // 从变量表读取发起人自选的审批人
                String chosen = variableMapper.getValue(instance.getId(), "assignee");
                return chosen != null ? Collections.singletonList(chosen) : Collections.emptyList();
            }
            case "apply_self":
                // 发起人自己审批
                return Collections.singletonList(instance.getApplicant());
            default:
                return Collections.singletonList("default_approver");
        }
    }

    /**
     * 条件表达式解析器 —— 判断当前流程实例是否满足指定条件
     * 
     * 支持的表达式格式：变量名 运算符 目标值
     * 例如：days > 3、approved == true、amount >= 5000
     * 
     * @param expression 条件表达式（如 "days > 3"），为空表示无条件满足
     * @param instanceId 流程实例ID，用于从变量表读取变量值
     * @return true=条件成立，false=条件不成立
     */
    private boolean evaluateCondition(String expression, Long instanceId) {
        if (expression == null || expression.isEmpty()) return true;

        // 从变量表取出当前实例的所有变量
        List<WfVariable> variables = variableMapper.selectByInstanceId(instanceId);
        // 注意：如果有重复的 varName，保留最新的值（后覆盖前）
        Map<String, String> varMap = variables.stream()
                .collect(Collectors.toMap(WfVariable::getVarName, WfVariable::getVarValue, (v1, v2) -> v2));

        // 解析表达式：days > 3 → 分割为 ["days", ">", "3"]
        String[] parts = expression.split(" ");
        if (parts.length != 3) return false;

        String varName = parts[0];      // 变量名，如 days
        String operator = parts[1];     // 运算符，如 >
        String targetValue = parts[2];  // 目标值，如 3
        String actualValue = varMap.get(varName);
        if (actualValue == null) return false;

        switch (operator) {
            case "==":
                return actualValue.equals(targetValue);
            case ">":
                return Integer.parseInt(actualValue) > Integer.parseInt(targetValue);
            case "<":
                return Integer.parseInt(actualValue) < Integer.parseInt(targetValue);
            case ">=":
                return Integer.parseInt(actualValue) >= Integer.parseInt(targetValue);
            case "<=":
                return Integer.parseInt(actualValue) <= Integer.parseInt(targetValue);
            default:
                return false;
        }
    }

    /**
     * 根据当前节点和连线条件，找到下一个应该到达的节点
     * 
     * 遍历当前节点的所有出线，找到第一个条件匹配的连线，返回其目标节点ID
     * 如果所有条件都不匹配，抛出异常
     *
     * @param currentNode 当前节点定义
     * @param instance    当前流程实例
     * @return 下一个节点的ID
     */
    private String getNextNodeId(WfNodeDefinition currentNode, WfProcessInstance instance) {
        List<WfLineDefinition> lines = lineMapper.selectByProcessAndFromNodeId(
                instance.getProcessDefId(), currentNode.getNodeId());
        for (WfLineDefinition line : lines) {
            if (evaluateCondition(line.getConditionExpression(), instance.getId())) {
                return line.getToNodeId();
            }
        }
        throw new ServiceException("没有找到匹配的连线条件，节点：" + currentNode.getNodeId());
    }

    /**
     * 判断当前审批节点是否已全部完成
     * 
     * 根据审批方式判断：
     * - single（单人审批）：有一个人完成就算完成
     * - countersign（会签）：所有人都完成才算完成
     * - or_sign（或签）：达到指定票数就算完成
     *
     * @param instance 当前流程实例
     * @param node     当前节点定义
     * @return true=节点已完成可进入下一节点，false=仍需等待
     */
    private boolean isNodeCompleted(WfProcessInstance instance, WfNodeDefinition node) {
        int totalTasks = taskMapper.countByInstanceAndNode(instance.getId(), node.getNodeId());
        int completedTasks = taskMapper.countByInstanceAndNodeAndStatus(instance.getId(), node.getNodeId(), "completed");

        if ("single".equals(node.getApproveMode())) {
            return completedTasks >= 1;
        } else if ("countersign".equals(node.getApproveMode())) {
            return completedTasks >= totalTasks;
        } else if ("or_sign".equals(node.getApproveMode())) {
            return completedTasks >= node.getApproveCount();
        }
        return false;
    }

    /**
     * 创建待办任务 —— 根据审批方式和联审串审类型创建
     * 
     * 串审serial：只创建第一个审批人的待办，该人通过后才创建下一个
     * 联审parallel：一次性创建所有审批人的待办，等全部通过才进入下一节点
     *
     * @param instance 当前流程实例
     * @param node     当前节点定义（包含审批方式、审批人配置等）
     */
    private void createTasks(WfProcessInstance instance, WfNodeDefinition node) {
        List<String> assignees = resolveAssignees(node, instance);

        if ("serial".equals(node.getAuditType())) {
            // 串审：同一时间只有一个人在审批，只创建第一个人的待办
            createSingleTask(instance, node, assignees.get(0));
        } else {
            // 联审：多个人同时审批，为每个人创建待办
            for (String assignee : assignees) {
                createSingleTask(instance, node, assignee);
            }
        }
    }

    /**
     * 创建单条待办任务 —— 同时写入审核记录表和任务表
     * 
     * 审核记录表（wf_audit_record）：记录审批轨迹，供历史查询
     * 任务表（wf_task）：待办列表，供用户查询当前待办
     *
     * @param instance 当前流程实例
     * @param node     当前节点定义
     * @param assignee 具体的审批人
     */
    private void createSingleTask(WfProcessInstance instance, WfNodeDefinition node, String assignee) {
        // 写入审核记录（状态为 pending，表示该节点待审批）
        WfAuditRecord record = new WfAuditRecord();
        record.setInstanceId(instance.getId());
        record.setBusinessTable(instance.getBusinessTable());
        record.setBusinessId(instance.getBusinessId());
        record.setNodeId(node.getNodeId());
        record.setNodeName(node.getNodeName());
        record.setAssignee(assignee);
        record.setStatus("pending");
        auditRecordMapper.insertWfAuditRecord(record);

        // 写入待办任务
        WfTask task = new WfTask();
        task.setInstanceId(instance.getId());
        task.setNodeId(node.getNodeId());
        task.setNodeName(node.getNodeName());
        task.setAssignee(assignee);
        taskMapper.insertWfTask(task);
    }

    /**
     * 【核心方法】流程引擎自动流转
     * 
     * 根据当前节点类型自动执行：
     * - start（开始节点）：无条件走向下一个节点
     * - condition（条件节点）：根据变量条件判断走向
     * - approve（审批节点）：创建待办任务，停止流转等待用户操作
     * - end（结束节点）：标记流程为已完成
     * 
     * 该方法会在以下时机被触发：
     * 1. 发起流程时（startProcess）
     * 2. 审批人操作后（approve）
     * 3. 超时自动处理后（WorkflowTimeoutTask）
     * 
     * @param instanceId 流程实例ID
     */
    @Transactional
    public void runProcess(Long instanceId) {
        WfProcessInstance instance = instanceMapper.selectWfProcessInstanceById(instanceId);
        if (instance == null) return;

        WfProcessDefinition definition = definitionMapper.selectWfProcessDefinitionById(instance.getProcessDefId());

        // 循环执行，直到走到审批节点（等待用户操作）或结束节点
        while (true) {
            String currentNodeId = instance.getCurrentNodeId();
            WfNodeDefinition node = nodeMapper.selectByProcessAndNodeId(definition.getId(), currentNodeId);

            switch (node.getNodeType()) {
                case "start":
                    // 开始节点：无业务逻辑，直接走向下一个节点
                    instance.setCurrentNodeId(getNextNodeId(node, instance));
                    instanceMapper.updateWfProcessInstance(instance);
                    break;

                case "condition":
                    // 条件节点：根据变量值判断走哪条连线
                    instance.setCurrentNodeId(getNextNodeId(node, instance));
                    instanceMapper.updateWfProcessInstance(instance);
                    break;

                case "approve":
                    // 审批节点：创建待办后停止流转，等待审批人操作
                    createTasks(instance, node);
                    return;

                case "end":
                    // 结束节点：标记流程为已完成
                    instance.setStatus("completed");
                    instance.setEndTime(new Date());
                    instance.setCurrentNodeId("end");
                    instanceMapper.updateWfProcessInstance(instance);
                    return;

                default:
                    throw new ServiceException("未知节点类型：" + node.getNodeType());
            }
        }
    }

    // ======================== 对外暴露的接口方法 ========================

    /**
     * 启动流程 —— 由业务方在提交单据时调用
     * 
     * 执行流程：
     * 1. 根据 processKey 查询流程定义
     * 2. 创建流程实例，状态为 running
     * 3. 将业务数据存入变量表（如 days、reason、amount 等）
     * 4. 记录发起审核记录
     * 5. 调用 runProcess 自动流转到第一个待办节点
     * 
     * 调用示例：
     *   Map<String, Object> vars = new HashMap<>();
     *   vars.put("days", 5);
     *   vars.put("reason", "年假");
     *   workflowEngine.startProcess("leave", "张三", vars, "leave_record", 1L);
     *
     * @param processKey   流程标识（如 "leave"、"expense"）
     * @param applicant    发起人用户名
     * @param variables    流程变量（用于条件判断，如 days、amount）
     * @param businessTable 业务表名（如 "leave_record"）
     * @param businessId   业务表主键ID
     * @return 流程实例ID
     */
    @Override
    @Transactional
    public Long startProcess(String processKey, String applicant,
                              Map<String, Object> variables,
                              String businessTable, Long businessId) {
        // 1. 查询流程定义
        WfProcessDefinition def = definitionMapper.selectWfProcessDefinitionByKey(processKey);
        if (def == null) throw new ServiceException("流程定义不存在：" + processKey);

        // 2. 创建流程实例
        WfProcessInstance instance = new WfProcessInstance();
        instance.setProcessDefId(def.getId());
        instance.setProcessDefVersion(def.getVersion());
        instance.setBusinessKey(businessTable + "-" + businessId);
        instance.setBusinessTable(businessTable);
        instance.setBusinessId(businessId);
        instance.setApplicant(applicant);
        instance.setStatus("running");
        instance.setCurrentNodeId("start");
        instanceMapper.insertWfProcessInstance(instance);

        // 3. 保存流程变量（条件快照，后续条件判断使用）
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            WfVariable var = new WfVariable();
            var.setInstanceId(instance.getId());
            var.setVarName(entry.getKey());
            var.setVarValue(String.valueOf(entry.getValue()));
            variableMapper.insertWfVariable(var);
        }

        // 4. 记录发起审核记录
        WfAuditRecord record = new WfAuditRecord();
        record.setInstanceId(instance.getId());
        record.setBusinessTable(businessTable);
        record.setBusinessId(businessId);
        record.setNodeId("start");
        record.setNodeName("发起");
        record.setAssignee(applicant);
        record.setAction("submit");
        record.setStatus("completed");
        record.setCompleteTime(new Date());
        auditRecordMapper.insertWfAuditRecord(record);

        // 5. 执行流程引擎，自动流转到第一个待办节点
        runProcess(instance.getId());

        return instance.getId();
    }

    /**
     * 审批操作 —— 同意或驳回
     * 
     * 执行流程：
     * 1. 完成任务（更新任务表状态为 completed）
     * 2. 记录审批结果到变量表（approved = true/false）
     * 3. 更新审核记录（操作、意见、完成时间）
     * 4. 判断当前节点是否全部完成：
     *    - 同意且节点完成 → 进入下一节点
     *    - 同意但节点未完成（联审） → 等待其他人
     *    - 驳回 → 回到开始节点（简化处理）
     * 5. 如果进入下一节点，继续调用 runProcess 自动流转
     *
     * @param taskId  待办任务ID
     * @param action  操作类型：agree（同意）/ reject（驳回）
     * @param comment 审批意见
     */
    @Override
    @Transactional
    public void approve(Long taskId, String action, String comment) {
        WfTask task = taskMapper.selectWfTaskById(taskId);
        if (task == null) throw new ServiceException("任务不存在");
        if (!"pending".equals(task.getStatus())) throw new ServiceException("任务已处理");

        WfProcessInstance instance = instanceMapper.selectWfProcessInstanceById(task.getInstanceId());
        WfNodeDefinition node = nodeMapper.selectByProcessAndNodeId(instance.getProcessDefId(), task.getNodeId());

        // 1. 完成任务
        task.setStatus("completed");
        task.setCompleteTime(new Date());
        taskMapper.updateWfTask(task);

        // 2. 将审批结果写入变量表，供后续条件判断使用
        WfVariable var = new WfVariable();
        var.setInstanceId(instance.getId());
        var.setVarName("approved");
        var.setVarValue("agree".equals(action) ? "true" : "false");
        variableMapper.insertWfVariable(var);

        // 3. 更新审核记录
        WfAuditRecord record = auditRecordMapper.selectByInstanceAndNodeAndAssignee(
                instance.getId(), task.getNodeId(), task.getAssignee());
        if (record != null) {
            record.setStatus("completed");
            record.setAction(action);
            record.setComment(comment);
            record.setCompleteTime(new Date());
            auditRecordMapper.updateWfAuditRecord(record);
        }

        // 4. 判断是否进入下一节点
        if ("agree".equals(action)) {
            if (isNodeCompleted(instance, node)) {
                // 当前节点所有审批人都已通过 → 进入下一节点
                instance.setCurrentNodeId(getNextNodeId(node, instance));
                instanceMapper.updateWfProcessInstance(instance);
                runProcess(instance.getId());
            }
            // 联审中还有人没批，不做任何操作，等待其他人审批
        } else {
            // 驳回：回到开始节点（简化处理，实际可配置驳回到指定节点）
            instance.setCurrentNodeId("start");
            instanceMapper.updateWfProcessInstance(instance);
            runProcess(instance.getId());
        }
    }

    /**
     * 查询指定用户的待办任务列表
     * 
     * 关联流程实例表一起查询，返回包含业务信息的待办数据
     *（前端需要根据 businessTable 和 businessId 展示具体业务详情）
     *
     * @param assignee 用户名
     * @return 待办任务列表（含 businessTable、businessKey、businessId、applicant）
     */
    @Override
    public List<WfTask> selectTasksByAssignee(String assignee) {
        return taskMapper.selectByAssignee(assignee);
    }

    /**
     * 查询审批轨迹 —— 根据业务单据查询完整的审批历史
     * 
     * 用于前端展示"审批详情"页面，按时间顺序显示所有操作记录
     * 例如：张三发起 → 赵六同意 → 钱七同意
     *
     * @param businessTable 业务表名
     * @param businessId    业务表主键ID
     * @return 按时间排序的审批记录列表
     */
    @Override
    public List<WfAuditRecord> selectAuditRecords(String businessTable, Long businessId) {
        return auditRecordMapper.selectByBusiness(businessTable, businessId);
    }
}
