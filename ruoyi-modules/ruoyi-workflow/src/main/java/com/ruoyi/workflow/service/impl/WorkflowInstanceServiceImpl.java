package com.ruoyi.workflow.service.impl;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.WorkflowException;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.system.api.RemoteUserService;
import com.ruoyi.workflow.domain.vo.*;
import com.ruoyi.workflow.service.FlowableService;
import com.ruoyi.workflow.service.IWorkflowInstanceService;
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
