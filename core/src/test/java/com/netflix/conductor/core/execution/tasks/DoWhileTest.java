/*
 * Copyright 2019 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.execution.tasks;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.WorkflowStatusListener;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.orchestration.ExecutionDAOFacade;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import com.netflix.conductor.service.ExecutionLockService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author Manan
 *
 */
public class DoWhileTest {

    private Workflow workflow;
    private Task loopTask;
    private TaskDef loopTaskDef;
    private WorkflowTask loopWorkflowTask;
    private WorkflowTask loopWorkflowTask1;
    private WorkflowTask loopWorkflowTask2;
    private Task task1;
    private Task task2;
    private WorkflowExecutor provider;
    private DoWhile doWhile;
    DeciderService deciderService;
    MetadataDAO metadataDAO;
    QueueDAO queueDAO ;
    MetadataMapperService metadataMapperService;
    WorkflowStatusListener workflowStatusListener ;
    ExecutionDAOFacade executionDAOFacade;
    ExecutionLockService executionLockService;
    Configuration config;
    ParametersUtils parametersUtils;


    @Before
    public void setup() {
        workflow = Mockito.mock(Workflow.class);
        deciderService = Mockito.mock(DeciderService.class);
        metadataDAO = Mockito.mock(MetadataDAO.class);
        queueDAO = Mockito.mock(QueueDAO.class);
        parametersUtils = Mockito.mock(ParametersUtils.class);
        metadataMapperService = Mockito.mock(MetadataMapperService.class);
        workflowStatusListener = Mockito.mock(WorkflowStatusListener.class);
        executionDAOFacade = Mockito.mock(ExecutionDAOFacade.class);
        executionLockService = Mockito.mock(ExecutionLockService.class);
        config = Mockito.mock(Configuration.class);
        provider = spy(new WorkflowExecutor(deciderService, metadataDAO, queueDAO, metadataMapperService,
                workflowStatusListener, executionDAOFacade, config, executionLockService, parametersUtils));
        loopWorkflowTask1 = new WorkflowTask();
        loopWorkflowTask1.setTaskReferenceName("task1");
        loopWorkflowTask1.setName("task1");
        loopWorkflowTask2 = new WorkflowTask();
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
        doWhile = new DoWhile();
        loopTaskDef = Mockito.mock(TaskDef.class);
        Mockito.doReturn(loopTaskDef).when(provider).getTaskDefinition(loopTask);
        Mockito.doReturn(task1).when(workflow).getTaskByRefName(task1.getReferenceTaskName());
        Mockito.doReturn(task2).when(workflow).getTaskByRefName(task2.getReferenceTaskName());
        Mockito.doReturn(task1).when(workflow).getTaskByRefName("task1__2");
        Mockito.doReturn(task2).when(workflow).getTaskByRefName("task2__2");
        Mockito.doReturn(new HashMap<>()).when(parametersUtils).getTaskInputV2(isA(Map.class), isA(Workflow.class), isA(String.class), isA(TaskDef.class));
    }


    @Test
    public void testSingleSuccessfulIteration() {
        Mockito.doReturn(Arrays.asList(task1, task2)).when(workflow).getTasks();
        loopWorkflowTask.setLoopCondition("if ($.loopTask['iteration'] < 1) { true; } else { false; }");
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertTrue(success);
        Mockito.verify(provider, times(0)).scheduleNextIteration(loopTask, workflow);
        Assert.assertEquals(loopTask.getStatus(), Task.Status.COMPLETED);
    }

    @Test
    public void testSingleFailedIteration() {
        task1.setStatus(Task.Status.FAILED);
        String reason = "Test";
        task1.setReasonForIncompletion(reason);
        Mockito.doReturn(Arrays.asList(task1, task2, loopTask)).when(workflow).getTasks();
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertTrue(success);
        Assert.assertEquals(loopTask.getStatus(), Task.Status.FAILED);
        Assert.assertFalse(reason.equals(loopTask.getReasonForIncompletion()));
    }

    @Test
    public void testInProgress() {
        loopTask.setStatus(Task.Status.IN_PROGRESS);
        task1.setStatus(Task.Status.IN_PROGRESS);
        Mockito.doReturn(Arrays.asList(task1, task2, loopTask)).when(workflow).getTasks();
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertFalse(success);
        Assert.assertTrue(loopTask.getStatus() == Task.Status.IN_PROGRESS);
    }

    @Test
    public void testSingleIteration() {
        loopTask.setStatus(Task.Status.IN_PROGRESS);
        Mockito.doReturn(Arrays.asList(task1, task2)).when(workflow).getTasks();
        loopWorkflowTask.setLoopCondition("if ($.loopTask['iteration'] > 1) { false; } else { true; }");
        Mockito.doNothing().when(provider).scheduleNextIteration(loopTask, workflow);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertTrue(success);
        Assert.assertEquals(loopTask.getIteration(), 2);
        Mockito.verify(provider, times(1)).scheduleNextIteration(loopTask, workflow);
        Assert.assertTrue(loopTask.getStatus() == Task.Status.IN_PROGRESS);
    }

    @Test
    public void testLoopOverTaskOutputInCondition() {
        loopTask.setStatus(Task.Status.IN_PROGRESS);
        Map<String, Object> output = new HashMap<>();
        output.put("value", 1);
        task1.setOutputData(output);
        Mockito.doReturn(Arrays.asList(task1, task2)).when(workflow).getTasks();
        loopWorkflowTask.setLoopCondition("if ($.task1['value'] == 1) { false; } else { true; }");
        Mockito.doNothing().when(provider).scheduleNextIteration(loopTask, workflow);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertTrue(success);
        Mockito.verify(provider, times(0)).scheduleNextIteration(loopTask, workflow);
        Assert.assertTrue(loopTask.getStatus() == Task.Status.COMPLETED);
    }

    @Test
    public void testInputParameterInCondition() {
        Map<String, Object> output = new HashMap<>();
        output.put("value", 1);
        loopTask.setInputData(output);
        loopTask.setStatus(Task.Status.IN_PROGRESS);
        loopWorkflowTask.setInputParameters(output);
        Mockito.doReturn(output).when(parametersUtils).getTaskInputV2(loopTask.getWorkflowTask().getInputParameters(), workflow, loopTask.getTaskId(), loopTaskDef);
        Mockito.doReturn(Arrays.asList(task1, task2)).when(workflow).getTasks();
        loopWorkflowTask.setLoopCondition("if ($.value == 1) { false; } else { true; }");
        Mockito.doNothing().when(provider).scheduleNextIteration(loopTask, workflow);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertTrue(success);
        Mockito.verify(provider, times(0)).scheduleNextIteration(loopTask, workflow);
        Assert.assertTrue(loopTask.getStatus() == Task.Status.COMPLETED);
    }

    @Test
    public void testSecondIteration() {
        loopTask.setStatus(Task.Status.IN_PROGRESS);
        Mockito.doReturn(Arrays.asList(task1, task2)).when(workflow).getTasks();
        loopWorkflowTask.setLoopCondition("if ($.loopTask['iteration'] > 1) { false; } else { true; }");
        Mockito.doNothing().when(provider).scheduleNextIteration(loopTask, workflow);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertTrue(success);
        Mockito.doReturn(Arrays.asList(task1, task2)).when(workflow).getTasks();
        task1.setReferenceTaskName("task1__2");
        task2.setReferenceTaskName("task1__2");
        success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertTrue(success);
        Mockito.verify(provider, times(1)).scheduleNextIteration(loopTask, workflow);
        Assert.assertEquals(loopTask.getStatus(), Task.Status.COMPLETED);
    }



    @Test
    public void testConditionException() {
        loopTask.setTaskId("1");
        loopWorkflowTask.setLoopCondition("This will give exception");
        Mockito.doNothing().when(provider).scheduleNextIteration(loopTask, workflow);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertTrue(success);
        Assert.assertTrue(loopTask.getStatus() == Task.Status.FAILED_WITH_TERMINAL_ERROR);
    }


}
