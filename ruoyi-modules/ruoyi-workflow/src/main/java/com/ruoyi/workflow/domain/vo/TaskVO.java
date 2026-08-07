package com.ruoyi.workflow.domain.vo;

import java.util.Date;
import java.util.Map;

/**
 * 任务 VO（待办/已办通用）
 *
 * @author ruoyi
 */
public class TaskVO {

    /** 任务ID */
    private String taskId;
    /** 任务名称 */
    private String taskName;
    /** 流程实例ID */
    private String processInstanceId;
    /** 流程名称 */
    private String processName;
    /** 创建时间 */
    private Date createTime;
    /** 已办: 开始时间 */
    private Date startTime;
    /** 已办: 结束时间 */
    private Date endTime;
    /** 已办: 耗时(毫秒) */
    private Long duration;
    /** 已办: 任务状态 */
    private String status;
    /** 业务变量 */
    private Map<String, Object> variables;
    /** 是否抄送任务 */
    private boolean ccTask;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }
    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }
    public boolean isCcTask() { return ccTask; }
    public void setCcTask(boolean ccTask) { this.ccTask = ccTask; }
}
