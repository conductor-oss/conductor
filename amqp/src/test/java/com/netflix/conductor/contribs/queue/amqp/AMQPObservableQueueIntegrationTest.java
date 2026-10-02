/*
 * Copyright 2023 Conductor Authors.
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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.netflix.conductor.contribs.queue.amqp.config.AMQPEventQueueProperties;
import com.netflix.conductor.contribs.queue.amqp.config.AMQPRetryPattern;
import com.netflix.conductor.contribs.queue.amqp.util.AMQPSettings;
import com.netflix.conductor.core.events.queue.Message;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.ConnectionFactory;
import rx.Observable;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
public class AMQPObservableQueueIntegrationTest {

    @Container
    private static final RabbitMQContainer rabbitMQContainer =
            new RabbitMQContainer("rabbitmq:3-management");

    private ConnectionFactory factory;
    private Address[] addresses;
    private AMQPRetryPattern retryPattern;

    @BeforeEach
    public void setup() {
        factory = new ConnectionFactory();
        factory.setHost(rabbitMQContainer.getHost());
        factory.setPort(rabbitMQContainer.getAmqpPort());
        addresses =
                new Address[] {
                    new Address(rabbitMQContainer.getHost(), rabbitMQContainer.getAmqpPort())
                };

        retryPattern = Mockito.mock(AMQPRetryPattern.class);
    }

    private AMQPSettings createSettings(String queueName, String exchangeType) {
        AMQPEventQueueProperties properties = new AMQPEventQueueProperties();
        properties.setExchangeType(exchangeType);

        AMQPSettings settings = new AMQPSettings(properties);
        AMQPSettings spySettings = Mockito.spy(settings);
        Mockito.when(spySettings.getQueueOrExchangeName()).thenReturn(queueName);
        Mockito.when(spySettings.getRoutingKey()).thenReturn(queueName);
        Mockito.when(spySettings.getDeliveryMode()).thenReturn(2);
        return spySettings;
    }

    @Test
    public void testPublishConsumeAckLoop() throws Exception {
        AMQPSettings settings = createSettings("test-queue", "direct");
        AMQPObservableQueue queue =
                new AMQPObservableQueue(factory, addresses, false, settings, retryPattern, 1, 1000);

        // Publish
        Message msg = new Message("test-id-1", "test-payload-1", null);
        queue.publish(Collections.singletonList(msg));

        // Consume
        Observable<Message> observable = queue.observe();
        List<Message> received = observable.take(1).toList().toBlocking().first();
        assertEquals(1, received.size());
        assertEquals("test-payload-1", received.get(0).getPayload());

        // Ack
        List<String> acked = queue.ack(Collections.singletonList(received.get(0)));
        assertEquals(1, acked.size());
    }

    @Test
    public void testExchangeRouting() throws Exception {
        AMQPSettings settings = createSettings("test-exchange", "topic");
        AMQPObservableQueue queue =
                new AMQPObservableQueue(factory, addresses, true, settings, retryPattern, 1, 1000);

        Message msg = new Message("test-id-2", "test-payload-2", null);
        queue.publish(Collections.singletonList(msg));

        Observable<Message> observable = queue.observe();
        List<Message> received = observable.take(1).toList().toBlocking().first();
        assertEquals(1, received.size());
        assertEquals("test-payload-2", received.get(0).getPayload());

        queue.ack(Collections.singletonList(received.get(0)));
    }

    @Test
    public void testCancelThrowsNumberFormatException() throws Exception {
        AMQPSettings settings = createSettings("test-cancel-queue", "direct");
        AMQPObservableQueue queue =
                new AMQPObservableQueue(factory, addresses, false, settings, retryPattern, 1, 1000);

        Message msg = new Message("test-id-3", "test-payload-3", null);
        queue.publish(Collections.singletonList(msg));

        Observable<Message> observable = queue.observe();
        List<Message> received = observable.take(1).toList().toBlocking().first();
        assertEquals(1, received.size());

        Message consumedMsg = received.get(0);
        // Simulate Event.cancel() replacing the receipt with a UUID
        consumedMsg.setReceipt(UUID.randomUUID().toString());

        // This reproduces Issue #779, but now we expect it to safely return instead of crashing
        queue.ack(Collections.singletonList(consumedMsg));
        // No exception should be thrown
    }
}
