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
package com.netflix.conductor.core.execution.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.netflix.conductor.core.events.EventQueues;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.MockQueueProvider;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.events.queue.dyno.DynoEventQueueProvider;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.TestConfiguration;
import com.netflix.conductor.dao.QueueDAO;

/**
 * @author Viren
 *
 */
public class TestEvent {

	@Before
	public void setup() {
		EventQueues.providers = new HashMap<>();
		EventQueues.providers.put("sqs", new MockQueueProvider("sqs"));
		EventQueues.providers.put("conductor", new MockQueueProvider("conductor"));
	}
	
	@Test
	public void testEvent() {
		System.setProperty("QUEUE_NAME", "queue_name_001");
		ParametersUtils pu = new ParametersUtils();
		String eventt = "queue_${QUEUE_NAME}";
		String event = pu.replace(eventt).toString();
		assertNotNull(event);
		assertEquals("queue_queue_name_001", event);
		
		eventt = "queue_9";
		event = pu.replace(eventt).toString();
		assertNotNull(event);
		assertEquals(eventt, event);
	}
	
	@Test
	public void testSinkParam() {
		String sink = "sqs:queue_name";
		
		Workflow workflow = new Workflow();
		workflow.setWorkflowType("wf0");
		
		Task task1 = new Task();
		task1.setReferenceTaskName("t1");
		task1.getOutputData().put("q", "t1_queue");
		workflow.getTasks().add(task1);
		
		Task task2 = new Task();
		task2.setReferenceTaskName("t2");
		task2.getOutputData().put("q", "task2_queue");
		workflow.getTasks().add(task2);
		
		Task task = new Task();
		task.setReferenceTaskName("event");
		task.getInputData().put("sink", sink);
		task.setTaskType(WorkflowTask.Type.EVENT.name());
		workflow.getTasks().add(task);
		
		Event event = new Event();
		ObservableQueue queue = event.getQueue(workflow, task);
		assertNotNull(task.getReasonForIncompletion(), queue);
		assertEquals("queue_name", queue.getName());
		assertEquals("sqs", queue.getType());
		
		sink = "sqs:${t1.output.q}";
		task.getInputData().put("sink", sink);
		queue = event.getQueue(workflow, task);
		assertNotNull(queue);
		assertEquals("t1_queue", queue.getName());
		assertEquals("sqs", queue.getType());
		System.out.println(task.getOutputData().get("event_produced"));
		
		sink = "sqs:${t2.output.q}";
		task.getInputData().put("sink", sink);
		queue = event.getQueue(workflow, task);
		assertNotNull(queue);
		assertEquals("task2_queue", queue.getName());
		assertEquals("sqs", queue.getType());
		System.out.println(task.getOutputData().get("event_produced"));
		
		sink = "conductor";
		task.getInputData().put("sink", sink);
		queue = event.getQueue(workflow, task);
		assertNotNull(queue);
		assertEquals(workflow.getWorkflowType() + ":" + task.getReferenceTaskName(), queue.getName());
		assertEquals("conductor", queue.getType());
		System.out.println(task.getOutputData().get("event_produced"));
		
		sink = "sqs:static_value";
		task.getInputData().put("sink", sink);
		queue = event.getQueue(workflow, task);
		assertNotNull(queue);
		assertEquals("static_value", queue.getName());
		assertEquals("sqs", queue.getType());
		assertEquals(sink, task.getOutputData().get("event_produced"));
		System.out.println(task.getOutputData().get("event_produced"));
		
		sink = "bad:queue";
		task.getInputData().put("sink", sink);
		queue = event.getQueue(workflow, task);
		assertNull(queue);
		assertEquals(Task.Status.FAILED, task.getStatus());
	}
	
	@Test
	public void test() throws Exception {
		Event event = new Event();
		Workflow workflow = new Workflow();
		workflow.setWorkflowType("testWorkflow");
		workflow.setVersion(2);
		
		Task task = new Task();
		task.getInputData().put("sink", "conductor");
		task.setReferenceTaskName("task0");
		task.setTaskId("task_id_0");
		
		QueueDAO dao = mock(QueueDAO.class);
		String[] publishedQueue = new String[1];
		List<Message> publishedMessages = new LinkedList<>();
		
		doAnswer(new Answer<Void>() {

			@SuppressWarnings("unchecked")
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				String queueName = invocation.getArgumentAt(0, String.class);
				System.out.println(queueName);
				publishedQueue[0] = queueName;
				List<Message> messages = invocation.getArgumentAt(1, List.class);
				publishedMessages.addAll(messages);
				return null;
			}
		}).when(dao).push(any(), any());
		
		doAnswer(new Answer<List<String>>() {

			@Override
			public List<String> answer(InvocationOnMock invocation) throws Throwable {
				String messageId = invocation.getArgumentAt(1, String.class);
				if(publishedMessages.get(0).getId().equals(messageId)) {
					publishedMessages.remove(0);
					return Arrays.asList(messageId);
				}
				return null;
			}
		}).when(dao).remove(any(), any());

		EventQueues.providers.put("conductor", new DynoEventQueueProvider(dao, new TestConfiguration()));
		event.start(workflow, task, null);
		
		assertEquals(Task.Status.COMPLETED, task.getStatus());
		assertNotNull(task.getOutputData());
		assertEquals("conductor:" + workflow.getWorkflowType() + ":" + task.getReferenceTaskName(), task.getOutputData().get("event_produced"));
		assertEquals(task.getOutputData().get("event_produced"), "conductor:" + publishedQueue[0]);
		assertEquals(1, publishedMessages.size());
		assertEquals(task.getTaskId(), publishedMessages.get(0).getId());
		assertNotNull(publishedMessages.get(0).getPayload());
		
		event.cancel(workflow, task, null);
		assertTrue(publishedMessages.isEmpty());
	}
	
	
	@Test
	public void testFailures() throws Exception {
		Event event = new Event();
		Workflow workflow = new Workflow();
		workflow.setWorkflowType("testWorkflow");
		workflow.setVersion(2);
		
		Task task = new Task();
		task.setReferenceTaskName("task0");
		task.setTaskId("task_id_0");
		
		event.start(workflow, task, null);
		assertEquals(Task.Status.FAILED, task.getStatus());
		assertNotNull(task.getReasonForIncompletion());
		System.out.println(task.getReasonForIncompletion());
		
		task.getInputData().put("sink", "bad_sink");
		task.setStatus(Status.SCHEDULED);
		
		event.start(workflow, task, null);
		assertEquals(Task.Status.FAILED, task.getStatus());
		assertNotNull(task.getReasonForIncompletion());
		System.out.println(task.getReasonForIncompletion());

		task.setStatus(Status.SCHEDULED);
		task.setScheduledTime(System.currentTimeMillis());
		event.execute(workflow, task, null);
		assertEquals(Task.Status.SCHEDULED, task.getStatus());
		
		task.setScheduledTime(System.currentTimeMillis() - 610_000);
		event.start(workflow, task, null);
		assertEquals(Task.Status.FAILED, task.getStatus());
	}
	
	@Test
	public void testDynamicSinks() {

		Event event = new Event();
		Workflow workflow = new Workflow();
		workflow.setWorkflowType("testWorkflow");
		workflow.setVersion(2);
		
		Task task = new Task();
		task.setReferenceTaskName("task0");
		task.setTaskId("task_id_0");
		task.setStatus(Status.IN_PROGRESS);
		task.getInputData().put("sink", "conductor:some_arbitary_queue");
		
		
		ObservableQueue queue = event.getQueue(workflow, task);
		assertEquals(Task.Status.IN_PROGRESS, task.getStatus());
		assertNotNull(queue);
		assertEquals("testWorkflow:some_arbitary_queue", queue.getName());
		assertEquals("testWorkflow:some_arbitary_queue", queue.getURI());
		assertEquals("conductor", queue.getType());
		assertEquals("conductor:testWorkflow:some_arbitary_queue", task.getOutputData().get("event_produced"));
		
		task.getInputData().put("sink", "conductor");
		queue = event.getQueue(workflow, task);
		assertEquals("not in progress: " + task.getReasonForIncompletion(), Task.Status.IN_PROGRESS, task.getStatus());
		assertNotNull(queue);
		assertEquals("testWorkflow:task0", queue.getName());
		
		task.getInputData().put("sink", "sqs:my_sqs_queue_name");
		queue = event.getQueue(workflow, task);
		assertEquals("not in progress: " + task.getReasonForIncompletion(), Task.Status.IN_PROGRESS, task.getStatus());
		assertNotNull(queue);
		assertEquals("my_sqs_queue_name", queue.getName());
		assertEquals("sqs", queue.getType());
		
		task.getInputData().put("sink", "sns:my_sqs_queue_name");
		queue = event.getQueue(workflow, task);
		assertEquals(Task.Status.FAILED, task.getStatus());
		
		
	}
	
}
