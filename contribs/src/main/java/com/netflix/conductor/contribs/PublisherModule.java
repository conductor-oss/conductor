package com.netflix.conductor.contribs;

import com.google.inject.AbstractModule;
import com.netflix.conductor.contribs.listener.WorkflowStatusListenerPublisher;
import com.netflix.conductor.core.execution.WorkflowStatusListener;

/**
 * Default implementation for the workflow status listener
 *
 */
public class PublisherModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(WorkflowStatusListener.class).to(WorkflowStatusListenerPublisher.class);
    }
}
