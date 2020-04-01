package com.netflix.conductor.contribs;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;

import junit.framework.Assert;

public class AMQPModuleTest {
	private Configuration config;

	@Before
	public void setUp() {
		config = Mockito.mock(Configuration.class);
	}

	@Test
	public void testGetQueue() {
		AMQPModule module = new AMQPModule();
		EventQueueProvider provider = module.getAMQQueueEventQueueProvider(config);
		Assert.assertNotNull(provider);
	}

	@Test
	public void testGetExchange() {
		AMQPModule module = new AMQPModule();
		EventQueueProvider provider = module.getAMQExchangeEventQueueProvider(config);
		Assert.assertNotNull(provider);
	}

	@Test
	public void testGetQueues() {
		AMQPModule module = new AMQPModule();
		Mockito.when(config.getProperty(Mockito.anyString(), Mockito.anyString()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		Mockito.when(config.getBooleanProperty(Mockito.anyString(), Mockito.anyBoolean()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		Mockito.when(config.getIntProperty(Mockito.anyString(), Mockito.anyInt()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		Map<Task.Status, ObservableQueue> queues = module.getQueues(config);
		Assert.assertNotNull(queues);
		Assert.assertEquals(2, queues.size());
		Assert.assertEquals(queues.containsKey(Task.Status.COMPLETED), true);
		Assert.assertEquals(queues.containsKey(Task.Status.FAILED), true);
	}

}
