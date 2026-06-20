package com.ruoyi.system.service.workflow;

import com.ruoyi.common.core.utils.SpringUtils;
import com.ruoyi.system.domain.workflow.WfTask;
import com.ruoyi.system.mapper.workflow.WfTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工作流超时处理定时任务
 * 
 * 功能说明：
 * 当审批节点配置了超时时间（timeout_hours > 0），
 * 如果审批人在规定时间内未处理待办任务，此定时任务会自动处理。
 * 
 * 超时动作（由节点定义的 timeout_action 控制）：
 * - auto_pass：系统自动通过
 * - auto_reject：系统自动驳回
 * - remind：发送催办通知（暂未实现）
 * - auto_transfer：转交给上级（暂未实现）
 * 
 * 当前实现：每分钟扫描一次，找到所有超时的待办，调用 approve 方法自动通过。
 */
@Component
public class WorkflowTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTimeoutTask.class);

    @Autowired
    private WfTaskMapper taskMapper;

    /**
     * 扫描并处理超时待办任务
     * 
     * 每小时执行一次
     * 查询条件：任务状态为 pending，且节点配置了超时时间，且创建时间已超过超时时间
     * 
     * 处理逻辑：
     * 1. 查询所有超时的待办任务
     * 2. 遍历每个超时任务，调用引擎的 approve 方法自动通过
     * 3. 记录超时自动处理日志
     */
    @Scheduled(fixedRate = 3600000)
    public void processTimeoutTasks() {
        List<WfTask> timeoutTasks = taskMapper.selectTimeoutTasks();
        if (timeoutTasks.isEmpty()) return;

        log.info("发现 {} 个超时待办任务", timeoutTasks.size());

        IWorkflowEngine engine = SpringUtils.getBean(IWorkflowEngine.class);
        for (WfTask task : timeoutTasks) {
            try {
                log.info("超时自动处理任务：taskId={}, nodeName={}, assignee={}",
                        task.getId(), task.getNodeName(), task.getAssignee());
                engine.approve(task.getId(), "auto_pass", "系统自动通过（超时）");
            } catch (Exception e) {
                log.error("超时任务处理失败：taskId={}", task.getId(), e);
            }
        }
    }
}
