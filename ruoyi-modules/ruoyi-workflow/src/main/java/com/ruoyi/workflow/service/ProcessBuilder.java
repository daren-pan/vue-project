package com.ruoyi.workflow.service;

import com.ruoyi.workflow.domain.vo.ProcessConfigDTO;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;

import java.util.*;

/**
 * 流程构建器：将节点与连线配置转为 BPMN 模型并部署。
 */
public class ProcessBuilder {

    private final BpmnModel model;
    private final Process process;

    private ProcessBuilder(String key, String name) {
        this.model = new BpmnModel();
        this.process = new Process();
        process.setId(key);
        process.setName(name);
        process.setExecutable(true);
        model.addProcess(process);
    }

    public static ProcessBuilder create(String key, String name) {
        return new ProcessBuilder(key, name);
    }

    // 节点

    public ProcessBuilder startEvent(String id, String name) {
        StartEvent e = new StartEvent();
        e.setId(id); e.setName(name);
        process.addFlowElement(e);
        return this;
    }

    public ProcessBuilder endEvent(String id, String name) {
        EndEvent e = new EndEvent();
        e.setId(id); e.setName(name);
        process.addFlowElement(e);
        return this;
    }

    public ProcessBuilder userTask(String id, String name, String assignee) {
        UserTask t = new UserTask();
        t.setId(id); t.setName(name);
        t.setAssignee(assignee);
        process.addFlowElement(t);
        return this;
    }

    // 会签：多人并行，全通过才推进
    public ProcessBuilder userTaskMulti(String id, String name, List<String> assigneeList) {
        UserTask t = new UserTask();
        t.setId(id); t.setName(name);
        t.setAssignee("${assignee}");

        MultiInstanceLoopCharacteristics mi = new MultiInstanceLoopCharacteristics();
        mi.setSequential(false);
        mi.setElementVariable("assignee");
        // 流程变量，发起时注入
        mi.setInputDataItem("${assigneeList_" + id + "}");
        mi.setCompletionCondition("${nrOfCompletedInstances == nrOfInstances}");
        t.setLoopCharacteristics(mi);

        process.addFlowElement(t);
        return this;
    }

    public ProcessBuilder gateway(String id, String name) {
        ExclusiveGateway g = new ExclusiveGateway();
        g.setId(id); g.setName(name);
        process.addFlowElement(g);
        return this;
    }

    // 连线

    public ProcessBuilder flow(String from, String to, String condition) {
        SequenceFlow sf = new SequenceFlow();
        sf.setId("flow_" + from + "_to_" + to);
        sf.setSourceRef(from);
        sf.setTargetRef(to);
        if (condition != null && !condition.isEmpty()) {
            condition = condition.replace("<=", "le").replace(">=", "ge")
                                 .replace("<>", "ne").replace("!=", "ne")
                                 .replace("<", "lt").replace(">", "gt");
            if (condition.startsWith("${") && condition.endsWith("}")) {
                sf.setConditionExpression(condition);
            } else {
                sf.setConditionExpression("${" + condition + "}");
            }
        }
        process.addFlowElement(sf);
        return this;
    }

    // 部署

    public String deploy(RepositoryService repositoryService, String deployUser) {
        return repositoryService.createDeployment()
                .addBpmnModel(process.getId() + ".bpmn20.xml", model)
                .name(process.getName())
                .category(deployUser != null ? deployUser : "系统")
                .deploy()
                .getId();
    }

    // 从表格配置构建

    public static String deployFromConfig(
            ProcessConfigDTO config,
            RepositoryService repositoryService,
            String deployUser) {

        ProcessBuilder pb = create(config.getProcessKey(), config.getProcessName());

        System.out.println("📋 收到部署请求: key=" + config.getProcessKey() + ", name=" + config.getProcessName());
        System.out.println("   节点(" + config.getNodes().size() + "): " +
            config.getNodes().stream().map(n -> n.getId() + "(" + n.getType() + ")").toList());
        System.out.println("   连线(" + config.getLines().size() + "): " +
            config.getLines().stream().map(l -> l.getFrom() + "→" + l.getTo() +
                (l.getCondition() != null && !l.getCondition().isEmpty() ? "[" + l.getCondition() + "]" : "")).toList());

        for (ProcessConfigDTO.NodeDef node : config.getNodes()) {
            switch (node.getType()) {
                case "startEvent" -> pb.startEvent(node.getId(), node.getName());
                case "endEvent"   -> pb.endEvent(node.getId(), node.getName());
                case "userTask"   -> {
                    List<String> list = node.getAssigneeList();
                    if (list != null && list.size() > 1) {
                        pb.userTaskMulti(node.getId(), node.getName(), list);
                    } else if (list != null && list.size() == 1) {
                        pb.userTask(node.getId(), node.getName(), list.get(0));
                    } else {
                        pb.userTask(node.getId(), node.getName(), node.getAssignee());
                    }
                }
                case "exclusiveGateway" -> pb.gateway(node.getId(), node.getName());
            }
        }

        for (ProcessConfigDTO.LineDef line : config.getLines()) {
            pb.flow(line.getFrom(), line.getTo(), line.getCondition());
        }

        // 抄送人+会签信息写入 BPMN documentation
        StringBuilder doc = new StringBuilder();
        if (config.getCcUsers() != null && !config.getCcUsers().isEmpty()) {
            doc.append("CC:").append(String.join(",", config.getCcUsers())).append(";");
        }
        config.getNodes().forEach(n -> {
            if (n.getAssigneeList() != null && n.getAssigneeList().size() > 1) {
                doc.append("SIGN:").append(n.getId()).append("=")
                   .append(String.join(",", n.getAssigneeList())).append(";");
            }
        });
        if (doc.length() > 0) pb.process.setDocumentation(doc.toString());

        return pb.deploy(repositoryService, deployUser);
    }
}
