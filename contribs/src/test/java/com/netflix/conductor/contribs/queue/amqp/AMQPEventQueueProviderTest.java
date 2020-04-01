package com.netflix.conductor.contribs.queue.amqp;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.queue.ObservableQueue;

import junit.framework.Assert;

public class AMQPEventQueueProviderTest {
	private Configuration config;

	@Before
	public void setUp() {
		config = Mockito.mock(Configuration.class);
	}

	@Test
	public void testAMQPEventQueueProvider_defaultconfig_exchange() {
		String exchangestring = "amqp_exchange:myExchangeName?exchangeType=topic&routingKey=test&deliveryMode=2";
		Mockito.when(config.getProperty(Mockito.anyString(), Mockito.anyString()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		Mockito.when(config.getBooleanProperty(Mockito.anyString(), Mockito.anyBoolean()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		Mockito.when(config.getIntProperty(Mockito.anyString(), Mockito.anyInt()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		AMQPEventQueueProvider eventqProvider = new AMQPEventQueueProvider(config, true);
		ObservableQueue queue = eventqProvider.getQueue(exchangestring);
		Assert.assertNotNull(queue);
		Assert.assertEquals(exchangestring, queue.getName());
		Assert.assertEquals(AMQPConstants.AMQP_EXCHANGE_TYPE, queue.getType());
	}

	@Test
	public void testAMQPEventQueueProvider_defaultconfig_queue() {
		String exchangestring = "amqp_queue:myQueueName?deliveryMode=2&durable=false&autoDelete=true&exclusive=true";
		Mockito.when(config.getProperty(Mockito.anyString(), Mockito.anyString()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		Mockito.when(config.getBooleanProperty(Mockito.anyString(), Mockito.anyBoolean()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		Mockito.when(config.getIntProperty(Mockito.anyString(), Mockito.anyInt()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		AMQPEventQueueProvider eventqProvider = new AMQPEventQueueProvider(config, false);
		ObservableQueue queue = eventqProvider.getQueue(exchangestring);
		Assert.assertNotNull(queue);
		Assert.assertEquals(exchangestring, queue.getName());
		Assert.assertEquals(AMQPConstants.AMQP_QUEUE_TYPE, queue.getType());
	}

}
