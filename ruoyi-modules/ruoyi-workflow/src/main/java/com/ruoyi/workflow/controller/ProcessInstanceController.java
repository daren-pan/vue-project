package com.ruoyi.workflow.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.workflow.service.FlowableService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 流程实例管理
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/instance")
public class ProcessInstanceController extends BaseController {

    @Autowired
    private FlowableService flowableService;

    /**
     * 发起请假流程
     */
    @PostMapping("/leave/start")
    public R<Map<String, Object>> startLeave(@RequestParam String applicant,
                                              @RequestParam(defaultValue = "1") int days,
                                              @RequestParam(defaultValue = "manager") String manager,
                                              @RequestParam(defaultValue = "director") String director) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("applicant", applicant);
        vars.put("days", days);
        vars.put("manager", manager);
        vars.put("director", director);

        ProcessInstance instance = flowableService.startProcess("leave", vars);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processInstanceId", instance.getId());
        result.put("applicant", applicant);
        result.put("days", days);
        result.put("needDirector", days > 3);
        result.put("tip", days > 3 ? "大于3天，部门经理审批后还需总监审批" : "≤3天，部门经理审批后即结束");
        return R.ok(result);
    }

    /**
     * 运行中的流程实例
     */
    @GetMapping("/running")
    public R<List<Map<String, Object>>> running() {
        List<ProcessInstance> instances = flowableService.listRunningProcesses();
        List<Map<String, Object>> list = instances.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("processInstanceId", i.getId());
            m.put("startTime", i.getStartTime());
            m.put("activityId", i.getActivityId());
            m.put("variables", flowableService.getVariables(i.getId()));
            return m;
        }).toList();
        return R.ok(list);
    }

    /**
     * 流程实例状态
     */
    @GetMapping("/{processInstanceId}")
    public R<Map<String, Object>> status(@PathVariable String processInstanceId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processInstanceId", processInstanceId);

        ProcessInstance instance = flowableService.getProcessInstance(processInstanceId);
        if (instance != null) {
            result.put("status", "运行中");
            result.put("activityId", instance.getActivityId());
            result.put("currentTasks", flowableService.listTasksByInstance(processInstanceId).stream().map(t -> {
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("taskId", t.getId());
                tm.put("taskName", t.getName());
                tm.put("assignee", t.getAssignee());
                return tm;
            }).toList());
        } else {
            var hi = flowableService.getHistoricProcessInstance(processInstanceId);
            if (hi != null) {
                result.put("status", "已结束");
                result.put("startTime", hi.getStartTime());
                result.put("endTime", hi.getEndTime());
            } else {
                result.put("status", "不存在");
            }
        }
        return R.ok(result);
    }

    /**
     * 审批轨迹
     */
    @GetMapping("/{processInstanceId}/track")
    public R<List<Map<String, Object>>> track(@PathVariable String processInstanceId) {
        List<HistoricTaskInstance> tasks = flowableService.listProcessTrack(processInstanceId);
        List<Map<String, Object>> list = tasks.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", t.getId());
            m.put("taskName", t.getName());
            m.put("assignee", t.getAssignee());
            m.put("startTime", t.getStartTime());
            m.put("endTime", t.getEndTime());
            m.put("duration", t.getDurationInMillis());
            return m;
        }).toList();
        return R.ok(list);
    }

    /**
     * 一键演示: ≤3天
     */
    @PostMapping("/demo/simple")
    public R<Map<String, Object>> demoSimple() {
        Map<String, Object> vars = Map.of("applicant", "张三", "days", 2, "manager", "李四", "director", "王五");
        ProcessInstance instance = flowableService.startProcess("leave", vars);

        List<Map<String, String>> steps = new ArrayList<>();
        steps.add(Map.of("step", "1", "desc", "张三发起2天请假"));

        var tasks = flowableService.listTasksByInstance(instance.getId());
        if (!tasks.isEmpty()) {
            flowableService.completeTask(tasks.get(0).getId());
            steps.add(Map.of("step", "2", "desc", "李四审批通过"));
        }

        boolean done = flowableService.getProcessInstance(instance.getId()) == null;
        steps.add(Map.of("step", "3", "desc", done ? "流程已完成（≤3天无需总监）" : "仍在运行"));

        return R.ok(Map.of("processInstanceId", instance.getId(), "steps", steps));
    }

    /**
     * 一键演示: >3天
     */
    @PostMapping("/demo/full")
    public R<Map<String, Object>> demoFull() {
        Map<String, Object> vars = Map.of("applicant", "张三", "days", 5, "manager", "李四", "director", "王五");
        ProcessInstance instance = flowableService.startProcess("leave", vars);

        List<Map<String, String>> steps = new ArrayList<>();
        steps.add(Map.of("step", "1", "desc", "张三发起5天请假, 流程ID: " + instance.getId()));

        var tasks = flowableService.listTasksByInstance(instance.getId());
        if (!tasks.isEmpty()) {
            flowableService.addComment(tasks.get(0).getId(), instance.getId(), "同意");
            flowableService.completeTask(tasks.get(0).getId());
            steps.add(Map.of("step", "2", "desc", "李四(部门经理)审批通过"));
        }

        tasks = flowableService.listTasksByInstance(instance.getId());
        if (!tasks.isEmpty()) {
            flowableService.addComment(tasks.get(0).getId(), instance.getId(), "批准");
            flowableService.completeTask(tasks.get(0).getId());
            steps.add(Map.of("step", "3", "desc", "王五(总监)审批通过"));
        }

        boolean done = flowableService.getProcessInstance(instance.getId()) == null;
        steps.add(Map.of("step", "4", "desc", done ? "流程已完成" : "仍在运行"));

        return R.ok(Map.of("processInstanceId", instance.getId(), "steps", steps));
    }
}
