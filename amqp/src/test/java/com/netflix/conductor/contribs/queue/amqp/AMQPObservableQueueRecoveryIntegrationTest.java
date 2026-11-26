/*
 * Copyright 2026 Conductor Authors.
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

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.netflix.conductor.contribs.queue.amqp.config.AMQPEventQueueProperties;
import com.netflix.conductor.contribs.queue.amqp.util.AMQPSettings;
import com.netflix.conductor.core.events.queue.Message;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoveryListener;
import rx.observers.TestSubscriber;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class AMQPObservableQueueRecoveryIntegrationTest {

    @Container
    private static final RabbitMQContainer rabbitMQContainer =
            new RabbitMQContainer("rabbitmq:3-management")
                    .withStartupTimeout(Duration.ofSeconds(60));

    @Test
    void acknowledgesRecoveredDeliveryOnItsConsumerChannel() throws Exception {
        runRecoveryScenario(false);
    }

    @Test
    void nacksRecoveredDeliveryOnItsConsumerChannel() throws Exception {
        runRecoveryScenario(true);
    }

    @Test
    void closeDuringRecoveryPreventsBrokerConnectionFromReappearing() throws Exception {
        resetAMQPConnection();
        ConnectionFactory factory = newConnectionFactory();
        Address[] addresses = {
            new Address(rabbitMQContainer.getHost(), rabbitMQContainer.getAmqpPort())
        };
        AMQPSettings settings =
                new AMQPSettings(new AMQPEventQueueProperties())
                        .fromURI("amqp_queue:recovery-close-" + UUID.randomUUID());
        AMQPObservableQueue queue =
                new AMQPObservableQueue(factory, addresses, false, settings, null, 1, 100);
        TestSubscriber<Message> subscriber = new TestSubscriber<>();
        CountDownLatch recoveryStarted = new CountDownLatch(1);
        CountDownLatch continueRecovery = new CountDownLatch(1);
        String clientProvidedName = null;

        try {
            queue.start();
            queue.observe().subscribe(subscriber);
            Connection subscriberConnection = getSubscriberConnection(queue);
            clientProvidedName = subscriberConnection.getClientProvidedName();
            addBlockingRecoveryListener(subscriberConnection, recoveryStarted, continueRecovery);

            closeBrokerConnection(clientProvidedName);
            assertTrue(
                    recoveryStarted.await(10, TimeUnit.SECONDS),
                    "RabbitMQ automatic recovery did not start");

            queue.close();
            continueRecovery.countDown();
        } finally {
            continueRecovery.countDown();
            subscriber.unsubscribe();
            queue.stop();
            queue.close();
            AMQPConnection.setAMQPConnection(null);
        }

        assertBrokerConnectionsClosed(Collections.singletonList(clientProvidedName));
    }

    private void runRecoveryScenario(boolean nack) throws Exception {
        resetAMQPConnection();
        String queueName = "recovery-" + UUID.randomUUID();
        ConnectionFactory factory = newConnectionFactory();
        Address[] addresses = {
            new Address(rabbitMQContainer.getHost(), rabbitMQContainer.getAmqpPort())
        };
        AMQPSettings settings =
                new AMQPSettings(new AMQPEventQueueProperties()).fromURI("amqp_queue:" + queueName);
        AMQPObservableQueue queue =
                new AMQPObservableQueue(factory, addresses, false, settings, null, 1, 100);
        TestSubscriber<Message> subscriber = new TestSubscriber<>();
        CountDownLatch recoveryStarted = new CountDownLatch(1);
        CountDownLatch continueRecovery = new CountDownLatch(1);
        CountDownLatch recoveryCompleted = new CountDownLatch(1);
        List<String> connectionNames = new java.util.ArrayList<>();

        try {
            queue.start();
            queue.observe().subscribe(subscriber);
            queue.publish(
                    Collections.singletonList(new Message("message-id", "test-payload", null)));
            subscriber.awaitValueCount(1, 10, TimeUnit.SECONDS);
            assertEquals(
                    1, subscriber.getOnNextEvents().size(), "Initial delivery was not received");
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(
                            () -> {
                                QueueCounts counts = getQueueCounts(queueName);
                                assertEquals(0, counts.ready, counts.toString());
                                assertEquals(1, counts.unacknowledged, counts.toString());
                                assertEquals(1, counts.consumers, counts.toString());
                            });

            Connection subscriberConnection = getSubscriberConnection(queue);
            Connection publisherConnection = getPublisherConnection(queue);
            connectionNames.add(subscriberConnection.getClientProvidedName());
            connectionNames.add(publisherConnection.getClientProvidedName());
            addBlockingRecoveryListener(subscriberConnection, recoveryStarted, continueRecovery);
            ((Recoverable) subscriberConnection)
                    .addRecoveryListener(
                            new RecoveryListener() {
                                @Override
                                public void handleRecoveryStarted(Recoverable recoverable) {}

                                @Override
                                public void handleRecovery(Recoverable recoverable) {
                                    recoveryCompleted.countDown();
                                }
                            });

            closeBrokerConnection(subscriberConnection.getClientProvidedName());
            assertTrue(
                    recoveryStarted.await(10, TimeUnit.SECONDS),
                    "RabbitMQ automatic recovery did not start");

            try {
                QueueCounts recoveringCounts = getQueueCounts(queueName);
                assertEquals(1, recoveringCounts.ready, recoveringCounts.toString());
                assertEquals(0, recoveringCounts.unacknowledged, recoveringCounts.toString());
                assertEquals(
                        1,
                        queue.size(),
                        "The interrupted delivery should be ready while its consumer recovers");
            } finally {
                continueRecovery.countDown();
            }

            assertTrue(
                    recoveryCompleted.await(10, TimeUnit.SECONDS),
                    "RabbitMQ automatic recovery did not complete");
            subscriber.awaitValueCount(2, 10, TimeUnit.SECONDS);
            List<Message> deliveries = subscriber.getOnNextEvents();
            assertEquals(2, deliveries.size(), "The unacknowledged message was not redelivered");
            assertEquals("test-payload", deliveries.get(1).getPayload());

            if (nack) {
                queue.nack(Collections.singletonList(deliveries.get(1)));
            } else {
                assertTrue(
                        queue.ack(Collections.singletonList(deliveries.get(1))).isEmpty(),
                        "Broker rejected the acknowledgement");
            }
            assertBrokerQueueDrained(queueName);
        } finally {
            continueRecovery.countDown();
            subscriber.unsubscribe();
            queue.stop();
            queue.close();
            AMQPConnection.setAMQPConnection(null);
        }

        assertBrokerConnectionsClosed(connectionNames);
    }

    private ConnectionFactory newConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitMQContainer.getHost());
        factory.setPort(rabbitMQContainer.getAmqpPort());
        factory.setUsername(rabbitMQContainer.getAdminUsername());
        factory.setPassword(rabbitMQContainer.getAdminPassword());
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(100);
        return factory;
    }

    private void addBlockingRecoveryListener(
            Connection connection,
            CountDownLatch recoveryStarted,
            CountDownLatch continueRecovery) {
        ((Recoverable) connection)
                .addRecoveryListener(
                        new RecoveryListener() {
                            @Override
                            public void handleRecoveryStarted(Recoverable recoverable) {
                                recoveryStarted.countDown();
                                try {
                                    if (!continueRecovery.await(10, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException(
                                                "Test did not release connection recovery");
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new RuntimeException(e);
                                }
                            }

                            @Override
                            public void handleRecovery(Recoverable recoverable) {}
                        });
    }

    private void assertBrokerQueueDrained(String queueName) {
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            QueueCounts counts = getQueueCounts(queueName);
                            assertEquals(0, counts.ready, "Messages remained ready at RabbitMQ");
                            assertEquals(
                                    0,
                                    counts.unacknowledged,
                                    "Messages remained unacknowledged at RabbitMQ");
                        });
    }

    private QueueCounts getQueueCounts(String queueName) throws Exception {
        org.testcontainers.containers.Container.ExecResult result =
                rabbitMQContainer.execInContainer(
                        "rabbitmqctl",
                        "list_queues",
                        "--quiet",
                        "name",
                        "messages_ready",
                        "messages_unacknowledged",
                        "consumers");
        assertEquals(0, result.getExitCode(), result.getStderr());
        for (String line : result.getStdout().split("\\R")) {
            String[] columns = line.trim().split("\\s+");
            if (columns.length == 4 && columns[0].equals(queueName)) {
                return new QueueCounts(
                        Integer.parseInt(columns[1]),
                        Integer.parseInt(columns[2]),
                        Integer.parseInt(columns[3]));
            }
        }
        throw new AssertionError("RabbitMQ did not report queue " + queueName);
    }

    private void closeBrokerConnection(String clientProvidedName) throws Exception {
        String connectionPid = getBrokerConnectionPid(clientProvidedName);
        org.testcontainers.containers.Container.ExecResult result =
                rabbitMQContainer.execInContainer(
                        "rabbitmqctl",
                        "close_connection",
                        connectionPid,
                        "test-triggered automatic recovery");
        assertEquals(0, result.getExitCode(), result.getStderr());
    }

    private void assertBrokerConnectionsClosed(List<String> clientProvidedNames) throws Exception {
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            for (String clientProvidedName : clientProvidedNames) {
                                assertFalse(
                                        hasBrokerConnection(clientProvidedName),
                                        "RabbitMQ still reports connection " + clientProvidedName);
                            }
                        });
    }

    private String getBrokerConnectionPid(String clientProvidedName) throws Exception {
        org.testcontainers.containers.Container.ExecResult result =
                rabbitMQContainer.execInContainer(
                        "rabbitmqctl", "list_connections", "--quiet", "pid", "client_properties");
        assertEquals(0, result.getExitCode(), result.getStderr());
        for (String line : result.getStdout().split("\\R")) {
            String[] columns = line.split("\\t", 2);
            if (columns.length == 2 && columns[1].contains(clientProvidedName)) {
                return columns[0];
            }
        }
        throw new AssertionError(
                "RabbitMQ did not report connection "
                        + clientProvidedName
                        + ": "
                        + result.getStdout());
    }

    private boolean hasBrokerConnection(String clientProvidedName) throws Exception {
        org.testcontainers.containers.Container.ExecResult result =
                rabbitMQContainer.execInContainer(
                        "rabbitmqctl", "list_connections", "--quiet", "client_properties");
        assertEquals(0, result.getExitCode(), result.getStderr());
        return result.getStdout().contains(clientProvidedName);
    }

    private void resetAMQPConnection() throws Exception {
        Field amqpConnectionField = AMQPConnection.class.getDeclaredField("amqpConnection");
        amqpConnectionField.setAccessible(true);
        AMQPConnection existing = (AMQPConnection) amqpConnectionField.get(null);
        if (existing != null) {
            existing.close();
        }
        AMQPConnection.setAMQPConnection(null);
    }

    private Connection getSubscriberConnection(AMQPObservableQueue queue) throws Exception {
        Field amqpConnectionField = AMQPObservableQueue.class.getDeclaredField("amqpConnection");
        amqpConnectionField.setAccessible(true);
        AMQPConnection amqpConnection = (AMQPConnection) amqpConnectionField.get(queue);

        Field subscriberConnectionField =
                AMQPConnection.class.getDeclaredField("subscriberConnection");
        subscriberConnectionField.setAccessible(true);
        return (Connection) subscriberConnectionField.get(amqpConnection);
    }

    private Connection getPublisherConnection(AMQPObservableQueue queue) throws Exception {
        Field amqpConnectionField = AMQPObservableQueue.class.getDeclaredField("amqpConnection");
        amqpConnectionField.setAccessible(true);
        AMQPConnection amqpConnection = (AMQPConnection) amqpConnectionField.get(queue);

        Field publisherConnectionField =
                AMQPConnection.class.getDeclaredField("publisherConnection");
        publisherConnectionField.setAccessible(true);
        return (Connection) publisherConnectionField.get(amqpConnection);
    }

    private record QueueCounts(int ready, int unacknowledged, int consumers) {}
}
