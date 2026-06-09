package com.ruoyi.system.domain.workflow;

import com.ruoyi.common.core.web.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 流程连线定义对象 wf_line_definition
 */
public class WfLineDefinition extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long processDefId;
    private String fromNodeId;
    private String toNodeId;
    private String conditionExpression;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProcessDefId() { return processDefId; }
    public void setProcessDefId(Long processDefId) { this.processDefId = processDefId; }
    public String getFromNodeId() { return fromNodeId; }
    public void setFromNodeId(String fromNodeId) { this.fromNodeId = fromNodeId; }
    public String getToNodeId() { return toNodeId; }
    public void setToNodeId(String toNodeId) { this.toNodeId = toNodeId; }
    public String getConditionExpression() { return conditionExpression; }
    public void setConditionExpression(String conditionExpression) { this.conditionExpression = conditionExpression; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("fromNodeId", getFromNodeId())
                .append("toNodeId", getToNodeId()).toString();
    }
}
