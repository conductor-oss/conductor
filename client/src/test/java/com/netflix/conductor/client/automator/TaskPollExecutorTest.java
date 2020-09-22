/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.client.automator;

import static com.netflix.conductor.common.metadata.tasks.TaskResult.Status.IN_PROGRESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.conductor.client.exceptions.ConductorClientException;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class TaskPollExecutorTest {

    private static final String TEST_TASK_DEF_NAME = "test";

    @Test
    public void testTaskExecutionException() {
        Worker worker = Worker.create(TEST_TASK_DEF_NAME, task -> {
            throw new NoSuchMethodError();
        });
        TaskClient taskClient = Mockito.mock(TaskClient.class);
        TaskPollExecutor taskPollExecutor = new TaskPollExecutor(null, taskClient, 1, 1, new HashMap<>(), "test-worker-%d");

        when(taskClient.pollTask(any(), any(), any())).thenReturn(testTask());
        when(taskClient.ack(any(), any())).thenReturn(true);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
                assertEquals("test-worker-1", Thread.currentThread().getName());
                Object[] args = invocation.getArguments();
                TaskResult result = (TaskResult) args[0];
                assertEquals(TaskResult.Status.FAILED, result.getStatus());
                latch.countDown();
                return null;
            }
        ).when(taskClient).updateTask(any());

        Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(() -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);

        Uninterruptibles.awaitUninterruptibly(latch);
        verify(taskClient).updateTask(any());
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testMultipleTasksExecution() {
        String outputKey = "KEY";
        Task task = testTask();
        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getTaskDefName()).thenReturn(TEST_TASK_DEF_NAME);
        when(worker.execute(any())).thenAnswer(new Answer() {
            private int count = 0;
            Map<String, Object> outputMap = new HashMap<>();

            public TaskResult answer(InvocationOnMock invocation) {
                // Sleep for 2 seconds to simulate task execution
                Uninterruptibles.sleepUninterruptibly(2, TimeUnit.SECONDS);
                TaskResult taskResult = new TaskResult(task);
                outputMap.put(outputKey, count++);
                taskResult.setOutputData(outputMap);
                return taskResult;
            }
        });

        TaskClient taskClient = Mockito.mock(TaskClient.class);
        TaskPollExecutor taskPollExecutor = new TaskPollExecutor(null, taskClient, 1, 1, new HashMap<>(), "test-worker-");
        when(taskClient.pollTask(any(), any(), any())).thenReturn(task);
        when(taskClient.ack(any(), any())).thenReturn(true);
        CountDownLatch latch = new CountDownLatch(3);
        doAnswer(new Answer() {
                     private int count = 0;

                     public TaskResult answer(InvocationOnMock invocation) {
                         Object[] args = invocation.getArguments();
                         TaskResult result = (TaskResult) args[0];
                         assertEquals(IN_PROGRESS, result.getStatus());
                         assertEquals(count, result.getOutputData().get(outputKey));
                         count++;
                         latch.countDown();
                         return null;
                     }
                 }
        ).when(taskClient).updateTask(any());

        Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(() -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);
        Uninterruptibles.awaitUninterruptibly(latch);

        // execute() is called 3 times on the worker (once for each task)
        verify(worker, times(3)).execute(any());
        verify(taskClient, times(3)).updateTask(any());
    }

    @Test
    public void testLargePayloadCanFailUpdateWithRetry() {
        Task task = testTask();

        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getTaskDefName()).thenReturn(TEST_TASK_DEF_NAME);
        when(worker.execute(any())).thenReturn(new TaskResult(task));

        TaskClient taskClient = Mockito.mock(TaskClient.class);
        when(taskClient.pollTask(any(), any(), any())).thenReturn(task);
        when(taskClient.ack(any(), any())).thenReturn(true);

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            TaskResult result = (TaskResult) args[0];
            assertNull(result.getReasonForIncompletion());
            result.setReasonForIncompletion("some_reason");
            throw new ConductorClientException();
        }).when(taskClient).evaluateAndUploadLargePayload(any(TaskResult.class), any());

        TaskPollExecutor taskPollExecutor = new TaskPollExecutor(null, taskClient, 1, 3, new HashMap<>(), "test-worker-");
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
                latch.countDown();
                return null;
            }
        ).when(worker).onErrorUpdate(any());

        Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(() -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);
        Uninterruptibles.awaitUninterruptibly(latch);

        // When evaluateAndUploadLargePayload fails indefinitely, task update shouldn't be called.
        verify(taskClient, times(0)).updateTask(any());
    }

    @Test
    public void testTaskPollException() {
        Task task = testTask();

        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getTaskDefName()).thenReturn("test");
        when(worker.execute(any())).thenReturn(new TaskResult(task));

        TaskClient taskClient = Mockito.mock(TaskClient.class);
        when(taskClient.pollTask(any(), any(), any()))
            .thenThrow(ConductorClientException.class)
            .thenReturn(task);

        TaskPollExecutor taskPollExecutor = new TaskPollExecutor(null, taskClient, 1, 1, new HashMap<>(), "test-worker-");
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
                Object[] args = invocation.getArguments();
                TaskResult result = (TaskResult) args[0];
                assertEquals(IN_PROGRESS, result.getStatus());
                assertEquals(task.getTaskId(), result.getTaskId());
                latch.countDown();
                return null;
            }
        ).when(taskClient).updateTask(any());

        Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(() -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);

        Uninterruptibles.awaitUninterruptibly(latch);
        verify(taskClient).updateTask(any());
    }

    @Test
    public void testTaskPoll() {
        Task task = testTask();

        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getTaskDefName()).thenReturn("test");
        when(worker.execute(any())).thenReturn(new TaskResult(task));

        TaskClient taskClient = Mockito.mock(TaskClient.class);
        when(taskClient.pollTask(any(), any(), any()))
            .thenReturn(new Task())
            .thenReturn(task);

        TaskPollExecutor taskPollExecutor = new TaskPollExecutor(null, taskClient, 1, 1, new HashMap<>(), "test-worker-");
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
                Object[] args = invocation.getArguments();
                TaskResult result = (TaskResult) args[0];
                assertEquals(IN_PROGRESS, result.getStatus());
                assertEquals(task.getTaskId(), result.getTaskId());
                latch.countDown();
                return null;
            }
        ).when(taskClient).updateTask(any());

        Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(() -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);

        Uninterruptibles.awaitUninterruptibly(latch);
        verify(taskClient).updateTask(any());
    }

    @Test
    public void testTaskPollDomain() {
        TaskClient taskClient = Mockito.mock(TaskClient.class);
        String testDomain = "foo";
        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put(TEST_TASK_DEF_NAME, testDomain);
        TaskPollExecutor taskPollExecutor = new TaskPollExecutor(null, taskClient, 1, 1, taskToDomain, "test-worker-");

        String workerName = "test-worker";
        Worker worker = mock(Worker.class);
        when(worker.getTaskDefName()).thenReturn(TEST_TASK_DEF_NAME);
        when(worker.getIdentity()).thenReturn(workerName);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
                latch.countDown();
                return null;
            }
        ).when(taskClient).pollTask(TEST_TASK_DEF_NAME, workerName, testDomain);

        Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(() -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);

        Uninterruptibles.awaitUninterruptibly(latch);
        verify(taskClient).pollTask(TEST_TASK_DEF_NAME, workerName, testDomain);
    }

    private Task testTask() {
        Task task = new Task();
        task.setTaskId(UUID.randomUUID().toString());
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setTaskDefName(TEST_TASK_DEF_NAME);
        return task;
    }
}