package com.netflix.conductor.core.events.amqp;

import com.netflix.conductor.contribs.queue.amqp.AMQObservableQueue;
import com.netflix.conductor.contribs.queue.amqp.AMQSettings;
import com.netflix.conductor.core.config.Configuration;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static com.netflix.conductor.contribs.queue.amqp.AMQProperties.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created at 21/03/2019 16:23
 *
 * @author Mickaël GREGORI <mickael.gregori@alchimie.com>
 * @version $Id$
 */
public class TestAMQEventQueueProvider {

    private Configuration configuration;

    @Before
    public void setup() {
        configuration = mock(Configuration.class);
    }

    @Test
    public void testGetQueueWithDefaultConfiguration() {
        when(configuration.getProperty(anyString(), anyString())).thenAnswer(invocation -> invocation.getArguments()[1]);
        when(configuration.getBooleanProperty(anyString(), anyBoolean())).thenAnswer(invocation -> invocation.getArguments()[1]);
        when(configuration.getIntProperty(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArguments()[1]);

        final String queueUri = "test_queue_1";
        AMQEventQueueProvider amqEventQueueProvider = new AMQEventQueueProvider(configuration, false);
        AMQObservableQueue amqObservableQueue = (AMQObservableQueue) amqEventQueueProvider.getQueue(queueUri);

        assertNotNull(amqObservableQueue);

        assertNotNull(amqObservableQueue.getConnectionFactory());
        assertEquals(ConnectionFactory.DEFAULT_HOST, amqObservableQueue.getConnectionFactory().getHost());
        assertEquals(ConnectionFactory.DEFAULT_AMQP_PORT, amqObservableQueue.getConnectionFactory().getPort());
        assertEquals(ConnectionFactory.DEFAULT_USER, amqObservableQueue.getConnectionFactory().getUsername());
        assertEquals(ConnectionFactory.DEFAULT_PASS, amqObservableQueue.getConnectionFactory().getPassword());
        assertEquals(ConnectionFactory.DEFAULT_VHOST, amqObservableQueue.getConnectionFactory().getVirtualHost());

        assertTrue(amqObservableQueue.getSettings().isDurable());
        assertFalse(amqObservableQueue.getSettings().isExclusive());

        assertNotNull(amqObservableQueue.getAddresses());

        assertEquals(AMQObservableQueue.AMQP_QUEUE_TYPE, amqObservableQueue.getType());
        assertEquals(queueUri, amqObservableQueue.getSettings().getQueueOrExchangeName());
        assertEquals(queueUri, amqObservableQueue.getURI());

        assertEquals(AMQObservableQueue.DEFAULT_BATCH_SIZE, amqObservableQueue.getBatchSize());
        assertEquals(AMQObservableQueue.DEFAULT_POLL_TIME_MS, amqObservableQueue.getPollTimeInMS());
        assertTrue(amqObservableQueue.getSettings().getArguments().isEmpty());
    }

    private static int getRandomInt(int multiplier) {
        return Double.valueOf(Math.random()*multiplier).intValue();
    }

    @Test
    public void testGetQueueWithSpecificConfiguration() {
        // Hosts
        final Address[] addresses = new Address[]{
                new Address("rabbit-1", getRandomInt(10000)),
                new Address("rabbit-2", getRandomInt(10000)),
        };
        when(configuration.getProperty(eq("workflow.event.queues.amqp.hosts"), anyString())).
                thenReturn(Arrays.stream(addresses).map(a -> a.toString()).collect(Collectors.joining(",")));

        // Port
        final int port = getRandomInt(10000);
        when(configuration.getIntProperty(eq("workflow.event.queues.amqp."+ PROPERTY_PORT), anyInt())).thenReturn(port);

        // Credentials
        final String username = RandomStringUtils.randomAscii(10),
                password =  RandomStringUtils.randomAlphanumeric(20),
                vhost =  RandomStringUtils.randomAlphabetic(20);
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_USERNAME), anyString())).thenReturn(username);
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_PASSWORD), anyString())).thenReturn(password);
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_VIRTUAL_HOST), anyString())).thenReturn(vhost);
        when(configuration.getIntProperty(eq("workflow.event.queues.amqp."+ PROPERTY_CONNECTION_TIMEOUT), anyInt()))
                .thenReturn(20);

        // Add priority for consume settings
        int maxPriority = getRandomInt(10) + 1;
        when(configuration.getIntProperty(eq("workflow.event.queues.amqp."+ PROPERTY_MAX_PRIORITY), anyInt()))
                .thenReturn(maxPriority);

        // Add polling settings
        int batchSize = getRandomInt(100000), pollTimeInMs = getRandomInt(100000);
        when(configuration.getIntProperty(eq("workflow.event.queues.amqp."+ PROPERTY_BATCH_SIZE), anyInt()))
                .thenReturn(batchSize);
        when(configuration.getIntProperty(eq("workflow.event.queues.amqp."+ PROPERTY_POLL_TIME_IN_MS), anyInt()))
                .thenReturn(pollTimeInMs);

        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_CONTENT_TYPE), anyString()))
                .thenReturn(AMQSettings.DEFAULT_CONTENT_TYPE);

        String contentEncoding = RandomStringUtils.randomAlphabetic(15);
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_CONTENT_ENCODING), anyString()))
                .thenReturn(contentEncoding);

        boolean isDurable = getRandomInt(10) > 5, isExclusive = getRandomInt(10) > 5,
                autoDelete = getRandomInt(10) > 5;
        when(configuration.getBooleanProperty(eq("workflow.event.queues.amqp."+ PROPERTY_IS_DURABLE), anyBoolean()))
                .thenReturn(isDurable);
        when(configuration.getBooleanProperty(eq("workflow.event.queues.amqp."+ PROPERTY_IS_EXCLUSIVE), anyBoolean()))
                .thenReturn(isExclusive);
        when(configuration.getBooleanProperty(eq("workflow.event.queues.amqp."+ PROPERTY_AUTO_DELETE), anyBoolean()))
                .thenReturn(autoDelete);

        final String queueUri =  RandomStringUtils.randomAlphabetic(20);
        AMQEventQueueProvider amqEventQueueProvider = new AMQEventQueueProvider(configuration, false);
        AMQObservableQueue amqObservableQueue = (AMQObservableQueue) amqEventQueueProvider
                .getQueue("amqp-queue:"+ queueUri +"?maxPriority=3");

        assertNotNull(amqObservableQueue);
        assertNotNull(amqObservableQueue.getConnectionFactory());
        assertEquals(port, amqObservableQueue.getConnectionFactory().getPort());
        assertEquals(username, amqObservableQueue.getConnectionFactory().getUsername());
        assertEquals(password, amqObservableQueue.getConnectionFactory().getPassword());
        assertEquals(vhost, amqObservableQueue.getConnectionFactory().getVirtualHost());
        assertEquals(20, amqObservableQueue.getConnectionFactory().getConnectionTimeout());

        assertNotNull(amqObservableQueue.getSettings());
        assertEquals(isDurable, amqObservableQueue.getSettings().isDurable());
        assertEquals(isExclusive, amqObservableQueue.getSettings().isExclusive());
        assertEquals(autoDelete, amqObservableQueue.getSettings().autoDelete());

        // Check content type and encoding
        assertEquals(contentEncoding, amqObservableQueue.getSettings().getContentEncoding());
        assertNotNull(amqObservableQueue.getSettings().getContentType());
        assertEquals(AMQSettings.DEFAULT_CONTENT_TYPE, amqObservableQueue.getSettings().getContentType());

        // Check resolved addresses from hosts
        assertNotNull(amqObservableQueue.getAddresses());
        assertEquals(addresses.length, amqObservableQueue.getAddresses().length);
        assertArrayEquals(addresses, amqObservableQueue.getAddresses());

        assertEquals(AMQSettings.AMQP_QUEUE_TYPE, amqObservableQueue.getType());
        assertEquals(queueUri, amqObservableQueue.getSettings().getQueueOrExchangeName());
        assertEquals(queueUri, amqObservableQueue.getURI());

        assertEquals(batchSize, amqObservableQueue.getBatchSize());
        assertEquals(pollTimeInMs, amqObservableQueue.getPollTimeInMS());
        assertFalse(amqObservableQueue.getSettings().getArguments().isEmpty());
        assertTrue(amqObservableQueue.getSettings().getArguments().containsKey("x-max-priority"));
        assertEquals(3, amqObservableQueue.getSettings().getArguments().get("x-max-priority"));
    }

    private void testGetQueueWithEmptyValue(String prop) {
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ prop), anyString())).
                thenReturn(StringUtils.EMPTY);
        AMQEventQueueProvider amqEventQueueProvider = new AMQEventQueueProvider(configuration, false);
        amqEventQueueProvider.getQueue(RandomStringUtils.randomAlphabetic(20));
    }

    private void testGetQueueWithNegativeValue(String prop) {
        when(configuration.getIntProperty(eq("workflow.event.queues.amqp."+ prop), anyInt())).
                thenReturn(-1);
        AMQEventQueueProvider amqEventQueueProvider = new AMQEventQueueProvider(configuration, false);
        amqEventQueueProvider.getQueue(RandomStringUtils.randomAlphabetic(20));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetQueueWithEmptyHosts() {
        testGetQueueWithEmptyValue(PROPERTY_HOSTS.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetQueueWithEmptyUsername() {
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_HOSTS), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        testGetQueueWithEmptyValue("username");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetQueueWithEmptyPassword() {
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_HOSTS), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_USERNAME), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        testGetQueueWithEmptyValue("password");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetQueueWithEmptyVhost() {
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_HOSTS), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_USERNAME), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_PASSWORD), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        testGetQueueWithEmptyValue(PROPERTY_VIRTUAL_HOST.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetQueueWithNegativePort() {
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_HOSTS), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_USERNAME), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_PASSWORD), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_VIRTUAL_HOST), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        testGetQueueWithNegativeValue(PROPERTY_PORT.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetQueueWithNegativeConnectionTimeout() {
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_HOSTS), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_USERNAME), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_PASSWORD), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_VIRTUAL_HOST), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getIntProperty(eq("workflow.event.queues.amqp."+ PROPERTY_PORT), anyInt())).
                thenReturn(getRandomInt(100));
        testGetQueueWithNegativeValue(PROPERTY_CONNECTION_TIMEOUT.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetQueueWithNegativeBatchSize() {
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_HOSTS), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_USERNAME), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_PASSWORD), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_VIRTUAL_HOST), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getIntProperty(eq("workflow.event.queues.amqp."+ PROPERTY_PORT), anyInt())).
                thenReturn(getRandomInt(100));
        when(configuration.getIntProperty(eq("workflow.event.queues.amqp."+ PROPERTY_CONNECTION_TIMEOUT), anyInt())).
                thenReturn(getRandomInt(100));
        testGetQueueWithNegativeValue(PROPERTY_BATCH_SIZE.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetQueueWithNegativePollTimeInMs() {
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_HOSTS), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_USERNAME), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_PASSWORD), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getProperty(eq("workflow.event.queues.amqp."+ PROPERTY_VIRTUAL_HOST), anyString())).
                thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(configuration.getIntProperty(eq("workflow.event.queues.amqp."+ PROPERTY_PORT), anyInt())).
                thenReturn(getRandomInt(100));
        when(configuration.getIntProperty(eq("workflow.event.queues.amqp."+ PROPERTY_CONNECTION_TIMEOUT), anyInt())).
                thenReturn(getRandomInt(100));
        when(configuration.getIntProperty(eq("workflow.event.queues.amqp."+ PROPERTY_BATCH_SIZE), anyInt())).
                thenReturn(getRandomInt(100));
        testGetQueueWithNegativeValue(PROPERTY_POLL_TIME_IN_MS.toString());
    }

}
