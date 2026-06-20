package com.ruoyi.system.domain.workflow;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import java.util.Date;

/**
 * 流程变量对象 wf_variable
 */
public class WfVariable {

    private Long id;
    private Long instanceId;
    private String varName;
    private String varValue;
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public String getVarName() { return varName; }
    public void setVarName(String varName) { this.varName = varName; }
    public String getVarValue() { return varValue; }
    public void setVarValue(String varValue) { this.varValue = varValue; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId()).append("varName", getVarName())
                .append("varValue", getVarValue()).toString();
    }
}
