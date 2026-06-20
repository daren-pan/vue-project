package com.ruoyi.system.service.workflow.impl;

import com.ruoyi.common.core.text.Convert;
import com.ruoyi.system.domain.workflow.WfProcessDefinition;
import com.ruoyi.system.mapper.workflow.WfProcessDefinitionMapper;
import com.ruoyi.system.mapper.workflow.WfNodeDefinitionMapper;
import com.ruoyi.system.mapper.workflow.WfLineDefinitionMapper;
import com.ruoyi.system.service.workflow.IWfProcessDefinitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 流程定义管理 Service 实现类
 * 
 * 提供流程定义的增删改查功能，删除时会级联删除关联的节点和连线定义
 */
@Service
public class WfProcessDefinitionServiceImpl implements IWfProcessDefinitionService {

    @Autowired
    private WfProcessDefinitionMapper definitionMapper;

    @Autowired
    private WfNodeDefinitionMapper nodeMapper;

    @Autowired
    private WfLineDefinitionMapper lineMapper;

    /**
     * 根据ID查询流程定义
     */
    @Override
    public WfProcessDefinition selectWfProcessDefinitionById(Long id) {
        return definitionMapper.selectWfProcessDefinitionById(id);
    }

    /**
     * 根据流程标识查询最新的一个可用版本
     * 用于启动流程时获取当前生效的流程定义
     */
    @Override
    public WfProcessDefinition selectWfProcessDefinitionByKey(String processKey) {
        return definitionMapper.selectWfProcessDefinitionByKey(processKey);
    }

    /**
     * 分页查询流程定义列表
     * 支持按流程标识、流程名称、状态模糊查询
     */
    @Override
    public List<WfProcessDefinition> selectWfProcessDefinitionList(WfProcessDefinition definition) {
        return definitionMapper.selectWfProcessDefinitionList(definition);
    }

    /**
     * 新增流程定义
     */
    @Override
    public int insertWfProcessDefinition(WfProcessDefinition definition) {
        return definitionMapper.insertWfProcessDefinition(definition);
    }

    /**
     * 修改流程定义
     */
    @Override
    public int updateWfProcessDefinition(WfProcessDefinition definition) {
        return definitionMapper.updateWfProcessDefinition(definition);
    }

    /**
     * 删除流程定义（逻辑删除）
     * 同时级联删除该流程下的所有节点定义和连线定义
     */
    @Override
    @Transactional
    public int deleteWfProcessDefinitionById(Long id) {
        // 先删除关联的节点和连线
        nodeMapper.deleteWfNodeDefinitionByProcessDefId(id);
        lineMapper.deleteWfLineDefinitionByProcessDefId(id);
        // 再逻辑删除流程定义本身
        return definitionMapper.deleteWfProcessDefinitionById(id);
    }
}
