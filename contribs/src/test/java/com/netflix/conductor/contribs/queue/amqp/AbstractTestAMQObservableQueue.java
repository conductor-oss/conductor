package com.netflix.conductor.contribs.queue.amqp;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.queue.Message;
import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.AMQImpl;
import org.apache.commons.lang3.StringUtils;
import org.mockito.internal.stubbing.answers.DoesNothing;
import org.mockito.internal.stubbing.answers.ReturnsArgumentAt;
import org.mockito.internal.stubbing.answers.ReturnsElementsOf;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.OngoingStubbing;
import rx.Observable;
import rx.observers.Subscribers;
import rx.observers.TestSubscriber;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * Created at 26/03/2019 16:39
 *
 * @author Mickaël GREGORI <mickael.gregori@alchimie.com>
 * @version $Id$
 */
abstract class AbstractTestAMQObservableQueue {

    final int batchSize = 10;
    final int pollTimeMs = 500;

    Address[] addresses;
    Configuration configuration;

    AbstractTestAMQObservableQueue() {
        configuration = mock(Configuration.class);
        Answer answer = new ReturnsArgumentAt(1);
        when(configuration.getProperty(anyString(), anyString())).thenAnswer(answer);
        when(configuration.getBooleanProperty(anyString(), anyBoolean())).thenAnswer(answer);
        when(configuration.getIntProperty(anyString(), anyInt())).thenAnswer(answer);
        addresses = new Address[] { new Address("localhost", AMQP.PROTOCOL.PORT ) };
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


    Channel mockBaseChannel() throws IOException, TimeoutException {
        Channel channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            when(channel.isOpen()).thenReturn(Boolean.FALSE);
            return new DoesNothing();
        }).when(channel).close();
        return channel;
    }

    Channel mockChannelForQueue(Channel channel, boolean isWorking, boolean exists, String name,
                                        List<GetResponse> queue) throws IOException {
        // queueDeclarePassive
        final AMQImpl.Queue.DeclareOk queueDeclareOK = new AMQImpl.Queue.DeclareOk(name, queue.size(), 1);
        if (exists) {
            when(channel.queueDeclarePassive(eq(name))).thenReturn(queueDeclareOK);
        }
        else {
            when(channel.queueDeclarePassive(eq(name))).thenThrow(new IOException("Queue "+ name +" exists"));
        }
        // queueDeclare
        OngoingStubbing<AMQP.Queue.DeclareOk> declareOkOngoingStubbing = when(channel.queueDeclare(eq(name), anyBoolean(), anyBoolean(), anyBoolean(), anyMap()))
                .thenReturn(queueDeclareOK);
        if (!isWorking) {
            declareOkOngoingStubbing
                    .thenThrow(new IOException("Cannot declare queue "+ name), new RuntimeException("Not working"));
        }
        // messageCount
        when(channel.messageCount(eq(name))).thenReturn(1l * queue.size());
        // basicGet
        OngoingStubbing<GetResponse> getResponseOngoingStubbing = when(channel.basicGet(eq(name), anyBoolean()))
                .thenAnswer(new ReturnsElementsOf(queue));
        if (!isWorking) {
            getResponseOngoingStubbing
                    .thenThrow(new IOException("Not working"), new RuntimeException("Not working"));
        }
        // basicPublish
        if (isWorking) {
            doNothing().when(channel)
                    .basicPublish(eq(StringUtils.EMPTY), eq(name), any(AMQP.BasicProperties.class), any(byte[].class));
        }
        else {
            doThrow(new IOException("Not working")).when(channel)
                    .basicPublish(eq(StringUtils.EMPTY), eq(name), any(AMQP.BasicProperties.class), any(byte[].class));
        }
        return channel;
    }

    Channel mockChannelForExchange(Channel channel, boolean isWorking, boolean exists, String queueName,
                                   String name, String type, String routingKey, List<GetResponse> queue)
            throws IOException {
        // exchangeDeclarePassive
        final AMQImpl.Exchange.DeclareOk exchangeDeclareOK = new AMQImpl.Exchange.DeclareOk();
        if (exists) {
            when(channel.exchangeDeclarePassive(eq(name))).thenReturn(exchangeDeclareOK);
        }
        else {
            when(channel.exchangeDeclarePassive(eq(name))).thenThrow(new IOException("Exchange "+ name +" exists"));
        }
        // exchangeDeclare
        OngoingStubbing<AMQP.Exchange.DeclareOk> declareOkOngoingStubbing = when(channel.exchangeDeclare(eq(name),
                eq(type), anyBoolean(), anyBoolean(), anyMap()))
                .thenReturn(exchangeDeclareOK);
        if (!isWorking) {
            declareOkOngoingStubbing
                    .thenThrow(new IOException("Cannot declare exchange "+ name + " of type "+ type),
                            new RuntimeException("Not working"));
        }
        // queueDeclarePassive
        final AMQImpl.Queue.DeclareOk queueDeclareOK = new AMQImpl.Queue.DeclareOk(queueName, queue.size(), 1);
        if (exists) {
            when(channel.queueDeclarePassive(eq(queueName))).thenReturn(queueDeclareOK);
        }
        else {
            when(channel.queueDeclarePassive(eq(queueName))).thenThrow(new IOException("Queue "+ queueName +" exists"));
        }
        // queueDeclare
        when(channel.queueDeclare(eq(queueName), anyBoolean(), anyBoolean(), anyBoolean(), anyMap()))
                .thenReturn(queueDeclareOK);
        // queueBind
        when(channel.queueBind(eq(queueName), eq(name), eq(routingKey))).thenReturn(new AMQImpl.Queue.BindOk());
        // messageCount
        when(channel.messageCount(eq(name))).thenReturn(1l * queue.size());
        // basicGet
        OngoingStubbing<GetResponse> getResponseOngoingStubbing = when(channel.basicGet(eq(queueName), anyBoolean()))
                .thenAnswer(new ReturnsElementsOf(queue));
        if (!isWorking) {
            getResponseOngoingStubbing
                    .thenThrow(new IOException("Not working"), new RuntimeException("Not working"));
        }
        // basicPublish
        if (isWorking) {
            doNothing().when(channel)
                    .basicPublish(eq(name), eq(routingKey), any(AMQP.BasicProperties.class), any(byte[].class));
        }
        else {
            doThrow(new IOException("Not working")).when(channel)
                    .basicPublish(eq(name), eq(routingKey), any(AMQP.BasicProperties.class), any(byte[].class));
        }
        return channel;
    }

    Connection mockGoodConnection(Channel channel) throws IOException {
        Connection connection = mock(Connection.class);
        when(connection.createChannel()).thenReturn(channel);
        when(connection.isOpen()).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            when(connection.isOpen()).thenReturn(Boolean.FALSE);
            return new DoesNothing();
        }).when(connection).close();
        return connection;
    }

    Connection mockBadConnection() throws IOException {
        Connection connection = mock(Connection.class);
        when(connection.createChannel()).thenThrow(new IOException("Can't create channel"));
        when(connection.isOpen()).thenReturn(Boolean.TRUE);
        doThrow(new IOException("Can't close connection")).when(connection).close();
        return connection;
    }

    ConnectionFactory mockConnectionFactory(Connection connection) throws IOException, TimeoutException {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        when(connectionFactory.newConnection(eq(addresses))).thenReturn(connection);
        return connectionFactory;
    }

    void runObserve(Channel channel, AMQObservableQueue observableQueue, String queueName, boolean useWorkingChannel,
                            int batchSize) throws IOException {

        final List<Message> found = new ArrayList<>(batchSize);
        TestSubscriber<Message> subscriber = TestSubscriber.create(Subscribers.create(found::add));
        Observable<Message> observable = observableQueue.observe().take(pollTimeMs * 2, TimeUnit.MILLISECONDS);
        assertNotNull(observable);
        observable.subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        subscriber.assertNoErrors();
        subscriber.assertCompleted();
        if (useWorkingChannel) {
            verify(channel, atLeast(batchSize)).basicGet(eq(queueName), anyBoolean());
            doNothing().when(channel).basicAck(anyLong(), eq(false));
            doAnswer(new DoesNothing()).when(channel).basicAck(anyLong(), eq(false));
            observableQueue.ack(Collections.synchronizedList(found));
        }
        else {
            assertNotNull(found);
            assertTrue(found.isEmpty());
        }
        observableQueue.close();
    }
}
