package com.ruoyi.system.controller.workflow;

import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.util.List;

import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.system.domain.workflow.WfTask;
import com.ruoyi.system.service.workflow.IWorkflowEngine;
import com.ruoyi.system.mapper.workflow.WfTaskMapper;
import com.ruoyi.system.mapper.workflow.WfAuditRecordMapper;
import com.ruoyi.system.mapper.workflow.WfProcessInstanceMapper;
import com.ruoyi.system.mapper.workflow.WfVariableMapper;
import com.ruoyi.system.domain.workflow.WfAuditRecord;
import com.ruoyi.system.domain.workflow.WfProcessInstance;
import com.ruoyi.system.domain.workflow.WfVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 审批任务 Controller
 * 
 * 对应页面：
 * 1. 我的待办 - 查看当前登录人的待审批任务
 * 2. 审批操作 - 同意/驳回
 * 3. 审批历史 - 查看指定单据的完整审批轨迹
 */
@RestController
@RequestMapping("/workflow/task")
public class WfTaskController extends BaseController {

    @Autowired
    private IWorkflowEngine workflowEngine;

    @Autowired
    private WfTaskMapper taskMapper;

    @Autowired
    private WfAuditRecordMapper auditRecordMapper;

    @Autowired
    private WfProcessInstanceMapper instanceMapper;

    @Autowired
    private WfVariableMapper variableMapper;

    /**
     * 查询指定用户的待办任务列表
     * 
     * 返回结果中包含 businessTable 和 businessId，
     * 前端根据这两个字段去对应的业务表查询详情并展示
     * 
     * @param assignee 用户名（当前登录人）
     */
    @GetMapping("/list")
    public TableDataInfo list(String assignee) {
        startPage();
        List<WfTask> list = workflowEngine.selectTasksByAssignee(assignee);
        return getDataTable(list);
    }

    /**
     * 审批操作 —— 同意/驳回
     * 
     * 调用流程引擎的 approve 方法，执行以下逻辑：
     * 1. 更新任务状态为 completed
     * 2. 记录审批意见
     * 3. 判断当前节点是否完成
     * 4. 如果完成则自动进入下一节点
     * 
     * @param taskId  待办任务ID
     * @param action  操作类型：agree（同意）/ reject（驳回）
     * @param comment 审批意见（可选）
     */
    @PostMapping("/approve")
    @Log(title = "审批任务", businessType = BusinessType.UPDATE)
    public AjaxResult approve(@RequestParam Long taskId,
                               @RequestParam String action,
                               @RequestParam(required = false) String comment) {
        workflowEngine.approve(taskId, action, comment);
        return success();
    }

    /**
     * 查询审批轨迹 —— 按时间线展示审批历史
     * 
     * 用于"审批详情"页面展示，格式如：
     *   张三 发起申请    09:00
     *   赵六 同意        09:30  审批意见：同意
     *   钱七 同意        10:00  审批意见：已阅
     *   流程结束         10:00
     * 
     * @param businessTable 业务表名（如 leave_record）
     * @param businessId    业务表主键ID
     */
    @GetMapping("/history")
    public AjaxResult history(@RequestParam String businessTable, @RequestParam Long businessId) {
        return success(workflowEngine.selectAuditRecords(businessTable, businessId));
    }

    /**
     * 【测试接口】模拟推一笔待办数据到 admin 用户
     *
     * 直接入库，不走流程引擎。已做防重复处理：
     * - 同一业务单据（business_key + node_id）已有待办时，不再新增
     * - 直接返回已有待办信息
     *
     * POST /system/workflow/task/testPushTask
     */
    @PostMapping("/testPushTask")
    public AjaxResult testPushTask() {
        String businessKey = "leave_record-1";
        String nodeId = "leader_approve";

        // 防重复：检查是否已有该业务的待办
        List<WfTask> existTasks = taskMapper.selectByBusinessKeyAndNode(businessKey, nodeId);
        if (existTasks != null && !existTasks.isEmpty()) {
            return success("该笔业务已推送过待办（任务ID: " + existTasks.get(0).getId() + "），请勿重复推送");
        }

        // 1. 创建流程实例
        WfProcessInstance instance = new WfProcessInstance();
        instance.setProcessDefId(1L);
        instance.setProcessDefVersion(1);
        instance.setBusinessKey(businessKey);
        instance.setBusinessTable("leave_record");
        instance.setBusinessId(1L);
        instance.setApplicant("张三");
        instance.setStatus("running");
        instance.setCurrentNodeId(nodeId);
        instanceMapper.insertWfProcessInstance(instance);

        // 2. 插入变量
        WfVariable var1 = new WfVariable();
        var1.setInstanceId(instance.getId());
        var1.setVarName("days"); var1.setVarValue("5");
        variableMapper.insertWfVariable(var1);
        WfVariable var2 = new WfVariable();
        var2.setInstanceId(instance.getId());
        var2.setVarName("reason"); var2.setVarValue("年假");
        variableMapper.insertWfVariable(var2);

        // 3. 插入审核记录
        WfAuditRecord r1 = new WfAuditRecord();
        r1.setInstanceId(instance.getId());
        r1.setBusinessTable("leave_record"); r1.setBusinessId(1L);
        r1.setNodeId("start"); r1.setNodeName("发起");
        r1.setAssignee("张三"); r1.setAction("submit");
        r1.setStatus("completed"); r1.setCompleteTime(new Date());
        auditRecordMapper.insertWfAuditRecord(r1);

        WfAuditRecord r2 = new WfAuditRecord();
        r2.setInstanceId(instance.getId());
        r2.setBusinessTable("leave_record"); r2.setBusinessId(1L);
        r2.setNodeId(nodeId); r2.setNodeName("组长审批");
        r2.setAssignee("admin"); r2.setStatus("pending");
        auditRecordMapper.insertWfAuditRecord(r2);

        // 4. 插入待办任务
        WfTask task = new WfTask();
        task.setInstanceId(instance.getId());
        task.setNodeId(nodeId);
        task.setNodeName("组长审批");
        task.setAssignee("admin");
        taskMapper.insertWfTask(task);

        return success("待办推送成功！请用 admin 登录 -> 工作流管理 -> 我的待办 查看效果");
    }
}

