package com.ruoyi.system.mapper.workflow;

import com.ruoyi.system.domain.workflow.WfTask;
import java.util.List;

public interface WfTaskMapper {

    WfTask selectWfTaskById(Long id);

    List<WfTask> selectByAssignee(String assignee);

    List<WfTask> selectByBusinessKeyAndNode(String businessKey, String nodeId);

    int countByInstanceAndNode(Long instanceId, String nodeId);

    int countByInstanceAndNodeAndStatus(Long instanceId, String nodeId, String status);

    List<WfTask> selectTimeoutTasks();

    List<WfTask> selectWfTaskList(WfTask task);

    int insertWfTask(WfTask task);

    int updateWfTask(WfTask task);

    int deleteWfTaskByInstanceId(Long instanceId);
}

