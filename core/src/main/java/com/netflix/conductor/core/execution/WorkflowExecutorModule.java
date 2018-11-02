package com.netflix.conductor.core.execution;

import com.google.inject.AbstractModule;

/**
 * Default implementation for the workflow status listener
 *
 */
public class WorkflowExecutorModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(WorkflowStatusListener.class).to(WorkflowStatusListenerStub.class);//default implementation
    }
}
