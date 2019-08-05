package com.netflix.conductor.contribs.queue.amqp;

import com.netflix.conductor.core.config.Configuration;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created at 22/03/2019 15:38
 *
 * @author Mickaël GREGORI <mickael.gregori@alchimie.com>
 * @version $Id$
 */
public class TestAMQSettings {

    private Configuration config;

    @Before
    public void setUp() {
        config = mock(Configuration.class);
    }

    @Test
    public void testQueueURIWithDefaultConfig() {
        when(config.getProperty(anyString(), anyString())).thenAnswer(invocation -> invocation.getArguments()[1]);
        when(config.getBooleanProperty(anyString(), anyBoolean())).thenAnswer(invocation -> invocation.getArguments()[1]);
        when(config.getIntProperty(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArguments()[1]);
        final String queueURI = "amqp-queue:myQueueName?deliveryMode=2&durable=false&autoDelete=true&exclusive=true";
        AMQSettings settings = new AMQSettings(config);
        settings.fromURI(queueURI);
        assertEquals(AMQSettings.DEFAULT_CONTENT_TYPE, settings.getContentType());
        assertEquals(AMQSettings.DEFAULT_CONTENT_ENCODING, settings.getContentEncoding());
        assertEquals("myQueueName", settings.getQueueOrExchangeName());
        assertEquals(2, settings.getDeliveryMode());
        assertEquals(false, settings.isDurable());
        assertEquals(true, settings.isExclusive());
        assertEquals(true, settings.autoDelete());
        assertTrue(settings.getArguments().isEmpty());
    }

    @Test
    public void testExchangeURIWithDefaultConfig() {
        when(config.getProperty(anyString(), anyString())).thenAnswer(invocation -> invocation.getArguments()[1]);
        when(config.getBooleanProperty(anyString(), anyBoolean())).thenAnswer(invocation -> invocation.getArguments()[1]);
        when(config.getIntProperty(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArguments()[1]);
        final String queueURI = "amqp-exchange:myExchangeName?exchangeType=topic&routingKey=myRoutingKey&deliveryMode=2";
        AMQSettings settings = new AMQSettings(config).fromURI(queueURI);
        assertEquals("myExchangeName", settings.getQueueOrExchangeName());
        assertEquals("topic", settings.getExchangeType());
        assertEquals("myRoutingKey", settings.getRoutingKey());
        assertEquals(2, settings.getDeliveryMode());
        // default values
        assertEquals(AMQSettings.DEFAULT_CONTENT_TYPE, settings.getContentType());
        assertEquals(AMQSettings.DEFAULT_CONTENT_ENCODING, settings.getContentEncoding());
        assertEquals(true, settings.isDurable());
        assertEquals(false, settings.isExclusive()); // default value
        assertEquals(false, settings.autoDelete()); // default value
        assertTrue(settings.getArguments().isEmpty());
    }
}
