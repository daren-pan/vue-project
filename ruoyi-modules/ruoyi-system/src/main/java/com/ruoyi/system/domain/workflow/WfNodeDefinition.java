package com.ruoyi.system.domain.workflow;

import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.web.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 流程节点定义对象 wf_node_definition
 */
public class WfNodeDefinition extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long processDefId;

    @Excel(name = "节点标识")
    private String nodeId;

    @Excel(name = "节点名称")
    private String nodeName;

    @Excel(name = "节点类型")
    private String nodeType;

    @Excel(name = "审批人类型")
    private String assigneeType;

    private String assigneeValue;

    @Excel(name = "审批方式")
    private String approveMode;

    private Integer approveCount;

    @Excel(name = "联审串审")
    private String auditType;

    private Integer timeoutHours;
    private String timeoutAction;
    private String actions;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProcessDefId() { return processDefId; }
    public void setProcessDefId(Long processDefId) { this.processDefId = processDefId; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public String getAssigneeType() { return assigneeType; }
    public void setAssigneeType(String assigneeType) { this.assigneeType = assigneeType; }
    public String getAssigneeValue() { return assigneeValue; }
    public void setAssigneeValue(String assigneeValue) { this.assigneeValue = assigneeValue; }
    public String getApproveMode() { return approveMode; }
    public void setApproveMode(String approveMode) { this.approveMode = approveMode; }
    public Integer getApproveCount() { return approveCount; }
    public void setApproveCount(Integer approveCount) { this.approveCount = approveCount; }
    public String getAuditType() { return auditType; }
    public void setAuditType(String auditType) { this.auditType = auditType; }
    public Integer getTimeoutHours() { return timeoutHours; }
    public void setTimeoutHours(Integer timeoutHours) { this.timeoutHours = timeoutHours; }
    public String getTimeoutAction() { return timeoutAction; }
    public void setTimeoutAction(String timeoutAction) { this.timeoutAction = timeoutAction; }
    public String getActions() { return actions; }
    public void setActions(String actions) { this.actions = actions; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId()).append("nodeId", getNodeId())
                .append("nodeName", getNodeName()).append("nodeType", getNodeType()).toString();
    }
}
