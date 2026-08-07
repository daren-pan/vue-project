package com.ruoyi.workflow.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.workflow.domain.vo.*;
import com.ruoyi.workflow.service.IWorkflowDefinitionService;
import org.flowable.engine.RepositoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.ruoyi.common.security.utils.SecurityUtils.getUsername;

/**
 * 流程定义管理
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/definition")
public class ProcessDefinitionController extends BaseController {

    @Autowired
    private IWorkflowDefinitionService workflowDefinitionService;

    @Autowired
    private RepositoryService repositoryService;

    /**
     * 查询所有最新版本的流程定义列表
     */
    @GetMapping("/list")
    public R<List<ProcessDefinitionVO>> list() {
        return R.ok(workflowDefinitionService.listDefinitions());
    }

    /**
     * 表格配置部署
     */
    @PostMapping("/deploy-table")
    public R<TaskResult> deployFromTable(@RequestBody ProcessConfigDTO config) {
        return R.<TaskResult>ok(workflowDefinitionService.deployFromTable(config, getUsername()));
    }

    /**
     * 删除指定版本的流程定义
     */
    @DeleteMapping("/{deploymentId}")
    public R<String> delete(@PathVariable String deploymentId) {
        repositoryService.deleteDeployment(deploymentId, true);
        return R.ok("删除成功");
    }

    /**
     * 应用某版本
     */
    @PostMapping("/{deploymentId}/apply")
    public R<TaskResult> apply(@PathVariable String deploymentId) {
        return R.<TaskResult>ok(workflowDefinitionService.applyVersion(deploymentId, getUsername()));
    }

    /**
     * 提取流程配置（反向解析 BPMN）
     */
    @GetMapping("/{deploymentId}/config")
    public R<ProcessConfigDTO> getConfig(@PathVariable String deploymentId) {
        ProcessConfigDTO config = workflowDefinitionService.extractConfig(deploymentId);
        if (config == null) return R.fail("流程定义不存在，deploymentId=" + deploymentId);
        return R.ok(config);
    }

    /**
     * 查询指定 key 的所有历史版本
     */
    @GetMapping("/history/{processKey}")
    public R<List<ProcessDefinitionVO>> history(@PathVariable String processKey) {
        return R.ok(workflowDefinitionService.listHistoryVersions(processKey));
    }
}
