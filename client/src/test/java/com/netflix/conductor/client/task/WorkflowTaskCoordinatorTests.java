/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.conductor.client.task;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.conductor.client.exceptions.ConductorClientException;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Viren
 *
 */
@Ignore
public class WorkflowTaskCoordinatorTests {

    @Test(expected=IllegalArgumentException.class)
    public void testNoWorkersException() {
        new WorkflowTaskCoordinator.Builder().build();
    }

    @Test
    public void testThreadPool() {
        Worker worker = Worker.create("test", TaskResult::new);
        WorkflowTaskCoordinator coordinator = new WorkflowTaskCoordinator.Builder().withWorkers(worker, worker, worker).withTaskClient(new TaskClient()).build();
        assertEquals(-1, coordinator.getThreadCount());		//Not initialized yet
        coordinator.init();
        assertEquals(3, coordinator.getThreadCount());
        assertEquals(100, coordinator.getWorkerQueueSize());		//100 is the default value
        assertEquals(500, coordinator.getSleepWhenRetry());
        assertEquals(3, coordinator.getUpdateRetryCount());

        coordinator = new WorkflowTaskCoordinator.Builder()
            .withWorkers(worker)
            .withThreadCount(100)
            .withWorkerQueueSize(400)
            .withSleepWhenRetry(100)
            .withUpdateRetryCount(10)
            .withTaskClient(new TaskClient())
            .withWorkerNamePrefix("test-worker-")
            .build();
        assertEquals(100, coordinator.getThreadCount());
        coordinator.init();
        assertEquals(100, coordinator.getThreadCount());
        assertEquals(400, coordinator.getWorkerQueueSize());
        assertEquals(100, coordinator.getSleepWhenRetry());
        assertEquals(10, coordinator.getUpdateRetryCount());
        assertEquals("test-worker-", coordinator.getWorkerNamePrefix());
    }

    @Test
    public void testTaskException() {
        Worker worker = Worker.create("test", task -> {
            throw new NoSuchMethodError();
        });
        TaskClient client = Mockito.mock(TaskClient.class);
        WorkflowTaskCoordinator coordinator = new WorkflowTaskCoordinator.Builder()
            .withWorkers(worker)
            .withThreadCount(1)
            .withWorkerQueueSize(1)
            .withSleepWhenRetry(100000)
            .withUpdateRetryCount(1)
            .withTaskClient(client)
            .withWorkerNamePrefix("test-worker-")
            .build();
        when(client.batchPollTasksInDomain(anyString(), isNull(), anyString(), anyInt(), anyInt())).thenReturn(ImmutableList.of(new Task()));
        when(client.ack(any(), any())).thenReturn(true);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
                assertEquals("test-worker-0", Thread.currentThread().getName());
                Object[] args = invocation.getArguments();
                TaskResult result = (TaskResult) args[0];
                assertEquals(TaskResult.Status.FAILED, result.getStatus());
                latch.countDown();
                return null;
            }
        ).when(client).updateTask(any());
        coordinator.init();
        Uninterruptibles.awaitUninterruptibly(latch);
        Mockito.verify(client).updateTask(any());
    }

    @Test
    public void testNoOpWhenAckFailed() {
        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(1000);
        when(worker.getPollCount()).thenReturn(1);
        when(worker.getTaskDefName()).thenReturn("test");
        when(worker.preAck(any())).thenReturn(true);

        TaskClient client = Mockito.mock(TaskClient.class);
        WorkflowTaskCoordinator coordinator = new WorkflowTaskCoordinator.Builder()
            .withWorkers(worker)
            .withThreadCount(1)
            .withWorkerQueueSize(1)
            .withSleepWhenRetry(100000)
            .withUpdateRetryCount(1)
            .withTaskClient(client)
            .withWorkerNamePrefix("test-worker-")
            .build();
        Task testTask = new Task();
        testTask.setStatus(Task.Status.IN_PROGRESS);
        when(client.batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt())).thenReturn(ImmutableList.of(testTask));
        when(client.ack(any(), any())).thenReturn(false);

        coordinator.init();
        Uninterruptibles.sleepUninterruptibly(2, TimeUnit.SECONDS);
        verify(client, atLeastOnce()).ack(any(), any());

        // then worker.execute must not be called and task must be updated with IN_PROGRESS status
        verify(worker, never()).execute(any());
        verify(client, never()).updateTask(any());
    }

    @Test
    public void testNoOpWhenAckThrowsException() {
        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(1000);
        when(worker.getPollCount()).thenReturn(1);
        when(worker.getTaskDefName()).thenReturn("test");
        when(worker.preAck(any())).thenReturn(true);

        TaskClient client = Mockito.mock(TaskClient.class);
        WorkflowTaskCoordinator coordinator = new WorkflowTaskCoordinator.Builder()
            .withWorkers(worker)
            .withThreadCount(1)
            .withWorkerQueueSize(1)
            .withSleepWhenRetry(100000)
            .withUpdateRetryCount(1)
            .withTaskClient(client)
            .withWorkerNamePrefix("test-worker-")
            .build();
        Task testTask = new Task();
        testTask.setStatus(Task.Status.IN_PROGRESS);
        when(client.batchPollTasksInDomain(any(), any(), any(), anyInt(), anyInt())).thenReturn(ImmutableList.of(testTask));
        when(client.ack(any(), any())).thenThrow(new RuntimeException("Ack failed"));

        coordinator.init();
        Uninterruptibles.sleepUninterruptibly(2, TimeUnit.SECONDS);
        verify(client).ack(any(), any());

        // then worker.execute must not be called and task must be updated with IN_PROGRESS status
        verify(worker, never()).execute(any());
        verify(client, never()).updateTask(any());
    }

    @Test
    public void testReturnTaskWhenRejectedExecutionExceptionThrown() {
        Task testTask = new Task();
        testTask.setStatus(Task.Status.IN_PROGRESS);

        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getPollCount()).thenReturn(1);
        when(worker.getTaskDefName()).thenReturn("test");
        when(worker.preAck(any())).thenReturn(true);
        when(worker.execute(any())).thenAnswer(invocation -> {
            // Sleep for 2 seconds to trigger RejectedExecutionException
            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
            return new TaskResult(testTask);
        });

        TaskClient client = Mockito.mock(TaskClient.class);
        WorkflowTaskCoordinator coordinator = new WorkflowTaskCoordinator.Builder()
            .withWorkers(worker)
            .withThreadCount(1)
            .withWorkerQueueSize(1)
            .withSleepWhenRetry(100000)
            .withUpdateRetryCount(1)
            .withTaskClient(client)
            .withWorkerNamePrefix("test-worker-")
            .build();
        when(client.batchPollTasksInDomain(anyString(), isNull(), isNull(), anyInt(), anyInt())).thenReturn(ImmutableList.of(testTask, testTask, testTask));
        when(client.ack(any(), any())).thenReturn(true);
        CountDownLatch latch = new CountDownLatch(3);
        doAnswer(invocation -> {
                Object[] args = invocation.getArguments();
                TaskResult result = (TaskResult) args[0];
                assertEquals(TaskResult.Status.IN_PROGRESS, result.getStatus());
                latch.countDown();
                return null;
            }
        ).when(client).updateTask(any());
        coordinator.init();
        Uninterruptibles.awaitUninterruptibly(latch);

        // With worker queue set to 1, first two tasks can be submitted, and third one would get
        // RejectedExceptionExcpetion, so worker.execute() should be called twice.
        verify(worker, times(2)).execute(any());

        // task must be updated with IN_PROGRESS status three times, two from worker.execute() and
        // one from returnTask caused by RejectedExecutionException.
        verify(client, times(3)).updateTask(any());
    }

    @Test
    public void testLargePayloadCanFailUpdateWithRetry() {
        Task testTask = new Task();
        testTask.setStatus(Task.Status.COMPLETED);

        Worker worker = mock(Worker.class);
        when(worker.getPollingInterval()).thenReturn(3000);
        when(worker.getPollCount()).thenReturn(1);
        when(worker.getTaskDefName()).thenReturn("test");
        when(worker.preAck(any())).thenReturn(true);
        when(worker.execute(any())).thenAnswer(invocation -> {
            // Sleep for 2 seconds to trigger RejectedExecutionException
            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
            return new TaskResult(testTask);
        });

        TaskClient taskClient = Mockito.mock(TaskClient.class);
        WorkflowTaskCoordinator coordinator = new WorkflowTaskCoordinator.Builder()
            .withWorkers(worker)
            .withThreadCount(1)
            .withWorkerQueueSize(1)
            .withSleepWhenRetry(100000)
            .withUpdateRetryCount(3)
            .withTaskClient(taskClient)
            .withWorkerNamePrefix("test-worker-")
            .build();

        when(taskClient.batchPollTasksInDomain(anyString(), isNull(), isNull(), anyInt(), anyInt())).thenReturn(ImmutableList.of(testTask));
        when(taskClient.ack(any(), any())).thenReturn(true);

        doThrow(ConductorClientException.class).when(taskClient).evaluateAndUploadLargePayload(any(TaskResult.class), any());

        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
                latch.countDown();
                return null;
            }
        ).when(worker).onErrorUpdate(any());

        coordinator.init();
        Uninterruptibles.awaitUninterruptibly(latch);

        // When evaluateAndUploadLargePayload fails indefinitely, task update shouldn't be called.
        verify(taskClient, times(0)).updateTask(any());
    }
}
