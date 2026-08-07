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

        // 注入会签列表变量（从流程定义 documentation 读取）
        try {
            var pd = flowableService.getLatestProcessDefinition(processKey);
            if (pd != null) {
                var bpmn = flowableService.getBpmnModel(pd.getId());
                if (bpmn != null && bpmn.getProcesses() != null && !bpmn.getProcesses().isEmpty()) {
                    String doc = bpmn.getProcesses().get(0).getDocumentation();
                    if (doc != null) {
                        for (String part : doc.split(";")) {
                            if (part.startsWith("SIGN:")) {
                                String[] kv = part.substring(5).split("=", 2);
                                if (kv.length == 2) {
                                    List<String> users = new ArrayList<>();
                                    for (String u : kv[1].split(",")) {
                                        String resolved = u.trim();
                                        if ("${deptLeader}".equals(resolved)) resolved = approvers.get("deptLeader");
                                        else if ("${parentDeptLeader}".equals(resolved)) resolved = approvers.get("parentDeptLeader");
                                        else if ("${applicant}".equals(resolved)) resolved = applicant;
                                        if (resolved != null && !resolved.isEmpty()) users.add(resolved);
                                    }
                                    if (!users.isEmpty()) body.put("assigneeList_" + kv[0], users);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[workflow] 注入会签列表失败: " + e.getMessage());
        }

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

        // 2. 已完成节点（跳过被取消/删除的任务，如撤回时未审批的节点）
        List<HistoricTaskInstance> tasks = flowableService.listProcessTrack(processInstanceId);
        Set<String> added = new HashSet<>();
        String mainApprover = (String) vars.get("_mainApprover");
        tasks.forEach(t -> {
            if (t.getEndTime() == null) return;
            // 跳过被取消的任务（撤回/驳回时未审批节点的 endTime 是删除时间，非真正审批完成）
            if (t.getDeleteReason() != null) return;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("node", t.getName());
            // 若 assignee 为空（加签场景 setAssignee(null) 后完成），从变量还原审批人
            String assignee = t.getAssignee() != null ? t.getAssignee() : mainApprover;
            m.put("assignee", assignee);
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
            added.add(t.getName() + (assignee != null ? assignee : ""));
        });

        // 3. 当前待审批节点（排除已完成中出现过的）
        boolean mainApprovedFlag = vars.get("_mainApproved") instanceof Boolean b && b;
        flowableService.listTasksByInstance(processInstanceId).forEach(t -> {
            // 加签等待中的主任务：assignee 被置为 null，用 _mainApprover 还原
            String curAssignee = t.getAssignee();
            if (curAssignee == null && mainApprover != null) curAssignee = mainApprover;
            String key = t.getName() + (curAssignee != null ? curAssignee : "");
            if (added.contains(key)) return;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("node", t.getName());
            m.put("startTime", t.getCreateTime());
            m.put("assignee", curAssignee);
            // 主审批人已通过（等待加签中）→ 显示"通过"而非"审批中"
            if (mainApprovedFlag && t.getAssignee() == null) {
                m.put("status", "completed");
                m.put("action", "通过(等待加签)");
            } else {
                m.put("status", "pending");
            }
            list.add(m);
            added.add(key);
        });

        // 4. 流程已撤回/已结束
        if (hi != null && hi.getDeleteReason() != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            if (hi.getDeleteReason().contains("撤回")) {
                m.put("node", "已撤回");
                m.put("action", "撤回");
            } else if (hi.getDeleteReason().contains("驳回")) {
                m.put("node", "已驳回");
                m.put("action", "驳回");
            } else {
                m.put("node", "已结束");
                m.put("action", "结束");
            }
            m.put("assignee", applicant);
            m.put("startTime", hi.getEndTime());
            m.put("endTime", hi.getEndTime());
            m.put("status", "completed");
            m.put("comment", hi.getDeleteReason());
            list.add(m);
        }

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
