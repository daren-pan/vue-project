package com.ruoyi.workflow.service.impl;

import com.ruoyi.workflow.domain.vo.ProcessConfigDTO;
import com.ruoyi.workflow.domain.vo.ProcessDefinitionVO;
import com.ruoyi.workflow.domain.vo.TaskResult;
import com.ruoyi.workflow.service.FlowableService;
import com.ruoyi.workflow.service.IWorkflowDefinitionService;
import com.ruoyi.workflow.service.ProcessBuilder;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 流程定义服务实现
 *
 * @author ruoyi
 */
@Service
public class WorkflowDefinitionServiceImpl implements IWorkflowDefinitionService {

    @Autowired
    private FlowableService flowableService;

    @Autowired
    private RepositoryService repositoryService;

    @Override
    public List<ProcessDefinitionVO> listDefinitions() {
        List<ProcessDefinition> defs = flowableService.listDefinitions();
        List<ProcessDefinitionVO> list = new ArrayList<>();
        for (ProcessDefinition d : defs) {
            ProcessDefinitionVO vo = new ProcessDefinitionVO();
            vo.setId(d.getId());
            vo.setDeploymentId(d.getDeploymentId());
            vo.setKey(d.getKey());
            vo.setName(d.getName());
            vo.setVersion(d.getVersion());
            Deployment deployment = repositoryService.createDeploymentQuery()
                    .deploymentId(d.getDeploymentId()).singleResult();
            vo.setDeployTime(deployment != null ? deployment.getDeploymentTime() : null);
            vo.setDeployer(deployment != null && deployment.getCategory() != null
                    ? deployment.getCategory() : "系统");
            try {
                BpmnModel bpmn = repositoryService.getBpmnModel(d.getId());
                if (bpmn != null && bpmn.getProcesses() != null && !bpmn.getProcesses().isEmpty()) {
                    String doc = bpmn.getProcesses().get(0).getDocumentation();
                    if (doc != null) {
                        for (String part : doc.split(";")) {
                            if (part.startsWith("CC:")) {
                                vo.setCcUsers(Arrays.asList(part.substring(3).split(",")));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[workflow] 解析BPMN抄送人失败(key=" + d.getKey() + "): " + e.getMessage());
            }
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<ProcessDefinitionVO> listHistoryVersions(String processKey) {
        List<ProcessDefinition> defs = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .orderByProcessDefinitionVersion().desc().list();
        List<ProcessDefinitionVO> list = new ArrayList<>();
        for (ProcessDefinition d : defs) {
            ProcessDefinitionVO vo = new ProcessDefinitionVO();
            vo.setId(d.getId());
            vo.setDeploymentId(d.getDeploymentId());
            vo.setVersion(d.getVersion());
            Deployment deployment = repositoryService.createDeploymentQuery()
                    .deploymentId(d.getDeploymentId()).singleResult();
            vo.setDeployTime(deployment != null ? deployment.getDeploymentTime() : null);
            vo.setDeployer(deployment != null && deployment.getCategory() != null
                    ? deployment.getCategory() : "系统");
            list.add(vo);
        }
        return list;
    }

    @Override
    public ProcessConfigDTO extractConfig(String deploymentId) {
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deploymentId).singleResult();
        if (pd == null) return null;

        BpmnModel model = repositoryService.getBpmnModel(pd.getId());
        org.flowable.bpmn.model.Process process = model.getProcesses().get(0);

        ProcessConfigDTO config = new ProcessConfigDTO();
        config.setProcessKey(process.getId());
        config.setProcessName(process.getName());

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
        List<ProcessConfigDTO.NodeDef> nodes = new ArrayList<>();
        for (var el : process.getFlowElements()) {
            if (el instanceof org.flowable.bpmn.model.SequenceFlow) continue;
            String baseId = el.getId().replaceAll("_\\d+$", "");
            if (!baseId.equals(el.getId()) && signMap.containsKey(baseId)) continue;
            ProcessConfigDTO.NodeDef nd = new ProcessConfigDTO.NodeDef();
            nd.setId(el.getId());
            nd.setName(el.getName());
            if (el instanceof org.flowable.bpmn.model.StartEvent) nd.setType("startEvent");
            else if (el instanceof org.flowable.bpmn.model.EndEvent) nd.setType("endEvent");
            else if (el instanceof org.flowable.bpmn.model.UserTask ut) {
                nd.setType("userTask");
                List<String> signUsers = signMap.get(el.getId());
                if (signUsers != null) {
                    nd.setAssigneeList(signUsers);
                } else if (ut.getLoopCharacteristics() == null) {
                    nd.setAssignee(ut.getAssignee());
                }
            }
            else if (el instanceof org.flowable.bpmn.model.ExclusiveGateway) nd.setType("exclusiveGateway");
            else continue;
            nodes.add(nd);
        }
        config.setNodes(nodes);

        List<ProcessConfigDTO.LineDef> lines = new ArrayList<>();
        for (var el : process.getFlowElements()) {
            if (el instanceof org.flowable.bpmn.model.SequenceFlow sf) {
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
        return config;
    }

    @Override
    public TaskResult deployFromTable(ProcessConfigDTO config, String deployUser) {
        String deploymentId = ProcessBuilder.deployFromConfig(config, repositoryService, deployUser);
        TaskResult r = new TaskResult();
        r.setDeploymentId(deploymentId);
        r.setProcessName(config.getProcessName());
        r.setProcessKey(config.getProcessKey());
        r.setTip("部署成功");
        return r;
    }

    @Override
    public TaskResult applyVersion(String deploymentId, String deployUser) {
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deploymentId).singleResult();
        if (pd == null) throw new RuntimeException("流程定义不存在，deploymentId=" + deploymentId);

        BpmnModel model = repositoryService.getBpmnModel(pd.getId());
        String newDeploymentId = repositoryService.createDeployment()
                .addBpmnModel(pd.getKey() + ".bpmn20.xml", model)
                .name(pd.getName())
                .category(deployUser != null ? deployUser : "系统")
                .deploy().getId();

        ProcessDefinition newPd = repositoryService.createProcessDefinitionQuery()
                .deploymentId(newDeploymentId).singleResult();

        TaskResult r = new TaskResult();
        r.setTip("已应用为最新版本");
        r.setVersion(newPd != null ? newPd.getVersion() : "?");
        return r;
    }
}
