/*
 * Copyright 2020 Conductor Authors.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

import static com.netflix.conductor.common.metadata.tasks.TaskResult.Status.COMPLETED;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaskRunnerConfigurerTest {
    private static final String TEST_TASK_DEF_NAME = "test";
    private TaskClient client;

    @BeforeEach
    public void setup() {
        client = Mockito.mock(TaskClient.class);
    }

    @Test
    public void testNoWorkersException() {
        assertThrows(NullPointerException.class, () -> new TaskRunnerConfigurer.Builder(null, null).build());
    }

    @Test
    public void testInvalidThreadConfig() {
        Worker worker1 = Worker.create("task1", TaskResult::new);
        Worker worker2 = Worker.create("task2", TaskResult::new);
        Map<String, Integer> taskThreadCount = new HashMap<>();
        taskThreadCount.put(worker1.getTaskDefName(), 0);
        taskThreadCount.put(worker2.getTaskDefName(), 3);

        assertThrows(IllegalArgumentException.class, () -> new TaskRunnerConfigurer.Builder(client, Arrays.asList(worker1, worker2))
            .withThreadCount(-1)
            .withTaskThreadCount(taskThreadCount)
            .build());

        assertThrows(IllegalArgumentException.class, () -> new TaskRunnerConfigurer.Builder(client, Arrays.asList(worker1, worker2))
            .withTaskThreadCount(taskThreadCount)
            .build());
    }

    @Test
    public void testMissingTaskThreadConfig() {
        Worker worker1 = Worker.create("task1", TaskResult::new);
        Worker worker2 = Worker.create("task2", TaskResult::new);
        Map<String, Integer> taskThreadCount = new HashMap<>();
        taskThreadCount.put(worker1.getTaskDefName(), 2);
        TaskRunnerConfigurer configurer =
                new TaskRunnerConfigurer.Builder(client, Arrays.asList(worker1, worker2))
                        .withTaskThreadCount(taskThreadCount)
                        .build();

        assertFalse(configurer.getTaskThreadCount().isEmpty());
        assertEquals(1, configurer.getTaskThreadCount().size());
        assertEquals(2, configurer.getTaskThreadCount().get("task1").intValue());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testPerTaskThreadPool() {
        Worker worker1 = Worker.create("task1", TaskResult::new);
        Worker worker2 = Worker.create("task2", TaskResult::new);
        Map<String, Integer> taskThreadCount = new HashMap<>();
        taskThreadCount.put(worker1.getTaskDefName(), 2);
        taskThreadCount.put(worker2.getTaskDefName(), 3);
        TaskRunnerConfigurer configurer =
                new TaskRunnerConfigurer.Builder(client, Arrays.asList(worker1, worker2))
                        .withTaskThreadCount(taskThreadCount)
                        .build();
        configurer.init();
        assertEquals(-1, configurer.getThreadCount());
        assertEquals(2, configurer.getTaskThreadCount().get("task1").intValue());
        assertEquals(3, configurer.getTaskThreadCount().get("task2").intValue());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testSharedThreadPool() {
        Worker worker = Worker.create(TEST_TASK_DEF_NAME, TaskResult::new);
        TaskRunnerConfigurer configurer =
                new TaskRunnerConfigurer.Builder(client, Arrays.asList(worker, worker, worker))
                        .build();
        configurer.init();
        assertEquals(-1, configurer.getThreadCount());
        assertEquals(500, configurer.getSleepWhenRetry());
        assertEquals(3, configurer.getUpdateRetryCount());
        assertEquals(10, configurer.getShutdownGracePeriodSeconds());
        assertTrue(configurer.getTaskThreadCount().isEmpty());

        configurer =
                new TaskRunnerConfigurer.Builder(client, Collections.singletonList(worker))
                        .withThreadCount(100)
                        .withSleepWhenRetry(100)
                        .withUpdateRetryCount(10)
                        .withShutdownGracePeriodSeconds(15)
                        .withWorkerNamePrefix("test-worker-")
                        .build();
        assertEquals(100, configurer.getThreadCount());
        configurer.init();
        assertEquals(100, configurer.getThreadCount());
        assertEquals(100, configurer.getSleepWhenRetry());
        assertEquals(10, configurer.getUpdateRetryCount());
        assertEquals(15, configurer.getShutdownGracePeriodSeconds());
        assertEquals("test-worker-", configurer.getWorkerNamePrefix());
        assertTrue(configurer.getTaskThreadCount().isEmpty());
    }

    @Test
    public void testMultipleWorkersExecution() throws Exception {
        String task1Name = "task1";
        Worker worker1 = mock(Worker.class);
        when(worker1.getPollingInterval()).thenReturn(3000);
        when(worker1.getTaskDefName()).thenReturn(task1Name);
        when(worker1.getIdentity()).thenReturn("worker1");
        when(worker1.execute(any()))
                .thenAnswer(
                        invocation -> {
                            // Sleep for 2 seconds to simulate task execution
                            Thread.sleep(2000);
                            TaskResult taskResult = new TaskResult();
                            taskResult.setStatus(COMPLETED);
                            return taskResult;
                        });

        String task2Name = "task2";
        Worker worker2 = mock(Worker.class);
        when(worker2.getPollingInterval()).thenReturn(3000);
        when(worker2.getTaskDefName()).thenReturn(task2Name);
        when(worker2.getIdentity()).thenReturn("worker2");
        when(worker2.execute(any()))
                .thenAnswer(
                        invocation -> {
                            // Sleep for 2 seconds to simulate task execution
                            Thread.sleep(2000);
                            TaskResult taskResult = new TaskResult();
                            taskResult.setStatus(COMPLETED);
                            return taskResult;
                        });

        Task task1 = testTask(task1Name);
        Task task2 = testTask(task2Name);
        TaskClient taskClient = Mockito.mock(TaskClient.class);
        TaskRunnerConfigurer configurer =
                new TaskRunnerConfigurer.Builder(taskClient, Arrays.asList(worker1, worker2))
                        .withThreadCount(1)
                        .withSleepWhenRetry(100000)
                        .withUpdateRetryCount(1)
                        .withWorkerNamePrefix("test-worker-")
                        .build();
        when(taskClient.batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt()))
                .thenAnswer(
                        invocation -> {
                            Object[] args = invocation.getArguments();
                            String taskName = args[0].toString();
                            if (taskName.equals(task1Name)) {
                                return List.of(task1);
                            } else if (taskName.equals(task2Name)) {
                                return List.of(task2);
                            } else {
                                return Collections.emptyList();
                            }
                        });
        when(taskClient.ack(any(), any())).thenReturn(true);

        AtomicInteger task1Counter = new AtomicInteger(0);
        AtomicInteger task2Counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);
        doAnswer(
                        invocation -> {
                            Object[] args = invocation.getArguments();
                            TaskResult result = (TaskResult) args[0];
                            assertEquals(COMPLETED, result.getStatus());
                            if (result.getWorkerId().equals("worker1")) {
                                task1Counter.incrementAndGet();
                            } else if (result.getWorkerId().equals("worker2")) {
                                task2Counter.incrementAndGet();
                            }
                            latch.countDown();
                            return null;
                        })
                .when(taskClient)
                .updateTask(any());
        configurer.init();
        latch.await();

        assertEquals(1, task1Counter.get());
        assertEquals(1, task2Counter.get());
    }

    @Test
    public void testLeaseExtension() throws Exception {
        TaskClient taskClient = mock(TaskClient.class);
        String taskName = "task1";

        Worker worker = mock(Worker.class);
        when(worker.getTaskDefName()).thenReturn(taskName);
        when(worker.leaseExtendEnabled()).thenReturn(true);

        doAnswer(invocation -> {
            TaskResult result = new TaskResult(invocation.getArgument(0));
            result.setStatus(TaskResult.Status.IN_PROGRESS);
            return result;
        }).when(worker).execute(any(Task.class));

        Task task = new Task();
        task.setTaskId("task123");
        task.setTaskDefName(taskName);
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setResponseTimeoutSeconds(2000);

        when(taskClient.batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt()))
            .thenAnswer((invocation) -> List.of(task));
        when(taskClient.ack(any(), any())).thenReturn(true);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(taskClient).updateTask(any(TaskResult.class));

        TaskRunnerConfigurer configurer = new TaskRunnerConfigurer.Builder(taskClient, List.of(worker))
            .withSleepWhenRetry(100)
            .withUpdateRetryCount(3)
            .withThreadCount(1)
            .build();

        configurer.init();
        latch.await();

        ArgumentCaptor<TaskResult> taskResultCaptor = ArgumentCaptor.forClass(TaskResult.class);
        verify(taskClient, atLeastOnce()).updateTask(taskResultCaptor.capture());

        TaskResult capturedResult = taskResultCaptor.getValue();
        assertNotNull(capturedResult);
        assertEquals("task123", capturedResult.getTaskId());
        assertEquals(TaskResult.Status.IN_PROGRESS, capturedResult.getStatus());

        verify(worker, atLeastOnce()).execute(task);
        assertTrue(worker.leaseExtendEnabled(), "Worker lease extension should be enabled");
    }

    private Task testTask(String taskDefName) {
        Task task = new Task();
        task.setTaskId(UUID.randomUUID().toString());
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setTaskDefName(taskDefName);
        return task;
    }
}
