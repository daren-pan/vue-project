package com.ruoyi.workflow.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.workflow.domain.vo.*;
import com.ruoyi.workflow.service.IWorkflowTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务管理（待办 / 审批 / 已办历史）
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

