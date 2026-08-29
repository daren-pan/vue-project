package com.ruoyi.workflow.service;

import com.ruoyi.workflow.domain.vo.InstanceStatusVO;
import com.ruoyi.workflow.domain.vo.ProcessInstanceVO;
import com.ruoyi.workflow.domain.vo.TaskResult;
import com.ruoyi.workflow.domain.vo.TrackNodeVO;

import java.util.List;
import java.util.Map;

/**
 * 流程实例服务接口
 *
 * @author ruoyi
 */
public interface IWorkflowInstanceService {

    /**
     * 通用发起流程
     */
    TaskResult startProcess(String processKey, String applicant, Map<String, Object> body);

    /**
     * 撤回流程
     */
    TaskResult withdrawProcess(String processInstanceId);

    /**
     * 系统自动通过 —— 按流程实例（单据）ID 自动通过当前节点的待办任务，推进到下一节点
     *
     * @param processInstanceId 流程实例（单据）ID
     * @return 自动通过结果，processFinished 标识是否推进到流程结束
     */
    TaskResult autoAdvance(String processInstanceId);

    /**
     * 查询所有运行中的流程实例
     */
    List<ProcessInstanceVO> listRunningInstances();

    /**
     * 查询流程实例状态
     */
    InstanceStatusVO getInstanceStatus(String processInstanceId);

    /**
     * 查询审批轨迹
     */
    List<TrackNodeVO> buildTrack(String processInstanceId);
}
