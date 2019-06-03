package com.netflix.conductor.core.execution.tasks;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.DeciderService;
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


    @Before
    public void setup() {
        workflow = new Workflow();
        deciderService = Mockito.mock(DeciderService.class);
        metadataDAO = Mockito.mock(MetadataDAO.class);
        queueDAO = Mockito.mock(QueueDAO.class);
        metadataMapperService = Mockito.mock(MetadataMapperService.class);
        workflowStatusListener = Mockito.mock(WorkflowStatusListener.class);
        executionDAOFacade = Mockito.mock(ExecutionDAOFacade.class);
        externalPayloadStorageUtils = Mockito.mock(ExternalPayloadStorageUtils.class);
        config = Mockito.mock(Configuration.class);
        provider = spy(new WorkflowExecutor(deciderService, metadataDAO, queueDAO, metadataMapperService,
                workflowStatusListener, executionDAOFacade, externalPayloadStorageUtils, config));
        loopWorkflowTask1 = new WorkflowTask();
        loopWorkflowTask1.setTaskReferenceName("task1");
        loopWorkflowTask2 = new WorkflowTask();
        loopWorkflowTask2.setTaskReferenceName("task2");
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
        loopWorkflowTask.setTaskReferenceName("loopWorkflowTask");
        loopWorkflowTask.setLoopCondition("if ($.task1['task1'] + $.task2['task2'] > 10) { false; } else { true; }");
        loopWorkflowTask.setLoopOver(Arrays.asList(task1.getWorkflowTask(), task2.getWorkflowTask()));
        loopTask.setWorkflowTask(loopWorkflowTask);
        doWhile = new DoWhile();
        workflow.setTasks(Arrays.asList(task1, task2, loopTask));
    }


    @Test
    public void testSingleSuccessfulIteration() {
        Map<String, Object> output1 = new HashMap<>();
        output1.put("task1", 7);
        task1.setOutputData(output1);
        Map<String, Object> output2 = new HashMap<>();
        output2.put("task2", 7);
        task2.setOutputData(output2);
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
        task1.setStatus(Task.Status.IN_PROGRESS);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertFalse(success);
    }

    @Test
    public void testSingleIteration() {
        Map<String, Object> output1 = new HashMap<>();
        output1.put("task1", 2);
        task1.setOutputData(output1);
        Map<String, Object> output2 = new HashMap<>();
        output1.put("task2", 2);
        task2.setOutputData(output2);
        loopTask.setTaskId("1");
        List<Task> list = Arrays.asList(task1, task2);
        Mockito.doReturn(false).when(provider).scheduleTask(workflow, list);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertTrue(success);
    }

    @Test(expected=RuntimeException.class)
    public void testConditionException() {
        loopTask.setTaskId("1");
        loopWorkflowTask.setLoopCondition("this will give exception");
        List<Task> list = Arrays.asList(task1, task2);
        Mockito.doReturn(false).when(provider).scheduleTask(workflow, list);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertFalse(success);
    }


}
