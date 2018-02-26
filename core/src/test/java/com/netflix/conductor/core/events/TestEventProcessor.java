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
package com.netflix.conductor.core.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.common.metadata.events.EventHandler.Action.Type;
import com.netflix.conductor.common.metadata.events.EventHandler.StartWorkflow;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.execution.TestConfiguration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;

import rx.Observable;

/**
 * @author Viren
 *
 */
public class TestEventProcessor {
	
	@Test
	public void testEventProcessor() throws Exception {
		String event = "sqs:arn:account090:sqstest1";
		String queueURI = "arn:account090:sqstest1";
	
		EventQueueProvider provider = mock(EventQueueProvider.class);
		
		ObservableQueue queue = mock(ObservableQueue.class);
		Message[] messages = new Message[1];
		messages[0] = new Message("t0", "{}", "t0");
		Observable<Message> msgObservable = Observable.from(messages);
		when(queue.observe()).thenReturn(msgObservable);
		when(queue.getURI()).thenReturn(queueURI);
		when(queue.getName()).thenReturn(queueURI);
		when(queue.getType()).thenReturn("sqs");
		when(provider.getQueue(queueURI)).thenReturn(queue);
		EventQueues.providers = new HashMap<>();
		
		EventQueues.providers.put("sqs", provider);
		
		EventHandler eh = new EventHandler();
		eh.setName(UUID.randomUUID().toString());
		eh.setActive(false);
		Action action = new Action();
		action.setAction(Type.start_workflow);
		action.setStart_workflow(new StartWorkflow());
		action.getStart_workflow().setName("workflow_x");
		action.getStart_workflow().setVersion(1);	//TODO: Remove this to simulate the null value for version being passed!
		eh.getActions().add(action);
		eh.setEvent(event);
		
		MetadataService ms = mock(MetadataService.class);
		

		when(ms.getEventHandlers()).thenReturn(Arrays.asList(eh));
		when(ms.getEventHandlersForEvent(eh.getEvent(), true)).thenReturn(Arrays.asList(eh));
		
		//Execution Service Mock
		ExecutionService eservice = mock(ExecutionService.class);
		when(eservice.addEventExecution(any())).thenReturn(true);
		
		//Workflow Executor Mock
		WorkflowExecutor executor = mock(WorkflowExecutor.class);
		String id = UUID.randomUUID().toString();
		AtomicBoolean started = new AtomicBoolean(false);
		doAnswer(new Answer<String>() {

			@Override
			public String answer(InvocationOnMock invocation) throws Throwable {
				started.set(true);
				return id;
			}
		}).when(executor).startWorkflow(action.getStart_workflow().getName(), 1, action.getStart_workflow().getCorrelationId(), action.getStart_workflow().getInput(), event);

		//Metadata Service Mock
		MetadataService metadata = mock(MetadataService.class);
		WorkflowDef def = new WorkflowDef();
		def.setVersion(1);
		def.setName(action.getStart_workflow().getName());
		when(metadata.getWorkflowDef(any(), any())).thenReturn(def);
		
		ActionProcessor ap = new ActionProcessor(executor, metadata);
		
		EventProcessor ep = new EventProcessor(eservice, ms, ap, new TestConfiguration(), new ObjectMapper());
		assertNotNull(ep.getQueues());
		assertEquals(1, ep.getQueues().size());
		
		String queueEvent = ep.getQueues().keySet().iterator().next();
		assertEquals(eh.getEvent(), queueEvent);
		
		String epQueue = ep.getQueues().values().iterator().next();
		assertEquals(queueURI, epQueue);
				
		Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
		assertTrue(started.get());
	}
	
}
