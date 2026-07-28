package com.ruoyi.workflow.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.system.api.RemoteUserService;
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

    @Autowired
    private RemoteUserService remoteUserService;

    /**
     * 通用发起流程
     * <p>
     * 前端只传业务参数，后端自动查审批链（部门经理、上级领导），找不到默认 admin。
     *
     * @param processKey 流程标识，必填，如 "leave"、"cost"
     * @param applicant  申请人用户名，必填，用于查询组织架构中的审批链
     * @param allParams  业务参数 Map，Spring 自动收集所有未匹配的 @RequestParam，
     *                   如 days=3、amount=5000 等，会合并到流程变量中
     * @return { processInstanceId: "xxx", tip: "流程已发起" }
     */
    @PostMapping("/start")
    public R<Map<String, Object>> start(@RequestParam String processKey,
                                         @RequestParam String applicant,
                                         @RequestParam Map<String, Object> allParams) {
        Map<String, String> approvers = Map.of("deptLeader", "admin", "parentDeptLeader", "admin");
        try {
            R<Map<String, String>> r = remoteUserService.getApprovers(applicant, SecurityConstants.FROM_SOURCE);
            if (r != null && r.getData() != null) approvers = r.getData();
        } catch (Exception ignored) {}

        Map<String, Object> vars = new HashMap<>(allParams);
        vars.remove("processKey");
        vars.put("deptLeader", approvers.get("deptLeader"));
        vars.put("parentDeptLeader", approvers.get("parentDeptLeader"));

        ProcessInstance instance = flowableService.startProcess(processKey, vars);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processInstanceId", instance.getId());
        result.put("tip", "流程已发起");
        return R.ok(result);
    }

    /**
     * 查询所有运行中的流程实例
     *
     * @return [{ processInstanceId, startTime, activityId, variables }]
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
     * 查询流程实例状态（运行中/已结束/不存在）
     *
     * @param processInstanceId 流程实例 ID
     * @return { status, activityId（运行中时）, currentTasks, startTime, endTime }
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
     * 查询审批轨迹（所有历史任务节点）
     *
     * @param processInstanceId 流程实例 ID
     * @return [{ taskId, taskName, assignee, startTime, endTime, duration }]
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
     * 一键演示：≤3天请假（无需总监审批）
     * <p>
     * 自动发起→审批→完成，用于快速测试流程。
     *
     * @return { processInstanceId, steps: [{ step, desc }] }
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
     * 一键演示：>3天请假（需总监审批）
     * <p>
     * 自动发起→经理审批→总监审批→完成，用于快速测试完整流程。
     *
     * @return { processInstanceId, steps: [{ step, desc }] }
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
