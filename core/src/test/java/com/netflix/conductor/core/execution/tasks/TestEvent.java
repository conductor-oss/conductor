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
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.dyno.DynoEventQueueProvider;
import com.netflix.conductor.core.execution.TestConfiguration;
import com.netflix.conductor.dao.QueueDAO;

/**
 * @author Viren
 *
 */
public class TestEvent {

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
		
		new DynoEventQueueProvider(dao, new TestConfiguration());
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
		assertTrue(task.getReasonForIncompletion() != null);
		System.out.println(task.getReasonForIncompletion());
		
		task.getInputData().put("sink", "bad_sink");
		task.setStatus(Status.SCHEDULED);
		
		event.start(workflow, task, null);
		assertEquals(Task.Status.FAILED, task.getStatus());
		assertTrue(task.getReasonForIncompletion() != null);
		System.out.println(task.getReasonForIncompletion());

		task.setStatus(Status.SCHEDULED);
		task.setScheduledTime(System.currentTimeMillis());
		event.execute(workflow, task, null);
		assertEquals(Task.Status.SCHEDULED, task.getStatus());
		
		task.setScheduledTime(System.currentTimeMillis() - 610_000);
		event.execute(workflow, task, null);
		assertEquals(Task.Status.FAILED, task.getStatus());
	}
}
