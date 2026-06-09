package com.ruoyi.system.mapper.workflow;

import com.ruoyi.system.domain.workflow.WfNodeDefinition;
import java.util.List;

public interface WfNodeDefinitionMapper {

    WfNodeDefinition selectWfNodeDefinitionById(Long id);

    List<WfNodeDefinition> selectWfNodeDefinitionByProcessDefId(Long processDefId);

    WfNodeDefinition selectByProcessAndNodeId(Long processDefId, String nodeId);

    List<WfNodeDefinition> selectWfNodeDefinitionList(WfNodeDefinition definition);

    int insertWfNodeDefinition(WfNodeDefinition definition);

    int updateWfNodeDefinition(WfNodeDefinition definition);

    int deleteWfNodeDefinitionByProcessDefId(Long processDefId);
}
