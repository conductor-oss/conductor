/*
 * Copyright 2022 Conductor Authors.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JoinTest {

    private static TaskModel taskWithJoinMode(WorkflowTask.JoinMode mode) {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setJoinMode(mode);
        TaskModel task = new TaskModel();
        task.setWorkflowTask(workflowTask);
        return task;
    }

    private static TaskModel forkedTaskWithOutput(String refName, Map<String, Object> output) {
        TaskModel forkedTask = new TaskModel();
        forkedTask.setReferenceTaskName(refName);
        forkedTask.setWorkflowTask(new WorkflowTask());
        forkedTask.setStatus(TaskModel.Status.COMPLETED);
        forkedTask.setOutputData(output);
        return forkedTask;
    }

    private static TaskModel joinTaskOn(String... refNames) {
        TaskModel joinTask = new TaskModel();
        joinTask.setWorkflowTask(new WorkflowTask());
        joinTask.setInputData(new HashMap<>(Map.of("joinOn", List.of(refNames))));
        return joinTask;
    }

    @Test
    public void testSynchronousJoinModeEvaluationOffset() {
        ConductorProperties properties = mock(ConductorProperties.class);
        when(properties.getSystemTaskPostponeThreshold()).thenReturn(200);

        Join join = new Join(properties);
        TaskModel task = taskWithJoinMode(WorkflowTask.JoinMode.SYNC);

        // Synchronous mode should always return 0 offset
        task.setPollCount(100);
        Optional<Long> offset = join.getEvaluationOffset(task, 10000L);
        assertTrue(offset.isPresent());
        assertEquals(0L, offset.get().longValue());

        // Even with high poll count, SYNC mode returns 0
        task.setPollCount(500);
        offset = join.getEvaluationOffset(task, 10000L);
        assertTrue(offset.isPresent());
        assertEquals(0L, offset.get().longValue());
    }

    @Test
    public void testAsynchronousJoinModeEvaluationOffset() {
        ConductorProperties properties = mock(ConductorProperties.class);
        when(properties.getSystemTaskPostponeThreshold()).thenReturn(200);

        Join join = new Join(properties);
        TaskModel task = taskWithJoinMode(WorkflowTask.JoinMode.ASYNC);

        // Low poll count should return 0
        task.setPollCount(100);
        Optional<Long> offset = join.getEvaluationOffset(task, 10000L);
        assertTrue(offset.isPresent());
        assertEquals(0L, offset.get().longValue());

        // High poll count should use exponential backoff
        task.setPollCount(250);
        offset = join.getEvaluationOffset(task, 10000L);
        assertTrue(offset.isPresent());
        assertTrue(offset.get() > 0L);
    }

    @Test
    public void testDefaultAsyncBehavior() {
        ConductorProperties properties = mock(ConductorProperties.class);
        when(properties.getSystemTaskPostponeThreshold()).thenReturn(200);

        Join join = new Join(properties);

        // No joinMode on workflowTask — should default to async behavior
        TaskModel task = new TaskModel();
        task.setWorkflowTask(new WorkflowTask());

        // Low poll count should return 0
        task.setPollCount(100);
        Optional<Long> offset = join.getEvaluationOffset(task, 10000L);
        assertTrue(offset.isPresent());
        assertEquals(0L, offset.get().longValue());

        // High poll count should use exponential backoff (default async behavior)
        task.setPollCount(250);
        offset = join.getEvaluationOffset(task, 10000L);
        assertTrue(offset.isPresent());
        assertTrue(offset.get() > 0L);
    }

    @Test
    public void testNullWorkflowTaskDefaultsToAsync() {
        ConductorProperties properties = mock(ConductorProperties.class);
        when(properties.getSystemTaskPostponeThreshold()).thenReturn(200);

        Join join = new Join(properties);

        // No workflowTask at all — should default to async behavior
        TaskModel task = new TaskModel();

        task.setPollCount(250);
        Optional<Long> offset = join.getEvaluationOffset(task, 10000L);
        assertTrue(offset.isPresent());
        assertTrue(offset.get() > 0L);
    }

    @Test
    public void testIsAsync() {
        ConductorProperties properties = mock(ConductorProperties.class);
        Join join = new Join(properties);

        // isAsync should always return true
        assertTrue(join.isAsync());
    }

    @Test
    public void testAgentWorkflowDefCompactsForkOutput() {
        ConductorProperties properties = mock(ConductorProperties.class);
        Join join = new Join(properties);

        WorkflowDef agentDef = new WorkflowDef();
        agentDef.setMetadata(Map.of("agent_sdk", "python"));
        assertTrue("precondition: def must classify as agent", agentDef.isAgent());

        Map<String, Object> forkOutput = new HashMap<>();
        forkOutput.put("state", "some-state");
        forkOutput.put("toolResult", "full raw tool payload, should not be copied");
        TaskModel forkedTask = forkedTaskWithOutput("t1", forkOutput);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(agentDef);
        workflow.setTasks(List.of(forkedTask));

        TaskModel joinTask = joinTaskOn("t1");

        boolean done = join.execute(workflow, joinTask, mock(WorkflowExecutor.class));

        assertTrue(done);
        assertEquals(TaskModel.Status.COMPLETED, joinTask.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> compact = (Map<String, Object>) joinTask.getOutputData().get("t1");
        assertEquals("state", "some-state", compact.get("state"));
        assertFalse("full tool output must not leak into JOIN output for agent executions",
                compact.containsKey("toolResult"));
    }

    @Test
    public void testNonAgentWorkflowDefCopiesFullForkOutput() {
        ConductorProperties properties = mock(ConductorProperties.class);
        Join join = new Join(properties);

        WorkflowDef plainDef = new WorkflowDef();
        assertFalse("precondition: untagged def must not classify as agent", plainDef.isAgent());

        Map<String, Object> forkOutput = new HashMap<>();
        forkOutput.put("state", "some-state");
        forkOutput.put("toolResult", "full raw tool payload");
        TaskModel forkedTask = forkedTaskWithOutput("t1", forkOutput);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(plainDef);
        workflow.setTasks(List.of(forkedTask));

        TaskModel joinTask = joinTaskOn("t1");

        boolean done = join.execute(workflow, joinTask, mock(WorkflowExecutor.class));

        assertTrue(done);
        assertEquals(TaskModel.Status.COMPLETED, joinTask.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> full = (Map<String, Object>) joinTask.getOutputData().get("t1");
        assertEquals("plain FORK/JOIN must keep copying the full fork output (stock Conductor behavior)",
                forkOutput, full);
    }

    @Test
    public void testNoWorkflowDefinitionDoesNotThrow() {
        ConductorProperties properties = mock(ConductorProperties.class);
        Join join = new Join(properties);

        Map<String, Object> forkOutput = Map.of("state", "some-state");
        TaskModel forkedTask = forkedTaskWithOutput("t1", forkOutput);

        WorkflowModel workflow = new WorkflowModel();
        // no workflow definition set at all — must not NPE, must fall back to full copy
        workflow.setTasks(List.of(forkedTask));

        TaskModel joinTask = joinTaskOn("t1");

        boolean done = join.execute(workflow, joinTask, mock(WorkflowExecutor.class));

        assertTrue(done);
        assertNull(workflow.getWorkflowDefinition());
        assertEquals(TaskModel.Status.COMPLETED, joinTask.getStatus());
    }
}
