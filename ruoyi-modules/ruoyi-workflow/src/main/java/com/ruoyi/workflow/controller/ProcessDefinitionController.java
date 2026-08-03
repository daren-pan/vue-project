package com.ruoyi.workflow.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.workflow.core.ProcessBuilder;
import com.ruoyi.workflow.model.ProcessConfigDTO;
import com.ruoyi.workflow.service.FlowableService;
import org.flowable.bpmn.model.*;
import org.flowable.bpmn.model.Process;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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
    private FlowableService flowableService;

    @Autowired
    private RepositoryService repositoryService;

    /**
     * 查询所有最新版本的流程定义列表
     *
     * @return [{ id, deploymentId, key, name, version, deployTime, deployer }]
     */
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        List<ProcessDefinition> defs = flowableService.listDefinitions();
        List<Map<String, Object>> list = defs.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("deploymentId", d.getDeploymentId());
            m.put("key", d.getKey());
            m.put("name", d.getName());
            m.put("version", d.getVersion());
            Deployment deployment = repositoryService.createDeploymentQuery()
                    .deploymentId(d.getDeploymentId()).singleResult();
            m.put("deployTime", deployment != null ? deployment.getDeploymentTime() : null);
            m.put("deployer", deployment != null && deployment.getCategory() != null
                ? deployment.getCategory() : "系统");
            // 从 BPMN documentation 提取抄送人
            try {
                BpmnModel bpmn = repositoryService.getBpmnModel(d.getId());
                if (bpmn != null && bpmn.getProcesses() != null && !bpmn.getProcesses().isEmpty()) {
                    String doc = bpmn.getProcesses().get(0).getDocumentation();
                    if (doc != null) {
                        for (String part : doc.split(";")) {
                            if (part.startsWith("CC:")) {
                                m.put("ccUsers", Arrays.asList(part.substring(3).split(",")));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[workflow] 解析BPMN抄送人失败(key=" + d.getKey() + "): " + e.getMessage());
            }
            return m;
        }).toList();
        return R.ok(list);
    }

    /**
     * 表格配置部署 —— 前端传节点 + 连线 JSON，后端用 Java DSL 构建并部署 BPMN
     *
     * @param config 流程配置 DTO（含 key、name、nodes、lines）
     * @return { deploymentId, processName, processKey, message }
     */
    @PostMapping("/deploy-table")
    public R<Map<String, Object>> deployFromTable(@RequestBody ProcessConfigDTO config) {
        String deployUser = getUsername(); // 当前登录用户
        String deploymentId = ProcessBuilder.deployFromConfig(config, repositoryService, deployUser);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deploymentId", deploymentId);
        result.put("processName", config.getProcessName());
        result.put("processKey", config.getProcessKey());
        result.put("message", "部署成功");
        return R.ok(result);
    }

    /**
     * 删除指定版本的流程定义（级联删除部署及运行中实例）
     *
     * @param deploymentId 部署 ID
     * @return 操作结果
     */
    @DeleteMapping("/{deploymentId}")
    public R<String> delete(@PathVariable String deploymentId) {
        repositoryService.deleteDeployment(deploymentId, true);
        return R.ok("删除成功");
    }

    /**
     * 应用某版本 —— 克隆指定部署的 BPMN 模型重新部署，使其成为最新版本
     *
     * @param deploymentId 部署 ID
     * @return { message, version }
     */
    @PostMapping("/{deploymentId}/apply")
    public R<Map<String, Object>> apply(@PathVariable String deploymentId) {
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deploymentId).singleResult();
        if (pd == null) return R.fail("流程定义不存在，deploymentId=" + deploymentId);

        BpmnModel model = repositoryService.getBpmnModel(pd.getId());
        String deployUser = getUsername();
        String newDeploymentId = repositoryService.createDeployment()
                .addBpmnModel(pd.getKey() + ".bpmn20.xml", model)
                .name(pd.getName())
                .category(deployUser != null ? deployUser : "系统")
                .deploy()
                .getId();

        ProcessDefinition newPd = repositoryService.createProcessDefinitionQuery()
                .deploymentId(newDeploymentId).singleResult();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "已应用为最新版本");
        result.put("version", newPd != null ? newPd.getVersion() : "?");
        return R.ok(result);
    }

    /**
     * 提取流程配置 —— 从已部署的 BPMN 模型反向解析出节点和连线，供前端编辑复用
     *
     * @param deploymentId 部署 ID
     * @return ProcessConfigDTO（含 nodes、lines）
     */
    @GetMapping("/{deploymentId}/config")
    public R<ProcessConfigDTO> getConfig(@PathVariable String deploymentId) {
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deploymentId).singleResult();
        if (pd == null) return R.fail("流程定义不存在，deploymentId=" + deploymentId);

        BpmnModel model = repositoryService.getBpmnModel(pd.getId());
        Process process = model.getProcesses().get(0);

        ProcessConfigDTO config = new ProcessConfigDTO();
        config.setProcessKey(process.getId());
        config.setProcessName(process.getName());

        // 提取节点
        List<ProcessConfigDTO.NodeDef> nodes = new ArrayList<>();
        // 从 documentation 解析会签配置 SIGN:id=user1,user2
        Map<String, List<String>> signMap = new HashMap<>();
        String doc = process.getDocumentation();
        if (doc != null) {
            for (String part : doc.split(";")) {
                if (part.startsWith("SIGN:")) {
                    String[] kv = part.substring(5).split("=", 2);
                    if (kv.length == 2) signMap.put(kv[0], Arrays.asList(kv[1].split(",")));
                }
            }
        }
        for (FlowElement el : process.getFlowElements()) {
            if (el instanceof SequenceFlow) continue;
            // 跳过会签拆分出的子节点（如 approval_2）
            String baseId = el.getId().replaceAll("_\\d+$", "");
            if (!baseId.equals(el.getId()) && signMap.containsKey(baseId)) continue;
            ProcessConfigDTO.NodeDef nd = new ProcessConfigDTO.NodeDef();
            nd.setId(el.getId());
            nd.setName(el.getName());
            if (el instanceof StartEvent) nd.setType("startEvent");
            else if (el instanceof EndEvent) nd.setType("endEvent");
            else if (el instanceof UserTask ut) {
                nd.setType("userTask");
                List<String> signUsers = signMap.get(el.getId());
                // 从 documentation 或 BPMN 模型检测会签
                if (signUsers != null) {
                    nd.setAssigneeList(signUsers);
                } else if (ut.getLoopCharacteristics() != null) {
                    // Multi-instance 节点，但 documentation 可能没存（旧数据），跳过
                    // assignee 是 ${assignee}，不设为宜
                } else {
                    nd.setAssignee(ut.getAssignee());
                }
            }
            else if (el instanceof ExclusiveGateway) nd.setType("exclusiveGateway");
            else continue;
            nodes.add(nd);
        }
        config.setNodes(nodes);

        // 提取连线
        List<ProcessConfigDTO.LineDef> lines = new ArrayList<>();
        for (FlowElement el : process.getFlowElements()) {
            if (el instanceof SequenceFlow sf) {
                ProcessConfigDTO.LineDef ld = new ProcessConfigDTO.LineDef();
                ld.setFrom(sf.getSourceRef());
                ld.setTo(sf.getTargetRef());
                if (sf.getConditionExpression() != null) {
                    ld.setCondition(sf.getConditionExpression());
                }
                lines.add(ld);
            }
        }
        config.setLines(lines);

        return R.ok(config);
    }

    /**
     * 查询指定 key 的所有历史版本
     *
     * @param processKey 流程标识 key
     * @return [{ id, deploymentId, version, deployTime, deployer }]
     */
    @GetMapping("/history/{processKey}")
    public R<List<Map<String, Object>>> history(@PathVariable String processKey) {
        List<ProcessDefinition> defs = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .orderByProcessDefinitionVersion().desc()
                .list();
        List<Map<String, Object>> list = defs.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("deploymentId", d.getDeploymentId());
            m.put("version", d.getVersion());
            Deployment deployment = repositoryService.createDeploymentQuery()
                    .deploymentId(d.getDeploymentId()).singleResult();
            m.put("deployTime", deployment != null ? deployment.getDeploymentTime() : null);
            m.put("deployer", deployment != null && deployment.getCategory() != null
                ? deployment.getCategory() : "系统");
            return m;
        }).toList();
        return R.ok(list);
    }
}
