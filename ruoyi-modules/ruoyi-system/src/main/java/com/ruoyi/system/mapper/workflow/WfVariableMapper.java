package com.ruoyi.system.mapper.workflow;

import com.ruoyi.system.domain.workflow.WfVariable;
import java.util.List;

public interface WfVariableMapper {

    List<WfVariable> selectByInstanceId(Long instanceId);

    String getValue(Long instanceId, String varName);

    int insertWfVariable(WfVariable variable);

    int deleteWfVariableByInstanceId(Long instanceId);
}
