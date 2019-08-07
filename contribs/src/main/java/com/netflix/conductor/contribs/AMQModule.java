package com.netflix.conductor.contribs;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.contribs.queue.QueueManager;
import com.netflix.conductor.contribs.queue.amqp.AMQObservableQueue;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.amqp.AMQEventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.conductor.core.events.EventQueues.EVENT_QUEUE_PROVIDERS_QUALIFIER;

/**
 * Module for support of AMQP queues
 * Do not forget to define me into the configuration file:
 * <pre>
 *     conductor.additional.modules=com.netflix.conductor.contribs.AMQModule
 * </pre>
 * Created at 19/03/2019 16:33
 *
 * @author Mickaël GREGORI <mickael.gregori@alchimie.com>
 * @version $Id$
 */
public class AMQModule extends AbstractModule {

    private static final boolean USE_EXCHANGE_BY_DEFAULT = false;

    private static Logger logger = LoggerFactory.getLogger(AMQModule.class);

    @Override
    protected void configure() {
        bind(QueueManager.class).asEagerSingleton();
        logger.info("RabbitMQ Module configured ...");
    }

    @ProvidesIntoMap
    @StringMapKey("amqp")
    @Singleton
    @Named(EVENT_QUEUE_PROVIDERS_QUALIFIER)
    public EventQueueProvider getAMQQueueEventQueueProvider(Configuration config) {
        return new AMQEventQueueProvider(config, false);
    }

    @ProvidesIntoMap
    @StringMapKey("amqp_exchange")
    @Singleton
    @Named(EVENT_QUEUE_PROVIDERS_QUALIFIER)
    public EventQueueProvider getAMQExchangeEventQueueProvider(Configuration config) {
        return new AMQEventQueueProvider(config, true);
    }

    @Provides
    public Map<Task.Status, ObservableQueue> getQueues(Configuration config) {
        String stack = "";
        if(config.getStack() != null && config.getStack().length() > 0) {
            stack = config.getStack() + "_";
        }
        final boolean useExchange = config.getBooleanProperty("workflow.listener.queue.useExchange",
                USE_EXCHANGE_BY_DEFAULT);
        Task.Status[] statuses = new Task.Status[]{Task.Status.COMPLETED, Task.Status.FAILED};
        Map<Task.Status, ObservableQueue> queues = new HashMap<>();
        for(Task.Status status : statuses) {
            String queueName = config.getProperty("workflow.listener.queue.prefix",
                    config.getAppId() + "_amqp_notify_" + stack + status.name());
            final ObservableQueue queue = new AMQObservableQueue.Builder(config)
                    .build(useExchange, queueName);
            queues.put(status, queue);
        }

        return queues;
    }
}