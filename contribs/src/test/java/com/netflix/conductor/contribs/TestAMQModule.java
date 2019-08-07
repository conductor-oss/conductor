package com.netflix.conductor.contribs;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created at 22/03/2019 16:37
 *
 * @author Mickaël GREGORI <mickael.gregori@alchimie.com>
 * @version $Id$
 */
public class TestAMQModule {

    private AMQModule module;
    private Configuration config;

    @Before
    public void setUp() {
        module = new AMQModule();
        config = mock(Configuration.class);
    }

    @Test
    public void testGetAMQQueueEventQueueProvider() {
        EventQueueProvider provider = module.getAMQQueueEventQueueProvider(config);
        Assert.assertNotNull(provider);
    }

    @Test
    public void testGetAMQExchangeEventQueueProvider() {
        EventQueueProvider provider = module.getAMQExchangeEventQueueProvider(config);
        Assert.assertNotNull(provider);
    }

    @Test
    public void testGetQueuesWithDefaultConfiguration() {
        when(config.getProperty(anyString(), anyString())).thenAnswer(invocation -> invocation.getArguments()[1]);
        when(config.getBooleanProperty(anyString(), anyBoolean())).thenAnswer(invocation -> invocation.getArguments()[1]);
        when(config.getIntProperty(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArguments()[1]);
        Map<Task.Status, ObservableQueue> queues = module.getQueues(config);
        Assert.assertNotNull(queues);
        Assert.assertFalse(queues.isEmpty());
        Assert.assertEquals(2, queues.size());
    }
}
