/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.contribs.queue.amqp;

import java.time.Duration;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.contribs.queue.amqp.config.AMQPEventQueueProperties;
import com.netflix.conductor.contribs.queue.amqp.util.AMQPSettings;

import com.rabbitmq.client.AMQP.PROTOCOL;
import com.rabbitmq.client.ConnectionFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AMQPSettingsTest {

    private AMQPEventQueueProperties properties;

    @Before
    public void setUp() {
        properties = mock(AMQPEventQueueProperties.class);
        when(properties.getBatchSize()).thenReturn(1);
        when(properties.getPollTimeDuration()).thenReturn(Duration.ofMillis(100));
        when(properties.getHosts()).thenReturn(ConnectionFactory.DEFAULT_HOST);
        when(properties.getUsername()).thenReturn(ConnectionFactory.DEFAULT_USER);
        when(properties.getPassword()).thenReturn(ConnectionFactory.DEFAULT_PASS);
        when(properties.getVirtualHost()).thenReturn(ConnectionFactory.DEFAULT_VHOST);
        when(properties.getPort()).thenReturn(PROTOCOL.PORT);
        when(properties.getConnectionTimeout())
                .thenReturn(Duration.ofMillis(ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT));
        when(properties.isUseNio()).thenReturn(false);
        when(properties.isDurable()).thenReturn(true);
        when(properties.isExclusive()).thenReturn(false);
        when(properties.isAutoDelete()).thenReturn(false);
        when(properties.getContentType()).thenReturn("application/json");
        when(properties.getContentEncoding()).thenReturn("UTF-8");
        when(properties.getExchangeType()).thenReturn("topic");
        when(properties.getDeliveryMode()).thenReturn(2);
        when(properties.isUseExchange()).thenReturn(true);
    }

    @Test
    public void testAMQPSettings_exchange_fromuri_defaultconfig() {
        String exchangestring =
                "amqp_exchange:myExchangeName?exchangeType=topic&routingKey=test&deliveryMode=2";
        AMQPSettings settings = new AMQPSettings(properties);
        settings.fromURI(exchangestring);
        assertEquals("topic", settings.getExchangeType());
        assertEquals("test", settings.getRoutingKey());
        assertEquals("myExchangeName", settings.getQueueOrExchangeName());
    }

    @Test
    public void testAMQPSettings_queue_fromuri_defaultconfig() {
        String exchangestring =
                "amqp_queue:myQueueName?deliveryMode=2&durable=false&autoDelete=true&exclusive=true";
        AMQPSettings settings = new AMQPSettings(properties);
        settings.fromURI(exchangestring);
        assertFalse(settings.isDurable());
        assertTrue(settings.isExclusive());
        assertTrue(settings.autoDelete());
        assertEquals(2, settings.getDeliveryMode());
        assertEquals("myQueueName", settings.getQueueOrExchangeName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAMQPSettings_exchange_fromuri_wrongdeliverymode() {
        String exchangestring =
                "amqp_exchange:myExchangeName?exchangeType=topic&routingKey=test&deliveryMode=3";
        AMQPSettings settings = new AMQPSettings(properties);
        settings.fromURI(exchangestring);
    }
}
