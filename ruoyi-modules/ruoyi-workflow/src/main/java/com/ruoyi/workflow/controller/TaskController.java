package com.ruoyi.workflow.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.workflow.service.FlowableService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 任务管理（待办 / 审批 / 已办历史）
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/task")
public class TaskController extends BaseController {

    @Autowired
    private FlowableService flowableService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RuntimeService runtimeService;

    /**
     * 查询某人待办任务
     */
    @GetMapping("/todo")
    public R<List<Map<String, Object>>> todo(@RequestParam String assignee) {
        List<Task> tasks = flowableService.listTodoTasks(assignee);
        List<Map<String, Object>> list = tasks.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", t.getId());
            m.put("taskName", t.getName());
            m.put("processInstanceId", t.getProcessInstanceId());
            // 加签任务从父任务取流程信息
            String pn = "-";
            String resolvedPiId = t.getProcessInstanceId();
            try {
                if (t.getParentTaskId() != null) {
                    Task parent = taskService.createTaskQuery().taskId(t.getParentTaskId()).singleResult();
                    if (parent != null) {
                        resolvedPiId = parent.getProcessInstanceId();
                        if (parent.getProcessDefinitionId() != null) {
                            pn = getProcessName(parent.getProcessDefinitionId());
                        }
                    }
                } else if (t.getProcessDefinitionId() != null) {
                    pn = getProcessName(t.getProcessDefinitionId());
                }
            } catch (Exception ignored) {}
            m.put("processName", pn);
            m.put("processInstanceId", resolvedPiId); // 加签任务用父任务ID，轨迹按钮才能用
            m.put("createTime", t.getCreateTime());
            m.put("variables", flowableService.getTaskVariables(t.getId()));
            if (resolvedPiId != null) {
                m.put("track", flowableService.listProcessTrack(resolvedPiId).stream().map(ht -> {
                    Map<String, Object> tm = new LinkedHashMap<>();
                    tm.put("taskName", ht.getName());
                    tm.put("assignee", ht.getAssignee());
                    tm.put("endTime", ht.getEndTime());
                    return tm;
                }).toList());
            }
            return m;
        }).toList();
        return R.ok(list);
    }

    /**
     * 审批通过（支持并行加签：主审批+加签人全部通过才算通过）
     */
    @PostMapping("/approve")
    public R<Map<String, Object>> approve(@RequestParam String taskId,
                                           @RequestParam(defaultValue = "同意") String comment) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) return R.fail("任务不存在或已处理");

        String piId = task.getProcessInstanceId();
        boolean isCoSign = task.getParentTaskId() != null;

        if (isCoSign) {
            // 加签任务：完成+减计数，若主审批人已通过且所有加签完成则推进主任务
            String parentPiId = taskService.createTaskQuery().taskId(task.getParentTaskId())
                    .singleResult().getProcessInstanceId();
            taskService.addComment(taskId, parentPiId,
                "加签审批(" + task.getAssignee() + "): " + comment);
            taskService.complete(taskId);

            Map<String, Object> vars = runtimeService.getVariables(parentPiId);
            int count = vars.get("_signCount") instanceof Integer i ? i : 1;
            int newCount = count - 1;
            vars.put("_signCount", newCount);
            runtimeService.setVariables(parentPiId, vars);

            // 所有加签完成且主审批人已通过 → 完成主任务推进流程
            boolean mainApproved = vars.get("_mainApproved") instanceof Boolean b && b;
            if (newCount <= 1 && mainApproved) {
                Task mainTask = taskService.createTaskQuery().taskId(task.getParentTaskId()).singleResult();
                if (mainTask != null) {
                    flowableService.completeTask(mainTask.getId(), Map.of("approved", true));
                }
            }
            piId = parentPiId;
        } else {
            // 主任务：有并行加签时暂不完成，等加签人全部通过
            flowableService.addComment(taskId, piId, comment);
            Map<String, Object> vars = runtimeService.getVariables(piId);
            int signCount = vars.get("_signCount") instanceof Integer i ? i : 1;
            if (signCount > 1) {
                // 还有加签人未批，暂存审批结果
                taskService.setVariable(taskId, "_mainApproved", true);
            } else {
                flowableService.completeTask(taskId, Map.of("approved", true));
            }
        }

        boolean done = piId != null && flowableService.getProcessInstance(piId) == null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("action", "通过");
        result.put("coSign", isCoSign);
        result.put("processFinished", done);
        return R.ok(result);
    }

    /**
     * 审批驳回
     */
    @PostMapping("/reject")
    public R<Map<String, Object>> reject(@RequestParam String taskId,
                                          @RequestParam(defaultValue = "不同意") String reason) {
        Task task = flowableService.getTask(taskId);
        if (task == null) {
            return R.fail("任务不存在或已处理");
        }

        flowableService.addComment(taskId, task.getProcessInstanceId(), "驳回: " + reason);
        flowableService.deleteProcessInstance(task.getProcessInstanceId(), "驳回: " + reason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("action", "驳回");
        result.put("reason", reason);
        return R.ok(result);
    }

    /**
     * 加签：在当前任务旁新增一个并行审批任务
     * 
     * @param taskId   当前任务ID
     * @param assignee 加签人用户名
     */
    @PostMapping("/addSign")
    public R<Map<String, Object>> addSign(@RequestParam String taskId,
                                           @RequestParam String assignee) {
        Task task = flowableService.getTask(taskId);
        if (task == null) return R.fail("任务不存在或已处理");

        Task signTask = taskService.newTask();
        signTask.setName(task.getName() + "(加签)");
        signTask.setAssignee(assignee);
        signTask.setParentTaskId(task.getId());
        ((TaskEntity) signTask).setProcessInstanceId(task.getProcessInstanceId());
        taskService.saveTask(signTask);

        // 计数
        Map<String, Object> vars = flowableService.getVariables(task.getProcessInstanceId());
        int count = vars.get("_signCount") instanceof Integer i ? i : 1;
        vars.put("_signCount", count + 1);
        runtimeService.setVariables(task.getProcessInstanceId(), vars);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signTaskId", signTask.getId());
        result.put("assignee", assignee);
        result.put("tip", "已加签给 " + assignee);
        return R.ok(result);
    }

    /**
     * 查询某人已办历史
     */
    @GetMapping("/history")
    public R<List<Map<String, Object>>> history(@RequestParam String assignee) {
        List<HistoricTaskInstance> tasks = flowableService.listHistoryTasks(assignee);
        List<Map<String, Object>> list = tasks.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", t.getId());
            m.put("taskName", t.getName());
            m.put("processName", t.getProcessInstanceId() != null
                ? getProcessNameByInstance(t.getProcessInstanceId()) : "-");
            m.put("processInstanceId", t.getProcessInstanceId());
            m.put("startTime", t.getCreateTime());
            m.put("endTime", t.getEndTime());
            m.put("duration", t.getDurationInMillis());
            m.put("status", t.getDeleteReason() != null ? "已退回" : "已通过");
            return m;
        }).toList();
        return R.ok(list);
    }

    private String getProcessName(String processDefinitionId) {
        try {
            ProcessDefinition pd = repositoryService.getProcessDefinition(processDefinitionId);
            return pd != null ? pd.getName() : "-";
        } catch (Exception e) { return "-"; }
    }

    private String getProcessNameByInstance(String processInstanceId) {
        try {
            var hi = flowableService.getHistoricProcessInstance(processInstanceId);
            if (hi != null) return getProcessName(hi.getProcessDefinitionId());
        } catch (Exception ignored) {}
        return "-";
    }
}
