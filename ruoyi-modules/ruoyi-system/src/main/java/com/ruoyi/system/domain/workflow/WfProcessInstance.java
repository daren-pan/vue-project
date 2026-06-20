package com.ruoyi.system.domain.workflow;

import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.web.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import java.util.Date;

/**
 * 流程实例对象 wf_process_instance
 */
public class WfProcessInstance extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long processDefId;
    private Integer processDefVersion;
    private String businessKey;
    private String businessTable;
    private Long businessId;

    @Excel(name = "发起人")
    private String applicant;

    @Excel(name = "状态")
    private String status;

    private String currentNodeId;

    @Excel(name = "发起时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @Excel(name = "结束时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProcessDefId() { return processDefId; }
    public void setProcessDefId(Long processDefId) { this.processDefId = processDefId; }
    public Integer getProcessDefVersion() { return processDefVersion; }
    public void setProcessDefVersion(Integer processDefVersion) { this.processDefVersion = processDefVersion; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public String getBusinessTable() { return businessTable; }
    public void setBusinessTable(String businessTable) { this.businessTable = businessTable; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public String getApplicant() { return applicant; }
    public void setApplicant(String applicant) { this.applicant = applicant; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId()).append("businessKey", getBusinessKey())
                .append("applicant", getApplicant()).append("status", getStatus())
                .append("currentNodeId", getCurrentNodeId()).toString();
    }
}
