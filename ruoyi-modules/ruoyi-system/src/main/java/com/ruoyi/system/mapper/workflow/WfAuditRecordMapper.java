package com.ruoyi.system.mapper.workflow;

import com.ruoyi.system.domain.workflow.WfAuditRecord;
import java.util.List;

public interface WfAuditRecordMapper {

    WfAuditRecord selectWfAuditRecordById(Long id);

    WfAuditRecord selectByInstanceAndNodeAndAssignee(Long instanceId, String nodeId, String assignee);

    List<WfAuditRecord> selectByInstanceId(Long instanceId);

    List<WfAuditRecord> selectByBusiness(String businessTable, Long businessId);

    List<WfAuditRecord> selectWfAuditRecordList(WfAuditRecord record);

    int insertWfAuditRecord(WfAuditRecord record);

    int updateWfAuditRecord(WfAuditRecord record);

    int deleteWfAuditRecordByInstanceId(Long instanceId);
}
