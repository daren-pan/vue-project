package com.ruoyi.workflow.core;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;

import java.util.*;

/**
 * 纯 Java 代码定义流程，不需要 BPMN XML
 *
 * <pre>
 * ProcessBuilder.create("leave", "请假审批")
 *     .startEvent("start", "开始")
 *     .userTask("mgr", "部门经理审批", "${manager}")
 *     .gateway("gate", "判断天数")
 *         .condition("days &lt;= 3", "≤3天")
 *         .endEvent("end", "结束")
 *         .condition("days &gt; 3", ">3天")
 *         .userTask("dir", "总监审批", "${director}")
 *         .endEvent("end2", "结束")
 *     .done()
 *     .deploy(repositoryService);
 * </pre>
 */
public class ProcessBuilder {

    private final BpmnModel model;
    private final Process process;
    private boolean autoConnect = true;
    private String lastElementId;
    private String lastGatewayId;

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

    // ────────── 节点 ──────────

    public ProcessBuilder startEvent(String id, String name) {
        StartEvent e = new StartEvent();
        e.setId(id); e.setName(name);
        return addElement(e);
    }

    public ProcessBuilder endEvent(String id, String name) {
        EndEvent e = new EndEvent();
        e.setId(id); e.setName(name);
        return addElement(e);
    }

    public ProcessBuilder userTask(String id, String name, String assignee) {
        UserTask t = new UserTask();
        t.setId(id); t.setName(name);
        t.setAssignee(assignee);
        return addElement(t);
    }

    public ProcessBuilder serviceTask(String id, String name, String delegateExpression) {
        ServiceTask t = new ServiceTask();
        t.setId(id); t.setName(name);
        t.setImplementationType("delegateExpression");
        t.setImplementation(delegateExpression);
        return addElement(t);
    }

    public GatewayBranch gateway(String id, String name) {
        ExclusiveGateway g = new ExclusiveGateway();
        g.setId(id); g.setName(name);
        addElement(g);
        this.lastGatewayId = id;
        return new GatewayBranch(this);
    }

    // ────────── 连线 ──────────

    private ProcessBuilder addElement(FlowElement element) {
        // 自动从上一个节点连线（网关除外，网关走 GatewayBranch 显式分支）
        if (autoConnect && lastElementId != null && !lastElementId.equals(lastGatewayId)) {
            SequenceFlow sf = new SequenceFlow();
            sf.setId("flow_" + lastElementId + "_to_" + element.getId());
            sf.setSourceRef(lastElementId);
            sf.setTargetRef(element.getId());
            process.addFlowElement(sf);
        }
        process.addFlowElement(element);
        lastElementId = element.getId();
        return this;
    }

    public ProcessBuilder flow(String from, String to, String condition) {
        SequenceFlow sf = new SequenceFlow();
        sf.setId("flow_" + from + "_to_" + to);
        sf.setSourceRef(from);
        sf.setTargetRef(to);
        if (condition != null && !condition.isEmpty()) {
            // 用户可直接写 ${days > 3} 或 days > 3，后端统一处理
            if (condition.startsWith("${") && condition.endsWith("}")) {
                sf.setConditionExpression(condition);
            } else {
                sf.setConditionExpression("${" + condition + "}");
            }
        }
        process.addFlowElement(sf);
        return this;
    }

    // ────────── 部署 ──────────

    /** 构建完成后部署 */
    public String deploy(RepositoryService repositoryService) {
        // 删除旧部署
        repositoryService.createDeploymentQuery()
                .deploymentName(process.getName())
                .list().forEach(d -> repositoryService.deleteDeployment(d.getId(), true));

        return repositoryService.createDeployment()
                .addBpmnModel(process.getId() + ".bpmn20.xml", model)
                .name(process.getName())
                .deploy()
                .getId();
    }

    /** 获取 BpmnModel 用于更多自定义 */
    public BpmnModel getModel() { return model; }

    /**
     * 网关分支构建器
     */
    public class GatewayBranch {
        private final ProcessBuilder parent;
        private final List<Branch> branches = new ArrayList<>();
        private String currentFrom;

        GatewayBranch(ProcessBuilder parent) {
            this.parent = parent;
            this.currentFrom = parent.lastGatewayId;
        }

        public GatewayBranch condition(String expression, String name) {
            if (branches.isEmpty() || branches.get(branches.size() - 1).to != null) {
                branches.add(new Branch());
                currentFrom = parent.lastGatewayId; // 新分支，从网关重新出发
            }
            branches.get(branches.size() - 1).expression = expression;
            return this;
        }

        public GatewayBranch userTask(String id, String name, String assignee) {
            parent.autoConnect = false;
            parent.userTask(id, name, assignee);
            parent.autoConnect = true;
            parent.flow(currentFrom, id, getLastExpression());
            currentFrom = id;
            return this;
        }

        public GatewayBranch endEvent(String id, String name) {
            parent.autoConnect = false;
            parent.endEvent(id, name);
            parent.autoConnect = true;
            parent.flow(currentFrom, id, getLastExpression());
            currentFrom = id;
            return this;
        }

        /** 网关结束，后面继续挂节点 */
        public ProcessBuilder done() {
            // 如果有合并节点需求，自动加一个并行网关合并
            if (currentFrom != null) {
                // 最后一条未结束的分支
            }
            parent.lastElementId = parent.lastGatewayId;
            return parent;
        }

        private String getLastExpression() {
            if (branches.isEmpty()) return null;
            Branch b = branches.get(branches.size() - 1);
            String expr = b.expression;
            b.to = "done"; // mark as consumed
            return expr;
        }

        static class Branch {
            String expression;
            String to;
        }
    }

    // ────────── 从表格配置构建 ──────────

    /**
     * 从 ProcessConfigDTO 直接构建并部署，不产生任何 BPMN XML。
     */
    public static String deployFromConfig(
            com.ruoyi.workflow.model.ProcessConfigDTO config,
            RepositoryService repositoryService) {

        ProcessBuilder pb = create(config.getProcessKey(), config.getProcessName());
        pb.autoConnect = false; // 表格配置使用显式连线，不自动串联

        // 调试：打印收到的节点和连线
        System.out.println("📋 收到部署请求: key=" + config.getProcessKey() + ", name=" + config.getProcessName());
        System.out.println("   节点(" + config.getNodes().size() + "): " +
            config.getNodes().stream().map(n -> n.getId() + "(" + n.getType() + ")").toList());
        System.out.println("   连线(" + config.getLines().size() + "): " +
            config.getLines().stream().map(l -> l.getFrom() + "→" + l.getTo() +
                (l.getCondition() != null && !l.getCondition().isEmpty() ? "[" + l.getCondition() + "]" : "")).toList());

        for (com.ruoyi.workflow.model.ProcessConfigDTO.NodeDef node : config.getNodes()) {
            switch (node.getType()) {
                case "startEvent" -> pb.startEvent(node.getId(), node.getName());
                case "endEvent"   -> pb.endEvent(node.getId(), node.getName());
                case "userTask"   -> pb.userTask(node.getId(), node.getName(), node.getAssignee());
                case "exclusiveGateway" -> pb.gateway(node.getId(), node.getName()).done();
            }
        }

        for (com.ruoyi.workflow.model.ProcessConfigDTO.LineDef line : config.getLines()) {
            pb.flow(line.getFrom(), line.getTo(), line.getCondition());
        }

        return pb.deploy(repositoryService);
    }
}
