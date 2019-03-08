package com.netflix.conductor.core.execution;

import com.google.inject.AbstractModule;
import com.netflix.conductor.service.AdminService;
import com.netflix.conductor.service.AdminServiceImpl;
import com.netflix.conductor.service.EventService;
import com.netflix.conductor.service.EventServiceImpl;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.service.MetadataServiceImpl;
import com.netflix.conductor.service.TaskService;
import com.netflix.conductor.service.TaskServiceImpl;
import com.netflix.conductor.service.WorkflowBulkService;
import com.netflix.conductor.service.WorkflowBulkServiceImpl;
import com.netflix.conductor.service.WorkflowService;
import com.netflix.conductor.service.WorkflowServiceImpl;

/**
 * Default implementation for the workflow status listener
 *
 */
public class WorkflowExecutorModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(WorkflowStatusListener.class).to(WorkflowStatusListenerStub.class);//default implementation

        //service layer
        bind(AdminService.class).to(AdminServiceImpl.class);
        bind(WorkflowService.class).to(WorkflowServiceImpl.class);
        bind(WorkflowBulkService.class).to(WorkflowBulkServiceImpl.class);
        bind(TaskService.class).to(TaskServiceImpl.class);
        bind(EventService.class).to(EventServiceImpl.class);
        bind(MetadataService.class).to(MetadataServiceImpl.class);
    }
}
