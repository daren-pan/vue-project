package com.ruoyi.workflow.service;

import com.ruoyi.workflow.domain.vo.TaskResult;
import com.ruoyi.workflow.domain.vo.TaskVO;

import java.util.List;

/**
 * 任务服务接口
 *
 * @author ruoyi
 */
public interface IWorkflowTaskService {

    /**
     * 查询待办任务列表
     */
    List<TaskVO> listTodoTasks(String assignee);

    /**
     * 查询已办历史
     */
    List<TaskVO> listHistoryTasks(String assignee);

    /**
     * 审批通过
     */
    TaskResult approve(String taskId, String comment);

    /**
     * 批量审批通过 —— 逐个审批，单个失败不影响其余
     */
    List<TaskResult> batchApprove(List<String> taskIds, String comment);

    /**
     * 审批驳回
     */
    TaskResult reject(String taskId, String reason);

    /**
     * 驳回到上一节点
     */
    TaskResult rollback(String taskId, String reason);

    /**
     * 加签
     */
    TaskResult addSign(String taskId, String assignee);

    /**
     * 关闭抄送任务
     */
    TaskResult dismiss(String taskId);

    /**
     * 强制删除流程实例
     */
    TaskResult deleteInstance(String processInstanceId);
}
