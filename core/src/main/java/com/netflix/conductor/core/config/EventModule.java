package com.netflix.conductor.core.config;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.google.inject.name.Named;
import com.netflix.conductor.core.events.ActionProcessor;
import com.netflix.conductor.core.events.EventProcessor;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.SimpleActionProcessor;
import com.netflix.conductor.core.events.SimpleEventProcessor;
import com.netflix.conductor.core.events.queue.EventPollSchedulerProvider;
import com.netflix.conductor.core.events.queue.dyno.DynoEventQueueProvider;
import com.netflix.conductor.dao.QueueDAO;
import rx.Scheduler;

import static com.netflix.conductor.core.events.EventQueues.EVENT_QUEUE_PROVIDERS_QUALIFIER;

public class EventModule extends AbstractModule {

    public static final String CONDUCTOR_QUALIFIER = "conductor";

    @Override
    protected void configure() {
        // start processing events when instance starts
        bind(ActionProcessor.class).to(SimpleActionProcessor.class);
        bind(EventProcessor.class).to(SimpleEventProcessor.class).asEagerSingleton();
        bind(Scheduler.class).toProvider(EventPollSchedulerProvider.class).asEagerSingleton();
    }

    @ProvidesIntoMap
    @StringMapKey(CONDUCTOR_QUALIFIER)
    @Singleton
    @Named(EVENT_QUEUE_PROVIDERS_QUALIFIER)
    public EventQueueProvider getDynoEventQueueProvider(QueueDAO queueDAO, Configuration configuration, Scheduler eventScheduler) {
        return new DynoEventQueueProvider(queueDAO, configuration, eventScheduler);
    }
}
