package com.ruoyi.system.mapper.workflow;

import com.ruoyi.system.domain.workflow.WfProcessDefinition;
import java.util.List;

public interface WfProcessDefinitionMapper {

    WfProcessDefinition selectWfProcessDefinitionById(Long id);

    WfProcessDefinition selectWfProcessDefinitionByKey(String processKey);

    List<WfProcessDefinition> selectWfProcessDefinitionList(WfProcessDefinition definition);

    int insertWfProcessDefinition(WfProcessDefinition definition);

    int updateWfProcessDefinition(WfProcessDefinition definition);

    int deleteWfProcessDefinitionById(Long id);
}
