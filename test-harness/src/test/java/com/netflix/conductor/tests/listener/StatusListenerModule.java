package com.netflix.conductor.tests.listener;

import com.google.inject.AbstractModule;
import com.netflix.conductor.contribs.listener.WorkflowStatusListenerPublisher;
import com.netflix.conductor.core.execution.WorkflowStatusListener;

public class StatusListenerModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(WorkflowStatusListener.class).to(WorkflowStatusListenerPublisher.class);
    }
}
