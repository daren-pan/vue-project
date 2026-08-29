package com.ruoyi.workflow.service.impl;

import com.ruoyi.common.core.exception.WorkflowException;
import com.ruoyi.system.api.RemoteUserService;
import com.ruoyi.workflow.domain.vo.TaskResult;
import com.ruoyi.workflow.service.FlowableService;
import com.ruoyi.workflow.service.IWorkflowTaskService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowInstanceServiceImpl#autoAdvance(String)} 单元测试
 *
 * @author ruoyi
 */
@ExtendWith(MockitoExtension.class)
class WorkflowInstanceServiceImplTest {

    @Mock
    private FlowableService flowableService;

    @Mock
    private TaskService taskService;

    @Mock
    private RemoteUserService remoteUserService;

    @Mock
    private IWorkflowTaskService workflowTaskService;

    @InjectMocks
    private WorkflowInstanceServiceImpl service;

    private static final String PI_ID = "PI_100001";

    @Test
    void autoAdvance_whenInstanceNotExistsAndNoHistory_throwsNotFound() {
        when(flowableService.getProcessInstance(PI_ID)).thenReturn(null);
        when(flowableService.getHistoricProcessInstance(PI_ID)).thenReturn(null);

        WorkflowException ex = assertThrows(WorkflowException.class,
                () -> service.autoAdvance(PI_ID));
        assertTrue(ex.getMessage().contains("不存在"));
        verifyNoInteractions(workflowTaskService);
    }

    @Test
    void autoAdvance_whenInstanceFinished_throwsFinished() {
        when(flowableService.getProcessInstance(PI_ID)).thenReturn(null);
        when(flowableService.getHistoricProcessInstance(PI_ID))
                .thenReturn(mock(HistoricProcessInstance.class));

        WorkflowException ex = assertThrows(WorkflowException.class,
                () -> service.autoAdvance(PI_ID));
        assertTrue(ex.getMessage().contains("已结束"));
        verifyNoInteractions(workflowTaskService);
    }

    @Test
    void autoAdvance_whenNoActiveTasks_throwsNoTask() {
        when(flowableService.getProcessInstance(PI_ID)).thenReturn(mock(ProcessInstance.class));
        when(flowableService.listTasksByInstance(PI_ID)).thenReturn(Collections.emptyList());

        WorkflowException ex = assertThrows(WorkflowException.class,
                () -> service.autoAdvance(PI_ID));
        assertTrue(ex.getMessage().contains("无待办任务"));
        verifyNoInteractions(workflowTaskService);
    }

    @Test
    void autoAdvance_whenTasksApproved_processTraversesToEnd() {
        when(flowableService.getProcessInstance(PI_ID))
                .thenReturn(mock(ProcessInstance.class))
                .thenReturn(null);
        Task task1 = mock(Task.class);
        when(task1.getId()).thenReturn("task1");
        Task task2 = mock(Task.class);
        when(task2.getId()).thenReturn("task2");
        when(flowableService.listTasksByInstance(PI_ID)).thenReturn(List.of(task1, task2));
        when(workflowTaskService.approve(anyString(), anyString())).thenAnswer(inv -> new TaskResult());

        TaskResult r = service.autoAdvance(PI_ID);

        assertEquals("自动通过", r.getAction());
        assertEquals(Boolean.TRUE, r.getProcessFinished());
        verify(workflowTaskService).approve("task1", "系统自动通过");
        verify(workflowTaskService).approve("task2", "系统自动通过");
    }

    @Test
    void autoAdvance_whenAllApprovalsFail_throwsNoAdvancedTask() {
        when(flowableService.getProcessInstance(PI_ID)).thenReturn(mock(ProcessInstance.class));
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task1");
        when(flowableService.listTasksByInstance(PI_ID)).thenReturn(List.of(task));
        when(workflowTaskService.approve(anyString(), anyString()))
                .thenThrow(new WorkflowException("任务不存在或已被处理"));

        WorkflowException ex = assertThrows(WorkflowException.class,
                () -> service.autoAdvance(PI_ID));
        assertTrue(ex.getMessage().contains("未发现可自动通过的任务"));
    }
}
