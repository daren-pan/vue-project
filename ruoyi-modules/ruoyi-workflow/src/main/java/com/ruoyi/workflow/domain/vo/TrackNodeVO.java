package com.ruoyi.workflow.domain.vo;

import java.util.Date;

/**
 * 审批轨迹节点 VO
 *
 * @author ruoyi
 */
public class TrackNodeVO {

    /** 节点名称 */
    private String node;
    /** 审批人/操作人 */
    private String assignee;
    /** 开始时间 */
    private Date startTime;
    /** 结束时间 */
    private Date endTime;
    /** 状态: completed / pending */
    private String status;
    /** 审批意见 */
    private String comment;
    /** 操作类型: 通过 / 驳回 / 驳回到上一步 / 加签通过 */
    private String action;

    public String getNode() { return node; }
    public void setNode(String node) { this.node = node; }
    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
