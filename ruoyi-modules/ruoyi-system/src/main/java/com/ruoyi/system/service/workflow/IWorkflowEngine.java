package com.ruoyi.system.service.workflow;

import com.ruoyi.system.domain.workflow.WfAuditRecord;
import com.ruoyi.system.domain.workflow.WfTask;
import java.util.List;
import java.util.Map;

/**
 * 工作流引擎接口
 * 
 * 定义工作流的核心操作：
 * 1. 启动流程 —— 业务单据提交时调用
 * 2. 审批操作 —— 审批人同意/驳回时调用
 * 3. 查询待办 —— 查看当前用户的待审批任务
 * 4. 查询审批轨迹 —— 查看指定单据的完整审批历史
 */
public interface IWorkflowEngine {

    /**
     * 启动流程
     * 
     * 业务方在提交单据时调用此方法，流程引擎会自动完成：
     * 1. 创建流程实例，状态为 running
     * 2. 保存业务变量到变量表（条件快照）
     * 3. 自动流转到第一个待办节点
     * 4. 返回流程实例ID
     * 
     * @param processKey   流程标识（如 "leave" 对应请假审批）
     * @param applicant    发起人用户名
     * @param variables    流程变量（用于条件判断，如 days=5, amount=1000）
     * @param businessTable 业务表名（如 "leave_record"）
     * @param businessId   业务表主键ID
     * @return 流程实例ID
     */
    Long startProcess(String processKey, String applicant, Map<String, Object> variables,
                      String businessTable, Long businessId);

    /**
     * 审批操作（同意/驳回）
     * 
     * 审批人点击同意或驳回时调用，流程引擎自动完成：
     * 1. 完成任务，记录审批意见
     * 2. 判断当前节点是否全部完成
     * 3. 如果完成则自动进入下一节点（或结束流程）
     * 4. 如果驳回则回到开始节点
     * 
     * @param taskId  待办任务ID
     * @param action  操作类型：agree（同意）/ reject（驳回）
     * @param comment 审批意见
     */
    void approve(Long taskId, String action, String comment);

    /**
     * 查询指定用户的待办任务列表
     * 
     * 关联流程实例表一起查询，返回待办详情
     * 返回数据中包含 businessTable 和 businessId，
     * 前端据此展示对应的业务单据详情链接
     * 
     * @param assignee 用户名（通常是当前登录用户）
     * @return 待办任务列表（含业务关联信息）
     */
    List<WfTask> selectTasksByAssignee(String assignee);

    /**
     * 查询审批轨迹
     * 
     * 根据业务单据查询完整的审批历史记录，
     * 用于"审批详情"页面展示审批时间线
     * 
     * @param businessTable 业务表名
     * @param businessId    业务表主键ID
     * @return 按时间排序的审批记录列表
     */
    List<WfAuditRecord> selectAuditRecords(String businessTable, Long businessId);
}
