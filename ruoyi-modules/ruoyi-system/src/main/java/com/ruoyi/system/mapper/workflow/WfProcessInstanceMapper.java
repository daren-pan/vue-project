package com.ruoyi.system.mapper.workflow;

import com.ruoyi.system.domain.workflow.WfProcessInstance;
import java.util.List;

public interface WfProcessInstanceMapper {

    WfProcessInstance selectWfProcessInstanceById(Long id);

    WfProcessInstance selectByBusiness(String businessTable, Long businessId);

    List<WfProcessInstance> selectWfProcessInstanceList(WfProcessInstance instance);

    int insertWfProcessInstance(WfProcessInstance instance);

    int updateWfProcessInstance(WfProcessInstance instance);

    int deleteWfProcessInstanceById(Long id);
}
