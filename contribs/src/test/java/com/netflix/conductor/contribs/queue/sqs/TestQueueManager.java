/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.conductor.contribs.queue.sqs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.contribs.queue.QueueManager;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.execution.tasks.Wait;
import com.netflix.conductor.service.ExecutionService;

/**
 * @author Viren
 *
 */
public class TestQueueManager {

	private static SQSObservableQueue queue;

	private static ExecutionService es;

	private ObjectMapper objectMapper = new JsonMapperProvider().get();

	private static final List<Message> messages = new LinkedList<>();

	private static final List<Task> updatedTasks = new LinkedList<>();

	@BeforeClass
	public static void setup() throws Exception {

		queue = mock(SQSObservableQueue.class);
		when(queue.getOrCreateQueue()).thenReturn("junit_queue_url");
		Answer<?> answer = new Answer<List<Message>>() {

			@Override
			public List<Message> answer(InvocationOnMock invocation) throws Throwable {
				List<Message> copy = new LinkedList<>();
				copy.addAll(messages);
				messages.clear();
				return copy;
			}
		};

		when(queue.receiveMessages()).thenAnswer(answer);
		when(queue.getOnSubscribe()).thenCallRealMethod();
		when(queue.observe()).thenCallRealMethod();
		when(queue.getName()).thenReturn(Status.COMPLETED.name());

		Task task0 = new Task();
		task0.setStatus(Status.IN_PROGRESS);
		task0.setTaskId("t0");
		task0.setReferenceTaskName("t0");
		task0.setTaskType(Wait.NAME);
		Workflow workflow0 = new Workflow();
		workflow0.setWorkflowId("v_0");
		workflow0.getTasks().add(task0);

		Task task2 = new Task();
		task2.setStatus(Status.IN_PROGRESS);
		task2.setTaskId("t2");
		task2.setTaskType(Wait.NAME);
		Workflow workflow2 = new Workflow();
		workflow2.setWorkflowId("v_2");
		workflow2.getTasks().add(task2);

		doAnswer(new Answer<Void>() {

			@SuppressWarnings("unchecked")
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				List<Message> msgs = invocation.getArgument(0, List.class);
				System.out.println("got messages to publish: " + msgs);
				messages.addAll(msgs);
				return null;
			}
		}).when(queue).publish(any());

		es = mock(ExecutionService.class);
		assertNotNull(es);

		doReturn(workflow0).when(es).getExecutionStatus(eq("v_0"), anyBoolean());

		doReturn(workflow2).when(es).getExecutionStatus(eq("v_2"), anyBoolean());

		doAnswer(new Answer<Void>() {

			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				System.out.println("Updating task: " + invocation.getArgument(0, Task.class));
				updatedTasks.add(invocation.getArgument(0, Task.class));
				return null;
			}
		}).when(es).updateTask(any(Task.class));

	}


	@Test
	public void test() throws Exception {
		Map<Status, ObservableQueue> queues = new HashMap<>();
		queues.put(Status.COMPLETED, queue);
		QueueManager qm = new QueueManager(queues, es, objectMapper);
		qm.updateByTaskRefName("v_0", "t0", new HashMap<>(), Status.COMPLETED);
		Uninterruptibles.sleepUninterruptibly(1_000, TimeUnit.MILLISECONDS);

		assertTrue(updatedTasks.stream().anyMatch(task -> task.getTaskId().equals("t0")));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testFailure() throws Exception {
		Map<Status, ObservableQueue> queues = new HashMap<>();
		queues.put(Status.COMPLETED, queue);
		QueueManager qm = new QueueManager(queues, es, objectMapper);
		qm.updateByTaskRefName("v_1", "t1", new HashMap<>(), Status.CANCELED);
		Uninterruptibles.sleepUninterruptibly(1_000, TimeUnit.MILLISECONDS);
	}

	@Test
	public void testWithTaskId() throws Exception {
		Map<Status, ObservableQueue> queues = new HashMap<>();
		queues.put(Status.COMPLETED, queue);
		QueueManager qm = new QueueManager(queues, es, objectMapper);
		qm.updateByTaskId("v_2", "t2", new HashMap<>(), Status.COMPLETED);
		Uninterruptibles.sleepUninterruptibly(1_000, TimeUnit.MILLISECONDS);

		assertTrue(updatedTasks.stream().anyMatch(task -> task.getTaskId().equals("t2")));
	}
}
