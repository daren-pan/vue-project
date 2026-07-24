package com.ruoyi.workflow.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.workflow.service.FlowableService;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
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
            m.put("createTime", t.getCreateTime());
            m.put("variables", flowableService.getTaskVariables(t.getId()));
            return m;
        }).toList();
        return R.ok(list);
    }

    /**
     * 审批通过
     */
    @PostMapping("/approve")
    public R<Map<String, Object>> approve(@RequestParam String taskId,
                                           @RequestParam(defaultValue = "同意") String comment) {
        Task task = flowableService.getTask(taskId);
        if (task == null) {
            return R.fail("任务不存在或已处理");
        }

        flowableService.addComment(taskId, task.getProcessInstanceId(), comment);
        flowableService.completeTask(taskId, Map.of("approved", true));

        boolean done = flowableService.getProcessInstance(task.getProcessInstanceId()) == null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("action", "通过");
        result.put("processFinished", done);

        if (!done) {
            var nextTasks = flowableService.listTasksByInstance(task.getProcessInstanceId());
            if (!nextTasks.isEmpty()) {
                result.put("nextTask", nextTasks.get(0).getName());
                result.put("nextAssignee", nextTasks.get(0).getAssignee());
            }
        }
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
     * 查询某人已办历史
     */
    @GetMapping("/history")
    public R<List<Map<String, Object>>> history(@RequestParam String assignee) {
        List<HistoricTaskInstance> tasks = flowableService.listHistoryTasks(assignee);
        List<Map<String, Object>> list = tasks.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", t.getId());
            m.put("taskName", t.getName());
            m.put("processInstanceId", t.getProcessInstanceId());
            m.put("startTime", t.getCreateTime());
            m.put("endTime", t.getEndTime());
            m.put("duration", t.getDurationInMillis());
            return m;
        }).toList();
        return R.ok(list);
    }
}
