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
     * 查询某人待办任务（含加签任务），附带审批进度预览
     * v2.0 - 支持加签、会签、抄送
     * CI/CD verified - seq build
     *
     * @param assignee 审批人用户名
     * @return [{ taskId, taskName, processInstanceId, processName, createTime, variables, track }]
     */
    @GetMapping("/todo")
    public R<List<Map<String, Object>>> todo(@RequestParam String assignee) {
        List<Task> tasks = flowableService.listTodoTasks(assignee);
        List<Map<String, Object>> list = tasks.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", t.getId());
            m.put("taskName", t.getName());
            String resolvedPiId = t.getProcessInstanceId();
            String pn = "-";

            // 抄送任务：从任务变量取 _ccProcessInstanceId
            if (resolvedPiId == null && t.getName() != null && t.getName().startsWith("[抄送]")) {
                Map<String, Object> tv = flowableService.getTaskVariables(t.getId());
                Object ccPiId = tv.get("_ccProcessInstanceId");
                if (ccPiId != null) resolvedPiId = ccPiId.toString();
                String ccName = t.getName();
                if (ccName.startsWith("[抄送] ")) pn = ccName.substring(5);
            }

            // 加签任务从父任务取流程信息
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
            } catch (Exception e) {
                System.err.println("[workflow] 查询父任务失败(taskId=" + t.getId() + "): " + e.getMessage());
            }
            m.put("processName", pn);
            m.put("processInstanceId", resolvedPiId);
            m.put("createTime", t.getCreateTime());
            // 从流程变量（含历史）读取业务数据
            Map<String, Object> vars = resolvedPiId != null
                ? new HashMap<>(flowableService.getVariables(resolvedPiId))
                : new HashMap<>(flowableService.getTaskVariables(t.getId()));
            // 补 processKey 供前端表单匹配
            if (!vars.containsKey("processKey") && resolvedPiId != null) {
                try {
                    var hi = flowableService.getHistoricProcessInstance(resolvedPiId);
                    if (hi != null && hi.getProcessDefinitionKey() != null) {
                        vars.put("processKey", hi.getProcessDefinitionKey());
                    }
                } catch (Exception ignored) {}
            }
            m.put("variables", vars);
            return m;
        }).toList();
        return R.ok(list);
    }

    /**
     * 审批通过（支持并行加签：主审批 + 加签人全部通过后流程才推进到下一节点）
     *
     * @param taskId  任务 ID
     * @param comment 审批意见，默认 "同意"
     * @return { taskId, action, coSign, processFinished }
     */
    @PostMapping("/approve")
    public R<Map<String, Object>> approve(@RequestParam String taskId,
                                           @RequestParam(defaultValue = "同意") String comment) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) return R.fail("审批失败：任务「" + taskId + "」不存在或已被处理");

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
            // 只有加签主人（任务ID匹配_signOwner）才走等待逻辑
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
            // 流程结束 → 自动抄送申请人 + 配置的抄送人
            Map<String, Object> vars = flowableService.getVariables(piId);
            String applicant = (String) vars.getOrDefault("applicant", "");
            Set<String> ccSet = new LinkedHashSet<>();
            // 申请人默认抄送
            if (!applicant.isEmpty()) ccSet.add(applicant);
            // 从流程变量取配置的抄送人
            Object ccObj = vars.get("ccUsers");
            if (ccObj instanceof List<?> list) {
                list.forEach(u -> { if (u != null) ccSet.add(u.toString()); });
            } else if (ccObj instanceof String s && !s.isEmpty()) {
                for (String u : s.split(",")) ccSet.add(u.trim());
            }
            // 创建抄送任务
            for (String user : ccSet) {
                if (user.isEmpty()) continue;
                Task ccTask = taskService.newTask();
                ccTask.setName("[抄送] " + getProcessNameByInstance(piId));
                ccTask.setAssignee(user);
                taskService.saveTask(ccTask);
                // 只存流程实例ID，业务数据从 act_hi_varinst 查
                taskService.setVariable(ccTask.getId(), "_ccProcessInstanceId", piId);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("action", "通过");
        result.put("coSign", isCoSign);
        result.put("processFinished", done);
        return R.ok(result);
    }

    /**
     * 审批驳回 —— 直接删除流程实例
     *
     * @param taskId 任务 ID
     * @param reason 驳回原因，默认 "不同意"
     * @return { taskId, action, reason }
     */
    @PostMapping("/reject")
    public R<Map<String, Object>> reject(@RequestParam String taskId,
                                          @RequestParam(defaultValue = "不同意") String reason) {
        Task task = flowableService.getTask(taskId);
        if (task == null) {
            return R.fail("驳回失败：任务「" + taskId + "」不存在或已被处理");
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
     * 驳回到上一节点 —— 取消当前任务，流程回退到上一个审批节点
     *
     * @param taskId 任务 ID
     * @param reason 驳回原因，默认 "需修改"
     * @return { taskId, action, reason }
     */
    @PostMapping("/rollback")
    public R<Map<String, Object>> rollback(@RequestParam String taskId,
                                            @RequestParam(defaultValue = "需修改") String reason) {
        Task task = flowableService.getTask(taskId);
        if (task == null) return R.fail("驳回失败：任务「" + taskId + "」不存在或已被处理");
        String piId = task.getProcessInstanceId();

        flowableService.addComment(taskId, piId, "驳回上一步: " + reason);
        try {
            flowableService.rollbackToPrevious(taskId);
        } catch (RuntimeException e) {
            // 无上一节点 → 降级为直接驳回
            flowableService.deleteProcessInstance(piId, "驳回: " + reason);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", taskId);
            result.put("action", "驳回");
            result.put("reason", reason);
            result.put("tip", "已是首个审批节点，已直接驳回");
            return R.ok(result);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("action", "驳回到上一步");
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
        if (task == null) return R.fail("加签失败：任务「" + taskId + "」不存在或已被处理");

        Task signTask = taskService.newTask();
        signTask.setName(task.getName() + "(加签)");
        signTask.setAssignee(assignee);
        signTask.setParentTaskId(task.getId());
        ((TaskEntity) signTask).setProcessInstanceId(task.getProcessInstanceId());
        taskService.saveTask(signTask);

        // 计数 + 标记主人
        Map<String, Object> vars = flowableService.getVariables(task.getProcessInstanceId());
        int count = vars.get("_signCount") instanceof Integer i ? i : 1;
        vars.put("_signCount", count + 1);
        vars.put("_signOwner", taskId);
        runtimeService.setVariables(task.getProcessInstanceId(), vars);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signTaskId", signTask.getId());
        result.put("assignee", assignee);
        result.put("tip", "已加签给 " + assignee);
        return R.ok(result);
    }

    /**
     * 关闭抄送任务（已阅）
     */
    @PostMapping("/dismiss")
    public R<Map<String, Object>> dismiss(@RequestParam String taskId) {
        Task task = flowableService.getTask(taskId);
        if (task == null) return R.fail("任务「" + taskId + "」不存在或已被处理");
        taskService.deleteTask(taskId, "已阅");
        // 清理历史记录
        flowableService.deleteHistoricTask(taskId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("action", "已阅");
        return R.ok(result);
    }

    /**
     * 强制删除流程实例 —— 清理异常/无法通过的问题流程
     *
     * @param processInstanceId 流程实例 ID
     * @return { processInstanceId, action }
     */
    @PostMapping("/deleteInstance")
    public R<Map<String, Object>> deleteInstance(@RequestParam String processInstanceId) {
        flowableService.deleteProcessInstance(processInstanceId, "管理员强制清理");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processInstanceId", processInstanceId);
        result.put("action", "强制删除");
        return R.ok(result);
    }

    /**
     * 查询某人已办历史
     *
     * @param assignee 审批人用户名
     * @return [{ taskId, taskName, processName, processInstanceId, startTime, endTime, duration, status }]
     */
    @GetMapping("/history")
    public R<List<Map<String, Object>>> history(@RequestParam String assignee) {
        List<HistoricTaskInstance> tasks = flowableService.listHistoryTasks(assignee);
        List<Map<String, Object>> list = new ArrayList<>(tasks.stream().map(t -> {
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
        }).toList());

        // 查申请人撤回的流程（无任务记录，通过历史实例查）
        flowableService.listWithdrawnProcesses(assignee).forEach(hi -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", hi.getId());
            m.put("taskName", "发起申请");
            m.put("processName", getProcessNameByInstance(hi.getId()));
            m.put("processInstanceId", hi.getId());
            m.put("startTime", hi.getStartTime());
            m.put("endTime", hi.getEndTime());
            m.put("duration", hi.getDurationInMillis());
            m.put("status", "已撤回");
            list.add(m);
        });

        // 按结束时间倒序
        list.sort((a, b) -> {
            Object ae = a.get("endTime");
            Object be = b.get("endTime");
            if (ae == null && be == null) return 0;
            if (ae == null) return 1;
            if (be == null) return -1;
            return ((java.util.Date) be).compareTo((java.util.Date) ae);
        });
        return R.ok(list);
    }

    /**
     * 根据流程定义 ID 获取流程名称
     */
    private String getProcessName(String processDefinitionId) {
        try {
            ProcessDefinition pd = repositoryService.getProcessDefinition(processDefinitionId);
            return pd != null ? pd.getName() : "-";
        } catch (Exception e) {
            System.err.println("[workflow] 查询流程名称失败(defId=" + processDefinitionId + "): " + e.getMessage());
            return "-";
        }
    }

    /**
     * 根据流程实例 ID 获取流程名称
     */
    private String getProcessNameByInstance(String processInstanceId) {
        try {
            var hi = flowableService.getHistoricProcessInstance(processInstanceId);
            if (hi != null) return getProcessName(hi.getProcessDefinitionId());
        } catch (Exception e) {
            System.err.println("[workflow] 查询历史实例失败(piId=" + processInstanceId + "): " + e.getMessage());
        }
        return "-";
    }
}
