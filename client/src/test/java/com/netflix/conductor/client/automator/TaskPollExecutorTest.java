/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.client.automator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.discovery.EurekaClient;

import static com.netflix.conductor.common.metadata.tasks.TaskResult.Status.COMPLETED;
import static com.netflix.conductor.common.metadata.tasks.TaskResult.Status.IN_PROGRESS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TaskPollExecutorTest {

    private static final String TEST_TASK_DEF_NAME = "test";

    private static final Map<String, Integer> TASK_THREAD_MAP =
            Collections.singletonMap(TEST_TASK_DEF_NAME, 1);

    @Test
    public void testTaskExecutionException() throws InterruptedException {
        Worker worker =
                Worker.create(
                        TEST_TASK_DEF_NAME,
                        task -> {
                            throw new NoSuchMethodError();
                        });
        TaskClient taskClient = Mockito.mock(TaskClient.class);
        TaskPollExecutor taskPollExecutor =
                new TaskPollExecutor(
                        null, taskClient, 1, new HashMap<>(), "test-worker-%d", TASK_THREAD_MAP);

        when(taskClient.batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(testTask()));
        when(taskClient.ack(any(), any())).thenReturn(true);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            assertEquals("test-worker-1", Thread.currentThread().getName());
                            Object[] args = invocation.getArguments();
                            TaskResult result = (TaskResult) args[0];
                            assertEquals(TaskResult.Status.FAILED, result.getStatus());
                            latch.countDown();
                            return null;
                        })
                .when(taskClient)
                .updateTask(any());

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        () -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);

        latch.await();
        verify(taskClient).updateTask(any());
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testMultipleTasksExecution() throws InterruptedException {
        String outputKey = "KEY";
        Task task = testTask();
        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getTaskDefName()).thenReturn(TEST_TASK_DEF_NAME);
        when(worker.execute(any()))
                .thenAnswer(
                        new Answer() {
                            private int count = 0;
                            Map<String, Object> outputMap = new HashMap<>();

                            public TaskResult answer(InvocationOnMock invocation)
                                    throws InterruptedException {
                                // Sleep for 2 seconds to simulate task execution
                                Thread.sleep(2000L);
                                TaskResult taskResult = new TaskResult(task);
                                outputMap.put(outputKey, count++);
                                taskResult.setOutputData(outputMap);
                                return taskResult;
                            }
                        });

        TaskClient taskClient = Mockito.mock(TaskClient.class);
        TaskPollExecutor taskPollExecutor =
                new TaskPollExecutor(
                        null, taskClient, 1, new HashMap<>(), "test-worker-", TASK_THREAD_MAP);
        when(taskClient.batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(task));
        when(taskClient.ack(any(), any())).thenReturn(true);
        CountDownLatch latch = new CountDownLatch(3);
        doAnswer(
                        new Answer() {
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
                        })
                .when(taskClient)
                .updateTask(any());

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        () -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);
        latch.await();

        // execute() is called 3 times on the worker (once for each task)
        verify(worker, times(3)).execute(any());
        verify(taskClient, times(3)).updateTask(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testLargePayloadCanFailUpdateWithRetry() throws InterruptedException {
        Task task = testTask();

        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getTaskDefName()).thenReturn(TEST_TASK_DEF_NAME);
        when(worker.execute(any())).thenReturn(new TaskResult(task));

        TaskClient taskClient = Mockito.mock(TaskClient.class);
        when(taskClient.batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(task));
        when(taskClient.ack(any(), any())).thenReturn(true);

        doAnswer(
                        invocation -> {
                            Object[] args = invocation.getArguments();
                            TaskResult result = (TaskResult) args[0];
                            assertNull(result.getReasonForIncompletion());
                            result.setReasonForIncompletion("some_reason_1");
                            throw new ConductorClientException();
                        })
                .when(taskClient)
                .evaluateAndUploadLargePayload(any(Map.class), any());

        TaskPollExecutor taskPollExecutor =
                new TaskPollExecutor(
                        null, taskClient, 1, new HashMap<>(), "test-worker-", TASK_THREAD_MAP);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            latch.countDown();
                            return null;
                        })
                .when(worker)
                .onErrorUpdate(any());

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        () -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);
        latch.await();

        // When evaluateAndUploadLargePayload fails indefinitely, task update shouldn't be called.
        verify(taskClient, times(0)).updateTask(any());
    }

    @Test
    public void testLargePayloadLocationUpdate() throws InterruptedException {
        Task task = testTask();
        String largePayloadLocation = "large_payload_location";

        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getTaskDefName()).thenReturn(TEST_TASK_DEF_NAME);
        when(worker.execute(any())).thenReturn(new TaskResult(task));

        TaskClient taskClient = Mockito.mock(TaskClient.class);
        when(taskClient.batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(task));
        when(taskClient.ack(any(), any())).thenReturn(true);
        //noinspection unchecked
        when(taskClient.evaluateAndUploadLargePayload(any(Map.class), any()))
                .thenReturn(Optional.of(largePayloadLocation));

        TaskPollExecutor taskPollExecutor =
                new TaskPollExecutor(
                        null, taskClient, 1, new HashMap<>(), "test-worker-", TASK_THREAD_MAP);
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(
                        invocation -> {
                            Object[] args = invocation.getArguments();
                            TaskResult result = (TaskResult) args[0];
                            assertNull(result.getOutputData());
                            assertEquals(
                                    largePayloadLocation,
                                    result.getExternalOutputPayloadStoragePath());
                            latch.countDown();
                            return null;
                        })
                .when(taskClient)
                .updateTask(any());

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        () -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);
        latch.await();

        verify(taskClient, times(1)).updateTask(any());
    }

    @Test
    public void testTaskPollException() throws InterruptedException {
        Task task = testTask();

        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getTaskDefName()).thenReturn(TEST_TASK_DEF_NAME);
        when(worker.execute(any())).thenReturn(new TaskResult(task));

        TaskClient taskClient = Mockito.mock(TaskClient.class);
        when(taskClient.batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(ConductorClientException.class)
                .thenReturn(Arrays.asList(task));

        TaskPollExecutor taskPollExecutor =
                new TaskPollExecutor(
                        null, taskClient, 1, new HashMap<>(), "test-worker-", TASK_THREAD_MAP);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            Object[] args = invocation.getArguments();
                            TaskResult result = (TaskResult) args[0];
                            assertEquals(IN_PROGRESS, result.getStatus());
                            assertEquals(task.getTaskId(), result.getTaskId());
                            latch.countDown();
                            return null;
                        })
                .when(taskClient)
                .updateTask(any());

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        () -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);

        latch.await();
        verify(taskClient).updateTask(any());
    }

    @Test
    public void testTaskPoll() throws InterruptedException {
        Task task = testTask();

        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getTaskDefName()).thenReturn("test");
        when(worker.execute(any())).thenReturn(new TaskResult(task));

        TaskClient taskClient = Mockito.mock(TaskClient.class);
        when(taskClient.batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(task));

        TaskPollExecutor taskPollExecutor =
                new TaskPollExecutor(
                        null, taskClient, 1, new HashMap<>(), "test-worker-", TASK_THREAD_MAP);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            Object[] args = invocation.getArguments();
                            TaskResult result = (TaskResult) args[0];
                            assertEquals(IN_PROGRESS, result.getStatus());
                            assertEquals(task.getTaskId(), result.getTaskId());
                            latch.countDown();
                            return null;
                        })
                .when(taskClient)
                .updateTask(any());

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        () -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);

        latch.await();
        verify(taskClient).updateTask(any());
    }

    @Test
    public void testTaskPollDomain() throws InterruptedException {
        TaskClient taskClient = Mockito.mock(TaskClient.class);
        String testDomain = "foo";
        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put(TEST_TASK_DEF_NAME, testDomain);
        TaskPollExecutor taskPollExecutor =
                new TaskPollExecutor(
                        null, taskClient, 1, taskToDomain, "test-worker-", TASK_THREAD_MAP);

        String workerName = "test-worker";
        Worker worker = mock(Worker.class);
        when(worker.getTaskDefName()).thenReturn(TEST_TASK_DEF_NAME);
        when(worker.getIdentity()).thenReturn(workerName);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            latch.countDown();
                            return null;
                        })
                .when(taskClient)
                .batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt());

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        () -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);

        latch.await();
        verify(taskClient).batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    public void testPollOutOfDiscoveryForTask() throws InterruptedException {
        Task task = testTask();

        EurekaClient client = mock(EurekaClient.class);
        when(client.getInstanceRemoteStatus()).thenReturn(InstanceInfo.InstanceStatus.UNKNOWN);

        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getTaskDefName()).thenReturn("task_run_always");
        when(worker.execute(any())).thenReturn(new TaskResult(task));

        TaskClient taskClient = Mockito.mock(TaskClient.class);
        when(taskClient.batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(new Task()))
                .thenReturn(Arrays.asList(task));

        TaskPollExecutor taskPollExecutor =
                new TaskPollExecutor(
                        client,
                        taskClient,
                        1,
                        new HashMap<>(),
                        "test-worker-",
                        Collections.singletonMap("task_run_always", 1));
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            Object[] args = invocation.getArguments();
                            TaskResult result = (TaskResult) args[0];
                            assertEquals(IN_PROGRESS, result.getStatus());
                            assertEquals(task.getTaskId(), result.getTaskId());
                            latch.countDown();
                            return null;
                        })
                .when(taskClient)
                .updateTask(any());

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        () -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);

        latch.await();
        verify(taskClient).updateTask(any());
    }

    @Test
    public void testPollOutOfDiscoveryAsDefaultFalseForTask()
            throws ExecutionException, InterruptedException {
        Task task = testTask();

        EurekaClient client = mock(EurekaClient.class);
        when(client.getInstanceRemoteStatus()).thenReturn(InstanceInfo.InstanceStatus.UNKNOWN);

        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getTaskDefName()).thenReturn("task_do_not_run_always");
        when(worker.execute(any())).thenReturn(new TaskResult(task));

        TaskClient taskClient = Mockito.mock(TaskClient.class);
        when(taskClient.batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(task));

        TaskPollExecutor taskPollExecutor =
                new TaskPollExecutor(
                        client, taskClient, 1, new HashMap<>(), "test-worker-", TASK_THREAD_MAP);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            Object[] args = invocation.getArguments();
                            TaskResult result = (TaskResult) args[0];
                            assertEquals(IN_PROGRESS, result.getStatus());
                            assertEquals(task.getTaskId(), result.getTaskId());
                            latch.countDown();
                            return null;
                        })
                .when(taskClient)
                .updateTask(any());

        ScheduledFuture f =
                Executors.newSingleThreadScheduledExecutor()
                        .schedule(
                                () -> taskPollExecutor.pollAndExecute(worker), 0, TimeUnit.SECONDS);

        f.get();
        verify(taskClient, times(0)).updateTask(any());
    }

    @Test
    public void testPollOutOfDiscoveryAsExplicitFalseForTask()
            throws ExecutionException, InterruptedException {
        Task task = testTask();

        EurekaClient client = mock(EurekaClient.class);
        when(client.getInstanceRemoteStatus()).thenReturn(InstanceInfo.InstanceStatus.UNKNOWN);

        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getTaskDefName()).thenReturn("task_explicit_do_not_run_always");
        when(worker.execute(any())).thenReturn(new TaskResult(task));

        TaskClient taskClient = Mockito.mock(TaskClient.class);
        when(taskClient.batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(task));

        TaskPollExecutor taskPollExecutor =
                new TaskPollExecutor(
                        client, taskClient, 1, new HashMap<>(), "test-worker-", TASK_THREAD_MAP);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            Object[] args = invocation.getArguments();
                            TaskResult result = (TaskResult) args[0];
                            assertEquals(IN_PROGRESS, result.getStatus());
                            assertEquals(task.getTaskId(), result.getTaskId());
                            latch.countDown();
                            return null;
                        })
                .when(taskClient)
                .updateTask(any());

        ScheduledFuture f =
                Executors.newSingleThreadScheduledExecutor()
                        .schedule(
                                () -> taskPollExecutor.pollAndExecute(worker), 0, TimeUnit.SECONDS);

        f.get();
        verify(taskClient, times(0)).updateTask(any());
    }

    @Test
    public void testPollOutOfDiscoveryIsIgnoredWhenDiscoveryIsUp() throws InterruptedException {
        Task task = testTask();

        EurekaClient client = mock(EurekaClient.class);
        when(client.getInstanceRemoteStatus()).thenReturn(InstanceInfo.InstanceStatus.UP);

        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getTaskDefName()).thenReturn("task_ignore_override");
        when(worker.execute(any())).thenReturn(new TaskResult(task));

        TaskClient taskClient = Mockito.mock(TaskClient.class);
        when(taskClient.batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(task));

        TaskPollExecutor taskPollExecutor =
                new TaskPollExecutor(
                        client,
                        taskClient,
                        1,
                        new HashMap<>(),
                        "test-worker-",
                        Collections.singletonMap("task_ignore_override", 1));
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            Object[] args = invocation.getArguments();
                            TaskResult result = (TaskResult) args[0];
                            assertEquals(IN_PROGRESS, result.getStatus());
                            assertEquals(task.getTaskId(), result.getTaskId());
                            latch.countDown();
                            return null;
                        })
                .when(taskClient)
                .updateTask(any());

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        () -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);

        latch.await();
        verify(taskClient).updateTask(any());
    }

    @Test
    public void testTaskThreadCount() throws InterruptedException {
        TaskClient taskClient = Mockito.mock(TaskClient.class);

        Map<String, Integer> taskThreadCount = new HashMap<>();
        taskThreadCount.put(TEST_TASK_DEF_NAME, 1);

        TaskPollExecutor taskPollExecutor =
                new TaskPollExecutor(
                        null, taskClient, -1, new HashMap<>(), "test-worker-", taskThreadCount);

        String workerName = "test-worker";
        Worker worker = mock(Worker.class);
        when(worker.getTaskDefName()).thenReturn(TEST_TASK_DEF_NAME);
        when(worker.getIdentity()).thenReturn(workerName);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            latch.countDown();
                            return null;
                        })
                .when(taskClient)
                .batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt());

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        () -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);

        latch.await();
        verify(taskClient).batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    public void testTaskLeaseExtend() throws InterruptedException {
        Task task = testTask();
        task.setResponseTimeoutSeconds(1);

        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getTaskDefName()).thenReturn("test");
        when(worker.execute(any())).thenReturn(new TaskResult(task));
        when(worker.leaseExtendEnabled()).thenReturn(true);

        TaskClient taskClient = Mockito.mock(TaskClient.class);
        when(taskClient.batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(task));

        TaskResult result = new TaskResult(task);
        result.getLogs().add(new TaskExecLog("lease extend"));
        result.setExtendLease(true);

        TaskPollExecutor taskPollExecutor =
                new TaskPollExecutor(
                        null, taskClient, 1, new HashMap<>(), "test-worker-", TASK_THREAD_MAP);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            assertTrue(
                                    taskPollExecutor.leaseExtendMap.containsKey(task.getTaskId()));
                            latch.countDown();
                            return null;
                        })
                .when(taskClient)
                .updateTask(any());

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        () -> taskPollExecutor.pollAndExecute(worker), 0, 5, TimeUnit.SECONDS);

        latch.await();
    }

    @Test
    public void testBatchTasksExecution() throws InterruptedException {
        int threadCount = 10;
        TaskClient taskClient = Mockito.mock(TaskClient.class);
        Map<String, Integer> taskThreadCount = new HashMap<>();
        taskThreadCount.put(TEST_TASK_DEF_NAME, threadCount);

        String workerName = "test-worker";
        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getBatchPollTimeoutInMS()).thenReturn(1000);
        when(worker.getTaskDefName()).thenReturn(TEST_TASK_DEF_NAME);
        when(worker.getIdentity()).thenReturn(workerName);

        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Task task = testTask();
            tasks.add(task);

            when(worker.execute(task))
                    .thenAnswer(
                            new Answer() {
                                Map<String, Object> outputMap = new HashMap<>();

                                public TaskResult answer(InvocationOnMock invocation)
                                        throws InterruptedException {
                                    // Sleep for 1 seconds to simulate task execution
                                    Thread.sleep(1000L);
                                    TaskResult taskResult = new TaskResult(task);
                                    outputMap.put("key", "value");
                                    taskResult.setOutputData(outputMap);
                                    taskResult.setStatus(COMPLETED);
                                    return taskResult;
                                }
                            });
        }
        when(taskClient.batchPollTasksInDomain(
                        TEST_TASK_DEF_NAME, null, workerName, threadCount, 1000))
                .thenReturn(tasks);
        when(taskClient.ack(any(), any())).thenReturn(true);

        TaskPollExecutor taskPollExecutor =
                new TaskPollExecutor(
                        null, taskClient, 1, new HashMap<>(), "test-worker-", taskThreadCount);

        CountDownLatch latch = new CountDownLatch(threadCount);
        doAnswer(
                        new Answer() {
                            public TaskResult answer(InvocationOnMock invocation) {
                                Object[] args = invocation.getArguments();
                                TaskResult result = (TaskResult) args[0];
                                assertEquals(COMPLETED, result.getStatus());
                                assertEquals("value", result.getOutputData().get("key"));
                                latch.countDown();
                                return null;
                            }
                        })
                .when(taskClient)
                .updateTask(any());

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        () -> taskPollExecutor.pollAndExecute(worker), 0, 1, TimeUnit.SECONDS);
        latch.await();

        // execute() is called 10 times on the worker (once for each task)
        verify(worker, times(threadCount)).execute(any());
        verify(taskClient, times(threadCount)).updateTask(any());
    }

    private Task testTask() {
        Task task = new Task();
        task.setTaskId(UUID.randomUUID().toString());
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setTaskDefName(TEST_TASK_DEF_NAME);
        return task;
    }
}
