package com.ruoyi.workflow.service.impl;

import com.ruoyi.common.core.exception.WorkflowException;
import com.ruoyi.workflow.domain.vo.TaskResult;
import com.ruoyi.workflow.domain.vo.TaskVO;
import com.ruoyi.workflow.service.FlowableService;
import com.ruoyi.workflow.service.IWorkflowTaskService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 任务服务实现
 *
 * @author ruoyi
 */
@Service
public class WorkflowTaskServiceImpl implements IWorkflowTaskService {

    @Autowired
    private FlowableService flowableService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RuntimeService runtimeService;

    @Override
    public List<TaskVO> listTodoTasks(String assignee) {
        List<Task> tasks = flowableService.listTodoTasks(assignee);
        List<TaskVO> list = new ArrayList<>();
        for (Task t : tasks) {
            TaskVO vo = new TaskVO();
            vo.setTaskId(t.getId());
            vo.setTaskName(t.getName());
            String resolvedPiId = t.getProcessInstanceId();
            String pn = "-";

            if (resolvedPiId == null && t.getName() != null && t.getName().startsWith("[抄送]")) {
                Map<String, Object> tv = flowableService.getTaskVariables(t.getId());
                Object ccPiId = tv.get("_ccProcessInstanceId");
                if (ccPiId != null) resolvedPiId = ccPiId.toString();
                if (t.getName().startsWith("[抄送] ")) pn = t.getName().substring(5);
                vo.setCcTask(true);
            }

            try {
                if (t.getParentTaskId() != null) {
                    Task parent = taskService.createTaskQuery().taskId(t.getParentTaskId()).singleResult();
                    if (parent != null) {
                        resolvedPiId = parent.getProcessInstanceId();
                        if (parent.getProcessDefinitionId() != null)
                            pn = flowableService.getProcessName(parent.getProcessDefinitionId());
                    }
                } else if (t.getProcessDefinitionId() != null) {
                    pn = flowableService.getProcessName(t.getProcessDefinitionId());
                }
            } catch (Exception e) {
                System.err.println("[workflow] 查询父任务失败(taskId=" + t.getId() + "): " + e.getMessage());
            }

            vo.setProcessName(pn);
            vo.setProcessInstanceId(resolvedPiId);
            vo.setCreateTime(t.getCreateTime());

            Map<String, Object> vars = resolvedPiId != null
                ? new HashMap<>(flowableService.getVariables(resolvedPiId))
                : new HashMap<>(flowableService.getTaskVariables(t.getId()));
            if (!vars.containsKey("processKey") && resolvedPiId != null) {
                try {
                    var hi = flowableService.getHistoricProcessInstance(resolvedPiId);
                    if (hi != null && hi.getProcessDefinitionKey() != null)
                        vars.put("processKey", hi.getProcessDefinitionKey());
                } catch (Exception ignored) {}
            }
            vo.setVariables(vars);
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<TaskVO> listHistoryTasks(String assignee) {
        List<HistoricTaskInstance> tasks = flowableService.listHistoryTasks(assignee);
        List<TaskVO> list = new ArrayList<>();
        for (HistoricTaskInstance t : tasks) {
            TaskVO vo = new TaskVO();
            vo.setTaskId(t.getId());
            vo.setTaskName(t.getName());
            vo.setProcessName(t.getProcessInstanceId() != null
                    ? flowableService.getProcessNameByInstance(t.getProcessInstanceId()) : "-");
            vo.setProcessInstanceId(t.getProcessInstanceId());
            vo.setStartTime(t.getCreateTime());
            vo.setEndTime(t.getEndTime());
            vo.setDuration(t.getDurationInMillis());
            vo.setStatus(t.getDeleteReason() != null ? "已退回" : "已通过");
            list.add(vo);
        }
        for (var hi : flowableService.listWithdrawnProcesses(assignee)) {
            TaskVO vo = new TaskVO();
            vo.setTaskId(hi.getId());
            vo.setTaskName("发起申请");
            vo.setProcessName(flowableService.getProcessNameByInstance(hi.getId()));
            vo.setProcessInstanceId(hi.getId());
            vo.setStartTime(hi.getStartTime());
            vo.setEndTime(hi.getEndTime());
            vo.setDuration(hi.getDurationInMillis());
            vo.setStatus("已撤回");
            list.add(vo);
        }
        return list;
    }

    @Override
    public TaskResult approve(String taskId, String comment) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw new WorkflowException("审批失败：任务「" + taskId + "」不存在或已被处理");

        String piId = task.getProcessInstanceId();
        boolean isCoSign = task.getParentTaskId() != null;

        if (isCoSign) {
            String parentPiId = taskService.createTaskQuery().taskId(task.getParentTaskId())
                    .singleResult().getProcessInstanceId();
            taskService.addComment(taskId, parentPiId, "加签审批(" + task.getAssignee() + "): " + comment);
            taskService.complete(taskId);

            Map<String, Object> vars = runtimeService.getVariables(parentPiId);
            int count = vars.get("_signCount") instanceof Integer i ? i : 1;
            int newCount = count - 1;
            vars.put("_signCount", newCount);
            runtimeService.setVariables(parentPiId, vars);

            boolean mainApproved = vars.get("_mainApproved") instanceof Boolean b && b;
            if (newCount <= 1 && mainApproved) {
                Task mainTask = taskService.createTaskQuery().taskId(task.getParentTaskId()).singleResult();
                if (mainTask != null) {
                    taskService.setAssignee(mainTask.getId(), (String) vars.get("_mainApprover"));
                    flowableService.completeTask(mainTask.getId(), Map.of("approved", true));
                }
            }
            piId = parentPiId;
        } else {
            flowableService.addComment(taskId, piId, comment);
            Map<String, Object> vars = runtimeService.getVariables(piId);
            String signOwner = (String) vars.get("_signOwner");
            boolean isSignOwner = taskId.equals(signOwner);
            int signCount = vars.get("_signCount") instanceof Integer i ? i : 1;
            if (isSignOwner && signCount > 1) {
                runtimeService.setVariable(piId, "_mainApproved", true);
                runtimeService.setVariable(piId, "_mainApprover", task.getAssignee());
                taskService.setAssignee(taskId, null);
            } else {
                flowableService.completeTask(taskId, Map.of("approved", true));
            }
        }

        boolean done = piId != null && flowableService.getProcessInstance(piId) == null;
        if (done && piId != null) {
            createCcTasks(piId);
        }

        TaskResult r = new TaskResult();
        r.setTaskId(taskId);
        r.setAction("通过");
        r.setCoSign(isCoSign);
        r.setProcessFinished(done);
        return r;
    }

    @Override
    public TaskResult reject(String taskId, String reason) {
        Task task = flowableService.getTask(taskId);
        if (task == null) throw new WorkflowException("驳回失败：任务「" + taskId + "」不存在或已被处理");
        flowableService.addComment(taskId, task.getProcessInstanceId(), "驳回: " + reason);
        flowableService.deleteProcessInstance(task.getProcessInstanceId(), "驳回: " + reason);
        TaskResult r = new TaskResult();
        r.setTaskId(taskId);
        r.setAction("驳回");
        r.setReason(reason);
        return r;
    }

    @Override
    public TaskResult rollback(String taskId, String reason) {
        Task task = flowableService.getTask(taskId);
        if (task == null) throw new WorkflowException("驳回失败：任务「" + taskId + "」不存在或已被处理");
        String piId = task.getProcessInstanceId();
        flowableService.addComment(taskId, piId, "驳回上一步: " + reason);
        try {
            flowableService.rollbackToPrevious(taskId);
        } catch (WorkflowException e) {
            flowableService.deleteProcessInstance(piId, "驳回: " + reason);
            TaskResult r = new TaskResult();
            r.setTaskId(taskId);
            r.setAction("驳回");
            r.setReason(reason);
            r.setTip("已是首个审批节点，已直接驳回");
            return r;
        }
        TaskResult r = new TaskResult();
        r.setTaskId(taskId);
        r.setAction("驳回到上一步");
        r.setReason(reason);
        return r;
    }

    @Override
    public TaskResult addSign(String taskId, String assignee) {
        Task task = flowableService.getTask(taskId);
        if (task == null) throw new WorkflowException("加签失败：任务「" + taskId + "」不存在或已被处理");

        Task signTask = taskService.newTask();
        signTask.setName(task.getName() + "(加签)");
        signTask.setAssignee(assignee);
        signTask.setParentTaskId(task.getId());
        ((TaskEntity) signTask).setProcessInstanceId(task.getProcessInstanceId());
        taskService.saveTask(signTask);

        Map<String, Object> vars = flowableService.getVariables(task.getProcessInstanceId());
        int count = vars.get("_signCount") instanceof Integer i ? i : 1;
        vars.put("_signCount", count + 1);
        vars.put("_signOwner", taskId);
        runtimeService.setVariables(task.getProcessInstanceId(), vars);

        TaskResult r = new TaskResult();
        r.setTaskId(signTask.getId());
        r.setAction("加签");
        r.setAssignee(assignee);
        r.setTip("已加签给 " + assignee);
        return r;
    }

    @Override
    public TaskResult dismiss(String taskId) {
        Task task = flowableService.getTask(taskId);
        if (task == null) throw new WorkflowException("任务「" + taskId + "」不存在或已被处理");
        taskService.deleteTask(taskId, "已阅");
        flowableService.deleteHistoricTask(taskId);
        TaskResult r = new TaskResult();
        r.setTaskId(taskId);
        r.setAction("已阅");
        return r;
    }

    @Override
    public TaskResult deleteInstance(String processInstanceId) {
        flowableService.deleteProcessInstance(processInstanceId, "管理员强制清理");
        TaskResult r = new TaskResult();
        r.setProcessInstanceId(processInstanceId);
        r.setAction("强制删除");
        return r;
    }

    // ==================== 私有辅助 ====================

    private void createCcTasks(String piId) {
        Map<String, Object> vars = flowableService.getVariables(piId);
        String applicant = (String) vars.getOrDefault("applicant", "");
        Set<String> ccSet = new LinkedHashSet<>();
        if (!applicant.isEmpty()) ccSet.add(applicant);
        Object ccObj = vars.get("ccUsers");
        if (ccObj instanceof List<?> list) {
            list.forEach(u -> { if (u != null) ccSet.add(u.toString()); });
        } else if (ccObj instanceof String s && !s.isEmpty()) {
            for (String u : s.split(",")) ccSet.add(u.trim());
        }
        for (String user : ccSet) {
            if (user.isEmpty()) continue;
            Task ccTask = taskService.newTask();
            ccTask.setName("[抄送] " + flowableService.getProcessNameByInstance(piId));
            ccTask.setAssignee(user);
            taskService.saveTask(ccTask);
            taskService.setVariable(ccTask.getId(), "_ccProcessInstanceId", piId);
        }
    }
}
