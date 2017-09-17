/**
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
/**
 * 
 */
package com.netflix.conductor.client.task;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

/**
 * @author Viren
 *
 */
public class WorkflowTaskCoordinatorTests {
	
	@Test(expected=IllegalArgumentException.class)
	public void testNoWorkersException() {
		new WorkflowTaskCoordinator.Builder().build();
	}
	
	@Test
	public void testThreadPool() {
		
		Worker worker = Worker.create("test", (Task task)-> new TaskResult(task));
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
		WorkflowTaskCoordinator coordinator = new WorkflowTaskCoordinator.Builder().withWorkers(worker, worker, worker).withTaskClient(client).build();
		coordinator = new WorkflowTaskCoordinator.Builder()
				.withWorkers(worker)
				.withThreadCount(1)
				.withWorkerQueueSize(1)
				.withSleepWhenRetry(100000)
				.withUpdateRetryCount(1)
				.withTaskClient(client)
				.withWorkerNamePrefix("test-worker-")
				.build();
		when(client.poll(anyString(), anyString(), anyString(), anyInt(), anyInt())).thenReturn(ImmutableList.of(new Task()));
		when(client.ack(anyString(), anyString())).thenReturn(true);
		CountDownLatch latch = new CountDownLatch(1);
		doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				assertEquals("test-worker-0", Thread.currentThread().getName());
				Object[] args = invocation.getArguments();
				TaskResult result = (TaskResult) args[0];
				assertEquals(TaskResult.Status.FAILED, result.getStatus());
				latch.countDown();
				return null;
			}
		}).when(client).updateTask(any());
		coordinator.init();
		Uninterruptibles.awaitUninterruptibly(latch);
		Mockito.verify(client).updateTask(any());

	}
}
