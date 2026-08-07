package com.ruoyi.workflow.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.workflow.domain.vo.*;
import com.ruoyi.workflow.service.IWorkflowInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 流程实例管理
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/instance")
public class ProcessInstanceController extends BaseController {

    @Autowired
    private IWorkflowInstanceService workflowInstanceService;

    /**
     * 通用发起流程
     */
    @PostMapping("/start")
    public R<TaskResult> start(@RequestBody Map<String, Object> body) {
        String processKey = (String) body.remove("processKey");
        String applicant = (String) body.remove("applicant");
        if (processKey == null || applicant == null) {
            return R.fail("processKey 和 applicant 不能为空");
        }
        return R.<TaskResult>ok(workflowInstanceService.startProcess(processKey, applicant, body));
    }

    /**
     * 查询所有运行中的流程实例
     */
    @GetMapping("/running")
    public R<List<ProcessInstanceVO>> running() {
        return R.ok(workflowInstanceService.listRunningInstances());
    }

    /**
     * 查询流程实例状态
     */
    @GetMapping("/{processInstanceId}")
    public R<InstanceStatusVO> status(@PathVariable String processInstanceId) {
        return R.ok(workflowInstanceService.getInstanceStatus(processInstanceId));
    }

    /**
     * 查询审批轨迹
     */
    @GetMapping("/{processInstanceId}/track")
    public R<List<TrackNodeVO>> track(@PathVariable String processInstanceId) {
        return R.ok(workflowInstanceService.buildTrack(processInstanceId));
    }

    /**
     * 撤回流程
     */
    @PostMapping("/{processInstanceId}/withdraw")
    public R<TaskResult> withdraw(@PathVariable String processInstanceId) {
        return R.<TaskResult>ok(workflowInstanceService.withdrawProcess(processInstanceId));
    }
}
