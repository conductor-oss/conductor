package com.netflix.conductor.contribs.queue.amqp;

import com.netflix.conductor.core.events.queue.Message;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import rx.Observable;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * Created at 21/03/2019 16:22
 *
 * @author Mickaël GREGORI <mickael.gregori@alchimie.com>
 * @version $Id$
 */
public class TestExchangeAMQObservableQueue extends AbstractTestAMQObservableQueue {

    @Test
    public void testGetMessagesFromExistingExchangeAndDefaultConfiguration()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testGetMessagesFromExchangeAndDefaultConfiguration(channel, connection,true, true);
    }

    @Test
    public void testGetMessagesFromNotExistingExchangeAndDefaultConfiguration()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testGetMessagesFromExchangeAndDefaultConfiguration(channel, connection,false, true);
    }

    @Test
    public void testPublishMessagesToNotExistingExchangeAndDefaultConfiguration()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testPublishMessagesToExchangeAndDefaultConfiguration(channel, connection,false, true);
    }

    @Test(expected = RuntimeException.class)
    public void testGetMessagesFromExchangeWithBadConnection()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockBadConnection();
        testGetMessagesFromExchangeAndDefaultConfiguration(channel, connection, true, true);
    }

    @Test(expected = RuntimeException.class)
    public void testPublishMessagesToExchangeWithBadConnection()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockBadConnection();
        testPublishMessagesToExchangeAndDefaultConfiguration(channel, connection, true, true);
    }

    @Test
    public void testGetMessagesFromExchangeWithBadChannel()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testGetMessagesFromExchangeAndDefaultConfiguration(channel, connection, true, false);
    }

    @Test(expected = RuntimeException.class)
    public void testPublishMessagesToExchangeWithBadChannel()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testPublishMessagesToExchangeAndDefaultConfiguration(channel, connection, true, false);
    }

    private void testGetMessagesFromExchangeAndDefaultConfiguration(Channel channel, Connection connection,
                                                                 boolean exists, boolean useWorkingChannel)
            throws IOException, TimeoutException {

        final Random random = new Random();

        final String name = RandomStringUtils.randomAlphabetic(30), type = "topic",
                routingKey = RandomStringUtils.randomAlphabetic(30);
        final String queueName = String.format("bound_to_%s", name);

        final AMQSettings settings = new AMQSettings(configuration)
                .fromURI("amqp-exchange:" + name +"?exchangeType="+ type +"&routingKey="+ routingKey
                        +"&deliveryMode=2&durable=true&exclusive=false&autoDelete=true");
        assertEquals(true, settings.isDurable());
        assertEquals(false, settings.isExclusive());
        assertEquals(true, settings.autoDelete());
        assertEquals(2, settings.getDeliveryMode());
        assertEquals(name, settings.getQueueOrExchangeName());
        assertEquals(type, settings.getExchangeType());
        assertEquals(routingKey, settings.getRoutingKey());

        List<GetResponse> queue = buildQueue(random, batchSize);
        channel = mockChannelForExchange(channel, useWorkingChannel, exists, queueName,
                name, type, routingKey, queue);

        AMQObservableQueue observableQueue = new AMQObservableQueue(
                mockConnectionFactory(connection),
                addresses, true, settings, batchSize, pollTimeMs);

        assertArrayEquals(addresses, observableQueue.getAddresses());
        assertEquals(AMQSettings.AMQP_EXCHANGE_TYPE, observableQueue.getType());
        assertEquals(name, observableQueue.getName());
        assertEquals(name, observableQueue.getURI());
        assertEquals(batchSize, observableQueue.getBatchSize());
        assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
        assertEquals(queue.size(), observableQueue.size());

        runObserve(channel, observableQueue, queueName, useWorkingChannel, batchSize);

        if (useWorkingChannel) {
            if (exists) {
                verify(channel, atLeastOnce()).exchangeDeclarePassive(eq(name));
            }
            else {
                verify(channel, atLeastOnce()).exchangeDeclare(eq(name), eq(type), eq(settings.isDurable()),
                        eq(settings.autoDelete()), eq(Collections.emptyMap()));
                verify(channel, atLeastOnce()).queueDeclare(eq(queueName), anyBoolean(), anyBoolean(),
                        anyBoolean(), anyMap());
            }
            verify(channel, atLeastOnce()).queueBind(eq(queueName), eq(name), eq(routingKey));
        }
    }

    private void testPublishMessagesToExchangeAndDefaultConfiguration(Channel channel, Connection connection,
                                                                 boolean exists, boolean useWorkingChannel)
            throws IOException, TimeoutException {
        final Random random = new Random();

        final String name = RandomStringUtils.randomAlphabetic(30), type = "topic",
                queueName = RandomStringUtils.randomAlphabetic(30),
                routingKey = RandomStringUtils.randomAlphabetic(30);

        final AMQSettings settings = new AMQSettings(configuration)
                .fromURI("amqp-exchange:" + name +"?exchangeType="+ type +"&routingKey="+ routingKey
                                +"&deliveryMode=2&durable=true&exclusive=false&autoDelete=true");
        assertEquals(true, settings.isDurable());
        assertEquals(false, settings.isExclusive());
        assertEquals(true, settings.autoDelete());
        assertEquals(2, settings.getDeliveryMode());
        assertEquals(name, settings.getQueueOrExchangeName());
        assertEquals(type, settings.getExchangeType());
        assertEquals(routingKey, settings.getRoutingKey());

        List<GetResponse> queue = buildQueue(random, batchSize);
        channel = mockChannelForExchange(channel, useWorkingChannel, exists, queueName,
                name, type, routingKey, queue);

        AMQObservableQueue observableQueue = new AMQObservableQueue(
                mockConnectionFactory(connection),
                addresses, true, settings, batchSize, pollTimeMs);

        assertArrayEquals(addresses, observableQueue.getAddresses());
        assertEquals(AMQSettings.AMQP_EXCHANGE_TYPE, observableQueue.getType());
        assertEquals(name, observableQueue.getName());
        assertEquals(name, observableQueue.getURI());
        assertEquals(batchSize, observableQueue.getBatchSize());
        assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
        assertEquals(queue.size(), observableQueue.size());

        List<Message> messages = new LinkedList<>();
        Observable.range(0, batchSize).forEach((Integer x) -> messages.add(new Message("" + x, "payload: " + x, null)));
        assertEquals(batchSize, messages.size());
        observableQueue.publish(messages);

        if (useWorkingChannel) {
            verify(channel, times(batchSize)).basicPublish(eq(name), eq(routingKey),
                    any(AMQP.BasicProperties.class), any(byte[].class));
        }
    }
}
