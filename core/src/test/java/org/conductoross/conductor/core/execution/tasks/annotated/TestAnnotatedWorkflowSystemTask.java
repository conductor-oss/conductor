/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.core.execution.tasks.annotated;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.conductoross.conductor.core.execution.tasks.TaskCancellationHandler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;
import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class TestAnnotatedWorkflowSystemTask {

    private WorkflowModel workflow;
    private WorkflowExecutor workflowExecutor;

    @Before
    public void setUp() {
        workflow = new WorkflowModel();
        workflow.setWorkflowId("test-workflow-123");
        workflowExecutor = mock(WorkflowExecutor.class);
    }

    static class TestWorkerBean {
        public Map<String, Object> successTask(@InputParam("input") String input) {
            return Map.of("output", "processed: " + input);
        }

        public void throwsException(@InputParam("input") String input) {
            throw new RuntimeException("Task failed");
        }

        public void throwsNonRetryable(@InputParam("input") String input) {
            throw new NonRetryableException("Terminal failure");
        }

        public Map<String, Object> returnsNull(@InputParam("input") String input) {
            return null;
        }

        public Map<String, Object> taskWithContext(
                TaskContext context, @InputParam("input") String input) {
            if (context == null) {
                throw new RuntimeException("TaskContext is null");
            }
            return Map.of("taskId", context.getTaskId(), "input", input);
        }
    }

    static class CancelAwareWorkerBean extends TestWorkerBean implements TaskCancellationHandler {
        private Task canceledTask;
        private String cancelReason;

        @Override
        public void cancel(Task task, String reason) {
            canceledTask = task;
            cancelReason = reason;
        }
    }

    @Test
    public void testSuccessfulExecution() throws Exception {
        TestWorkerBean bean = new TestWorkerBean();
        Method method = TestWorkerBean.class.getMethod("successTask", String.class);
        WorkerTask annotation = createAnnotation("test_task");

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask("test_task", method, bean, annotation);

        TaskModel task = createTask(Map.of("input", "hello"));

        boolean result = systemTask.execute(workflow, task, workflowExecutor);

        assertTrue(result);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("processed: hello", task.getOutputData().get("output"));
    }

    @Test
    public void testTaskWithException() throws Exception {
        TestWorkerBean bean = new TestWorkerBean();
        Method method = TestWorkerBean.class.getMethod("throwsException", String.class);
        WorkerTask annotation = createAnnotation("failing_task");

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask("failing_task", method, bean, annotation);

        TaskModel task = createTask(Map.of("input", "test"));

        boolean result = systemTask.execute(workflow, task, workflowExecutor);

        assertTrue(result);
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains("Task failed"));
    }

    @Test
    public void testTaskWithNonRetryableException() throws Exception {
        TestWorkerBean bean = new TestWorkerBean();
        Method method = TestWorkerBean.class.getMethod("throwsNonRetryable", String.class);
        WorkerTask annotation = createAnnotation("terminal_task");

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask("terminal_task", method, bean, annotation);

        TaskModel task = createTask(Map.of("input", "test"));

        boolean result = systemTask.execute(workflow, task, workflowExecutor);

        assertTrue(result);
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains("Terminal failure"));
    }

    @Test
    public void testTaskReturnsNull() throws Exception {
        TestWorkerBean bean = new TestWorkerBean();
        Method method = TestWorkerBean.class.getMethod("returnsNull", String.class);
        WorkerTask annotation = createAnnotation("null_task");

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask("null_task", method, bean, annotation);

        TaskModel task = createTask(Map.of("input", "test"));

        boolean result = systemTask.execute(workflow, task, workflowExecutor);

        assertTrue(result);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertTrue(task.getOutputData().isEmpty());
    }

    @Test
    public void testTaskWithContext() throws Exception {
        TestWorkerBean bean = new TestWorkerBean();
        Method method =
                TestWorkerBean.class.getMethod("taskWithContext", TaskContext.class, String.class);
        WorkerTask annotation = createAnnotation("context_task");

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask("context_task", method, bean, annotation);

        TaskModel task = createTask(Map.of("input", "context_test"));
        task.setTaskId("ctx-task-id");

        boolean result = systemTask.execute(workflow, task, workflowExecutor);

        assertTrue(result);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("ctx-task-id", task.getOutputData().get("taskId"));
        assertEquals("context_test", task.getOutputData().get("input"));
    }

    @Test
    public void testIsAsync() throws Exception {
        TestWorkerBean bean = new TestWorkerBean();
        Method method = TestWorkerBean.class.getMethod("successTask", String.class);
        WorkerTask annotation = createAnnotation("async_task");

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask("async_task", method, bean, annotation);

        assertTrue(systemTask.isAsync());
    }

    @Test
    public void testCancel() throws Exception {
        TestWorkerBean bean = new TestWorkerBean();
        Method method = TestWorkerBean.class.getMethod("successTask", String.class);
        WorkerTask annotation = createAnnotation("cancel_task");

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask("cancel_task", method, bean, annotation);

        TaskModel task = createTask(Map.of("input", "test"));

        systemTask.cancel(workflow, task, workflowExecutor);

        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
    }

    @Test
    public void testCancelInvokesBeanLifecycleHook() throws Exception {
        CancelAwareWorkerBean bean = new CancelAwareWorkerBean();
        Method method = TestWorkerBean.class.getMethod("successTask", String.class);
        WorkerTask annotation = createAnnotation("cancel_task");
        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask("cancel_task", method, bean, annotation);
        TaskModel task = createTask(Map.of("input", "test"));
        task.setReasonForIncompletion("parent terminated");
        task.setTaskType("cancel_task");
        systemTask.cancel(workflow, task, workflowExecutor);

        assertEquals("cancel_task", bean.canceledTask.getTaskType());
        assertEquals("task-123", bean.canceledTask.getTaskId());
        assertEquals("parent terminated", bean.cancelReason);
        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
    }

    @Test
    public void testGetTaskType() throws Exception {
        TestWorkerBean bean = new TestWorkerBean();
        Method method = TestWorkerBean.class.getMethod("successTask", String.class);
        WorkerTask annotation = createAnnotation("my_task");

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask("my_task", method, bean, annotation);

        assertEquals("my_task", systemTask.getTaskType());
    }

    @Test
    public void testGetAnnotation() throws Exception {
        TestWorkerBean bean = new TestWorkerBean();
        Method method = TestWorkerBean.class.getMethod("successTask", String.class);
        WorkerTask annotation = createAnnotation("test");

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask("test", method, bean, annotation);

        assertSame(annotation, systemTask.getAnnotation());
    }

    @Test
    public void testGetMethod() throws Exception {
        TestWorkerBean bean = new TestWorkerBean();
        Method method = TestWorkerBean.class.getMethod("successTask", String.class);
        WorkerTask annotation = createAnnotation("test");

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask("test", method, bean, annotation);

        assertSame(method, systemTask.getMethod());
    }

    @Test
    public void testGetBean() throws Exception {
        TestWorkerBean bean = new TestWorkerBean();
        Method method = TestWorkerBean.class.getMethod("successTask", String.class);
        WorkerTask annotation = createAnnotation("test");

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask("test", method, bean, annotation);

        assertSame(bean, systemTask.getBean());
    }

    private TaskModel createTask(Map<String, Object> inputData) {
        TaskModel task = new TaskModel();
        task.setTaskId("task-123");
        task.setTaskType(TaskType.TASK_TYPE_AGENT);
        task.setWorkflowInstanceId("workflow-123");
        task.setInputData(new HashMap<>(inputData));
        task.setOutputData(new HashMap<>());
        task.setStatus(TaskModel.Status.SCHEDULED);
        return task;
    }

    // ── Issue #1321: the status contract prevents duplicate execution on queue redelivery ──
    //
    // The annotated method blocks synchronously (e.g. an LLM provider call) with nothing
    // persisted until it returns, so a redelivered queue message used to make a second worker
    // invoke the same method again. The adapter now follows the system-task status contract:
    // SCHEDULED means start (the IN_PROGRESS transition is persisted BEFORE the method is
    // invoked), IN_PROGRESS means an invocation is in flight - don't do anything. The one
    // exception: a worker-requested callback (IN_PROGRESS + callbackAfterSeconds > 0) is
    // re-invoked, preserving the long-running LLM/A2A worker flow.

    /** Records invocations and, at invocation time, whether the claim was already persisted. */
    static class ClaimProbeBean {
        final java.util.concurrent.atomic.AtomicInteger invocations =
                new java.util.concurrent.atomic.AtomicInteger();
        volatile boolean claimPersistedAtInvocation;
        volatile boolean claimPersisted;

        public Map<String, Object> blockingCall() {
            invocations.incrementAndGet();
            claimPersistedAtInvocation = claimPersisted;
            return Map.of("ok", true);
        }
    }

    @Test
    public void testInProgressTransitionPersistedBeforeInvocation() throws Exception {
        ClaimProbeBean bean = new ClaimProbeBean();
        Method method = ClaimProbeBean.class.getMethod("blockingCall");
        ExecutionDAOFacade facade = mock(ExecutionDAOFacade.class);
        Mockito.doAnswer(
                        invocation -> {
                            TaskModel persisted = invocation.getArgument(0);
                            assertEquals(TaskModel.Status.IN_PROGRESS, persisted.getStatus());
                            assertEquals(0, persisted.getCallbackAfterSeconds());
                            bean.claimPersisted = true;
                            return null;
                        })
                .when(facade)
                .updateTask(Mockito.any(TaskModel.class));

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask(
                        "claim_task", method, bean, createAnnotation("claim_task"), facade);

        TaskModel task = createTask(Map.of());
        task.setTaskType("claim_task");

        systemTask.start(workflow, task, workflowExecutor);

        assertTrue(
                "the IN_PROGRESS transition must be persisted before the blocking method is"
                        + " invoked",
                bean.claimPersistedAtInvocation);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
    }

    @Test
    public void testRedeliveredInFlightExecutionIsSkipped() throws Exception {
        ClaimProbeBean bean = new ClaimProbeBean();
        Method method = ClaimProbeBean.class.getMethod("blockingCall");
        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask(
                        "claimed_task",
                        method,
                        bean,
                        createAnnotation("claimed_task"),
                        mock(ExecutionDAOFacade.class));

        TaskModel task = createTask(Map.of());
        task.setTaskType("claimed_task");
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setCallbackAfterSeconds(0);

        boolean result = systemTask.execute(workflow, task, workflowExecutor);

        assertFalse("redelivered execution must report no state change", result);
        assertEquals(
                "the blocking method must NOT be invoked a second time", 0, bean.invocations.get());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
    }

    @Test
    public void testInFlightRedeliveryIsPostponedPastTheBlockingCall() throws Exception {
        Method method = ClaimProbeBean.class.getMethod("blockingCall");
        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask(
                        "offset_task",
                        method,
                        new ClaimProbeBean(),
                        createAnnotation("offset_task"),
                        mock(ExecutionDAOFacade.class));

        TaskModel task = createTask(Map.of());
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setCallbackAfterSeconds(0);

        // No response timeout on the task: the default in-flight postpone applies.
        assertEquals(
                java.util.Optional.of(
                        AnnotatedWorkflowSystemTask.DEFAULT_IN_FLIGHT_POSTPONE_SECONDS),
                systemTask.getEvaluationOffset(task, 30));

        // With a response timeout, the postpone matches it.
        task.setResponseTimeoutSeconds(45);
        assertEquals(java.util.Optional.of(45L), systemTask.getEvaluationOffset(task, 30));

        // Worker-requested callbacks keep their own interval.
        task.setCallbackAfterSeconds(5);
        assertEquals(java.util.Optional.of(5L), systemTask.getEvaluationOffset(task, 30));
    }

    @Test
    public void testLegitimateInProgressCallbackReexecutionIsNotBlocked() throws Exception {
        // LLM/A2A workers legitimately return IN_PROGRESS + callbackAfterSeconds and expect
        // re-execution on the callback. callbackAfterSeconds > 0 distinguishes that from an
        // in-flight redelivery, so the guard must let the re-execution through.
        ClaimProbeBean bean = new ClaimProbeBean();
        Method method = ClaimProbeBean.class.getMethod("blockingCall");
        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask(
                        "callback_task",
                        method,
                        bean,
                        createAnnotation("callback_task"),
                        mock(ExecutionDAOFacade.class));

        TaskModel task = createTask(Map.of());
        task.setTaskType("callback_task");
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setCallbackAfterSeconds(5);

        boolean result = systemTask.execute(workflow, task, workflowExecutor);

        assertTrue(result);
        assertEquals(1, bean.invocations.get());
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
    }

    @Test
    public void testExecutesWithoutFacadeAsBefore() throws Exception {
        ClaimProbeBean bean = new ClaimProbeBean();
        Method method = ClaimProbeBean.class.getMethod("blockingCall");
        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask(
                        "legacy_task", method, bean, createAnnotation("legacy_task"));

        TaskModel task = createTask(Map.of());
        task.setTaskType("legacy_task");

        systemTask.start(workflow, task, workflowExecutor);

        assertEquals(1, bean.invocations.get());
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
    }

    private WorkerTask createAnnotation(String taskName) {
        WorkerTask annotation = Mockito.mock(WorkerTask.class);
        Mockito.when(annotation.value()).thenReturn(taskName);
        Mockito.when(annotation.threadCount()).thenReturn(1);
        Mockito.when(annotation.pollingInterval()).thenReturn(100);
        Mockito.when(annotation.domain()).thenReturn("");
        // pollTimeout and pollerCount not available in SDK v3.x
        return annotation;
    }
}
