package com.netflix.conductor.core.execution.tasks;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.conductor.core.execution.tasks.Terminate.getTerminationStatusParameter;
import static com.netflix.conductor.core.execution.tasks.Terminate.getTerminationWorkflowOutputParameter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class TestTerminateTask {


    private WorkflowExecutor executor = mock(WorkflowExecutor.class);

    @Test
    public void should_fail_if_input_status_is_not_valid() {
        Workflow workflow = new Workflow();
        Terminate terminateTask = new Terminate();

        Map<String, Object> input = new HashMap<>();
        input.put(getTerminationStatusParameter(), "PAUSED");

        Task task = new Task();
        task.getInputData().putAll(input);
        terminateTask.execute(workflow, task, executor);
        assertEquals(Task.Status.FAILED, task.getStatus());
    }

    @Test
    public void should_fail_if_input_status_is_empty() {
        Workflow workflow = new Workflow();
        Terminate terminateTask = new Terminate();

        Map<String, Object> input = new HashMap<>();
        input.put(getTerminationStatusParameter(), "");

        Task task = new Task();
        task.getInputData().putAll(input);
        terminateTask.execute(workflow, task, executor);
        assertEquals(Task.Status.FAILED, task.getStatus());
    }

    @Test
    public void should_fail_if_input_status_is_null() {
        Workflow workflow = new Workflow();
        Terminate terminateTask = new Terminate();

        Map<String, Object> input = new HashMap<>();
        input.put(getTerminationStatusParameter(), null);

        Task task = new Task();
        task.getInputData().putAll(input);
        terminateTask.execute(workflow, task, executor);
        assertEquals(Task.Status.FAILED, task.getStatus());
    }

    @Test
    public void should_complete_workflow_on_terminate_task_success() {
        Workflow workflow = new Workflow();
        Terminate terminateTask = new Terminate();
        workflow.setOutput(Collections.singletonMap("output", "${task1.output.value}"));

        HashMap<String, Object> expectedOutput = new HashMap<String, Object>() {{ put("output", "${task0.output.value}"); }};

        Map<String, Object> input = new HashMap<>();
        input.put(getTerminationStatusParameter(), "COMPLETED");
        input.put(getTerminationWorkflowOutputParameter(), "${task0.output.value}");

        Task task = new Task();
        task.getInputData().putAll(input);
        terminateTask.execute(workflow, task, executor);
        assertEquals(Task.Status.COMPLETED, task.getStatus());
        assertEquals(expectedOutput, task.getOutputData());
    }

    @Test
    public void should_fail_workflow_on_terminate_task_success() {
        Workflow workflow = new Workflow();
        Terminate terminateTask = new Terminate();
        workflow.setOutput(Collections.singletonMap("output", "${task1.output.value}"));

        HashMap<String, Object> expectedOutput = new HashMap<String, Object>() {{ put("output", "${task0.output.value}"); }};

        Map<String, Object> input = new HashMap<>();
        input.put(getTerminationStatusParameter(), "FAILED");
        input.put(getTerminationWorkflowOutputParameter(), "${task0.output.value}");

        Task task = new Task();
        task.getInputData().putAll(input);
        terminateTask.execute(workflow, task, executor);
        assertEquals(Task.Status.COMPLETED, task.getStatus());
        assertEquals(expectedOutput, task.getOutputData());
    }

    @Test
    public void should_fail_workflow_on_terminate_task_success_with_empty_output() {
        Workflow workflow = new Workflow();
        Terminate terminateTask = new Terminate();

        Map<String, Object> input = new HashMap<>();
        input.put(getTerminationStatusParameter(), "FAILED");

        Task task = new Task();
        task.getInputData().putAll(input);
        terminateTask.execute(workflow, task, executor);
        assertEquals(Task.Status.COMPLETED, task.getStatus());
        assertTrue(task.getOutputData().isEmpty());
    }

    @Test
    public void should_fail_workflow_on_terminate_task_success_with_resolved_output() {
        Workflow workflow = new Workflow();
        Terminate terminateTask = new Terminate();

        HashMap<String, Object> expectedOutput = new HashMap<String, Object>() {{ put("result", 1); }};

        Map<String, Object> input = new HashMap<>();
        input.put(getTerminationStatusParameter(), "FAILED");
        input.put(getTerminationWorkflowOutputParameter(), expectedOutput);

        Task task = new Task();
        task.getInputData().putAll(input);
        terminateTask.execute(workflow, task, executor);
        assertEquals(Task.Status.COMPLETED, task.getStatus());
    }
}
