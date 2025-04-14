/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.common.run;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import static org.junit.jupiter.api.Assertions.*;

public class TestWorkflowPojoMethods {

    private Workflow workflow;

    @BeforeEach
    public void setUp() {
        workflow = new Workflow();
    }

    @Test
    public void testDefaultConstructor() {
        assertNotNull(workflow);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertNotNull(workflow.getTasks());
        assertTrue(workflow.getTasks().isEmpty());
        assertNotNull(workflow.getInput());
        assertTrue(workflow.getInput().isEmpty());
        assertNotNull(workflow.getOutput());
        assertTrue(workflow.getOutput().isEmpty());
        assertNotNull(workflow.getTaskToDomain());
        assertTrue(workflow.getTaskToDomain().isEmpty());
        assertNotNull(workflow.getFailedReferenceTaskNames());
        assertTrue(workflow.getFailedReferenceTaskNames().isEmpty());
        assertNotNull(workflow.getVariables());
        assertTrue(workflow.getVariables().isEmpty());
        assertNotNull(workflow.getFailedTaskNames());
        assertTrue(workflow.getFailedTaskNames().isEmpty());
        assertNotNull(workflow.getHistory());
        assertTrue(workflow.getHistory().isEmpty());
    }

    @Test
    public void testGetSetIdempotencyKey() {
        String idempotencyKey = "idempotency123";
        workflow.setIdempotencyKey(idempotencyKey);
        assertEquals(idempotencyKey, workflow.getIdempotencyKey());
    }

    @Test
    public void testGetSetRateLimitKey() {
        String rateLimitKey = "rateLimit123";
        workflow.setRateLimitKey(rateLimitKey);
        assertEquals(rateLimitKey, workflow.getRateLimitKey());
    }

    @Test
    public void testGetSetRateLimited() {
        assertFalse(workflow.isRateLimited());
        workflow.setRateLimited(true);
        assertTrue(workflow.isRateLimited());
    }

    @Test
    public void testGetSetHistory() {
        List<Workflow> history = new LinkedList<>();
        Workflow historyItem = new Workflow();
        history.add(historyItem);

        workflow.setHistory(history);
        assertEquals(history, workflow.getHistory());
        assertEquals(1, workflow.getHistory().size());
    }

    @Test
    public void testGetSetStatus() {
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

        workflow.setStatus(Workflow.WorkflowStatus.COMPLETED);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());

        workflow.setStatus(Workflow.WorkflowStatus.FAILED);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
    }

    @Test
    public void testGetSetStartTime() {
        long startTime = System.currentTimeMillis();
        workflow.setStartTime(startTime);
        assertEquals(startTime, workflow.getStartTime());
        assertEquals(startTime, workflow.getCreateTime()); // startTime is actually createTime
    }

    @Test
    public void testGetSetEndTime() {
        long endTime = System.currentTimeMillis();
        workflow.setEndTime(endTime);
        assertEquals(endTime, workflow.getEndTime());
    }

    @Test
    public void testGetSetWorkflowId() {
        String workflowId = "workflow123";
        workflow.setWorkflowId(workflowId);
        assertEquals(workflowId, workflow.getWorkflowId());
    }

    @Test
    public void testGetSetTasks() {
        List<Task> tasks = new LinkedList<>();
        Task task = new Task();
        task.setReferenceTaskName("task1");
        tasks.add(task);

        workflow.setTasks(tasks);
        assertEquals(tasks, workflow.getTasks());
        assertEquals(1, workflow.getTasks().size());
    }

    @Test
    public void testGetSetInput() {
        Map<String, Object> input = new HashMap<>();
        input.put("key1", "value1");

        workflow.setInput(input);
        assertEquals(input, workflow.getInput());
        assertEquals(1, workflow.getInput().size());
        assertEquals("value1", workflow.getInput().get("key1"));

        // Test with null input
        workflow.setInput(null);
        assertNotNull(workflow.getInput());
        assertTrue(workflow.getInput().isEmpty());
    }

    @Test
    public void testGetSetTaskToDomain() {
        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("task1", "domain1");

        workflow.setTaskToDomain(taskToDomain);
        assertEquals(taskToDomain, workflow.getTaskToDomain());
        assertEquals(1, workflow.getTaskToDomain().size());
        assertEquals("domain1", workflow.getTaskToDomain().get("task1"));
    }

    @Test
    public void testGetSetOutput() {
        Map<String, Object> output = new HashMap<>();
        output.put("result", "success");

        workflow.setOutput(output);
        assertEquals(output, workflow.getOutput());
        assertEquals(1, workflow.getOutput().size());
        assertEquals("success", workflow.getOutput().get("result"));

        // Test with null output
        workflow.setOutput(null);
        assertNotNull(workflow.getOutput());
        assertTrue(workflow.getOutput().isEmpty());
    }

    @Test
    public void testGetSetCorrelationId() {
        String correlationId = "correlation123";
        workflow.setCorrelationId(correlationId);
        assertEquals(correlationId, workflow.getCorrelationId());
    }

    @Test
    public void testGetSetReRunFromWorkflowId() {
        String reRunFromWorkflowId = "previousWorkflow123";
        workflow.setReRunFromWorkflowId(reRunFromWorkflowId);
        assertEquals(reRunFromWorkflowId, workflow.getReRunFromWorkflowId());
    }

    @Test
    public void testGetSetReasonForIncompletion() {
        String reason = "Task failed due to timeout";
        workflow.setReasonForIncompletion(reason);
        assertEquals(reason, workflow.getReasonForIncompletion());
    }

    @Test
    public void testGetSetParentWorkflowId() {
        String parentWorkflowId = "parent123";
        workflow.setParentWorkflowId(parentWorkflowId);
        assertEquals(parentWorkflowId, workflow.getParentWorkflowId());
    }

    @Test
    public void testGetSetParentWorkflowTaskId() {
        String parentWorkflowTaskId = "parentTask123";
        workflow.setParentWorkflowTaskId(parentWorkflowTaskId);
        assertEquals(parentWorkflowTaskId, workflow.getParentWorkflowTaskId());
    }

    @Test
    public void testGetSetEvent() {
        String event = "workflow.started";
        workflow.setEvent(event);
        assertEquals(event, workflow.getEvent());
    }

    @Test
    public void testGetSetFailedReferenceTaskNames() {
        Set<String> failedRefs = new HashSet<>();
        failedRefs.add("failedTask1");
        failedRefs.add("failedTask2");

        workflow.setFailedReferenceTaskNames(failedRefs);
        assertEquals(failedRefs, workflow.getFailedReferenceTaskNames());
        assertEquals(2, workflow.getFailedReferenceTaskNames().size());
        assertTrue(workflow.getFailedReferenceTaskNames().contains("failedTask1"));
    }

    @Test
    public void testGetSetWorkflowDefinition() {
        WorkflowDef def = new WorkflowDef();
        def.setName("testWorkflow");
        def.setVersion(1);

        workflow.setWorkflowDefinition(def);
        assertEquals(def, workflow.getWorkflowDefinition());
    }

    @Test
    public void testGetSetExternalInputPayloadStoragePath() {
        String path = "s3://bucket/workflow/input";
        workflow.setExternalInputPayloadStoragePath(path);
        assertEquals(path, workflow.getExternalInputPayloadStoragePath());
    }

    @Test
    public void testGetSetExternalOutputPayloadStoragePath() {
        String path = "s3://bucket/workflow/output";
        workflow.setExternalOutputPayloadStoragePath(path);
        assertEquals(path, workflow.getExternalOutputPayloadStoragePath());
    }

    @Test
    public void testGetSetPriority() {
        int priority = 50;
        workflow.setPriority(priority);
        assertEquals(priority, workflow.getPriority());
    }

    @Test
    public void testPriorityValidation() {
        // Valid values
        workflow.setPriority(0);
        assertEquals(0, workflow.getPriority());

        workflow.setPriority(99);
        assertEquals(99, workflow.getPriority());

        // Invalid values should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> workflow.setPriority(-1));
        assertThrows(IllegalArgumentException.class, () -> workflow.setPriority(100));
    }

    @Test
    public void testGetSetVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("var1", "value1");

        workflow.setVariables(variables);
        assertEquals(variables, workflow.getVariables());
        assertEquals(1, workflow.getVariables().size());
        assertEquals("value1", workflow.getVariables().get("var1"));
    }

    @Test
    public void testGetSetLastRetriedTime() {
        long time = System.currentTimeMillis();
        workflow.setLastRetriedTime(time);
        assertEquals(time, workflow.getLastRetriedTime());
    }

    @Test
    public void testHasParent() {
        assertFalse(workflow.hasParent());

        workflow.setParentWorkflowId("parent123");
        assertTrue(workflow.hasParent());

        workflow.setParentWorkflowId("");
        assertFalse(workflow.hasParent());

        workflow.setParentWorkflowId(null);
        assertFalse(workflow.hasParent());
    }

    @Test
    public void testGetSetFailedTaskNames() {
        Set<String> failedTasks = new HashSet<>();
        failedTasks.add("task1");
        failedTasks.add("task2");

        workflow.setFailedTaskNames(failedTasks);
        assertEquals(failedTasks, workflow.getFailedTaskNames());
        assertEquals(2, workflow.getFailedTaskNames().size());
        assertTrue(workflow.getFailedTaskNames().contains("task1"));
    }

    @Test
    public void testGetTaskByRefName() {
        List<Task> tasks = new ArrayList<>();

        Task task1 = new Task();
        task1.setReferenceTaskName("ref1");
        task1.setTaskDefName("task1");
        task1.setSeq(1);
        tasks.add(task1);

        Task task2 = new Task();
        task2.setReferenceTaskName("ref2");
        task2.setTaskDefName("task2");
        task2.setSeq(2);
        tasks.add(task2);

        Task task3 = new Task();
        task3.setReferenceTaskName("ref1");  // Same ref as task1
        task3.setTaskDefName("task3");
        task3.setSeq(3);
        tasks.add(task3);

        workflow.setTasks(tasks);

        // Should return the last task with the matching ref name
        assertEquals(task3, workflow.getTaskByRefName("ref1"));
        assertEquals(task2, workflow.getTaskByRefName("ref2"));
        assertNull(workflow.getTaskByRefName("nonExistentRef"));

        // Should throw exception for null refName
        assertThrows(RuntimeException.class, () -> workflow.getTaskByRefName(null));

        // Test with a task missing referenceTaskName
        Task badTask = new Task();
        badTask.setTaskDefName("badTask");
        badTask.setSeq(4);
        tasks.add(badTask);

        workflow.setTasks(tasks);
        assertThrows(RuntimeException.class, () -> workflow.getTaskByRefName("anyRef"));
    }

    @Test
    public void testCopy() {
        // Setup a workflow with non-default values
        WorkflowDef def = new WorkflowDef();
        def.setName("testWorkflow");
        def.setVersion(2);

        workflow.setWorkflowId("workflow123");
        workflow.setCorrelationId("correlation123");
        workflow.setStatus(Workflow.WorkflowStatus.COMPLETED);
        workflow.setParentWorkflowId("parent123");
        workflow.setParentWorkflowTaskId("parentTask123");
        workflow.setReRunFromWorkflowId("previousWorkflow123");
        workflow.setEvent("workflow.completed");
        workflow.setReasonForIncompletion("none");
        workflow.setWorkflowDefinition(def);
        workflow.setPriority(75);
        workflow.setEndTime(1000L);
        workflow.setLastRetriedTime(2000L);
        workflow.setExternalInputPayloadStoragePath("s3://input");
        workflow.setExternalOutputPayloadStoragePath("s3://output");

        Map<String, Object> input = new HashMap<>();
        input.put("key1", "value1");
        workflow.setInput(input);

        Map<String, Object> output = new HashMap<>();
        output.put("result", "success");
        workflow.setOutput(output);

        Map<String, Object> variables = new HashMap<>();
        variables.put("var1", "val1");
        workflow.setVariables(variables);

        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("task1", "domain1");
        workflow.setTaskToDomain(taskToDomain);

        Set<String> failedRefs = new HashSet<>();
        failedRefs.add("failedRef1");
        workflow.setFailedReferenceTaskNames(failedRefs);

        List<Task> tasks = new ArrayList<>();
        Task task = new Task();
        task.setReferenceTaskName("refTask1");
        tasks.add(task);
        workflow.setTasks(tasks);

        // Create copy and verify
        Workflow copy = workflow.copy();

        // Verify same values
        assertEquals(workflow.getWorkflowId(), copy.getWorkflowId());
        assertEquals(workflow.getCorrelationId(), copy.getCorrelationId());
        assertEquals(workflow.getStatus(), copy.getStatus());
        assertEquals(workflow.getParentWorkflowId(), copy.getParentWorkflowId());
        assertEquals(workflow.getParentWorkflowTaskId(), copy.getParentWorkflowTaskId());
        assertEquals(workflow.getReRunFromWorkflowId(), copy.getReRunFromWorkflowId());
        assertEquals(workflow.getEvent(), copy.getEvent());
        assertEquals(workflow.getReasonForIncompletion(), copy.getReasonForIncompletion());
        assertEquals(workflow.getWorkflowDefinition(), copy.getWorkflowDefinition());
        assertEquals(workflow.getPriority(), copy.getPriority());
        assertEquals(workflow.getEndTime(), copy.getEndTime());
        assertEquals(workflow.getLastRetriedTime(), copy.getLastRetriedTime());
        assertEquals(workflow.getExternalInputPayloadStoragePath(), copy.getExternalInputPayloadStoragePath());
        assertEquals(workflow.getExternalOutputPayloadStoragePath(), copy.getExternalOutputPayloadStoragePath());

        // Verify maps and collections (should be equal but not the same instance)
        assertEquals(workflow.getInput(), copy.getInput());

        assertEquals(workflow.getOutput(), copy.getOutput());

        assertEquals(workflow.getVariables(), copy.getVariables());

        assertEquals(workflow.getTaskToDomain(), copy.getTaskToDomain());

        assertEquals(workflow.getFailedReferenceTaskNames(), copy.getFailedReferenceTaskNames());

        // Verify tasks (should be equal in content but not same instances)
        assertEquals(workflow.getTasks().size(), copy.getTasks().size());
        assertNotSame(workflow.getTasks(), copy.getTasks());
        assertNotSame(workflow.getTasks().get(0), copy.getTasks().get(0));
        assertEquals(workflow.getTasks().get(0).getReferenceTaskName(), copy.getTasks().get(0).getReferenceTaskName());
    }

    @Test
    public void testToString() {
        WorkflowDef def = new WorkflowDef();
        def.setName("testWorkflow");
        def.setVersion(1);

        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("workflow123");
        workflow.setStatus(Workflow.WorkflowStatus.RUNNING);

        String expected = "testWorkflow.1/workflow123.RUNNING";
        assertEquals(expected, workflow.toString());

        // Test with null workflow definition
        workflow.setWorkflowDefinition(null);
        assertEquals("null.null/workflow123.RUNNING", workflow.toString());
    }

    @Test
    public void testToShortString() {
        WorkflowDef def = new WorkflowDef();
        def.setName("testWorkflow");
        def.setVersion(2);

        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("workflow123");

        String expected = "testWorkflow.2/workflow123";
        assertEquals(expected, workflow.toShortString());

        // Test with null workflow definition
        workflow.setWorkflowDefinition(null);
        assertEquals("null.null/workflow123", workflow.toShortString());
    }

    @Test
    public void testEquals() {
        Workflow workflow1 = new Workflow();
        workflow1.setWorkflowId("workflow123");

        Workflow workflow2 = new Workflow();
        workflow2.setWorkflowId("workflow123");

        Workflow workflow3 = new Workflow();
        workflow3.setWorkflowId("differentId");

        // Same workflow ID should be equal
        assertEquals(workflow1, workflow2);

        // Different workflow ID should not be equal
        assertNotEquals(workflow1, workflow3);

        // Same object should be equal to itself
        assertEquals(workflow1, workflow1);

        // Different types should not be equal
        assertNotEquals(workflow1, "not a workflow");

        // Null should not be equal
        assertNotEquals(workflow1, null);
    }

    @Test
    public void testHashCode() {
        Workflow workflow1 = new Workflow();
        workflow1.setWorkflowId("workflow123");

        Workflow workflow2 = new Workflow();
        workflow2.setWorkflowId("workflow123");

        Workflow workflow3 = new Workflow();
        workflow3.setWorkflowId("differentId");

        // Same workflow ID should have same hash code
        assertEquals(workflow1.hashCode(), workflow2.hashCode());

        // Different workflow ID should have different hash code
        assertNotEquals(workflow1.hashCode(), workflow3.hashCode());
    }

    @Test
    public void testGetWorkflowName() {
        WorkflowDef def = new WorkflowDef();
        def.setName("testWorkflow");

        workflow.setWorkflowDefinition(def);
        assertEquals("testWorkflow", workflow.getWorkflowName());

        // Should throw exception when definition is null
        workflow.setWorkflowDefinition(null);
        assertThrows(NullPointerException.class, () -> workflow.getWorkflowName());
    }

    @Test
    public void testGetWorkflowVersion() {
        WorkflowDef def = new WorkflowDef();
        def.setName("testWorkflow");
        def.setVersion(3);

        workflow.setWorkflowDefinition(def);
        assertEquals(3, workflow.getWorkflowVersion());

        // Should throw exception when definition is null
        workflow.setWorkflowDefinition(null);
        assertThrows(NullPointerException.class, () -> workflow.getWorkflowVersion());
    }

    @Test
    public void testWorkflowStatusIsTerminal() {
        assertTrue(Workflow.WorkflowStatus.COMPLETED.isTerminal());
        assertTrue(Workflow.WorkflowStatus.FAILED.isTerminal());
        assertTrue(Workflow.WorkflowStatus.TIMED_OUT.isTerminal());
        assertTrue(Workflow.WorkflowStatus.TERMINATED.isTerminal());

        assertFalse(Workflow.WorkflowStatus.RUNNING.isTerminal());
        assertFalse(Workflow.WorkflowStatus.PAUSED.isTerminal());
    }

    @Test
    public void testWorkflowStatusIsSuccessful() {
        assertTrue(Workflow.WorkflowStatus.COMPLETED.isSuccessful());
        assertTrue(Workflow.WorkflowStatus.PAUSED.isSuccessful());

        assertFalse(Workflow.WorkflowStatus.FAILED.isSuccessful());
        assertFalse(Workflow.WorkflowStatus.TIMED_OUT.isSuccessful());
        assertFalse(Workflow.WorkflowStatus.TERMINATED.isSuccessful());
        assertFalse(Workflow.WorkflowStatus.RUNNING.isSuccessful());
    }
}