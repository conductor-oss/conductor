package com.netflix.conductor.core.execution.tasks;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.ScriptEvaluator;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.WorkflowStatusListener;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.orchestration.ExecutionDAOFacade;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;

import javax.script.ScriptException;
import javax.validation.constraints.AssertTrue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.spy;

/**
 * @author Manan
 *
 */
public class DoWhileTest {

    private Workflow workflow;
    private Task loopTask;
    private WorkflowTask loopWorkflowTask;
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
        loopTask = new Task();
        loopTask.setReferenceTaskName("loopTask");
        Map<String, Object> map = new HashMap<>();
        map.put("loopOver", Arrays.asList("task1", "task2"));
        loopTask.setInputData(map);
        loopTask.setTaskType(TaskType.DO_WHILE.name());
        loopWorkflowTask = new WorkflowTask();
        loopWorkflowTask.setLoopCondition("if ($.task1 + $.task2 > 10) { false; } else { true; }");
        loopWorkflowTask.setLoopOver(Arrays.asList("task1", "task2"));
        loopTask.setWorkflowTask(loopWorkflowTask);
        task1 = new Task();
        task1.setReferenceTaskName("task1");
        task1.setStatus(Task.Status.COMPLETED);
        task1.setTaskType(TaskType.HTTP.name());
        task2 = new Task();
        task2.setReferenceTaskName("task2");
        task2.setStatus(Task.Status.COMPLETED);
        task2.setTaskType(TaskType.HTTP.name());workflow.setTasks(Arrays.asList(task1, task2, loopTask));
        doWhile = new DoWhile();
    }


    @Test
    public void testSingleSuccessfulIteration() {
        Map<String, Object> output = new HashMap<>();
        output.put("body", 7);
        task1.setOutputData(output);
        task2.setOutputData(output);
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
        Map<String, Object> output = new HashMap<>();
        output.put("body", 3);
        loopTask.setTaskId("1");
        task1.setOutputData(output);
        task2.setOutputData(output);
        List<Task> list = Arrays.asList(task1, task2);
        Mockito.doReturn(false).when(provider).scheduleTask(workflow, list);
        boolean success = doWhile.execute(workflow, loopTask, provider);
        Assert.assertFalse(success);
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
