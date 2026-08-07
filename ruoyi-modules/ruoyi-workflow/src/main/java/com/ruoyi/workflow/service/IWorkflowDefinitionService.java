package com.ruoyi.workflow.service;

import com.ruoyi.workflow.domain.vo.ProcessConfigDTO;
import com.ruoyi.workflow.domain.vo.ProcessDefinitionVO;
import com.ruoyi.workflow.domain.vo.TaskResult;

import java.util.List;

/**
 * 流程定义服务接口
 *
 * @author ruoyi
 */
public interface IWorkflowDefinitionService {

    /**
     * 查询所有最新版本的流程定义列表
     */
    List<ProcessDefinitionVO> listDefinitions();

    /**
     * 查询指定 key 的所有历史版本
     */
    List<ProcessDefinitionVO> listHistoryVersions(String processKey);

    /**
     * 从已部署 BPMN 反向解析出 ProcessConfigDTO
     */
    ProcessConfigDTO extractConfig(String deploymentId);

    /**
     * 表格配置部署
     */
    TaskResult deployFromTable(ProcessConfigDTO config, String deployUser);

    /**
     * 应用某版本 —— 克隆 BPMN 重新部署为最新版本
     */
    TaskResult applyVersion(String deploymentId, String deployUser);
}
