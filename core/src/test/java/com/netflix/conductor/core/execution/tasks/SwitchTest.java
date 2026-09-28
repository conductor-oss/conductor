/*
 * Copyright 2026 Conductor Authors.
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

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class SwitchTest {

    private final Evaluator regular = mock(Evaluator.class);
    private final Evaluator deferred = mock(Evaluator.class);
    private final WorkflowExecutor executor = mock(WorkflowExecutor.class);
    private final WorkflowModel workflow = new WorkflowModel();

    private Switch subject() {
        when(regular.isDeferred()).thenReturn(false);
        when(deferred.isDeferred()).thenReturn(true);
        return new Switch(Map.of("value-param", regular, "decision", deferred));
    }

    private TaskModel switchTask(String evaluatorType) {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setType("SWITCH");
        workflowTask.setTaskReferenceName("route");
        workflowTask.setEvaluatorType(evaluatorType);
        workflowTask.setExpression("route");
        TaskModel task = new TaskModel();
        task.setTaskType("SWITCH");
        task.setReferenceTaskName("route");
        task.setWorkflowTask(workflowTask);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.getInputData().put("state", "refund");
        if ("decision".equals(evaluatorType)) {
            task.getInputData().put(Switch.DEFERRED_EVALUATOR, true);
        }
        return task;
    }

    // ── Category 1: existing SWITCH behaviour is unchanged ────────────────────────────

    @Test
    public void regularSwitchIsSynchronousAndExecuteOnlyCompletes() {
        Switch sw = subject();
        TaskModel task = switchTask("value-param");

        assertFalse("a regular evaluator never makes the task async", sw.isAsync(task));

        sw.start(workflow, task, executor);
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertTrue(sw.execute(workflow, task, executor));
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        verify(regular, never()).evaluate(anyString(), any());
        assertFalse(task.getOutputData().containsKey(Switch.SELECTED_CASE));
    }

    @Test
    public void taskWithoutWorkflowTaskIsSynchronous() {
        TaskModel task = new TaskModel();
        task.setTaskType("SWITCH");
        assertFalse(subject().isAsync(task));
    }

    @Test
    public void noArgConstructorKeepsLegacyBehaviour() {
        Switch sw = new Switch();
        TaskModel task = switchTask("decision");
        task.getInputData().remove(Switch.DEFERRED_EVALUATOR);
        assertFalse(sw.isAsync(task));
        sw.execute(workflow, task, executor);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
    }

    // ── Category 2: deferred evaluator runs in the executable ─────────────────────────

    @Test
    public void deferredSwitchIsAsyncAndTypeGetsAPoller() {
        Switch sw = subject();
        assertTrue("type-level: SWITCH gets a poller", sw.isAsync());
        assertTrue(sw.isAsync(switchTask("decision")));
    }

    @Test
    public void deferredStartEvaluatesAndKeepsWholeResultInOutput() {
        Switch sw = subject();
        TaskModel task = switchTask("decision");
        task.setStatus(TaskModel.Status.SCHEDULED);
        when(deferred.evaluate(eq("route"), any()))
                .thenReturn(Map.of("selectedCase", "billing", "cost", 0.00001));

        sw.start(workflow, task, executor);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("billing", task.getOutputData().get(Switch.SELECTED_CASE));
        assertEquals(List.of("billing"), task.getOutputData().get(Switch.EVALUATION_RESULT));
        assertEquals(0.00001, task.getOutputData().get("cost"));
        assertEquals("billing", task.getInputData().get("case"));
        verify(deferred).evaluate("route", Map.of("state", "refund"));
    }

    @Test
    public void deferredPlainResultBecomesSelectedCase() {
        Switch sw = subject();
        TaskModel task = switchTask("decision");
        when(deferred.evaluate(anyString(), any())).thenReturn("technical");

        sw.execute(workflow, task, executor);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("technical", task.getOutputData().get(Switch.SELECTED_CASE));
    }

    @Test
    public void transientFailureLeavesTaskRetriable() {
        Switch sw = subject();
        TaskModel task = switchTask("decision");
        when(deferred.evaluate(anyString(), any()))
                .thenThrow(new IllegalStateException("Decision HTTP status 503"));

        sw.start(workflow, task, executor);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getStatus().isRetriable());
        assertEquals("Decision HTTP status 503", task.getReasonForIncompletion());
    }

    @Test
    public void terminalFailureIsNotRetriable() {
        Switch sw = subject();
        TaskModel task = switchTask("decision");
        when(deferred.evaluate(anyString(), any()))
                .thenThrow(new NonTransientException("model and state required"));

        sw.start(workflow, task, executor);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertFalse(task.getStatus().isRetriable());
    }

    @Test
    public void deferredExecuteDoesNotReEvaluateATerminalTask() {
        Switch sw = subject();
        TaskModel task = switchTask("decision");
        task.setStatus(TaskModel.Status.COMPLETED);

        sw.execute(workflow, task, executor);

        verify(deferred, never()).evaluate(anyString(), any());
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
    }

    @Test
    public void missingDeferredEvaluatorFailsClosed() {
        Switch sw = new Switch(Map.of());
        TaskModel task = switchTask("decision");
        task.setStatus(TaskModel.Status.SCHEDULED);

        assertTrue("the persisted marker keeps the task async", sw.isAsync(task));

        sw.start(workflow, task, executor);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(
                "Deferred SWITCH evaluator 'decision' is not registered",
                task.getReasonForIncompletion());
    }
}
