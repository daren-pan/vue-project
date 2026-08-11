package com.ruoyi.workflow.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.workflow.domain.vo.*;
import com.ruoyi.workflow.service.IWorkflowTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务管理（待办 / 审批 / 已办历史）
 * v2.1 - 支持 MCP 辅助查询
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/task")
public class TaskController extends BaseController {

    @Autowired
    private IWorkflowTaskService workflowTaskService;

    /**
     * 查询某人待办任务（含加签、抄送）
     */
    @GetMapping("/todo")
    public R<List<TaskVO>> todo(@RequestParam String assignee) {
        return R.ok(workflowTaskService.listTodoTasks(assignee));
    }

    /**
     * 查询某人已办历史
     */
    @GetMapping("/history")
    public R<List<TaskVO>> history(@RequestParam String assignee) {
        return R.ok(workflowTaskService.listHistoryTasks(assignee));
    }

    /**
     * 审批通过
     */
    @PostMapping("/approve")
    public R<TaskResult> approve(@RequestParam String taskId,
                                 @RequestParam(defaultValue = "同意") String comment) {
        return R.ok(workflowTaskService.approve(taskId, comment));
    }

    /**
     * 批量审批通过 —— 逐个审批，单个失败不影响其余
     */
    @PostMapping("/batchApprove")
    public R<List<TaskResult>> batchApprove(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> taskIds = (List<String>) body.get("taskIds");
        String comment = (String) body.getOrDefault("comment", "同意");
        if (taskIds == null || taskIds.isEmpty()) {
            return R.fail("taskIds 不能为空");
        }
        return R.ok(workflowTaskService.batchApprove(taskIds, comment));
    }

    /**
     * 审批驳回
     */
    @PostMapping("/reject")
    public R<TaskResult> reject(@RequestParam String taskId,
                                @RequestParam(defaultValue = "不同意") String reason) {
        return R.ok(workflowTaskService.reject(taskId, reason));
    }

    /**
     * 驳回到上一节点
     */
    @PostMapping("/rollback")
    public R<TaskResult> rollback(@RequestParam String taskId,
                                  @RequestParam(defaultValue = "需修改") String reason) {
        return R.ok(workflowTaskService.rollback(taskId, reason));
    }

    /**
     * 加签
     */
    @PostMapping("/addSign")
    public R<TaskResult> addSign(@RequestParam String taskId,
                                 @RequestParam String assignee) {
        return R.ok(workflowTaskService.addSign(taskId, assignee));
    }

    /**
     * 关闭抄送任务（已阅）
     */
    @PostMapping("/dismiss")
    public R<TaskResult> dismiss(@RequestParam String taskId) {
        return R.ok(workflowTaskService.dismiss(taskId));
    }

    /**
     * 强制删除流程实例
     */
    @PostMapping("/deleteInstance")
    public R<TaskResult> deleteInstance(@RequestParam String processInstanceId) {
        return R.ok(workflowTaskService.deleteInstance(processInstanceId));
    }
}

