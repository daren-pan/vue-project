package com.ruoyi.workflow.service;

import org.flowable.engine.*;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Flowable 核心服务 —— 封装常用 API，供 Controller 和 Feign 调用
 *
 * @author ruoyi
 */
@Service
public class FlowableService {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private RepositoryService repositoryService;

    // ==================== 流程定义 ====================

    /**
     * 查询所有最新版本的流程定义
     *
     * @return 按 key 升序排列的流程定义列表
     */
    public List<ProcessDefinition> listDefinitions() {
        return repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .orderByProcessDefinitionKey().asc()
                .list();
    }

    /**
     * 获取指定 key 的最新版流程定义
     */
    public ProcessDefinition getLatestProcessDefinition(String processKey) {
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
    }

    /**
     * 获取流程定义的 BPMN 模型
     */
    public org.flowable.bpmn.model.BpmnModel getBpmnModel(String processDefinitionId) {
        return repositoryService.getBpmnModel(processDefinitionId);
    }

    // ==================== 流程实例 ====================

    /**
     * 按流程 key 启动流程实例
     *
     * @param processKey 流程定义 key
     * @param variables  流程变量
     * @return 新创建的流程实例
     */
    public ProcessInstance startProcess(String processKey, Map<String, Object> variables) {
        return runtimeService.startProcessInstanceByKey(processKey, variables);
    }

    /**
     * 按流程 key 启动流程实例（带业务键）
     *
     * @param processKey  流程定义 key
     * @param businessKey 业务标识，用于关联业务单据
     * @param variables   流程变量
     * @return 新创建的流程实例
     */
    public ProcessInstance startProcess(String processKey, String businessKey, Map<String, Object> variables) {
        return runtimeService.startProcessInstanceByKey(processKey, businessKey, variables);
    }

    /**
     * 查询所有运行中的流程实例
     *
     * @return 按启动时间倒序排列的流程实例列表
     */
    public List<ProcessInstance> listRunningProcesses() {
        return runtimeService.createProcessInstanceQuery()
                .orderByStartTime().desc()
                .list();
    }

    /**
     * 按 ID 查询流程实例（仅运行中）
     *
     * @param processInstanceId 流程实例 ID
     * @return 流程实例，不存在返回 null
     */
    public ProcessInstance getProcessInstance(String processInstanceId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }

    /**
     * 获取流程实例的所有变量（运行中或已结束均可）
     */
    public Map<String, Object> getVariables(String processInstanceId) {
        try {
            return runtimeService.getVariables(processInstanceId);
        } catch (Exception e) {
            // 流程已结束，从历史查
            List<HistoricVariableInstance> hvars =
                historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstanceId).list();
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            for (var hv : hvars) {
                map.put(hv.getVariableName(), hv.getValue());
            }
            return map;
        }
    }

    /**
     * 删除流程实例（驳回/撤回/作废）
     * 先清理独立任务（加签、抄送等无 execution 的任务），避免 NPE
     *
     * @param processInstanceId 流程实例 ID
     * @param reason            删除原因
     */
    public void deleteProcessInstance(String processInstanceId, String reason) {
        // 1. 先清理关联的独立任务（加签、抄送等），这些任务无 execution 会触发 NPE
        List<Task> orphanTasks = taskService.createTaskQuery()
            .processInstanceId(processInstanceId).list();
        for (Task t : orphanTasks) {
            try { taskService.deleteTask(t.getId(), reason); } catch (Exception ignored) {}
        }
        // 2. 再删除流程实例
        try {
            runtimeService.deleteProcessInstance(processInstanceId, reason);
        } catch (Exception e) {
            // 如果运行时已不存在，尝试清理历史
            try {
                historyService.deleteHistoricProcessInstance(processInstanceId);
            } catch (Exception ignored) {}
            // 重新抛出如果不是 NPE（NPE 说明上面已处理干净，流程实际已删）
            if (!(e instanceof NullPointerException)) throw e;
        }
    }

    /**
     * 查询流程实例中已完成的任务列表（按结束时间降序）
     *
     * @param processInstanceId 流程实例 ID
     * @return 已完成的历史任务列表
     */
    public List<HistoricTaskInstance> listFinishedTasks(String processInstanceId) {
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .list();
    }

    /**
     * 驳回到上一节点 —— 取消当前任务，回退到最近完成的用户任务
     *
     * @param taskId 当前任务 ID
     */
    public void rollbackToPrevious(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw new RuntimeException("任务「" + taskId + "」不存在，无法回退");
        String piId = task.getProcessInstanceId();
        if (piId == null) return;

        // 找最近完成的用户任务
        List<HistoricTaskInstance> finished = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(piId)
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .list();
        String targetActivityId = null;
        for (HistoricTaskInstance ht : finished) {
            if (ht.getTaskDefinitionKey() != null) {
                targetActivityId = ht.getTaskDefinitionKey();
                break;
            }
        }
        if (targetActivityId == null) throw new RuntimeException("已是首个审批节点，无法驳回到上一步");

        // 取消当前所有活跃任务
        List<Task> activeTasks = taskService.createTaskQuery().processInstanceId(piId).list();
        for (Task t : activeTasks) {
            if (t.getParentTaskId() != null) {
                taskService.deleteTask(t.getId(), "驳回到上一步");
            }
        }
        // 找到 BPMN 任务（非独立加签）作为回退起点
        String currentDefKey = null;
        for (Task t : activeTasks) {
            if (t.getParentTaskId() == null && t.getTaskDefinitionKey() != null) {
                currentDefKey = t.getTaskDefinitionKey();
                break;
            }
        }
        if (currentDefKey == null) throw new RuntimeException("未找到可回退的当前节点");
        // 移动流程回到上一个节点
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(piId)
                .moveActivityIdTo(currentDefKey, targetActivityId)
                .changeState();
    }

    // ==================== 待办任务 ====================

    /**
     * 查询指定用户的待办任务列表（含普通任务和加签独立任务）
     *
     * @param assignee 审批人用户名
     * @return 按创建时间倒序的待办任务列表
     */
    public List<Task> listTodoTasks(String assignee) {
        return taskService.createTaskQuery()
                .taskAssignee(assignee)
                .orderByTaskCreateTime().desc()
                .list();
    }

    /**
     * 查询指定流程实例的当前活跃任务
     *
     * @param processInstanceId 流程实例 ID
     * @return 活跃任务列表
     */
    public List<Task> listTasksByInstance(String processInstanceId) {
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list();
    }

    /**
     * 按 ID 查询任务
     *
     * @param taskId 任务 ID
     * @return 任务对象，不存在返回 null
     */
    public Task getTask(String taskId) {
        return taskService.createTaskQuery().taskId(taskId).singleResult();
    }

    /**
     * 获取任务级别的变量
     *
     * @param taskId 任务 ID
     * @return 变量 Map
     */
    public Map<String, Object> getTaskVariables(String taskId) {
        return taskService.getVariables(taskId);
    }

    // ==================== 审批操作 ====================

    /**
     * 完成任务（带变量），推进流程到下一节点
     *
     * @param taskId    任务 ID
     * @param variables 提交时携带的变量
     */
    public void completeTask(String taskId, Map<String, Object> variables) {
        taskService.complete(taskId, variables);
    }

    /**
     * 完成任务（无额外变量）
     *
     * @param taskId 任务 ID
     */
    public void completeTask(String taskId) {
        taskService.complete(taskId);
    }

    /**
     * 添加审批意见
     *
     * @param taskId            任务 ID
     * @param processInstanceId 流程实例 ID
     * @param comment           审批意见内容
     */
    public void addComment(String taskId, String processInstanceId, String comment) {
        taskService.addComment(taskId, processInstanceId, comment);
    }

    // ==================== 历史 ====================

    /**
     * 查询指定用户的已办历史任务
     *
     * @param assignee 审批人用户名
     * @return 按完成时间倒序的已办任务列表
     */
    public List<HistoricTaskInstance> listHistoryTasks(String assignee) {
        return historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(assignee)
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .list();
    }

    /**
     * 查询审批轨迹（过滤掉被回退的任务）
     */
    public List<HistoricTaskInstance> listProcessTrack(String processInstanceId) {
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricTaskInstanceEndTime().asc()
                .list().stream()
                .filter(t -> {
                    String r = t.getDeleteReason();
                    return r == null || (!r.contains("change activity") && !r.contains("驳回上一步"));
                })
                .toList();
    }

    /**
     * 删除历史任务（抄送已阅后清理）
     */
    public void deleteHistoricTask(String taskId) {
        try { historyService.deleteHistoricTaskInstance(taskId); } catch (Exception ignored) {}
    }

    /**
     * 查询申请人撤回的流程实例
     */
    public List<HistoricProcessInstance> listWithdrawnProcesses(String applicant) {
        return historyService.createHistoricProcessInstanceQuery()
                .involvedUser(applicant)
                .deleted()
                .orderByProcessInstanceEndTime().desc()
                .list().stream()
                .filter(hi -> {
                    String reason = hi.getDeleteReason();
                    return reason != null && reason.contains("撤回");
                })
                .toList();
    }

    /**
     * 按 ID 查询历史流程实例（已结束的流程）
     *
     * @param processInstanceId 流程实例 ID
     * @return 历史流程实例，不存在返回 null
     */
    public HistoricProcessInstance getHistoricProcessInstance(String processInstanceId) {
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }
}
