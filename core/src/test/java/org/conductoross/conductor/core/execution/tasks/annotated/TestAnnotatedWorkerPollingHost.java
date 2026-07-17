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
package org.conductoross.conductor.core.execution.tasks.annotated;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.conductoross.conductor.core.execution.tasks.AnnotatedSystemTaskWorker;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;
import com.netflix.conductor.service.ExecutionService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestAnnotatedWorkerPollingHost {

    private ExecutionService executionService;
    private MetadataDAO metadataDAO;
    private WorkerTaskAnnotationScanner scanner;
    private AnnotatedWorkerPollingHost host;

    static class PollWorkerBean implements AnnotatedSystemTaskWorker {
        public Map<String, Object> successTask(@InputParam("input") String input) {
            return Map.of("output", "processed: " + input);
        }

        public void throwsException(@InputParam("input") String input) {
            throw new RuntimeException("worker blew up");
        }

        public void throwsNonRetryable(@InputParam("input") String input) {
            throw new NonRetryableException("terminal failure");
        }
    }

    /** Bean with a real @WorkerTask annotation, for scanner-mode tests. */
    static class ScannedPollBean implements AnnotatedSystemTaskWorker {
        @WorkerTask("scanned_poll_task")
        public Map<String, Object> doWork(@InputParam("input") String input) {
            return Map.of("output", input);
        }
    }

    @Before
    public void setUp() {
        executionService = mock(ExecutionService.class);
        metadataDAO = mock(MetadataDAO.class);
        Set<WorkflowSystemTask> asyncSystemTasks = new HashSet<>();
        scanner =
                new WorkerTaskAnnotationScanner(
                        List.of(),
                        asyncSystemTasks,
                        mock(ParametersUtils.class),
                        metadataDAO,
                        WorkerTaskAnnotationScanner.MODE_POLL_WORKER);
        host = new AnnotatedWorkerPollingHost(scanner, executionService, metadataDAO);
        // pollAndExecute is guarded by the running flag
        setRunning();
    }

    private void setRunning() {
        try {
            var field = AnnotatedWorkerPollingHost.class.getDeclaredField("running");
            field.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicBoolean) field.get(host)).set(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AnnotatedWorkflowSystemTask adapter(String taskType, String methodName)
            throws Exception {
        Method method = PollWorkerBean.class.getMethod(methodName, String.class);
        WorkerTask annotation = Mockito.mock(WorkerTask.class);
        Mockito.when(annotation.value()).thenReturn(taskType);
        Mockito.when(annotation.threadCount()).thenReturn(1);
        Mockito.when(annotation.pollingInterval()).thenReturn(100);
        Mockito.when(annotation.domain()).thenReturn("");
        return new AnnotatedWorkflowSystemTask(taskType, method, new PollWorkerBean(), annotation);
    }

    private Task polledTask(String taskType, Map<String, Object> input) {
        Task task = new Task();
        task.setTaskId("task-1");
        task.setTaskType(taskType);
        task.setTaskDefName(taskType);
        task.setWorkflowInstanceId("wf-1");
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setInputData(new HashMap<>(input));
        return task;
    }

    @Test
    public void testPollExecuteUpdateCompletes() throws Exception {
        AnnotatedWorkflowSystemTask worker = adapter("poll_task", "successTask");
        when(executionService.poll(eq("poll_task"), anyString(), isNull()))
                .thenReturn(polledTask("poll_task", Map.of("input", "hello")));

        host.pollAndExecute(worker);

        ArgumentCaptor<TaskResult> captor = ArgumentCaptor.forClass(TaskResult.class);
        verify(executionService).updateTask(captor.capture());
        TaskResult result = captor.getValue();
        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals("task-1", result.getTaskId());
        assertEquals("wf-1", result.getWorkflowInstanceId());
        assertEquals("processed: hello", result.getOutputData().get("output"));
    }

    @Test
    public void testWorkerExceptionReportsFailed() throws Exception {
        AnnotatedWorkflowSystemTask worker = adapter("failing_task", "throwsException");
        when(executionService.poll(eq("failing_task"), anyString(), isNull()))
                .thenReturn(polledTask("failing_task", Map.of("input", "x")));

        host.pollAndExecute(worker);

        ArgumentCaptor<TaskResult> captor = ArgumentCaptor.forClass(TaskResult.class);
        verify(executionService).updateTask(captor.capture());
        assertEquals(TaskResult.Status.FAILED, captor.getValue().getStatus());
        assertTrue(captor.getValue().getReasonForIncompletion().contains("worker blew up"));
    }

    @Test
    public void testNonRetryableExceptionReportsTerminalFailure() throws Exception {
        AnnotatedWorkflowSystemTask worker = adapter("terminal_task", "throwsNonRetryable");
        when(executionService.poll(eq("terminal_task"), anyString(), isNull()))
                .thenReturn(polledTask("terminal_task", Map.of("input", "x")));

        host.pollAndExecute(worker);

        ArgumentCaptor<TaskResult> captor = ArgumentCaptor.forClass(TaskResult.class);
        verify(executionService).updateTask(captor.capture());
        assertEquals(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR, captor.getValue().getStatus());
    }

    @Test
    public void testEmptyPollDoesNothing() throws Exception {
        AnnotatedWorkflowSystemTask worker = adapter("idle_task", "successTask");
        when(executionService.poll(eq("idle_task"), anyString(), isNull())).thenReturn(null);

        host.pollAndExecute(worker);

        verify(executionService, never()).updateTask(any(TaskResult.class));
    }

    @Test
    public void testTaskDefAutoRegisteredWhenAbsent() {
        when(metadataDAO.getTaskDef("new_task")).thenReturn(null);

        host.registerTaskDefIfAbsent("new_task");

        ArgumentCaptor<TaskDef> captor = ArgumentCaptor.forClass(TaskDef.class);
        verify(metadataDAO).createTaskDef(captor.capture());
        TaskDef def = captor.getValue();
        assertEquals("new_task", def.getName());
        assertEquals(
                AnnotatedWorkerPollingHost.DEFAULT_RESPONSE_TIMEOUT_SECONDS,
                def.getResponseTimeoutSeconds());
        assertEquals(AnnotatedWorkerPollingHost.DEFAULT_RETRY_COUNT, def.getRetryCount());
    }

    @Test
    public void testTaskDefNotOverwrittenWhenPresent() {
        when(metadataDAO.getTaskDef("existing_task")).thenReturn(new TaskDef("existing_task"));

        host.registerTaskDefIfAbsent("existing_task");

        verify(metadataDAO, never()).createTaskDef(any());
    }

    @Test
    public void testPollWorkerModeDoesNotRegisterSystemTasks() {
        Set<WorkflowSystemTask> asyncSystemTasks = new HashSet<>();
        WorkerTaskAnnotationScanner pollScanner =
                new WorkerTaskAnnotationScanner(
                        List.of(new ScannedPollBean()),
                        asyncSystemTasks,
                        mock(ParametersUtils.class),
                        metadataDAO,
                        WorkerTaskAnnotationScanner.MODE_POLL_WORKER);
        pollScanner.afterPropertiesSet();

        assertTrue("poll-worker mode must not register system tasks", asyncSystemTasks.isEmpty());
        assertFalse(
                "workers must still be discovered for the polling host",
                pollScanner.getDiscoveredWorkers().isEmpty());
        assertFalse(
                "task mappers must still be registered for the decider",
                pollScanner.annotatedTaskSystems().isEmpty());
    }

    @Test
    public void testSystemTaskModeStillRegistersSystemTasks() {
        Set<WorkflowSystemTask> asyncSystemTasks = new HashSet<>();
        WorkerTaskAnnotationScanner systemScanner =
                new WorkerTaskAnnotationScanner(
                        List.of(new ScannedPollBean()),
                        asyncSystemTasks,
                        mock(ParametersUtils.class),
                        metadataDAO,
                        WorkerTaskAnnotationScanner.MODE_SYSTEM_TASK);
        systemScanner.afterPropertiesSet();

        assertFalse(
                "system-task mode keeps the historical registration", asyncSystemTasks.isEmpty());
    }
}
