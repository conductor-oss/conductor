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
import java.util.LinkedList;
import java.util.List;
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
import com.netflix.conductor.core.events.EventQueues.QueueType;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.events.queue.dyno.DynoEventQueueProvider;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;

import rx.Observable;

/**
 * @author Viren
 *
 */
public class TestEventProcessor {

	private static ObjectMapper om = new ObjectMapper();
	
	@Test
	public void testEventProcessor() throws Exception {
		String event = "sqs:arn:account090:sqstest1";
		String queueURI = "arn:account090:sqstest1";
		List<Message> published = new LinkedList<>();
		
		EventQueueProvider provider = mock(EventQueueProvider.class);
		
		ObservableQueue queue = mock(ObservableQueue.class);
		Message[] messages = new Message[1];
		messages[0] = new Message("t0", "{}", "t0");
		Observable<Message> msgObservable = Observable.from(messages);
		when(queue.observe()).thenReturn(msgObservable);
		when(queue.getURI()).thenReturn(queueURI);
		when(queue.getType()).thenReturn("sqs");
		when(provider.getQueue(queueURI)).thenReturn(queue);
		
		
		ObservableQueue actionQueue = mock(ObservableQueue.class);
		when(actionQueue.getURI()).thenReturn(queueURI);
		when(actionQueue.getType()).thenReturn("sqs");
		when(provider.getQueue(ActionProcessor.queueName)).thenReturn(actionQueue);
		
		EventQueues.registerProvider(QueueType.sqs, provider);
		
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
		QueueDAO dao = mock(QueueDAO.class);
		
		doAnswer(new Answer<Void>() {

			@SuppressWarnings("unchecked")
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				List<Message> toBePublished = invocation.getArgumentAt(0, List.class);
				toBePublished.forEach(msg -> published.add(msg));
				return null;
			}
		}).when(actionQueue).publish(any());

		when(ms.getEventHandlers()).thenReturn(Arrays.asList(eh));
		when(ms.getEventHandlersForEvent(eh.getEvent(), true)).thenReturn(Arrays.asList(eh));
		
		DynoEventQueueProvider queueProvider = mock(DynoEventQueueProvider.class);
		when(queueProvider.getQueue(ActionProcessor.queueName)).thenReturn(actionQueue);
		
		EventProcessor ep = new EventProcessor(queueProvider, ms, om);
		assertNotNull(ep.getQueues());
		assertEquals(1, ep.getQueues().size());
		
		String queueEvent = ep.getQueues().keySet().iterator().next();
		assertEquals(eh.getEvent(), queueEvent);
		
		String epQueue = ep.getQueues().values().iterator().next();
		assertEquals(queueURI, epQueue);
		assertEquals(1, published.size());
		String payload = published.get(0).getPayload();
		assertNotNull(payload);
		
		when(actionQueue.getName()).thenReturn("hello");
		when(actionQueue.getURI()).thenReturn("hello");
		when(actionQueue.observe()).thenReturn(Observable.from(published));
		when(queueProvider.getQueue(any())).thenReturn(actionQueue);
		
		WorkflowExecutor executor = mock(WorkflowExecutor.class);
		String id = UUID.randomUUID().toString();
		AtomicBoolean started = new AtomicBoolean(false);
		doAnswer(new Answer<String>() {

			@Override
			public String answer(InvocationOnMock invocation) throws Throwable {
				started.set(true);
				return id;
			}
		}).when(executor).startWorkflow(action.getStart_workflow().getName(), 1, action.getStart_workflow().getCorrelationId(), action.getStart_workflow().getInput());
		ActionProcessor ap = new ActionProcessor(queueProvider, executor, mock(ExecutionService.class), new ObjectMapper());
		Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
		assertTrue(started.get());
	}
	
}
