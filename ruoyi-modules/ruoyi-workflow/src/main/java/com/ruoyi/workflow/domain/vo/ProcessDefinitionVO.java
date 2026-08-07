package com.ruoyi.workflow.domain.vo;

import java.util.Date;
import java.util.List;

/**
 * 流程定义列表项 VO
 *
 * @author ruoyi
 */
public class ProcessDefinitionVO {

    /** 流程定义ID */
    private String id;
    /** 部署ID */
    private String deploymentId;
    /** 流程标识 */
    private String key;
    /** 流程名称 */
    private String name;
    /** 版本号 */
    private Integer version;
    /** 部署时间 */
    private Date deployTime;
    /** 部署人 */
    private String deployer;
    /** 抄送人列表 */
    private List<String> ccUsers;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Date getDeployTime() { return deployTime; }
    public void setDeployTime(Date deployTime) { this.deployTime = deployTime; }
    public String getDeployer() { return deployer; }
    public void setDeployer(String deployer) { this.deployer = deployer; }
    public List<String> getCcUsers() { return ccUsers; }
    public void setCcUsers(List<String> ccUsers) { this.ccUsers = ccUsers; }
}
