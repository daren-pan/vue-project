package com.ruoyi.workflow.service;

import org.flowable.engine.*;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
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

    public List<ProcessDefinition> listDefinitions() {
        return repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .orderByProcessDefinitionKey().asc()
                .list();
    }

    // ==================== 流程实例 ====================

    public ProcessInstance startProcess(String processKey, Map<String, Object> variables) {
        return runtimeService.startProcessInstanceByKey(processKey, variables);
    }

    public ProcessInstance startProcess(String processKey, String businessKey, Map<String, Object> variables) {
        return runtimeService.startProcessInstanceByKey(processKey, businessKey, variables);
    }

    public List<ProcessInstance> listRunningProcesses() {
        return runtimeService.createProcessInstanceQuery()
                .orderByStartTime().desc()
                .list();
    }

    public ProcessInstance getProcessInstance(String processInstanceId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }

    public Map<String, Object> getVariables(String processInstanceId) {
        return runtimeService.getVariables(processInstanceId);
    }

    public void deleteProcessInstance(String processInstanceId, String reason) {
        runtimeService.deleteProcessInstance(processInstanceId, reason);
    }

    // ==================== 待办任务 ====================

    public List<Task> listTodoTasks(String assignee) {
        return taskService.createTaskQuery()
                .taskAssignee(assignee)
                .orderByTaskCreateTime().desc()
                .list();
    }

    public List<Task> listTasksByInstance(String processInstanceId) {
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list();
    }

    public Task getTask(String taskId) {
        return taskService.createTaskQuery().taskId(taskId).singleResult();
    }

    public Map<String, Object> getTaskVariables(String taskId) {
        return taskService.getVariables(taskId);
    }

    // ==================== 审批操作 ====================

    public void completeTask(String taskId, Map<String, Object> variables) {
        taskService.complete(taskId, variables);
    }

    public void completeTask(String taskId) {
        taskService.complete(taskId);
    }

    public void addComment(String taskId, String processInstanceId, String comment) {
        taskService.addComment(taskId, processInstanceId, comment);
    }

    // ==================== 历史 ====================

    public List<HistoricTaskInstance> listHistoryTasks(String assignee) {
        return historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(assignee)
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .list();
    }

    public List<HistoricTaskInstance> listProcessTrack(String processInstanceId) {
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricTaskInstanceStartTime().asc()
                .list();
    }

    public HistoricProcessInstance getHistoricProcessInstance(String processInstanceId) {
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }
}
