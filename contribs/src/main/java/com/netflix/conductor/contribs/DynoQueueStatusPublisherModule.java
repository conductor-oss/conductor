package com.netflix.conductor.contribs;

import com.google.inject.AbstractModule;
import com.netflix.conductor.contribs.listener.DynoQueueStatusPublisher;
import com.netflix.conductor.core.execution.WorkflowStatusListener;

public class DynoQueueStatusPublisherModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(WorkflowStatusListener.class).to(DynoQueueStatusPublisher.class);
    }
}
