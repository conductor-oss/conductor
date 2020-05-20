package com.netflix.conductor.core.execution.tasks;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Xesxen
 */
public class TestSubWorkflow {
    private WorkflowExecutor workflowExecutor;
    private SubWorkflow subWorkflow;

    @Before
    public void setup() {
        workflowExecutor = mock(WorkflowExecutor.class);
        subWorkflow = new SubWorkflow();
    }

    @Test
    public void testStartSubWorkflow() {
        WorkflowDef workflowDef = new WorkflowDef();
        Workflow workflowInstance = new Workflow();
        workflowInstance.setWorkflowDefinition(workflowDef);

        Task task = new Task();
        task.setOutputData(new HashMap<>());

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 3);
        task.setInputData(inputData);

        String workflowId = "workflow_1";
        Workflow workflow = new Workflow();
        workflow.setWorkflowId(workflowId);

        when(workflowExecutor.startWorkflow(eq("UnitWorkFlow"), eq(3), eq(inputData), eq(null), any(), any(), any(), eq(null), any()))
                .thenReturn(workflowId);

        when(workflowExecutor.getWorkflow(anyString(), eq(false))).thenReturn(workflow);

        workflow.setStatus(Workflow.WorkflowStatus.RUNNING);
        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
        assertEquals(Task.Status.IN_PROGRESS, task.getStatus());

        workflow.setStatus(Workflow.WorkflowStatus.TERMINATED);
        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
        assertEquals(Task.Status.FAILED, task.getStatus());

        workflow.setStatus(Workflow.WorkflowStatus.COMPLETED);
        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
        assertEquals(Task.Status.COMPLETED, task.getStatus());
    }

    @Test
    public void testStartSubWorkflowWithEmptyWorkflowInput() {
        WorkflowDef workflowDef = new WorkflowDef();
        Workflow workflowInstance = new Workflow();
        workflowInstance.setWorkflowDefinition(workflowDef);

        Task task = new Task();
        task.setOutputData(new HashMap<>());

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 3);

        Map<String, Object> workflowInput = new HashMap<>();
        inputData.put("workflowInput", workflowInput);
        task.setInputData(inputData);

        when(workflowExecutor.startWorkflow(eq("UnitWorkFlow"), eq(3), eq(inputData), eq(null), any(), any(), any(), eq(null), any()))
                .thenReturn("workflow_1");

        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
    }

    @Test
    public void testStartSubWorkflowWithWorkflowInput() {
        WorkflowDef workflowDef = new WorkflowDef();
        Workflow workflowInstance = new Workflow();
        workflowInstance.setWorkflowDefinition(workflowDef);

        Task task = new Task();
        task.setOutputData(new HashMap<>());

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 3);

        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("test", "value");
        inputData.put("workflowInput", workflowInput);
        task.setInputData(inputData);

        when(workflowExecutor.startWorkflow(eq("UnitWorkFlow"), eq(3), eq(workflowInput), eq(null), any(), any(), any(), eq(null), any()))
                .thenReturn("workflow_1");

        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
    }

    @Test
    public void testStartSubWorkflowTaskToDomain() {
        WorkflowDef workflowDef = new WorkflowDef();
        Workflow workflowInstance = new Workflow();
        workflowInstance.setWorkflowDefinition(workflowDef);
        Map<String, String> taskToDomain = new HashMap<String, String>() {{put("*", "unittest"); }};

        Task task = new Task();
        task.setOutputData(new HashMap<>());

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 2);
        inputData.put("subWorkflowTaskToDomain", taskToDomain);
        task.setInputData(inputData);

        when(workflowExecutor.startWorkflow(eq("UnitWorkFlow"), eq(2), eq(inputData), eq(null), any(), any(), any(), eq(null), eq(taskToDomain)))
                .thenReturn("workflow_1");

        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
    }

    @Test
    public void testExecuteSubWorkflowWithoutId() {
        WorkflowDef workflowDef = new WorkflowDef();
        Workflow workflowInstance = new Workflow();
        workflowInstance.setWorkflowDefinition(workflowDef);

        Task task = new Task();
        task.setOutputData(new HashMap<>());

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 2);
        task.setInputData(inputData);

        when(workflowExecutor.startWorkflow(eq("UnitWorkFlow"), eq(2), eq(inputData), eq(null), any(), any(), any(), eq(null), eq(null)))
                .thenReturn("workflow_1");

        assertFalse(subWorkflow.execute(workflowInstance, task, workflowExecutor));
    }

    @Test
    public void testExecuteWorkflowStatus() {
        WorkflowDef workflowDef = new WorkflowDef();
        Workflow workflowInstance = new Workflow();
        Workflow subWorkflowInstance = new Workflow();
        workflowInstance.setWorkflowDefinition(workflowDef);
        Map<String, String> taskToDomain = new HashMap<String, String>() {{put("*", "unittest"); }};

        Task task = new Task();
        Map<String, Object> outputData = new HashMap<>();
        task.setOutputData(outputData);
        task.setSubWorkflowId("sub-workflow-id");

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 2);
        inputData.put("subWorkflowTaskToDomain", taskToDomain);
        task.setInputData(inputData);

        when(workflowExecutor.startWorkflow(eq("UnitWorkFlow"), eq(2), eq(inputData), eq(null), any(), any(), any(), eq(null), eq(taskToDomain)))
                .thenReturn("workflow_1");
        when(workflowExecutor.getWorkflow(eq("sub-workflow-id"), eq(false)))
                .thenReturn(subWorkflowInstance);

        subWorkflowInstance.setStatus(Workflow.WorkflowStatus.RUNNING);
        assertFalse(subWorkflow.execute(workflowInstance, task, workflowExecutor));
        assertNull(task.getStatus());
        assertNull(task.getReasonForIncompletion());

        subWorkflowInstance.setStatus(Workflow.WorkflowStatus.PAUSED);
        assertFalse(subWorkflow.execute(workflowInstance, task, workflowExecutor));
        assertNull(task.getStatus());
        assertNull(task.getReasonForIncompletion());

        subWorkflowInstance.setStatus(Workflow.WorkflowStatus.COMPLETED);
        assertTrue(subWorkflow.execute(workflowInstance, task, workflowExecutor));
        assertEquals(Task.Status.COMPLETED, task.getStatus());
        assertNull(task.getReasonForIncompletion());

        subWorkflowInstance.setStatus(Workflow.WorkflowStatus.FAILED);
        subWorkflowInstance.setReasonForIncompletion("unit1");
        assertTrue(subWorkflow.execute(workflowInstance, task, workflowExecutor));
        assertEquals(Task.Status.FAILED, task.getStatus());
        assertEquals("unit1", task.getReasonForIncompletion());

        subWorkflowInstance.setStatus(Workflow.WorkflowStatus.TIMED_OUT);
        subWorkflowInstance.setReasonForIncompletion("unit2");
        assertTrue(subWorkflow.execute(workflowInstance, task, workflowExecutor));
        assertEquals(Task.Status.TIMED_OUT, task.getStatus());
        assertEquals("unit2", task.getReasonForIncompletion());

        subWorkflowInstance.setStatus(Workflow.WorkflowStatus.TERMINATED);
        subWorkflowInstance.setReasonForIncompletion("unit3");
        assertTrue(subWorkflow.execute(workflowInstance, task, workflowExecutor));
        assertEquals(Task.Status.FAILED, task.getStatus());
        assertEquals("unit3", task.getReasonForIncompletion());
    }

    @Test
    public void testCancelWithWorkflowId() {
        WorkflowDef workflowDef = new WorkflowDef();
        Workflow workflowInstance = new Workflow();
        Workflow subWorkflowInstance = new Workflow();
        workflowInstance.setWorkflowDefinition(workflowDef);

        Task task = new Task();
        Map<String, Object> outputData = new HashMap<>();
        task.setOutputData(outputData);
        task.setSubWorkflowId("sub-workflow-id");

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 2);
        task.setInputData(inputData);

        when(workflowExecutor.startWorkflow(eq("UnitWorkFlow"), eq(2), eq(inputData), eq(null), any(), any(), any(), eq(null), eq(null)))
                .thenReturn("workflow_1");
        when(workflowExecutor.getWorkflow(eq("sub-workflow-id"), eq(true)))
                .thenReturn(subWorkflowInstance);

        workflowInstance.setStatus(Workflow.WorkflowStatus.TIMED_OUT);
        subWorkflow.cancel(workflowInstance, task, workflowExecutor);

        assertEquals(Workflow.WorkflowStatus.TERMINATED, subWorkflowInstance.getStatus());
    }

    @Test
    public void testCancelWithoutWorkflowId() {
        WorkflowDef workflowDef = new WorkflowDef();
        Workflow workflowInstance = new Workflow();
        Workflow subWorkflowInstance = new Workflow();
        workflowInstance.setWorkflowDefinition(workflowDef);

        Task task = new Task();
        Map<String, Object> outputData = new HashMap<>();
        task.setOutputData(outputData);

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 2);
        task.setInputData(inputData);

        when(workflowExecutor.startWorkflow(eq("UnitWorkFlow"), eq(2), eq(inputData), eq(null), any(), any(), any(), eq(null), eq(null)))
                .thenReturn("workflow_1");
        when(workflowExecutor.getWorkflow(eq("sub-workflow-id"), eq(false)))
                .thenReturn(subWorkflowInstance);

        subWorkflow.cancel(workflowInstance, task, workflowExecutor);

        assertEquals(Workflow.WorkflowStatus.RUNNING, subWorkflowInstance.getStatus());
    }

    @Test
    public void testIsAsync() {
        SubWorkflow subWorkflow = new SubWorkflow();
        assertTrue(subWorkflow.isAsync());
    }

    @Test
    public void testStartSubWorkflowWithSubWorkflowDefinition() {
        WorkflowDef workflowDef = new WorkflowDef();
        Workflow workflowInstance = new Workflow();
        workflowInstance.setWorkflowDefinition(workflowDef);

        WorkflowDef subWorkflowDef = new WorkflowDef();
        subWorkflowDef.setName("subWorkflow_1");

        Task task = new Task();
        task.setOutputData(new HashMap<>());

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 2);
        inputData.put("subWorkflowDefinition", subWorkflowDef);
        task.setInputData(inputData);

        when(workflowExecutor.startWorkflow(eq(subWorkflowDef), eq(inputData), eq(null), any(), eq(0), any(), any(), eq(null), any()))
            .thenReturn("workflow_1");

        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
    }
}
