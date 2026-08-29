package com.ruoyi.workflow.service.impl;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.WorkflowException;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.system.api.RemoteUserService;
import com.ruoyi.workflow.domain.vo.*;
import com.ruoyi.workflow.service.FlowableService;
import com.ruoyi.workflow.service.IWorkflowInstanceService;
import com.ruoyi.workflow.service.IWorkflowTaskService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 流程实例服务实现
 *
 * @author ruoyi
 */
@Service
public class WorkflowInstanceServiceImpl implements IWorkflowInstanceService {

    @Autowired
    private FlowableService flowableService;

    @Autowired
    private TaskService taskService;

    @Autowired(required = false)
    private RemoteUserService remoteUserService;

    @Autowired
    private IWorkflowTaskService workflowTaskService;

    @Override
    public TaskResult startProcess(String processKey, String applicant, Map<String, Object> body) {
        Map<String, String> approvers = resolveApprovers(applicant);
        body.put("applicant", applicant);
        body.put("deptLeader", approvers.get("deptLeader"));
        body.put("parentDeptLeader", approvers.get("parentDeptLeader"));
        injectSignAssignees(processKey, body, approvers, applicant);

        ProcessInstance instance = flowableService.startProcess(processKey, body);
        TaskResult r = new TaskResult();
        r.setProcessInstanceId(instance.getId());
        r.setAction("发起");
        r.setTip("流程已发起");
        return r;
    }

    @Override
    public TaskResult withdrawProcess(String processInstanceId) {
        Map<String, Object> vars = flowableService.getVariables(processInstanceId);
        String applicant = (String) vars.getOrDefault("applicant", "");
        String currentUser = SecurityUtils.getUsername();

        if (!currentUser.equals(applicant)) {
            throw new WorkflowException("当前用户「" + currentUser + "」不是申请人「" + applicant + "」，仅申请人可撤回");
        }
        List<HistoricTaskInstance> finished = flowableService.listFinishedTasks(processInstanceId);
        if (finished != null && !finished.isEmpty()) {
            throw new WorkflowException("流程已有审批记录（共" + finished.size() + "条），无法撤回");
        }
        flowableService.deleteProcessInstance(processInstanceId, "申请人撤回");
        TaskResult r = new TaskResult();
        r.setProcessInstanceId(processInstanceId);
        r.setAction("撤回");
        return r;
    }

    @Override
    public TaskResult autoAdvance(String processInstanceId) {
        // 1. 校验流程实例必须是运行中
        ProcessInstance instance = flowableService.getProcessInstance(processInstanceId);
        if (instance == null) {
            var hi = flowableService.getHistoricProcessInstance(processInstanceId);
            if (hi != null) {
                throw new WorkflowException("流程「" + processInstanceId + "」已结束，无法自动推进");
            }
            throw new WorkflowException("流程实例「" + processInstanceId + "」不存在");
        }
        // 2. 校验当前节点有待办任务
        List<Task> activeTasks = flowableService.listTasksByInstance(processInstanceId);
        if (activeTasks == null || activeTasks.isEmpty()) {
            throw new WorkflowException("流程「" + processInstanceId + "」当前节点无待办任务，无法自动推进");
        }
        // 3. 逐个自动通过当前节点任务（复用加签/会签推进逻辑，已处理任务跳过）
        boolean advanced = false;
        for (Task task : activeTasks) {
            try {
                workflowTaskService.approve(task.getId(), "系统自动通过");
                advanced = true;
            } catch (WorkflowException e) {
                // 任务已被处理（如会签主任务已由子任务完成触发），跳过并继续
            }
        }
        if (!advanced) {
            throw new WorkflowException("流程「" + processInstanceId + "」当前节点未发现可自动通过的任务");
        }
        // 4. 计算流程是否已结束，构造返回结果
        boolean done = flowableService.getProcessInstance(processInstanceId) == null;
        TaskResult r = new TaskResult();
        r.setProcessInstanceId(processInstanceId);
        r.setAction("自动通过");
        r.setTip(done ? "流程已自动推进至结束" : "已自动通过当前节点，流程已推进到下一节点");
        r.setProcessFinished(done);
        return r;
    }

    @Override
    public List<ProcessInstanceVO> listRunningInstances() {
        List<ProcessInstance> instances = flowableService.listRunningProcesses();
        List<ProcessInstanceVO> list = new ArrayList<>();
        for (ProcessInstance i : instances) {
            ProcessInstanceVO vo = new ProcessInstanceVO();
            vo.setProcessInstanceId(i.getId());
            vo.setStartTime(i.getStartTime());
            vo.setActivityId(i.getActivityId());
            vo.setVariables(flowableService.getVariables(i.getId()));
            list.add(vo);
        }
        return list;
    }

    @Override
    public InstanceStatusVO getInstanceStatus(String processInstanceId) {
        InstanceStatusVO vo = new InstanceStatusVO();
        vo.setProcessInstanceId(processInstanceId);
        ProcessInstance instance = flowableService.getProcessInstance(processInstanceId);
        if (instance != null) {
            vo.setStatus("运行中");
            vo.setActivityId(instance.getActivityId());
            List<InstanceStatusVO.TaskBrief> briefs = new ArrayList<>();
            for (Task t : flowableService.listTasksByInstance(processInstanceId)) {
                InstanceStatusVO.TaskBrief tb = new InstanceStatusVO.TaskBrief();
                tb.setTaskId(t.getId());
                tb.setTaskName(t.getName());
                tb.setAssignee(t.getAssignee());
                briefs.add(tb);
            }
            vo.setCurrentTasks(briefs);
        } else {
            var hi = flowableService.getHistoricProcessInstance(processInstanceId);
            if (hi != null) {
                vo.setStatus("已结束");
                vo.setStartTime(hi.getStartTime());
                vo.setEndTime(hi.getEndTime());
            } else {
                vo.setStatus("不存在");
            }
        }
        return vo;
    }

    @Override
    public List<TrackNodeVO> buildTrack(String processInstanceId) {
        List<TrackNodeVO> list = new ArrayList<>();
        Map<String, Object> vars = flowableService.getVariables(processInstanceId);
        String applicant = vars.getOrDefault("applicant", "未知").toString();
        var hi = flowableService.getHistoricProcessInstance(processInstanceId);

        TrackNodeVO start = new TrackNodeVO();
        start.setNode("发起申请");
        start.setAssignee(applicant);
        start.setStartTime(hi != null ? hi.getStartTime() : null);
        start.setEndTime(hi != null ? hi.getStartTime() : null);
        start.setStatus("completed");
        list.add(start);

        List<HistoricTaskInstance> tasks = flowableService.listProcessTrack(processInstanceId);
        Set<String> added = new HashSet<>();
        String mainApprover = (String) vars.get("_mainApprover");
        for (HistoricTaskInstance t : tasks) {
            if (t.getEndTime() == null || t.getDeleteReason() != null) continue;
            TrackNodeVO node = new TrackNodeVO();
            node.setNode(t.getName());
            String assignee = t.getAssignee() != null ? t.getAssignee() : mainApprover;
            node.setAssignee(assignee);
            node.setStartTime(t.getStartTime());
            node.setEndTime(t.getEndTime());
            node.setStatus("completed");
            try {
                List<Comment> comments = taskService.getTaskComments(t.getId());
                if (comments != null && !comments.isEmpty()) {
                    String msg = comments.get(0).getFullMessage();
                    node.setComment(msg);
                    if (msg != null) {
                        if (msg.contains("驳回上一步")) node.setAction("驳回到上一步");
                        else if (msg.contains("驳回")) node.setAction("驳回");
                        else if (msg.contains("加签审批")) node.setAction("加签通过");
                        else node.setAction("通过");
                    }
                }
            } catch (Exception ignored) {}
            list.add(node);
            added.add(t.getName() + (assignee != null ? assignee : ""));
        }

        boolean mainApprovedFlag = vars.get("_mainApproved") instanceof Boolean b && b;
        for (Task t : flowableService.listTasksByInstance(processInstanceId)) {
            String curAssignee = t.getAssignee();
            if (curAssignee == null && mainApprover != null) curAssignee = mainApprover;
            String key = t.getName() + (curAssignee != null ? curAssignee : "");
            if (added.contains(key)) continue;
            TrackNodeVO node = new TrackNodeVO();
            node.setNode(t.getName());
            node.setStartTime(t.getCreateTime());
            node.setAssignee(curAssignee);
            if (mainApprovedFlag && t.getAssignee() == null) {
                node.setStatus("completed");
                node.setAction("通过(等待加签)");
            } else {
                node.setStatus("pending");
            }
            list.add(node);
            added.add(key);
        }

        if (hi != null && hi.getDeleteReason() != null) {
            TrackNodeVO node = new TrackNodeVO();
            if (hi.getDeleteReason().contains("撤回")) {
                node.setNode("已撤回");
                node.setAction("撤回");
            } else if (hi.getDeleteReason().contains("驳回")) {
                node.setNode("已驳回");
                node.setAction("驳回");
            } else {
                node.setNode("已结束");
                node.setAction("结束");
            }
            node.setAssignee(applicant);
            node.setStartTime(hi.getEndTime());
            node.setEndTime(hi.getEndTime());
            node.setStatus("completed");
            node.setComment(hi.getDeleteReason());
            list.add(node);
        }
        return list;
    }

    // ==================== 私有辅助 ====================

    private Map<String, String> resolveApprovers(String applicant) {
        Map<String, String> approvers = new HashMap<>();
        approvers.put("deptLeader", "admin");
        approvers.put("parentDeptLeader", "admin");
        try {
            if (remoteUserService != null) {
                R<Map<String, String>> r = remoteUserService.getApprovers(applicant, SecurityConstants.FROM_SOURCE);
                if (r != null && r.getData() != null) approvers = r.getData();
            }
        } catch (Exception e) {
            System.err.println("[workflow] 获取审批链失败(applicant=" + applicant + "): " + e.getMessage());
        }
        return approvers;
    }

    private void injectSignAssignees(String processKey, Map<String, Object> body,
                                      Map<String, String> approvers, String applicant) {
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
    }
}
