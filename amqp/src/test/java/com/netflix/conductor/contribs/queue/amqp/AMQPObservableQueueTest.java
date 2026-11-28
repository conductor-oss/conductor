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

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.DoesNothing;
import org.mockito.stubbing.OngoingStubbing;

import com.netflix.conductor.contribs.queue.amqp.config.AMQPEventQueueProperties;
import com.netflix.conductor.contribs.queue.amqp.config.AMQPRetryPattern;
import com.netflix.conductor.contribs.queue.amqp.util.AMQPConstants;
import com.netflix.conductor.contribs.queue.amqp.util.AMQPSettings;
import com.netflix.conductor.contribs.queue.amqp.util.ConnectionType;
import com.netflix.conductor.contribs.queue.amqp.util.RetryType;
import com.netflix.conductor.core.events.queue.Message;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.PROTOCOL;
import com.rabbitmq.client.AMQP.Queue.DeclareOk;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.RecoverableConnection;
import com.rabbitmq.client.impl.AMQImpl;
import rx.Observable;
import rx.observers.Subscribers;
import rx.observers.TestSubscriber;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
public class AMQPObservableQueueTest {

    final int batchSize = 10;
    final int pollTimeMs = 500;

    Address[] addresses;
    AMQPEventQueueProperties properties;

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
        when(properties.getConnectionTimeoutInMilliSecs()).thenReturn(60000);
        when(properties.isUseNio()).thenReturn(false);
        when(properties.isDurable()).thenReturn(true);
        when(properties.isExclusive()).thenReturn(false);
        when(properties.isAutoDelete()).thenReturn(false);
        when(properties.getContentType()).thenReturn("application/json");
        when(properties.getContentEncoding()).thenReturn("UTF-8");
        when(properties.getExchangeType()).thenReturn("topic");
        when(properties.getDeliveryMode()).thenReturn(2);
        when(properties.isUseExchange()).thenReturn(true);
        addresses = new Address[] {new Address("localhost", PROTOCOL.PORT)};
        resetAMQPConnectionState();
    }

    private void resetAMQPConnectionState() {
        AMQPConnection.setAMQPConnection(null);
        clearStaticMap("availableChannelPool");
        clearStaticMap("subscriberReservedChannelPool");
        setStaticField("retrySettings", null);
    }

    private void clearStaticMap(String fieldName) {
        Object value = getStaticField(fieldName);
        if (value instanceof Map<?, ?> map) {
            map.clear();
        }
    }

    private void setStaticField(String fieldName, Object value) {
        try {
            Field field = AMQPConnection.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to reset AMQPConnection field " + fieldName, e);
        }
    }

    private Object getStaticField(String fieldName) {
        try {
            Field field = AMQPConnection.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to read AMQPConnection field " + fieldName, e);
        }
    }

    List<GetResponse> buildQueue(final Random random, final int bound) {
        final LinkedList<GetResponse> queue = new LinkedList();
        for (int i = 0; i < bound; i++) {
            AMQP.BasicProperties props = mock(AMQP.BasicProperties.class);
            when(props.getMessageId()).thenReturn(UUID.randomUUID().toString());
            Envelope envelope = mock(Envelope.class);
            when(envelope.getDeliveryTag()).thenReturn(random.nextLong());
            GetResponse response = mock(GetResponse.class);
            when(response.getProps()).thenReturn(props);
            when(response.getEnvelope()).thenReturn(envelope);
            when(response.getBody()).thenReturn("{}".getBytes());
            when(response.getMessageCount()).thenReturn(bound - i);
            queue.add(response);
        }
        return queue;
    }

    private Message createDeliveryMessage(AMQPSettings settings, long deliveryTag)
            throws Exception {
        AMQP.BasicProperties properties = mock(AMQP.BasicProperties.class);
        when(properties.getMessageId()).thenReturn("message-id");
        Envelope envelope = mock(Envelope.class);
        when(envelope.getDeliveryTag()).thenReturn(deliveryTag);
        GetResponse response = mock(GetResponse.class);
        when(response.getProps()).thenReturn(properties);
        when(response.getEnvelope()).thenReturn(envelope);
        when(response.getBody()).thenReturn("payload".getBytes());

        Method asMessage =
                AMQPObservableQueue.class.getDeclaredMethod(
                        "asMessage", AMQPSettings.class, GetResponse.class);
        asMessage.setAccessible(true);
        return (Message) asMessage.invoke(null, settings, response);
    }

    Channel mockBaseChannel() throws IOException, TimeoutException {
        Channel channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(Boolean.TRUE);
        /*
         * doAnswer(invocation -> { when(channel.isOpen()).thenReturn(Boolean.FALSE);
         * return DoesNothing.doesNothing(); }).when(channel).close();
         */
        return channel;
    }

    Channel mockChannelForQueue(
            Channel channel,
            boolean isWorking,
            boolean exists,
            String name,
            List<GetResponse> queue)
            throws IOException {
        // queueDeclarePassive
        final AMQImpl.Queue.DeclareOk queueDeclareOK =
                new AMQImpl.Queue.DeclareOk(name, queue.size(), 1);
        if (exists) {
            when(channel.queueDeclarePassive(eq(name))).thenReturn(queueDeclareOK);
        } else {
            when(channel.queueDeclarePassive(eq(name)))
                    .thenThrow(new IOException("Queue " + name + " exists"));
        }
        // queueDeclare
        OngoingStubbing<DeclareOk> declareOkOngoingStubbing =
                when(channel.queueDeclare(
                                eq(name), anyBoolean(), anyBoolean(), anyBoolean(), anyMap()))
                        .thenReturn(queueDeclareOK);
        if (!isWorking) {
            declareOkOngoingStubbing.thenThrow(
                    new IOException("Cannot declare queue " + name),
                    new RuntimeException("Not working"));
        }
        // messageCount
        when(channel.messageCount(eq(name))).thenReturn((long) queue.size());
        // basicGet
        OngoingStubbing<String> getResponseOngoingStubbing =
                Mockito.when(channel.basicConsume(eq(name), anyBoolean(), any(Consumer.class)))
                        .thenReturn(name);
        if (!isWorking) {
            getResponseOngoingStubbing.thenThrow(
                    new IOException("Not working"), new RuntimeException("Not working"));
        }
        // basicPublish
        if (isWorking) {
            doNothing()
                    .when(channel)
                    .basicPublish(
                            eq(StringUtils.EMPTY),
                            eq(name),
                            any(AMQP.BasicProperties.class),
                            any(byte[].class));
        } else {
            doThrow(new IOException("Not working"))
                    .when(channel)
                    .basicPublish(
                            eq(StringUtils.EMPTY),
                            eq(name),
                            any(AMQP.BasicProperties.class),
                            any(byte[].class));
        }
        return channel;
    }

    Channel mockChannelForExchange(
            Channel channel,
            boolean isWorking,
            boolean exists,
            String queueName,
            String name,
            String type,
            String routingKey,
            List<GetResponse> queue)
            throws IOException {
        // exchangeDeclarePassive
        final AMQImpl.Exchange.DeclareOk exchangeDeclareOK = new AMQImpl.Exchange.DeclareOk();
        if (exists) {
            when(channel.exchangeDeclarePassive(eq(name))).thenReturn(exchangeDeclareOK);
        } else {
            when(channel.exchangeDeclarePassive(eq(name)))
                    .thenThrow(new IOException("Exchange " + name + " exists"));
        }
        // exchangeDeclare
        OngoingStubbing<AMQP.Exchange.DeclareOk> declareOkOngoingStubbing =
                when(channel.exchangeDeclare(
                                eq(name), eq(type), anyBoolean(), anyBoolean(), anyMap()))
                        .thenReturn(exchangeDeclareOK);
        if (!isWorking) {
            declareOkOngoingStubbing.thenThrow(
                    new IOException("Cannot declare exchange " + name + " of type " + type),
                    new RuntimeException("Not working"));
        }
        // queueDeclarePassive
        final AMQImpl.Queue.DeclareOk queueDeclareOK =
                new AMQImpl.Queue.DeclareOk(queueName, queue.size(), 1);
        if (exists) {
            when(channel.queueDeclarePassive(eq(queueName))).thenReturn(queueDeclareOK);
        } else {
            when(channel.queueDeclarePassive(eq(queueName)))
                    .thenThrow(new IOException("Queue " + queueName + " exists"));
        }
        // queueDeclare
        when(channel.queueDeclare(
                        eq(queueName), anyBoolean(), anyBoolean(), anyBoolean(), anyMap()))
                .thenReturn(queueDeclareOK);
        // queueBind
        when(channel.queueBind(eq(queueName), eq(name), eq(routingKey)))
                .thenReturn(new AMQImpl.Queue.BindOk());
        // messageCount
        when(channel.messageCount(eq(queueName))).thenReturn((long) queue.size());
        // basicGet

        OngoingStubbing<String> getResponseOngoingStubbing =
                Mockito.when(channel.basicConsume(eq(queueName), anyBoolean(), any(Consumer.class)))
                        .thenReturn(queueName);

        if (!isWorking) {
            getResponseOngoingStubbing.thenThrow(
                    new IOException("Not working"), new RuntimeException("Not working"));
        }
        // basicPublish
        if (isWorking) {
            doNothing()
                    .when(channel)
                    .basicPublish(
                            eq(name),
                            eq(routingKey),
                            any(AMQP.BasicProperties.class),
                            any(byte[].class));
        } else {
            doThrow(new IOException("Not working"))
                    .when(channel)
                    .basicPublish(
                            eq(name),
                            eq(routingKey),
                            any(AMQP.BasicProperties.class),
                            any(byte[].class));
        }
        return channel;
    }

    Connection mockGoodConnection(Channel channel) throws IOException {
        Connection connection = mock(Connection.class);
        when(connection.createChannel()).thenReturn(channel);
        when(connection.isOpen()).thenReturn(Boolean.TRUE);
        /*
         * doAnswer(invocation -> { when(connection.isOpen()).thenReturn(Boolean.FALSE);
         * return DoesNothing.doesNothing(); }).when(connection).close();
         */ return connection;
    }

    Connection mockBadConnection() throws IOException {
        Connection connection = mock(Connection.class);
        when(connection.createChannel()).thenThrow(new IOException("Can't create channel"));
        when(connection.isOpen()).thenReturn(Boolean.TRUE);
        doThrow(new IOException("Can't close connection")).when(connection).close();
        return connection;
    }

    ConnectionFactory mockConnectionFactory(Connection connection)
            throws IOException, TimeoutException {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        when(connectionFactory.newConnection(eq(addresses), Mockito.anyString()))
                .thenReturn(connection);
        return connectionFactory;
    }

    void runObserve(
            Channel channel,
            AMQPObservableQueue observableQueue,
            String queueName,
            boolean useWorkingChannel,
            int batchSize)
            throws IOException {

        final List<Message> found = new ArrayList<>(batchSize);
        TestSubscriber<Message> subscriber = TestSubscriber.create(Subscribers.create(found::add));
        rx.Observable<Message> observable =
                observableQueue.observe().take(pollTimeMs * 2, TimeUnit.MILLISECONDS);
        assertNotNull(observable);
        observable.subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        subscriber.assertNoErrors();
        subscriber.assertCompleted();
        if (useWorkingChannel) {
            verify(channel, atLeast(1))
                    .basicConsume(eq(queueName), anyBoolean(), any(Consumer.class));
            doNothing().when(channel).basicAck(anyLong(), eq(false));
            doAnswer(DoesNothing.doesNothing()).when(channel).basicAck(anyLong(), eq(false));
            observableQueue.ack(Collections.synchronizedList(found));
        } else {
            assertNotNull(found);
            assertTrue(found.isEmpty());
        }
        observableQueue.close();
    }

    @Test
    public void
            testGetMessagesFromExistingExchangeWithDurableExclusiveAutoDeleteQueueConfiguration()
                    throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testGetMessagesFromExchangeAndCustomConfigurationFromURI(
                channel, connection, true, true, true, true, true);
    }

    @Test
    public void testGetMessagesFromExistingExchangeWithDefaultConfiguration()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testGetMessagesFromExchangeAndDefaultConfiguration(channel, connection, true, true);
    }

    @Test
    public void testPublishMessagesToNotExistingExchangeAndDefaultConfiguration()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testPublishMessagesToExchangeAndDefaultConfiguration(channel, connection, false, true);
    }

    @Test
    public void testAckOnExchangeIsSkipped() throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);

        final String name = RandomStringUtils.randomAlphabetic(30),
                type = "topic",
                routingKey = RandomStringUtils.randomAlphabetic(30);
        AMQPRetryPattern retrySettings = null;
        final AMQPSettings settings =
                new AMQPSettings(properties)
                        .fromURI(
                                "amqp_exchange:"
                                        + name
                                        + "?exchangeType="
                                        + type
                                        + "&routingKey="
                                        + routingKey);
        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        mockConnectionFactory(connection),
                        addresses,
                        true,
                        settings,
                        retrySettings,
                        batchSize,
                        pollTimeMs);
        List<Message> messages = new LinkedList<>();
        Message msg = new Message();
        msg.setId("0e3eef8f-ebb1-4244-9665-759ab5bdf433");
        msg.setPayload("Payload");
        msg.setReceipt("1");
        messages.add(msg);
        List<String> failedMessages = observableQueue.ack(messages);
        assertNotNull(failedMessages);
        assertTrue(failedMessages.isEmpty());
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    public void testAckOnQueueCallsBasicAck() throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);

        final String queueName = RandomStringUtils.randomAlphabetic(30);
        AMQPSettings settings = new AMQPSettings(properties).fromURI("amqp_queue:" + queueName);
        List<GetResponse> queue = buildQueue(new Random(), batchSize);
        channel = mockChannelForQueue(channel, true, true, queueName, queue);
        doNothing().when(channel).basicAck(anyLong(), eq(false));

        AMQPRetryPattern retrySettings = null;
        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        mockConnectionFactory(connection),
                        addresses,
                        false,
                        settings,
                        retrySettings,
                        batchSize,
                        pollTimeMs);
        List<Message> messages = new LinkedList<>();
        Message msg = new Message();
        msg.setId("0e3eef8f-ebb1-4244-9665-759ab5bdf433");
        msg.setPayload("Payload");
        msg.setReceipt("1");
        messages.add(msg);
        List<String> failedMessages = observableQueue.ack(messages);
        assertNotNull(failedMessages);
        assertTrue(failedMessages.isEmpty());
        verify(channel, times(1)).basicAck(1L, false);
    }

    @Test
    public void testAckAndNackUseTheOriginalDeliveryChannel() throws Exception {
        String queueName = RandomStringUtils.randomAlphabetic(30);
        AMQPSettings settings = new AMQPSettings(properties).fromURI("amqp_queue:" + queueName);
        Channel originalChannel = mockBaseChannel();
        Channel replacementChannel = mockBaseChannel();
        Connection subscriberConnection = mockGoodConnection(replacementChannel);
        ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.newConnection(eq(addresses), Mockito.endsWith("Subscriber")))
                .thenReturn(subscriberConnection);

        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        factory, addresses, false, settings, null, batchSize, pollTimeMs);
        try {
            AMQPConnection.getInstance(factory, addresses, null)
                    .getOrCreateChannel(ConnectionType.SUBSCRIBER, queueName);

            Message ackMessage = createDeliveryMessage(settings, 1);
            Message nackMessage = createDeliveryMessage(settings, 2);
            observableQueue.registerDeliveryChannel(ackMessage, originalChannel);
            observableQueue.registerDeliveryChannel(nackMessage, originalChannel);

            Message equalMessage = new Message("message-id", "payload", "1");
            assertEquals(Message.class, ackMessage.getClass());
            assertEquals(ackMessage, equalMessage);
            assertEquals(ackMessage.hashCode(), equalMessage.hashCode());
            assertEquals(2, observableQueue.pendingDeliveryCount());

            assertTrue(observableQueue.ack(Collections.singletonList(ackMessage)).isEmpty());
            assertEquals(1, observableQueue.pendingDeliveryCount());
            assertEquals(
                    Collections.singletonList("1"),
                    observableQueue.ack(Collections.singletonList(ackMessage)));
            observableQueue.nack(Collections.singletonList(nackMessage));
            observableQueue.nack(Collections.singletonList(nackMessage));
            assertEquals(0, observableQueue.pendingDeliveryCount());

            verify(originalChannel, times(1)).basicAck(1L, false);
            verify(originalChannel, times(1)).basicNack(2L, false, false);
            verify(replacementChannel, never()).basicAck(anyLong(), anyBoolean());
            verify(replacementChannel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        } finally {
            observableQueue.close();
        }
    }

    @Test
    public void testDeliveryMetadataUsesIdentityForEqualMessages() {
        Message first = new Message("same-id", "same-payload", "same-receipt");
        Message second = new Message("same-id", "same-payload", "same-receipt");
        Channel firstChannel = mock(Channel.class);
        Channel secondChannel = mock(Channel.class);
        AMQPDeliveryChannelRegistry registry = new AMQPDeliveryChannelRegistry();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        registry.register(first, firstChannel);
        registry.register(second, secondChannel);

        assertSame(firstChannel, registry.get(first).channel());
        assertSame(secondChannel, registry.get(second).channel());
    }

    @Test
    public void testNackRetryExhaustionKeepsDeliveryChannelAndReturns() throws Exception {
        String queueName = RandomStringUtils.randomAlphabetic(30);
        AMQPSettings settings = new AMQPSettings(properties).fromURI("amqp_queue:" + queueName);
        Channel originalChannel = mockBaseChannel();
        Channel replacementChannel = mockBaseChannel();
        Connection subscriberConnection = mockGoodConnection(replacementChannel);
        ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.newConnection(eq(addresses), Mockito.endsWith("Subscriber")))
                .thenReturn(subscriberConnection);

        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        factory,
                        addresses,
                        false,
                        settings,
                        new AMQPRetryPattern(1, 0, RetryType.REGULARINTERVALS),
                        batchSize,
                        pollTimeMs);
        try {
            Message message = createDeliveryMessage(settings, 3);
            observableQueue.registerDeliveryChannel(message, originalChannel);
            doThrow(new IOException("NACK failed"))
                    .when(originalChannel)
                    .basicNack(3L, false, false);

            observableQueue.nack(Collections.singletonList(message));

            verify(originalChannel, times(2)).basicNack(3L, false, false);
            verify(replacementChannel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
            assertEquals(1, observableQueue.pendingDeliveryCount());
        } finally {
            observableQueue.close();
        }
    }

    @Test
    public void testNackFailureWithoutRetryKeepsOriginalDeliveryChannel() throws Exception {
        String queueName = RandomStringUtils.randomAlphabetic(30);
        AMQPSettings settings = new AMQPSettings(properties).fromURI("amqp_queue:" + queueName);
        Channel originalChannel = mockBaseChannel();
        Channel replacementChannel = mockBaseChannel();
        Connection subscriberConnection = mockGoodConnection(replacementChannel);
        ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.newConnection(eq(addresses), Mockito.endsWith("Subscriber")))
                .thenReturn(subscriberConnection);

        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        factory, addresses, false, settings, null, batchSize, pollTimeMs);
        try {
            Message message = createDeliveryMessage(settings, 7);
            observableQueue.registerDeliveryChannel(message, originalChannel);
            doThrow(new IOException("NACK failed"))
                    .doNothing()
                    .when(originalChannel)
                    .basicNack(7L, false, false);

            observableQueue.nack(Collections.singletonList(message));

            assertEquals(1, observableQueue.pendingDeliveryCount());
            observableQueue.nack(Collections.singletonList(message));

            verify(originalChannel, times(2)).basicNack(7L, false, false);
            verify(replacementChannel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
            assertEquals(0, observableQueue.pendingDeliveryCount());
        } finally {
            observableQueue.close();
        }
    }

    @Test
    public void testAckRetryUsesOriginalDeliveryChannel() throws Exception {
        String queueName = RandomStringUtils.randomAlphabetic(30);
        AMQPSettings settings = new AMQPSettings(properties).fromURI("amqp_queue:" + queueName);
        Channel originalChannel = mockBaseChannel();
        Channel replacementChannel = mockBaseChannel();
        Connection subscriberConnection = mockGoodConnection(replacementChannel);
        ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.newConnection(eq(addresses), Mockito.endsWith("Subscriber")))
                .thenReturn(subscriberConnection);

        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        factory,
                        addresses,
                        false,
                        settings,
                        new AMQPRetryPattern(1, 0, RetryType.REGULARINTERVALS),
                        batchSize,
                        pollTimeMs);
        try {
            Message message = createDeliveryMessage(settings, 6);
            observableQueue.registerDeliveryChannel(message, originalChannel);
            doThrow(new IOException("ACK retry"))
                    .doNothing()
                    .when(originalChannel)
                    .basicAck(6L, false);

            assertTrue(observableQueue.ack(Collections.singletonList(message)).isEmpty());

            verify(originalChannel, times(2)).basicAck(6L, false);
            verify(replacementChannel, never()).basicAck(anyLong(), anyBoolean());
            assertEquals(0, observableQueue.pendingDeliveryCount());
        } finally {
            observableQueue.close();
        }
    }

    @Test
    public void testQueueSamplingCompletesWhileAckIsInProgress() throws Exception {
        String queueName = RandomStringUtils.randomAlphabetic(30);
        AMQPSettings settings = new AMQPSettings(properties).fromURI("amqp_queue:" + queueName);
        Channel originalChannel = mockBaseChannel();
        Channel publisherChannel = mockBaseChannel();
        when(publisherChannel.messageCount(eq(queueName))).thenReturn(0L);
        Connection publisherConnection = mockGoodConnection(publisherChannel);
        ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.newConnection(eq(addresses), Mockito.endsWith("Publisher")))
                .thenReturn(publisherConnection);

        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        factory, addresses, false, settings, null, batchSize, pollTimeMs);
        CountDownLatch ackStarted = new CountDownLatch(1);
        CountDownLatch releaseAck = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Message message = createDeliveryMessage(settings, 4);
            observableQueue.registerDeliveryChannel(message, originalChannel);
            doAnswer(
                            invocation -> {
                                ackStarted.countDown();
                                assertTrue(
                                        "ACK was not released",
                                        releaseAck.await(5, TimeUnit.SECONDS));
                                return null;
                            })
                    .when(originalChannel)
                    .basicAck(4L, false);

            Future<List<String>> acknowledgement =
                    executor.submit(() -> observableQueue.ack(Collections.singletonList(message)));
            assertTrue("ACK did not enter the channel", ackStarted.await(5, TimeUnit.SECONDS));

            assertEquals(0, observableQueue.size());
            verify(publisherChannel).messageCount(queueName);
            verify(originalChannel, never()).messageCount(queueName);

            releaseAck.countDown();
            assertTrue(acknowledgement.get(5, TimeUnit.SECONDS).isEmpty());
            verify(originalChannel).basicAck(4L, false);
        } finally {
            releaseAck.countDown();
            executor.shutdownNow();
            observableQueue.close();
        }
    }

    @Test
    public void testQueueSamplingCompletesWhileNackIsInProgress() throws Exception {
        String queueName = RandomStringUtils.randomAlphabetic(30);
        AMQPSettings settings = new AMQPSettings(properties).fromURI("amqp_queue:" + queueName);
        Channel originalChannel = mockBaseChannel();
        Channel publisherChannel = mockBaseChannel();
        when(publisherChannel.messageCount(eq(queueName))).thenReturn(0L);
        Connection publisherConnection = mockGoodConnection(publisherChannel);
        ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.newConnection(eq(addresses), Mockito.endsWith("Publisher")))
                .thenReturn(publisherConnection);

        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        factory, addresses, false, settings, null, batchSize, pollTimeMs);
        CountDownLatch nackStarted = new CountDownLatch(1);
        CountDownLatch releaseNack = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Message message = createDeliveryMessage(settings, 5);
            observableQueue.registerDeliveryChannel(message, originalChannel);
            doAnswer(
                            invocation -> {
                                nackStarted.countDown();
                                assertTrue(
                                        "NACK was not released",
                                        releaseNack.await(5, TimeUnit.SECONDS));
                                return null;
                            })
                    .when(originalChannel)
                    .basicNack(5L, false, false);

            Future<?> acknowledgement =
                    executor.submit(() -> observableQueue.nack(Collections.singletonList(message)));
            assertTrue("NACK did not enter the channel", nackStarted.await(5, TimeUnit.SECONDS));

            assertEquals(0, observableQueue.size());
            verify(publisherChannel).messageCount(queueName);
            verify(originalChannel, never()).messageCount(queueName);

            releaseNack.countDown();
            acknowledgement.get(5, TimeUnit.SECONDS);
            verify(originalChannel).basicNack(5L, false, false);
            assertEquals(0, observableQueue.pendingDeliveryCount());
        } finally {
            releaseNack.countDown();
            executor.shutdownNow();
            observableQueue.close();
        }
    }

    @Test
    public void testQueueDepthSamplingDoesNotReplaceRecoveringSubscriberChannel() throws Exception {
        String queueName = RandomStringUtils.randomAlphabetic(30);
        AMQPSettings settings = new AMQPSettings(properties).fromURI("amqp_queue:" + queueName);
        Channel subscriberChannel = mockBaseChannel();
        when(subscriberChannel.isOpen()).thenReturn(true, false);
        Channel replacementSubscriberChannel = mockBaseChannel();
        Channel publisherChannel = mockBaseChannel();
        when(publisherChannel.messageCount(eq(queueName))).thenReturn(0L);
        Connection subscriberConnection = mockGoodConnection(subscriberChannel);
        when(subscriberConnection.createChannel())
                .thenReturn(subscriberChannel, replacementSubscriberChannel);
        Connection publisherConnection = mockGoodConnection(publisherChannel);
        ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.newConnection(eq(addresses), Mockito.endsWith("Subscriber")))
                .thenReturn(subscriberConnection);
        when(factory.newConnection(eq(addresses), Mockito.endsWith("Publisher")))
                .thenReturn(publisherConnection);

        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        factory, addresses, false, settings, null, batchSize, pollTimeMs);
        try {
            AMQPConnection.getInstance(factory, addresses, null)
                    .getOrCreateChannel(ConnectionType.SUBSCRIBER, queueName);

            assertEquals(0, observableQueue.size());

            verify(subscriberConnection, times(1)).createChannel();
            verify(publisherChannel).messageCount(queueName);
            verify(subscriberChannel, never()).messageCount(queueName);
        } finally {
            observableQueue.close();
        }
    }

    @Test
    public void testCloseClosesRecoverableConnectionsEvenWhenNotOpen() throws Exception {
        Channel subscriberChannel = mockBaseChannel();
        Channel publisherChannel = mockBaseChannel();
        RecoverableConnection subscriberConnection = mock(RecoverableConnection.class);
        RecoverableConnection publisherConnection = mock(RecoverableConnection.class);
        when(subscriberConnection.isOpen()).thenReturn(true, false);
        when(publisherConnection.isOpen()).thenReturn(true, true);
        when(subscriberConnection.createChannel()).thenReturn(subscriberChannel);
        when(publisherConnection.createChannel()).thenReturn(publisherChannel);
        ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.newConnection(eq(addresses), Mockito.endsWith("Subscriber")))
                .thenReturn(subscriberConnection);
        when(factory.newConnection(eq(addresses), Mockito.endsWith("Publisher")))
                .thenReturn(publisherConnection);

        AMQPConnection connection = AMQPConnection.getInstance(factory, addresses, null);
        connection.getOrCreateChannel(ConnectionType.SUBSCRIBER, "subscriber-queue");
        connection.getOrCreateChannel(ConnectionType.PUBLISHER, "publisher-queue");

        connection.close();
        connection.close();

        verify(subscriberConnection, times(1)).abort();
        verify(subscriberConnection, never()).close();
        verify(publisherConnection, times(1)).close();
        verify(publisherConnection, never()).abort();
    }

    private void testGetMessagesFromExchangeAndDefaultConfiguration(
            Channel channel, Connection connection, boolean exists, boolean useWorkingChannel)
            throws IOException, TimeoutException {

        final Random random = new Random();

        final String name = RandomStringUtils.randomAlphabetic(30),
                type = "topic",
                routingKey = RandomStringUtils.randomAlphabetic(30);
        final String queueName = String.format("bound_to_%s", name);

        final AMQPSettings settings =
                new AMQPSettings(properties)
                        .fromURI(
                                "amqp_exchange:"
                                        + name
                                        + "?exchangeType="
                                        + type
                                        + "&routingKey="
                                        + routingKey);
        assertTrue(settings.isDurable());
        assertFalse(settings.isExclusive());
        assertFalse(settings.autoDelete());
        assertEquals(2, settings.getDeliveryMode());
        assertEquals(name, settings.getQueueOrExchangeName());
        assertEquals(type, settings.getExchangeType());
        assertEquals(routingKey, settings.getRoutingKey());
        assertEquals(queueName, settings.getExchangeBoundQueueName());

        List<GetResponse> queue = buildQueue(random, batchSize);
        channel =
                mockChannelForExchange(
                        channel,
                        useWorkingChannel,
                        exists,
                        queueName,
                        name,
                        type,
                        routingKey,
                        queue);
        AMQPRetryPattern retrySettings = null;
        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        mockConnectionFactory(connection),
                        addresses,
                        true,
                        settings,
                        retrySettings,
                        batchSize,
                        pollTimeMs);

        assertArrayEquals(addresses, observableQueue.getAddresses());
        assertEquals(AMQPConstants.AMQP_EXCHANGE_TYPE, observableQueue.getType());
        assertEquals(
                AMQPConstants.AMQP_EXCHANGE_TYPE
                        + ":"
                        + name
                        + "?exchangeType="
                        + type
                        + "&routingKey="
                        + routingKey,
                observableQueue.getName());
        assertEquals(name, observableQueue.getURI());
        assertEquals(batchSize, observableQueue.getBatchSize());
        assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
        assertEquals(queue.size(), observableQueue.size());

        runObserve(channel, observableQueue, queueName, useWorkingChannel, batchSize);

        if (useWorkingChannel) {
            verify(channel, atLeastOnce())
                    .exchangeDeclare(
                            eq(name),
                            eq(type),
                            eq(settings.isDurable()),
                            eq(settings.autoDelete()),
                            eq(Collections.emptyMap()));
            verify(channel, atLeastOnce())
                    .queueDeclare(
                            eq(queueName),
                            eq(settings.isDurable()),
                            eq(settings.isExclusive()),
                            eq(settings.autoDelete()),
                            anyMap());

            verify(channel, atLeastOnce()).queueBind(eq(queueName), eq(name), eq(routingKey));
        }
    }

    private void testGetMessagesFromExchangeAndCustomConfigurationFromURI(
            Channel channel,
            Connection connection,
            boolean exists,
            boolean useWorkingChannel,
            boolean durable,
            boolean exclusive,
            boolean autoDelete)
            throws IOException, TimeoutException {

        final Random random = new Random();

        final String name = RandomStringUtils.randomAlphabetic(30),
                type = "topic",
                routingKey = RandomStringUtils.randomAlphabetic(30);
        final String queueName = String.format("bound_to_%s", name);

        final AMQPSettings settings =
                new AMQPSettings(properties)
                        .fromURI(
                                "amqp_exchange:"
                                        + name
                                        + "?exchangeType="
                                        + type
                                        + "&bindQueueName="
                                        + queueName
                                        + "&routingKey="
                                        + routingKey
                                        + "&deliveryMode=2"
                                        + "&durable="
                                        + durable
                                        + "&exclusive="
                                        + exclusive
                                        + "&autoDelete="
                                        + autoDelete);
        assertEquals(durable, settings.isDurable());
        assertEquals(exclusive, settings.isExclusive());
        assertEquals(autoDelete, settings.autoDelete());
        assertEquals(2, settings.getDeliveryMode());
        assertEquals(name, settings.getQueueOrExchangeName());
        assertEquals(type, settings.getExchangeType());
        assertEquals(queueName, settings.getExchangeBoundQueueName());
        assertEquals(routingKey, settings.getRoutingKey());

        List<GetResponse> queue = buildQueue(random, batchSize);
        channel =
                mockChannelForExchange(
                        channel,
                        useWorkingChannel,
                        exists,
                        queueName,
                        name,
                        type,
                        routingKey,
                        queue);
        AMQPRetryPattern retrySettings = null;
        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        mockConnectionFactory(connection),
                        addresses,
                        true,
                        settings,
                        retrySettings,
                        batchSize,
                        pollTimeMs);

        assertArrayEquals(addresses, observableQueue.getAddresses());
        assertEquals(AMQPConstants.AMQP_EXCHANGE_TYPE, observableQueue.getType());
        assertEquals(
                AMQPConstants.AMQP_EXCHANGE_TYPE
                        + ":"
                        + name
                        + "?exchangeType="
                        + type
                        + "&bindQueueName="
                        + queueName
                        + "&routingKey="
                        + routingKey
                        + "&deliveryMode=2"
                        + "&durable="
                        + durable
                        + "&exclusive="
                        + exclusive
                        + "&autoDelete="
                        + autoDelete,
                observableQueue.getName());
        assertEquals(name, observableQueue.getURI());
        assertEquals(batchSize, observableQueue.getBatchSize());
        assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
        assertEquals(queue.size(), observableQueue.size());

        runObserve(channel, observableQueue, queueName, useWorkingChannel, batchSize);

        if (useWorkingChannel) {
            verify(channel, atLeastOnce())
                    .exchangeDeclare(
                            eq(name),
                            eq(type),
                            eq(settings.isDurable()),
                            eq(settings.autoDelete()),
                            eq(Collections.emptyMap()));
            verify(channel, atLeastOnce())
                    .queueDeclare(
                            eq(queueName),
                            eq(settings.isDurable()),
                            eq(settings.isExclusive()),
                            eq(settings.autoDelete()),
                            anyMap());

            verify(channel, atLeastOnce()).queueBind(eq(queueName), eq(name), eq(routingKey));
        }
    }

    private void testPublishMessagesToExchangeAndDefaultConfiguration(
            Channel channel, Connection connection, boolean exists, boolean useWorkingChannel)
            throws IOException, TimeoutException {
        final Random random = new Random();

        final String name = RandomStringUtils.randomAlphabetic(30),
                type = "topic",
                routingKey = RandomStringUtils.randomAlphabetic(30);

        final String queueName = String.format("bound_to_%s", name);

        final AMQPSettings settings =
                new AMQPSettings(properties)
                        .fromURI(
                                "amqp_exchange:"
                                        + name
                                        + "?exchangeType="
                                        + type
                                        + "&routingKey="
                                        + routingKey
                                        + "&deliveryMode=2&durable=true&exclusive=false&autoDelete=true");
        assertTrue(settings.isDurable());
        assertFalse(settings.isExclusive());
        assertTrue(settings.autoDelete());
        assertEquals(2, settings.getDeliveryMode());
        assertEquals(name, settings.getQueueOrExchangeName());
        assertEquals(type, settings.getExchangeType());
        assertEquals(routingKey, settings.getRoutingKey());

        List<GetResponse> queue = buildQueue(random, batchSize);
        channel =
                mockChannelForExchange(
                        channel,
                        useWorkingChannel,
                        exists,
                        queueName,
                        name,
                        type,
                        routingKey,
                        queue);
        AMQPRetryPattern retrySettings = null;
        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        mockConnectionFactory(connection),
                        addresses,
                        true,
                        settings,
                        retrySettings,
                        batchSize,
                        pollTimeMs);

        assertArrayEquals(addresses, observableQueue.getAddresses());
        assertEquals(AMQPConstants.AMQP_EXCHANGE_TYPE, observableQueue.getType());
        assertEquals(
                AMQPConstants.AMQP_EXCHANGE_TYPE
                        + ":"
                        + name
                        + "?exchangeType="
                        + type
                        + "&routingKey="
                        + routingKey
                        + "&deliveryMode=2&durable=true&exclusive=false&autoDelete=true",
                observableQueue.getName());
        assertEquals(name, observableQueue.getURI());
        assertEquals(batchSize, observableQueue.getBatchSize());
        assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
        assertEquals(queue.size(), observableQueue.size());

        List<Message> messages = new LinkedList<>();
        Observable.range(0, batchSize)
                .forEach((Integer x) -> messages.add(new Message("" + x, "payload: " + x, null)));
        assertEquals(batchSize, messages.size());
        observableQueue.publish(messages);

        if (useWorkingChannel) {
            verify(channel, times(batchSize))
                    .basicPublish(
                            eq(name),
                            eq(routingKey),
                            any(AMQP.BasicProperties.class),
                            any(byte[].class));
        }
    }

    @Test
    public void testGetMessagesFromExistingQueueAndDefaultConfiguration()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testGetMessagesFromQueueAndDefaultConfiguration(channel, connection, true, true);
    }

    @Test
    public void testGetMessagesFromNotExistingQueueAndDefaultConfiguration()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testGetMessagesFromQueueAndDefaultConfiguration(channel, connection, false, true);
    }

    @Test
    public void testGetMessagesFromQueueWithBadChannel() throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testGetMessagesFromQueueAndDefaultConfiguration(channel, connection, true, false);
    }

    @Test(expected = RuntimeException.class)
    public void testPublishMessagesToQueueWithBadChannel() throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testPublishMessagesToQueueAndDefaultConfiguration(channel, connection, true, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAMQPObservalbleQueue_empty() throws IOException, TimeoutException {
        AMQPSettings settings = new AMQPSettings(properties).fromURI("amqp_queue:test");
        AMQPRetryPattern retrySettings = null;
        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        null, addresses, false, settings, retrySettings, batchSize, pollTimeMs);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAMQPObservalbleQueue_addressEmpty() throws IOException, TimeoutException {
        AMQPSettings settings = new AMQPSettings(properties).fromURI("amqp_queue:test");
        AMQPRetryPattern retrySettings = null;
        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        mockConnectionFactory(mockGoodConnection(mockBaseChannel())),
                        null,
                        false,
                        settings,
                        retrySettings,
                        batchSize,
                        pollTimeMs);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAMQPObservalbleQueue_settingsEmpty() throws IOException, TimeoutException {
        AMQPRetryPattern retrySettings = null;
        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        mockConnectionFactory(mockGoodConnection(mockBaseChannel())),
                        addresses,
                        false,
                        null,
                        retrySettings,
                        batchSize,
                        pollTimeMs);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAMQPObservalbleQueue_batchsizezero() throws IOException, TimeoutException {
        AMQPSettings settings = new AMQPSettings(properties).fromURI("amqp_queue:test");
        AMQPRetryPattern retrySettings = null;
        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        mockConnectionFactory(mockGoodConnection(mockBaseChannel())),
                        addresses,
                        false,
                        settings,
                        retrySettings,
                        0,
                        pollTimeMs);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAMQPObservalbleQueue_polltimezero() throws IOException, TimeoutException {
        AMQPSettings settings = new AMQPSettings(properties).fromURI("amqp_queue:test");
        AMQPRetryPattern retrySettings = null;
        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        mockConnectionFactory(mockGoodConnection(mockBaseChannel())),
                        addresses,
                        false,
                        settings,
                        retrySettings,
                        batchSize,
                        0);
    }

    @Test
    public void testclosetExistingQueueAndDefaultConfiguration()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testGetMessagesFromQueueAndDefaultConfiguration_close(channel, connection, false, true);
    }

    private void testGetMessagesFromQueueAndDefaultConfiguration(
            Channel channel, Connection connection, boolean queueExists, boolean useWorkingChannel)
            throws IOException, TimeoutException {
        final Random random = new Random();

        final String queueName = RandomStringUtils.randomAlphabetic(30);
        AMQPSettings settings = new AMQPSettings(properties).fromURI("amqp_queue:" + queueName);

        List<GetResponse> queue = buildQueue(random, batchSize);
        channel = mockChannelForQueue(channel, useWorkingChannel, queueExists, queueName, queue);
        AMQPRetryPattern retrySettings = null;
        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        mockConnectionFactory(connection),
                        addresses,
                        false,
                        settings,
                        retrySettings,
                        batchSize,
                        pollTimeMs);

        assertArrayEquals(addresses, observableQueue.getAddresses());
        assertEquals(AMQPConstants.AMQP_QUEUE_TYPE, observableQueue.getType());
        assertEquals(AMQPConstants.AMQP_QUEUE_TYPE + ":" + queueName, observableQueue.getName());
        assertEquals(queueName, observableQueue.getURI());
        assertEquals(batchSize, observableQueue.getBatchSize());
        assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
        assertEquals(queue.size(), observableQueue.size());

        runObserve(channel, observableQueue, queueName, useWorkingChannel, batchSize);
    }

    private void testGetMessagesFromQueueAndDefaultConfiguration_close(
            Channel channel, Connection connection, boolean queueExists, boolean useWorkingChannel)
            throws IOException, TimeoutException {
        final Random random = new Random();

        final String queueName = RandomStringUtils.randomAlphabetic(30);
        AMQPSettings settings = new AMQPSettings(properties).fromURI("amqp_queue:" + queueName);

        List<GetResponse> queue = buildQueue(random, batchSize);
        channel = mockChannelForQueue(channel, useWorkingChannel, queueExists, queueName, queue);
        AMQPRetryPattern retrySettings = null;
        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        mockConnectionFactory(connection),
                        addresses,
                        false,
                        settings,
                        retrySettings,
                        batchSize,
                        pollTimeMs);
        observableQueue.close();
        assertArrayEquals(addresses, observableQueue.getAddresses());
        assertEquals(AMQPConstants.AMQP_QUEUE_TYPE, observableQueue.getType());
        assertEquals(AMQPConstants.AMQP_QUEUE_TYPE + ":" + queueName, observableQueue.getName());
        assertEquals(queueName, observableQueue.getURI());
        assertEquals(batchSize, observableQueue.getBatchSize());
        assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
        assertEquals(queue.size(), observableQueue.size());
    }

    private void testPublishMessagesToQueueAndDefaultConfiguration(
            Channel channel, Connection connection, boolean queueExists, boolean useWorkingChannel)
            throws IOException, TimeoutException {
        final Random random = new Random();

        final String queueName = RandomStringUtils.randomAlphabetic(30);
        final AMQPSettings settings =
                new AMQPSettings(properties)
                        .fromURI(
                                "amqp_queue:"
                                        + queueName
                                        + "?deliveryMode=2&durable=true&exclusive=false&autoDelete=true");
        assertTrue(settings.isDurable());
        assertFalse(settings.isExclusive());
        assertTrue(settings.autoDelete());
        assertEquals(2, settings.getDeliveryMode());

        List<GetResponse> queue = buildQueue(random, batchSize);
        channel = mockChannelForQueue(channel, useWorkingChannel, queueExists, queueName, queue);
        AMQPRetryPattern retrySettings = new AMQPRetryPattern(3, 5, RetryType.REGULARINTERVALS);
        AMQPObservableQueue observableQueue =
                new AMQPObservableQueue(
                        mockConnectionFactory(connection),
                        addresses,
                        false,
                        settings,
                        retrySettings,
                        batchSize,
                        pollTimeMs);

        assertArrayEquals(addresses, observableQueue.getAddresses());
        assertEquals(AMQPConstants.AMQP_QUEUE_TYPE, observableQueue.getType());
        assertEquals(
                AMQPConstants.AMQP_QUEUE_TYPE
                        + ":"
                        + queueName
                        + "?deliveryMode=2&durable=true&exclusive=false&autoDelete=true",
                observableQueue.getName());
        assertEquals(queueName, observableQueue.getURI());
        assertEquals(batchSize, observableQueue.getBatchSize());
        assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
        assertEquals(queue.size(), observableQueue.size());

        List<Message> messages = new LinkedList<>();
        Observable.range(0, batchSize)
                .forEach((Integer x) -> messages.add(new Message("" + x, "payload: " + x, null)));
        assertEquals(batchSize, messages.size());
        observableQueue.publish(messages);

        if (useWorkingChannel) {
            verify(channel, times(batchSize))
                    .basicPublish(
                            eq(StringUtils.EMPTY),
                            eq(queueName),
                            any(AMQP.BasicProperties.class),
                            any(byte[].class));
        }
    }
}
