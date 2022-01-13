/*
 * Copyright 2020 Netflix, Inc.
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

import com.google.common.util.concurrent.Uninterruptibles;

import static com.netflix.conductor.common.metadata.tasks.TaskResult.Status.COMPLETED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TaskRunnerConfigurerTest {

    private static final String TEST_TASK_DEF_NAME = "test";

    @Test(expected = NullPointerException.class)
    public void testNoWorkersException() {
        new TaskRunnerConfigurer.Builder(null, null).build();
    }

    @Test(expected = ConductorClientException.class)
    public void testInvalidThreadConfig() {
        Worker worker1 = Worker.create("task1", TaskResult::new);
        Worker worker2 = Worker.create("task2", TaskResult::new);
        Map<String, Integer> taskThreadCount = new HashMap<>();
        taskThreadCount.put(worker1.getTaskDefName(), 2);
        taskThreadCount.put(worker2.getTaskDefName(), 3);
        new TaskRunnerConfigurer.Builder(new TaskClient(), Arrays.asList(worker1, worker2))
                .withThreadCount(10)
                .withTaskThreadCount(taskThreadCount)
                .build();
    }

    @Test(expected = ConductorClientException.class)
    public void testMissingTaskThreadConfig() {
        Worker worker1 = Worker.create("task1", TaskResult::new);
        Worker worker2 = Worker.create("task2", TaskResult::new);
        Map<String, Integer> taskThreadCount = new HashMap<>();
        taskThreadCount.put(worker1.getTaskDefName(), 2);
        new TaskRunnerConfigurer.Builder(new TaskClient(), Arrays.asList(worker1, worker2))
                .withTaskThreadCount(taskThreadCount)
                .build();
    }

    @Test
    public void testPerTaskThreadPool() {
        Worker worker1 = Worker.create("task1", TaskResult::new);
        Worker worker2 = Worker.create("task2", TaskResult::new);
        Map<String, Integer> taskThreadCount = new HashMap<>();
        taskThreadCount.put(worker1.getTaskDefName(), 2);
        taskThreadCount.put(worker2.getTaskDefName(), 3);
        TaskRunnerConfigurer configurer =
                new TaskRunnerConfigurer.Builder(new TaskClient(), Arrays.asList(worker1, worker2))
                        .withTaskThreadCount(taskThreadCount)
                        .build();
        configurer.init();
        assertEquals(-1, configurer.getThreadCount());
        assertEquals(2, configurer.getTaskThreadCount().get("task1").intValue());
        assertEquals(3, configurer.getTaskThreadCount().get("task2").intValue());
    }

    @Test
    public void testSharedThreadPool() {
        Worker worker = Worker.create(TEST_TASK_DEF_NAME, TaskResult::new);
        TaskRunnerConfigurer configurer =
                new TaskRunnerConfigurer.Builder(
                                new TaskClient(), Arrays.asList(worker, worker, worker))
                        .build();
        configurer.init();
        assertEquals(3, configurer.getThreadCount());
        assertEquals(500, configurer.getSleepWhenRetry());
        assertEquals(3, configurer.getUpdateRetryCount());
        assertEquals(10, configurer.getShutdownGracePeriodSeconds());
        assertTrue(configurer.getTaskThreadCount().isEmpty());

        configurer =
                new TaskRunnerConfigurer.Builder(
                                new TaskClient(), Collections.singletonList(worker))
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
    public void testMultipleWorkersExecution() {
        String task1Name = "task1";
        Worker worker1 = mock(Worker.class);
        when(worker1.getPollingInterval()).thenReturn(3000);
        when(worker1.getTaskDefName()).thenReturn(task1Name);
        when(worker1.getIdentity()).thenReturn("worker1");
        when(worker1.execute(any()))
                .thenAnswer(
                        invocation -> {
                            // Sleep for 2 seconds to simulate task execution
                            Uninterruptibles.sleepUninterruptibly(2, TimeUnit.SECONDS);
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
                            Uninterruptibles.sleepUninterruptibly(2, TimeUnit.SECONDS);
                            TaskResult taskResult = new TaskResult();
                            taskResult.setStatus(COMPLETED);
                            return taskResult;
                        });

        Task task1 = testTask(task1Name);
        Task task2 = testTask(task2Name);
        TaskClient taskClient = Mockito.mock(TaskClient.class);
        TaskRunnerConfigurer configurer =
                new TaskRunnerConfigurer.Builder(taskClient, Arrays.asList(worker1, worker2))
                        .withThreadCount(2)
                        .withSleepWhenRetry(100000)
                        .withUpdateRetryCount(1)
                        .withWorkerNamePrefix("test-worker-")
                        .build();
        when(taskClient.pollTask(any(), any(), any()))
                .thenAnswer(
                        invocation -> {
                            Object[] args = invocation.getArguments();
                            String taskName = args[0].toString();
                            if (taskName.equals(task1Name)) {
                                return task1;
                            } else if (taskName.equals(task2Name)) {
                                return task2;
                            } else {
                                return null;
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
        Uninterruptibles.awaitUninterruptibly(latch);

        assertEquals(1, task1Counter.get());
        assertEquals(1, task2Counter.get());
    }

    private Task testTask(String taskDefName) {
        Task task = new Task();
        task.setTaskId(UUID.randomUUID().toString());
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setTaskDefName(taskDefName);
        return task;
    }
}
