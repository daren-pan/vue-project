package com.ruoyi.workflow.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.system.api.RemoteUserService;
import com.ruoyi.workflow.service.FlowableService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
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

    @Autowired
    private org.flowable.engine.TaskService taskService;

    /**
     * 通用发起流程（JSON body）
     * <pre>
     * {
     *   "processKey": "leave",    // 必填，流程标识
     *   "applicant": "张三",       // 必填，申请人
     *   "days": 3,                // 业务参数，自动进入流程变量
     *   "reason": "个人原因"        // 其他业务参数...
     * }
     * </pre>
     * 后端自动查审批链（部门经理 deptLeader、上级领导 parentDeptLeader），找不到默认 admin。
     *
     * @return { processInstanceId, tip }
     */
    @PostMapping("/start")
    public R<Map<String, Object>> start(@RequestBody Map<String, Object> body) {
        String processKey = (String) body.remove("processKey");
        String applicant = (String) body.remove("applicant");
        if (processKey == null || applicant == null) {
            return R.fail("processKey 和 applicant 不能为空");
        }

        Map<String, String> approvers = Map.of("deptLeader", "admin", "parentDeptLeader", "admin");
        try {
            R<Map<String, String>> r = remoteUserService.getApprovers(applicant, SecurityConstants.FROM_SOURCE);
            if (r != null && r.getData() != null) approvers = r.getData();
        } catch (Exception e) {
            System.err.println("[workflow] 获取审批链失败(applicant=" + applicant + "): " + e.getMessage());
        }

        // 剩下的 body 字段即为业务变量
        body.put("applicant", applicant);
        body.put("deptLeader", approvers.get("deptLeader"));
        body.put("parentDeptLeader", approvers.get("parentDeptLeader"));

        ProcessInstance instance = flowableService.startProcess(processKey, body);
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
     * 查询审批轨迹 —— 返回发起记录 + 已完成节点 + 待审批节点（含加签任务），前端按 status 着色
     *
     * @param processInstanceId 流程实例 ID
     * @return [{ node, assignee, startTime, endTime, status: "completed"|"pending" }]
     */
    @GetMapping("/{processInstanceId}/track")
    public R<List<Map<String, Object>>> track(@PathVariable String processInstanceId) {
        List<Map<String, Object>> list = new ArrayList<>();

        // 1. 申请人发起记录
        Map<String, Object> vars = flowableService.getVariables(processInstanceId);
        String applicant = vars.getOrDefault("applicant", "未知").toString();

        var hi = flowableService.getHistoricProcessInstance(processInstanceId);
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("node", "发起申请");
        start.put("assignee", applicant);
        start.put("startTime", hi != null ? hi.getStartTime() : null);
        start.put("endTime", hi != null ? hi.getStartTime() : null);
        start.put("status", "completed");
        list.add(start);

        // 2. 已完成节点
        List<HistoricTaskInstance> tasks = flowableService.listProcessTrack(processInstanceId);
        Set<String> added = new HashSet<>();
        tasks.forEach(t -> {
            if (t.getEndTime() == null) return;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("node", t.getName());
            m.put("assignee", t.getAssignee());
            m.put("startTime", t.getStartTime());
            m.put("endTime", t.getEndTime());
            m.put("status", "completed");
            // 查询审批意见
            try {
                List<Comment> comments = taskService.getTaskComments(t.getId());
                if (comments != null && !comments.isEmpty()) {
                    String msg = comments.get(0).getFullMessage();
                    m.put("comment", msg);
                    // 自动识别操作类型
                    if (msg != null) {
                        if (msg.contains("驳回上一步")) m.put("action", "驳回到上一步");
                        else if (msg.contains("驳回")) m.put("action", "驳回");
                        else if (msg.contains("加签审批")) m.put("action", "加签通过");
                        else m.put("action", "通过");
                    }
                }
            } catch (Exception ignored) {}
            list.add(m);
            added.add(t.getName() + t.getAssignee());
        });

        // 3. 当前待审批节点（排除已完成中出现过的）
        flowableService.listTasksByInstance(processInstanceId).forEach(t -> {
            String key = t.getName() + t.getAssignee();
            if (added.contains(key)) return;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("node", t.getName());
            m.put("assignee", t.getAssignee());
            m.put("startTime", t.getCreateTime());
            m.put("status", "pending");
            list.add(m);
            added.add(key);
        });

        return R.ok(list);
    }

    /**
     * 撤回流程 —— 仅申请人可撤回尚未被审批的流程
     *
     * @param processInstanceId 流程实例 ID
     * @return { processInstanceId, action }
     */
    @PostMapping("/{processInstanceId}/withdraw")
    public R<Map<String, Object>> withdraw(@PathVariable String processInstanceId) {
        ProcessInstance pi = flowableService.getProcessInstance(processInstanceId);
        if (pi == null) return R.fail("流程「" + processInstanceId + "」已结束或不存在，请检查流程实例ID");

        Map<String, Object> vars = flowableService.getVariables(processInstanceId);
        String applicant = (String) vars.getOrDefault("applicant", "");
        String currentUser = SecurityUtils.getUsername();

        if (!currentUser.equals(applicant)) {
            return R.fail("当前用户「" + currentUser + "」不是申请人「" + applicant + "」，仅申请人可撤回");
        }

        List<HistoricTaskInstance> finished = flowableService.listFinishedTasks(processInstanceId);
        if (finished != null && !finished.isEmpty()) {
            return R.fail("流程「" + processInstanceId + "」已有审批记录（共" + finished.size() + "条），无法撤回");
        }

        flowableService.deleteProcessInstance(processInstanceId, "申请人撤回");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processInstanceId", processInstanceId);
        result.put("action", "撤回");
        return R.ok(result);
    }
}
