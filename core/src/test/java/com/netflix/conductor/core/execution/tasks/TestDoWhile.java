/*
 *  Copyright 2021 Netflix, Inc.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.execution.tasks;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.orchestration.ExecutionDAOFacade;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionLockService;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestDoWhile {

    DeciderService deciderService;
    MetadataDAO metadataDAO;
    QueueDAO queueDAO;
    MetadataMapperService metadataMapperService;
    WorkflowStatusListener workflowStatusListener;
    ExecutionDAOFacade executionDAOFacade;
    ExecutionLockService executionLockService;
    ConductorProperties properties;
    ParametersUtils parametersUtils;
    SystemTaskRegistry systemTaskRegistry;
    private Workflow workflow;
    private Task loopTask;
    private TaskDef loopTaskDef;
    private WorkflowTask loopWorkflowTask;
    private Task task1;
    private Task task2;
    private WorkflowExecutor provider;
    private DoWhile doWhile;

    @Before
    public void setup() {
        workflow = mock(Workflow.class);
        deciderService = mock(DeciderService.class);
        metadataDAO = mock(MetadataDAO.class);
        queueDAO = mock(QueueDAO.class);
        parametersUtils = mock(ParametersUtils.class);
        metadataMapperService = mock(MetadataMapperService.class);
        workflowStatusListener = mock(WorkflowStatusListener.class);
        executionDAOFacade = mock(ExecutionDAOFacade.class);
        executionLockService = mock(ExecutionLockService.class);
        properties = mock(ConductorProperties.class);
        systemTaskRegistry = mock(SystemTaskRegistry.class);
        when(properties.getActiveWorkerLastPollTimeout()).thenReturn(Duration.ofSeconds(100));
        when(properties.getTaskExecutionPostponeDuration()).thenReturn(Duration.ofSeconds(60));
        when(properties.getWorkflowOffsetTimeout()).thenReturn(Duration.ofSeconds(30));
        provider = spy(new WorkflowExecutor(deciderService, metadataDAO, queueDAO, metadataMapperService,
            workflowStatusListener, executionDAOFacade, properties, executionLockService, systemTaskRegistry, parametersUtils));
        WorkflowTask loopWorkflowTask1 = new WorkflowTask();
        loopWorkflowTask1.setTaskReferenceName("task1");
        loopWorkflowTask1.setName("task1");
        WorkflowTask loopWorkflowTask2 = new WorkflowTask();
        loopWorkflowTask2.setTaskReferenceName("task2");
        loopWorkflowTask2.setName("task2");
        task1 = new Task();
        task1.setWorkflowTask(loopWorkflowTask1);
        task1.setReferenceTaskName("task1");
        task1.setStatus(Task.Status.COMPLETED);
        task1.setTaskType(TaskType.HTTP.name());
        task1.setInputData(new HashMap<>());
        task1.setIteration(1);
        task2 = new Task();
        task2.setWorkflowTask(loopWorkflowTask2);
        task2.setReferenceTaskName("task2");
        task2.setStatus(Task.Status.COMPLETED);
        task2.setTaskType(TaskType.HTTP.name());
        task2.setInputData(new HashMap<>());
        task2.setIteration(1);
        loopTask = new Task();
        loopTask.setReferenceTaskName("loopTask");
        loopTask.setTaskType(TaskType.DO_WHILE.name());
        loopTask.setInputData(new HashMap<>());
        loopTask.setIteration(1);
        loopWorkflowTask = new WorkflowTask();
        loopWorkflowTask.setTaskReferenceName("loopTask");
        loopWorkflowTask.setType(TaskType.DO_WHILE.name());
        loopWorkflowTask.setName("loopTask");
        loopWorkflowTask.setLoopCondition("if ($.loopTask['iteration'] < 1) { false; } else { true; }");
        loopWorkflowTask.setLoopOver(Arrays.asList(task1.getWorkflowTask(), task2.getWorkflowTask()));
        loopTask.setWorkflowTask(loopWorkflowTask);
        doWhile = new DoWhile(parametersUtils);
        loopTaskDef = mock(TaskDef.class);
        doReturn(loopTaskDef).when(provider).getTaskDefinition(loopTask);
        doReturn(task1).when(workflow).getTaskByRefName(task1.getReferenceTaskName());
        doReturn(task2).when(workflow).getTaskByRefName(task2.getReferenceTaskName());
        doReturn(task1).when(workflow).getTaskByRefName("task1__2");
        doReturn(task2).when(workflow).getTaskByRefName("task2__2");
        doReturn(new HashMap<>()).when(parametersUtils)
            .getTaskInputV2(isA(Map.class), isA(Workflow.class), isA(String.class), isA(TaskDef.class));
    }

    @Test
    public void testSingleSuccessfulIteration() {
        doReturn(Arrays.asList(task1, task2)).when(workflow).getTasks();
        loopWorkflowTask.setLoopCondition("if ($.loopTask['iteration'] < 1) { true; } else { false; }");
        boolean success = doWhile.execute(workflow, loopTask, provider);
        assertTrue(success);
        verify(provider, times(0)).scheduleNextIteration(loopTask, workflow);
        assertEquals(loopTask.getStatus(), Task.Status.COMPLETED);
    }

    @Test
    public void testSingleFailedIteration() {
        task1.setStatus(Task.Status.FAILED);
        String reason = "Test";
        task1.setReasonForIncompletion(reason);
        doReturn(Arrays.asList(task1, task2, loopTask)).when(workflow).getTasks();
        boolean success = doWhile.execute(workflow, loopTask, provider);
        assertTrue(success);
        assertEquals(loopTask.getStatus(), Task.Status.FAILED);
        assertNotEquals(reason, loopTask.getReasonForIncompletion());
    }

    @Test
    public void testInProgress() {
        loopTask.setStatus(Task.Status.IN_PROGRESS);
        task1.setStatus(Task.Status.IN_PROGRESS);
        doReturn(Arrays.asList(task1, task2, loopTask)).when(workflow).getTasks();
        boolean success = doWhile.execute(workflow, loopTask, provider);
        assertFalse(success);
        assertSame(loopTask.getStatus(), Status.IN_PROGRESS);
    }

    @Test
    public void testSingleIteration() {
        loopTask.setStatus(Task.Status.IN_PROGRESS);
        doReturn(Arrays.asList(task1, task2)).when(workflow).getTasks();
        loopWorkflowTask.setLoopCondition("if ($.loopTask['iteration'] > 1) { false; } else { true; }");
        doNothing().when(provider).scheduleNextIteration(loopTask, workflow);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        assertTrue(success);
        assertEquals(loopTask.getIteration(), 2);
        verify(provider, times(1)).scheduleNextIteration(loopTask, workflow);
        assertSame(loopTask.getStatus(), Status.IN_PROGRESS);
    }

    @Test
    public void testLoopOverTaskOutputInCondition() {
        loopTask.setStatus(Task.Status.IN_PROGRESS);
        Map<String, Object> output = new HashMap<>();
        output.put("value", 1);
        task1.setOutputData(output);
        doReturn(Arrays.asList(task1, task2)).when(workflow).getTasks();
        loopWorkflowTask.setLoopCondition("if ($.task1['value'] == 1) { false; } else { true; }");
        doNothing().when(provider).scheduleNextIteration(loopTask, workflow);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        assertTrue(success);
        verify(provider, times(0)).scheduleNextIteration(loopTask, workflow);
        assertSame(loopTask.getStatus(), Status.COMPLETED);
    }

    @Test
    public void testInputParameterInCondition() {
        Map<String, Object> output = new HashMap<>();
        output.put("value", 1);
        loopTask.setInputData(output);
        loopTask.setStatus(Task.Status.IN_PROGRESS);
        loopWorkflowTask.setInputParameters(output);
        doReturn(output).when(parametersUtils)
            .getTaskInputV2(loopTask.getWorkflowTask().getInputParameters(), workflow, loopTask.getTaskId(),
                loopTaskDef);
        doReturn(Arrays.asList(task1, task2)).when(workflow).getTasks();
        loopWorkflowTask.setLoopCondition("if ($.value == 1) { false; } else { true; }");
        doNothing().when(provider).scheduleNextIteration(loopTask, workflow);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        assertTrue(success);
        verify(provider, times(0)).scheduleNextIteration(loopTask, workflow);
        assertSame(loopTask.getStatus(), Status.COMPLETED);
    }

    @Test
    public void testSecondIteration() {
        loopTask.setStatus(Task.Status.IN_PROGRESS);
        doReturn(Arrays.asList(task1, task2)).when(workflow).getTasks();
        loopWorkflowTask.setLoopCondition("if ($.loopTask['iteration'] > 1) { false; } else { true; }");
        doNothing().when(provider).scheduleNextIteration(loopTask, workflow);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        assertTrue(success);
        doReturn(Arrays.asList(task1, task2)).when(workflow).getTasks();
        task1.setReferenceTaskName("task1__2");
        task2.setReferenceTaskName("task1__2");
        success = doWhile.execute(workflow, loopTask, provider);
        assertTrue(success);
        verify(provider, times(1)).scheduleNextIteration(loopTask, workflow);
        assertEquals(loopTask.getStatus(), Task.Status.COMPLETED);
    }

    @Test
    public void testConditionException() {
        loopTask.setTaskId("1");
        loopWorkflowTask.setLoopCondition("This will give exception");
        doNothing().when(provider).scheduleNextIteration(loopTask, workflow);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        assertTrue(success);
        assertSame(loopTask.getStatus(), Status.FAILED_WITH_TERMINAL_ERROR);
    }
}
