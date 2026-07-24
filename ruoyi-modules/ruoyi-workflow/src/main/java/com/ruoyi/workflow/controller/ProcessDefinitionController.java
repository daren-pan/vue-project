package com.ruoyi.workflow.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.workflow.core.ProcessBuilder;
import com.ruoyi.workflow.model.ProcessConfigDTO;
import com.ruoyi.workflow.service.FlowableService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 流程定义管理
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/definition")
public class ProcessDefinitionController extends BaseController {

    @Autowired
    private FlowableService flowableService;

    @Autowired
    private RepositoryService repositoryService;

    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        List<ProcessDefinition> defs = flowableService.listDefinitions();
        List<Map<String, Object>> list = defs.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("key", d.getKey());
            m.put("name", d.getName());
            m.put("version", d.getVersion());
            m.put("deploymentId", d.getDeploymentId());
            return m;
        }).toList();
        return R.ok(list);
    }

    /**
     * 表格配置部署 —— 前端传节点+连线 JSON，后端直接用 Java DSL 部署
     */
    @PostMapping("/deploy-table")
    public R<Map<String, Object>> deployFromTable(@RequestBody ProcessConfigDTO config) {
        String deploymentId = ProcessBuilder.deployFromConfig(config, repositoryService);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deploymentId", deploymentId);
        result.put("processName", config.getProcessName());
        result.put("processKey", config.getProcessKey());
        result.put("message", "部署成功");
        return R.ok(result);
    }
}
