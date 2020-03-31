package com.netflix.conductor.contribs.queue.amqp;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.core.config.Configuration;

import junit.framework.Assert;

public class AMQPSettingsTest {
	private Configuration config;

	@Before
	public void setUp() {
		config = Mockito.mock(Configuration.class);
	}

	@Test
	public void testAMQPSettings_exchange_fromuri_defaultconfig() {
		String exchangestring = "amqp_exchange:myExchangeName?exchangeType=topic&routingKey=test&deliveryMode=2";
		Mockito.when(config.getProperty(Mockito.anyString(), Mockito.anyString()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		Mockito.when(config.getBooleanProperty(Mockito.anyString(), Mockito.anyBoolean()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		Mockito.when(config.getIntProperty(Mockito.anyString(), Mockito.anyInt()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		AMQPSettings settings = new AMQPSettings(config);
		settings = settings.fromURI(exchangestring);
		Assert.assertEquals("topic", settings.getExchangeType());
		Assert.assertEquals("test", settings.getRoutingKey());
		Assert.assertEquals("myExchangeName", settings.getQueueOrExchangeName());
	}

	@Test
	public void testAMQPSettings_queue_fromuri_defaultconfig() {
		String exchangestring = "amqp_queue:myQueueName?deliveryMode=2&durable=false&autoDelete=true&exclusive=true";
		Mockito.when(config.getProperty(Mockito.anyString(), Mockito.anyString()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		Mockito.when(config.getBooleanProperty(Mockito.anyString(), Mockito.anyBoolean()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		Mockito.when(config.getIntProperty(Mockito.anyString(), Mockito.anyInt()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		AMQPSettings settings = new AMQPSettings(config);
		settings = settings.fromURI(exchangestring);
		Assert.assertEquals(false, settings.isDurable());
		Assert.assertEquals(true, settings.isExclusive());
		Assert.assertEquals(true, settings.autoDelete());
		Assert.assertEquals(2, settings.getDeliveryMode());
		Assert.assertEquals("myQueueName", settings.getQueueOrExchangeName());
	}
	@Test(expected=IllegalArgumentException.class)
	public void testAMQPSettings_exchange_fromuri_wrongdeliverymode() {
		String exchangestring = "amqp_exchange:myExchangeName?exchangeType=topic&routingKey=test&deliveryMode=3";
		Mockito.when(config.getProperty(Mockito.anyString(), Mockito.anyString()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		Mockito.when(config.getBooleanProperty(Mockito.anyString(), Mockito.anyBoolean()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		Mockito.when(config.getIntProperty(Mockito.anyString(), Mockito.anyInt()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		AMQPSettings settings = new AMQPSettings(config);
		settings = settings.fromURI(exchangestring);

	}
	


}
