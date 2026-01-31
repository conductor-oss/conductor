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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
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

    private TaskModel createTask(Map<String, Object> inputData) {
        TaskModel task = new TaskModel();
        task.setTaskId("task-123");
        task.setWorkflowInstanceId("workflow-123");
        task.setInputData(new HashMap<>(inputData));
        task.setOutputData(new HashMap<>());
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
