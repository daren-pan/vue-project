package com.ruoyi.system.service.workflow;

import com.ruoyi.system.domain.workflow.WfProcessDefinition;
import java.util.List;

/**
 * 流程定义管理 Service 接口
 * 
 * 提供流程模板的增删改查功能
 * 流程定义是整个工作流的基础，定义了流程的节点、连线、审批人配置等
 */
public interface IWfProcessDefinitionService {

    /**
     * 根据ID查询流程定义
     */
    WfProcessDefinition selectWfProcessDefinitionById(Long id);

    /**
     * 根据流程标识查询最新的可用版本
     * 用于发起流程时获取当前生效的流程模板
     */
    WfProcessDefinition selectWfProcessDefinitionByKey(String processKey);

    /**
     * 分页查询流程定义列表
     * 支持按流程标识、流程名称、状态模糊查询
     */
    List<WfProcessDefinition> selectWfProcessDefinitionList(WfProcessDefinition definition);

    /**
     * 新增流程定义
     */
    int insertWfProcessDefinition(WfProcessDefinition definition);

    /**
     * 修改流程定义
     */
    int updateWfProcessDefinition(WfProcessDefinition definition);

    /**
     * 删除流程定义（逻辑删除，标记 del_flag = '2'）
     * 会级联删除关联的节点定义和连线定义
     */
    int deleteWfProcessDefinitionById(Long id);
}
