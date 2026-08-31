package com.ruoyi.workflow.domain.vo;

import java.util.Date;
import java.util.List;

/**
 * 流程实例状态 VO
 *
 * @author ruoyi
 */
public class InstanceStatusVO {

    /** 流程实例ID */
    private String processInstanceId;
    /** 关联业务单据 key */
    private String businessKey;
    /** 运行中 / 已结束 / 不存在 */
    private String status;
    /** 当前活动节点（运行中） */
    private String activityId;
    /** 流程开始时间 */
    private Date startTime;
    /** 流程结束时间 */
    private Date endTime;
    /** 当前待办任务 */
    private List<TaskBrief> currentTasks;

    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getActivityId() { return activityId; }
    public void setActivityId(String activityId) { this.activityId = activityId; }
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }
    public List<TaskBrief> getCurrentTasks() { return currentTasks; }
    public void setCurrentTasks(List<TaskBrief> currentTasks) { this.currentTasks = currentTasks; }

    /** 任务简要信息 */
    public static class TaskBrief {
        /** 任务ID */
        private String taskId;
        /** 任务名称 */
        private String taskName;
        /** 审批人 */
        private String assignee;

        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public String getTaskName() { return taskName; }
        public void setTaskName(String taskName) { this.taskName = taskName; }
        public String getAssignee() { return assignee; }
        public void setAssignee(String assignee) { this.assignee = assignee; }
    }
}
