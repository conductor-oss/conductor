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
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;
import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

    // ── Issue #1321: extend queue visibility over blocking method invocations ──
    //
    // The adapter runs annotated worker methods (e.g. LLM_CHAT_COMPLETE provider
    // calls) synchronously in start() while the task stays SCHEDULED. If the
    // QueueDAO's unack/redelivery window is shorter than the invocation, the queue
    // message is redelivered mid-execution and a second worker invokes the same
    // method again. start() must extend the message's visibility to the task's
    // responseTimeoutSeconds BEFORE invoking the method.

    /** Records whether queue visibility had already been extended when the method ran. */
    static class VisibilityProbeBean {
        final AtomicBoolean visibilityExtended;
        volatile boolean extendedAtInvocation;

        VisibilityProbeBean(AtomicBoolean visibilityExtended) {
            this.visibilityExtended = visibilityExtended;
        }

        public Map<String, Object> blockingCall() {
            extendedAtInvocation = visibilityExtended.get();
            return Map.of("ok", true);
        }
    }

    @Test
    public void testStartExtendsQueueVisibilityToResponseTimeoutBeforeInvocation()
            throws Exception {
        AtomicBoolean visibilityExtended = new AtomicBoolean(false);
        VisibilityProbeBean bean = new VisibilityProbeBean(visibilityExtended);
        Method method = VisibilityProbeBean.class.getMethod("blockingCall");
        WorkerTask annotation = createAnnotation("llm_like_task");
        QueueDAO queueDAO = mock(QueueDAO.class);
        Mockito.doAnswer(
                        invocation -> {
                            visibilityExtended.set(true);
                            return null;
                        })
                .when(queueDAO)
                .setUnackTimeout(anyString(), anyString(), anyLong());

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask(
                        "llm_like_task", method, bean, annotation, queueDAO);

        TaskModel task = createTask(Map.of());
        task.setTaskType("llm_like_task");
        TaskDef taskDef = new TaskDef();
        taskDef.setName("llm_like_task");
        taskDef.setResponseTimeoutSeconds(3600);
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("llm_like_task");
        workflowTask.setTaskDefinition(taskDef);
        task.setWorkflowTask(workflowTask);

        systemTask.start(workflow, task, workflowExecutor);

        Mockito.verify(queueDAO)
                .setUnackTimeout(QueueUtils.getQueueName(task), task.getTaskId(), 3600_000L);
        assertTrue(
                "queue visibility must be extended before the blocking method is invoked",
                bean.extendedAtInvocation);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
    }

    @Test
    public void testStartWithoutTaskDefDoesNotTouchQueueVisibility() throws Exception {
        VisibilityProbeBean bean = new VisibilityProbeBean(new AtomicBoolean(false));
        Method method = VisibilityProbeBean.class.getMethod("blockingCall");
        WorkerTask annotation = createAnnotation("no_taskdef_task");
        QueueDAO queueDAO = mock(QueueDAO.class);

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask(
                        "no_taskdef_task", method, bean, annotation, queueDAO);

        TaskModel task = createTask(Map.of());
        task.setTaskType("no_taskdef_task");

        systemTask.start(workflow, task, workflowExecutor);

        Mockito.verify(queueDAO, Mockito.never())
                .setUnackTimeout(anyString(), anyString(), anyLong());
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
    }

    @Test
    public void testStartWithZeroResponseTimeoutDoesNotTouchQueueVisibility() throws Exception {
        VisibilityProbeBean bean = new VisibilityProbeBean(new AtomicBoolean(false));
        Method method = VisibilityProbeBean.class.getMethod("blockingCall");
        WorkerTask annotation = createAnnotation("zero_timeout_task");
        QueueDAO queueDAO = mock(QueueDAO.class);

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask(
                        "zero_timeout_task", method, bean, annotation, queueDAO);

        TaskModel task = createTask(Map.of());
        task.setTaskType("zero_timeout_task");
        TaskDef taskDef = new TaskDef();
        taskDef.setName("zero_timeout_task");
        taskDef.setResponseTimeoutSeconds(0);
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("zero_timeout_task");
        workflowTask.setTaskDefinition(taskDef);
        task.setWorkflowTask(workflowTask);

        systemTask.start(workflow, task, workflowExecutor);

        Mockito.verify(queueDAO, Mockito.never())
                .setUnackTimeout(anyString(), anyString(), anyLong());
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
    }

    @Test
    public void testStartWithoutQueueDAOStillExecutes() throws Exception {
        VisibilityProbeBean bean = new VisibilityProbeBean(new AtomicBoolean(false));
        Method method = VisibilityProbeBean.class.getMethod("blockingCall");
        WorkerTask annotation = createAnnotation("legacy_ctor_task");

        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask("legacy_ctor_task", method, bean, annotation);

        TaskModel task = createTask(Map.of());
        task.setTaskType("legacy_ctor_task");

        systemTask.start(workflow, task, workflowExecutor);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
    }

    private TaskModel createTask(Map<String, Object> inputData) {
        TaskModel task = new TaskModel();
        task.setTaskId("task-123");
        task.setWorkflowInstanceId("workflow-123");
        task.setInputData(new HashMap<>(inputData));
        task.setOutputData(new HashMap<>());
        task.setStatus(TaskModel.Status.SCHEDULED);
        return task;
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
