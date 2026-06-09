package com.ruoyi.system.domain.workflow;

import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.web.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 流程定义对象 wf_process_definition
 */
public class WfProcessDefinition extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;

    @Excel(name = "流程标识")
    private String processKey;

    @Excel(name = "流程名称")
    private String processName;

    @Excel(name = "版本号")
    private Integer version;

    @Excel(name = "状态")
    private String status;

    private String delFlag;

    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProcessKey() { return processKey; }
    public void setProcessKey(String processKey) { this.processKey = processKey; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("processKey", getProcessKey())
                .append("processName", getProcessName())
                .append("version", getVersion())
                .append("status", getStatus())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("remark", getRemark())
                .toString();
    }
}
