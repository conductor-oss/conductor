package com.netflix.conductor.contribs.queue.amqp;

import com.netflix.conductor.core.events.queue.Message;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import rx.Observable;

import java.io.IOException;
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
public class TestQueueAMQObservableQueue extends AbstractTestAMQObservableQueue {

    @Test
    public void testGetMessagesFromExistingQueueAndDefaultConfiguration()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testGetMessagesFromQueueAndDefaultConfiguration(channel, connection,true, true);
    }

    @Test
    public void testGetMessagesFromNotExistingQueueAndDefaultConfiguration()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testGetMessagesFromQueueAndDefaultConfiguration(channel, connection,false, true);
    }

    @Test
    public void testPublishMessagesToNotExistingQueueAndDefaultConfiguration()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testPublishMessagesToQueueAndDefaultConfiguration(channel, connection,false, true);
    }

    @Test(expected = RuntimeException.class)
    public void testGetMessagesFromQueueWithBadConnection()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockBadConnection();
        testGetMessagesFromQueueAndDefaultConfiguration(channel, connection, true, true);
    }

    @Test(expected = RuntimeException.class)
    public void testPublishMessagesToQueueWithBadConnection()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockBadConnection();
        testPublishMessagesToQueueAndDefaultConfiguration(channel, connection, true, true);
    }

    @Test
    public void testGetMessagesFromQueueWithBadChannel()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testGetMessagesFromQueueAndDefaultConfiguration(channel, connection, true, false);
    }

    @Test(expected = RuntimeException.class)
    public void testPublishMessagesToQueueWithBadChannel()
            throws IOException, TimeoutException {
        // Mock channel and connection
        Channel channel = mockBaseChannel();
        Connection connection = mockGoodConnection(channel);
        testPublishMessagesToQueueAndDefaultConfiguration(channel, connection, true, false);
    }

    private void testGetMessagesFromQueueAndDefaultConfiguration(Channel channel, Connection connection,
                                                                 boolean queueExists, boolean useWorkingChannel)
            throws IOException, TimeoutException {
        final Random random = new Random();

        final String queueName = RandomStringUtils.randomAlphabetic(30);
        AMQSettings settings = new AMQSettings(configuration).fromURI("amqp-queue:" + queueName);

        List<GetResponse> queue = buildQueue(random, batchSize);
        channel = mockChannelForQueue(channel, useWorkingChannel, queueExists, queueName, queue);

        AMQObservableQueue observableQueue = new AMQObservableQueue(
                mockConnectionFactory(connection),
                addresses, false, settings, batchSize, pollTimeMs);

        assertArrayEquals(addresses, observableQueue.getAddresses());
        assertEquals(AMQSettings.AMQP_QUEUE_TYPE, observableQueue.getType());
        assertEquals(queueName, observableQueue.getName());
        assertEquals(queueName, observableQueue.getURI());
        assertEquals(batchSize, observableQueue.getBatchSize());
        assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
        assertEquals(queue.size(), observableQueue.size());

        runObserve(channel, observableQueue, queueName, useWorkingChannel, batchSize);
    }

    private void testPublishMessagesToQueueAndDefaultConfiguration(Channel channel, Connection connection,
                                                                 boolean queueExists, boolean useWorkingChannel)
            throws IOException, TimeoutException {
        final Random random = new Random();

        final String queueName = RandomStringUtils.randomAlphabetic(30);
        final AMQSettings settings = new AMQSettings(configuration)
                .fromURI("amqp-queue:" + queueName +"?deliveryMode=2&durable=true&exclusive=false&autoDelete=true");
        assertEquals(true, settings.isDurable());
        assertEquals(false, settings.isExclusive());
        assertEquals(true, settings.autoDelete());
        assertEquals(2, settings.getDeliveryMode());

        List<GetResponse> queue = buildQueue(random, batchSize);
        channel = mockChannelForQueue(channel, useWorkingChannel, queueExists, queueName, queue);

        AMQObservableQueue observableQueue = new AMQObservableQueue(
                mockConnectionFactory(connection),
                addresses, false, settings, batchSize, pollTimeMs);

        assertArrayEquals(addresses, observableQueue.getAddresses());
        assertEquals(AMQSettings.AMQP_QUEUE_TYPE, observableQueue.getType());
        assertEquals(queueName, observableQueue.getName());
        assertEquals(queueName, observableQueue.getURI());
        assertEquals(batchSize, observableQueue.getBatchSize());
        assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
        assertEquals(queue.size(), observableQueue.size());

        List<Message> messages = new LinkedList<>();
        Observable.range(0, batchSize).forEach((Integer x) -> messages.add(new Message("" + x, "payload: " + x, null)));
        assertEquals(batchSize, messages.size());
        observableQueue.publish(messages);

        if (useWorkingChannel) {
            verify(channel, times(batchSize)).basicPublish(eq(StringUtils.EMPTY), eq(queueName),
                    any(AMQP.BasicProperties.class), any(byte[].class));
        }
    }


}
