package com.netflix.conductor.core.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.common.metadata.events.EventHandler.Action.Type;
import com.netflix.conductor.common.metadata.events.EventHandler.StartWorkflow;
import com.netflix.conductor.common.metadata.events.EventHandler.TaskDetails;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskResult.Status;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class TestActionProcessor {
    private WorkflowExecutor workflowExecutor;
    private ActionProcessor actionProcessor;

    @Before
    public void setup() {
        workflowExecutor = mock(WorkflowExecutor.class);

        actionProcessor = new ActionProcessor(workflowExecutor, new ParametersUtils());
    }

    @Test
    public void testStartWorkflow() throws Exception {
        StartWorkflow startWorkflow = new StartWorkflow();
        startWorkflow.setName("testWorkflow");
        startWorkflow.getInput().put("testInput", "${testId}");

        Action action = new Action();
        action.setAction(Type.START_WORKFLOW);
        action.setStartWorkflow(startWorkflow);

        Object payload = new ObjectMapper().readValue("{\"testId\":\"test_1\"}", Object.class);

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testWorkflow");
        workflowDef.setVersion(1);

        when(workflowExecutor.startWorkflow(eq("testWorkflow"), eq(null), any(), any(), eq("testEvent")))
            .thenReturn("workflow_1");

        Map<String, Object> output = actionProcessor.execute(action, payload, "testEvent", "testMessage");

        assertNotNull(output);
        assertEquals("workflow_1", output.get("workflowId"));

        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(workflowExecutor).startWorkflow(eq("testWorkflow"), eq(null), any(), argumentCaptor.capture(), eq("testEvent"));
        assertEquals("test_1", argumentCaptor.getValue().get("testInput"));
        assertEquals("testMessage", argumentCaptor.getValue().get("conductor.event.messageId"));
        assertEquals("testEvent", argumentCaptor.getValue().get("conductor.event.name"));
    }

    @Test
    public void testCompleteTask() throws Exception {
        TaskDetails taskDetails = new TaskDetails();
        taskDetails.setWorkflowId("${workflowId}");
        taskDetails.setTaskRefName("testTask");

        Action action = new Action();
        action.setAction(Type.COMPLETE_TASK);
        action.setCompleteTask(taskDetails);

        Object payload = new ObjectMapper().readValue("{\"workflowId\":\"workflow_1\"}", Object.class);

        Task task = new Task();
        task.setReferenceTaskName("testTask");
        Workflow workflow = new Workflow();
        workflow.getTasks().add(task);

        when(workflowExecutor.getWorkflow(eq("workflow_1"), anyBoolean())).thenReturn(workflow);

        actionProcessor.execute(action, payload, "testEvent", "testMessage");

        ArgumentCaptor<TaskResult> argumentCaptor = ArgumentCaptor.forClass(TaskResult.class);
        verify(workflowExecutor).updateTask(argumentCaptor.capture());
        assertEquals(Status.COMPLETED, argumentCaptor.getValue().getStatus());
        assertEquals("testMessage", argumentCaptor.getValue().getOutputData().get("conductor.event.messageId"));
        assertEquals("testEvent", argumentCaptor.getValue().getOutputData().get("conductor.event.name"));
        assertEquals("workflow_1", argumentCaptor.getValue().getOutputData().get("workflowId"));
        assertEquals("testTask", argumentCaptor.getValue().getOutputData().get("taskRefName"));
    }

    @Test
    public void testCompleteTaskByTaskId() throws Exception {
        TaskDetails taskDetails = new TaskDetails();
        taskDetails.setWorkflowId("${workflowId}");
        taskDetails.setTaskId("${taskId}");

        Action action = new Action();
        action.setAction(Type.COMPLETE_TASK);
        action.setCompleteTask(taskDetails);

        Object payload = new ObjectMapper().readValue("{\"workflowId\":\"workflow_1\", \"taskId\":\"task_1\"}", Object.class);

        Task task = new Task();
        task.setTaskId("task_1");
        task.setReferenceTaskName("testTask");

        when(workflowExecutor.getTask(eq("task_1"))).thenReturn(task);

        actionProcessor.execute(action, payload, "testEvent", "testMessage");

        ArgumentCaptor<TaskResult> argumentCaptor = ArgumentCaptor.forClass(TaskResult.class);
        verify(workflowExecutor).updateTask(argumentCaptor.capture());
        assertEquals(Status.COMPLETED, argumentCaptor.getValue().getStatus());
        assertEquals("testMessage", argumentCaptor.getValue().getOutputData().get("conductor.event.messageId"));
        assertEquals("testEvent", argumentCaptor.getValue().getOutputData().get("conductor.event.name"));
        assertEquals("workflow_1", argumentCaptor.getValue().getOutputData().get("workflowId"));
        assertEquals("task_1", argumentCaptor.getValue().getOutputData().get("taskId"));
    }
}
