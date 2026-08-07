package com.ruoyi.workflow.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * 工作流统一响应实体
 *
 * @author ruoyi
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private String taskId;
    /** 流程实例ID */
    private String processInstanceId;
    /** 部署ID */
    private String deploymentId;
    /** 流程名称 */
    private String processName;
    /** 流程标识key */
    private String processKey;
    /** 操作类型：通过 / 驳回 / 驳回到上一步 / 加签 / 已阅 / 强制删除 / 发起 / 撤回 */
    private String action;
    /** 审批意见或驳回原因 */
    private String reason;
    /** 提示信息 */
    private String tip;
    /** 加签人或操作人用户名 */
    private String assignee;
    /** 版本号 */
    private Object version;
    /** 是否为加签任务 */
    private Boolean coSign;
    /** 流程是否已结束 */
    private Boolean processFinished;

    // ---------- getter / setter ----------
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }

    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public String getProcessKey() { return processKey; }
    public void setProcessKey(String processKey) { this.processKey = processKey; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getTip() { return tip; }
    public void setTip(String tip) { this.tip = tip; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public Object getVersion() { return version; }
    public void setVersion(Object version) { this.version = version; }

    public Boolean getCoSign() { return coSign; }
    public void setCoSign(Boolean coSign) { this.coSign = coSign; }

    public Boolean getProcessFinished() { return processFinished; }
    public void setProcessFinished(Boolean processFinished) { this.processFinished = processFinished; }
}
