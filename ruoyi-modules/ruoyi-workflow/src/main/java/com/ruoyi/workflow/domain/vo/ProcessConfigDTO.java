package com.ruoyi.workflow.domain.vo;

import java.util.List;

/**
 * 流程配置 DTO —— 前端表格形式提交的流程定义
 *
 * @author ruoyi
 */
public class ProcessConfigDTO {

    /** 流程标识（如 leave） */
    private String processKey;
    /** 流程名称（如 请假审批） */
    private String processName;
    /** 节点列表 */
    private List<NodeDef> nodes;
    /** 连线列表 */
    private List<LineDef> lines;
    /** 抄送人列表（流程结束自动通知） */
    private List<String> ccUsers;

    public String getProcessKey() { return processKey; }
    public void setProcessKey(String processKey) { this.processKey = processKey; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public List<NodeDef> getNodes() { return nodes; }
    public void setNodes(List<NodeDef> nodes) { this.nodes = nodes; }
    public List<LineDef> getLines() { return lines; }
    public void setLines(List<LineDef> lines) { this.lines = lines; }
    public List<String> getCcUsers() { return ccUsers; }
    public void setCcUsers(List<String> ccUsers) { this.ccUsers = ccUsers; }

    /**
     * 节点定义
     */
    public static class NodeDef {
        /** 节点标识（如 managerApprove） */
        private String id;
        /** 节点名称（如 部门经理审批） */
        private String name;
        /** 节点类型：startEvent / endEvent / userTask / exclusiveGateway */
        private String type;
        /** 审批人（仅 userTask 有效），支持变量 ${manager}。会签时不填此项 */
        private String assignee;
        /** 会签人列表（仅 userTask 有效），多人时创建会签节点 */
        private List<String> assigneeList;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getAssignee() { return assignee; }
        public void setAssignee(String assignee) { this.assignee = assignee; }
        public List<String> getAssigneeList() { return assigneeList; }
        public void setAssigneeList(List<String> assigneeList) { this.assigneeList = assigneeList; }
    }

    /**
     * 连线定义
     */
    public static class LineDef {
        /** 来源节点 id */
        private String from;
        /** 目标节点 id */
        private String to;
        /** 条件表达式（如 days > 3），仅网关分支需要 */
        private String condition;

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
    }
}
