package com.ruoyi.system.mapper.workflow;

import com.ruoyi.system.domain.workflow.WfLineDefinition;
import java.util.List;

public interface WfLineDefinitionMapper {

    WfLineDefinition selectWfLineDefinitionById(Long id);

    List<WfLineDefinition> selectByProcessAndFromNodeId(Long processDefId, String fromNodeId);

    List<WfLineDefinition> selectWfLineDefinitionByProcessDefId(Long processDefId);

    List<WfLineDefinition> selectWfLineDefinitionList(WfLineDefinition definition);

    int insertWfLineDefinition(WfLineDefinition definition);

    int updateWfLineDefinition(WfLineDefinition definition);

    int deleteWfLineDefinitionByProcessDefId(Long processDefId);
}
