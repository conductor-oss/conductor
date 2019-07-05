package com.netflix.conductor.core.execution.tasks;

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
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.spy;

/**
 * @author Manan
 *
 */
public class DoWhileTest {

    private Workflow workflow;
    private Task loopTask;
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
    ExternalPayloadStorageUtils externalPayloadStorageUtils;
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
        externalPayloadStorageUtils = Mockito.mock(ExternalPayloadStorageUtils.class);
        config = Mockito.mock(Configuration.class);
        provider = spy(new WorkflowExecutor(deciderService, metadataDAO, queueDAO, metadataMapperService,
                workflowStatusListener, executionDAOFacade, externalPayloadStorageUtils, config));
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
        task2 = new Task();
        task2.setWorkflowTask(loopWorkflowTask2);
        task2.setReferenceTaskName("task2");
        task2.setStatus(Task.Status.COMPLETED);
        task2.setTaskType(TaskType.HTTP.name());
        loopTask = new Task();
        loopTask.setReferenceTaskName("loopTask");
        loopTask.setTaskType(TaskType.DO_WHILE.name());
        loopWorkflowTask = new WorkflowTask();
        loopWorkflowTask.setTaskReferenceName("loopTask");
        loopWorkflowTask.setName("loopTask");
        loopWorkflowTask.setLoopCondition("if ($.loopTask['iteration'] < 1) { false; } else { true; }");
        loopWorkflowTask.setLoopOver(Arrays.asList(task1.getWorkflowTask(), task2.getWorkflowTask()));
        loopTask.setWorkflowTask(loopWorkflowTask);
        doWhile = new DoWhile();
        workflow.setTasks(Arrays.asList(task1, task2, loopTask));
        Mockito.doReturn(new TaskDef()).when(provider).getTaskDefinition(loopTask);
        Mockito.doReturn(task1).when(workflow).getTaskByRefName(task1.getReferenceTaskName());
        Mockito.doReturn(task2).when(workflow).getTaskByRefName(task2.getReferenceTaskName());
        Mockito.doReturn(new HashMap<>()).when(parametersUtils).getTaskInputV2(isA(Map.class), isA(Workflow.class), isA(String.class), isA(TaskDef.class));
    }


    @Test
    public void testSingleSuccessfulIteration() {
        loopWorkflowTask.setLoopCondition("if ($.loopTask['iteration'] < 1) { false; } else { true; }");
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertTrue(success);
        Assert.assertEquals(loopTask.getStatus(), Task.Status.COMPLETED);
    }

    @Test
    public void testSingleFailedIteration() {
        task1.setStatus(Task.Status.FAILED);
        String reason = "Test";
        task1.setReasonForIncompletion(reason);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertTrue(success);
        Assert.assertEquals(loopTask.getStatus(), Task.Status.FAILED);
        Assert.assertFalse(reason.equals(loopTask.getReasonForIncompletion()));
    }

    @Test
    public void testInProgress() {
        loopTask.setStatus(Task.Status.IN_PROGRESS);
        task1.setStatus(Task.Status.IN_PROGRESS);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertFalse(success);
        Assert.assertTrue(loopTask.getStatus() == Task.Status.IN_PROGRESS);
    }

    @Test
    public void testSingleIteration() {
        loopTask.setStatus(Task.Status.IN_PROGRESS);
        loopWorkflowTask.setLoopCondition("if ($.loopTask['iteration'] > 1) { false; } else { true; }");
        Mockito.doNothing().when(provider).scheduleLoopTasks(loopTask, workflow);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertTrue(success);
        Assert.assertTrue(loopTask.getStatus() == Task.Status.IN_PROGRESS);
    }

    @Test
    public void testConditionException() {
        loopTask.setTaskId("1");
        loopWorkflowTask.setLoopCondition("this will give exception");
        Mockito.doNothing().when(provider).scheduleLoopTasks(loopTask, workflow);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertTrue(success);
        Assert.assertTrue(loopTask.getStatus() == Task.Status.FAILED_WITH_TERMINAL_ERROR);
    }


}
