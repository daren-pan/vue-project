package com.ruoyi.workflow.domain.vo;

import java.util.Date;
import java.util.Map;

/**
 * 运行中流程实例 VO
 *
 * @author ruoyi
 */
public class ProcessInstanceVO {

    /** 流程实例ID */
    private String processInstanceId;
    /** 启动时间 */
    private Date startTime;
    /** 当前活动节点ID */
    private String activityId;
    /** 流程变量 */
    private Map<String, Object> variables;

    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public String getActivityId() { return activityId; }
    public void setActivityId(String activityId) { this.activityId = activityId; }
    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }
}
