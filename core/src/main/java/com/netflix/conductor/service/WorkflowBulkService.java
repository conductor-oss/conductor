package com.netflix.conductor.service;

import com.google.inject.Singleton;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.service.utils.ServiceUtils;

import javax.inject.Inject;
import java.util.List;

@Singleton
@Trace
public class WorkflowBulkService {
    private static final int MAX_REQUEST_ITEMS = 1000;
    private final WorkflowExecutor workflowExecutor;

    @Inject
    public WorkflowBulkService(WorkflowExecutor workflowExecutor) {
        this.workflowExecutor = workflowExecutor;
    }

    public void pauseWorkflow(List<String> workflowIds) {
        ServiceUtils.checkNotNullOrEmpty(workflowIds, "WorkflowIds list cannot be null.");
        ServiceUtils.checkArgument(workflowIds.size() < MAX_REQUEST_ITEMS, String.format("Cannot process more than %s workflows. Please use multiple requests", MAX_REQUEST_ITEMS));
        for (String workflowId : workflowIds) {
            workflowExecutor.pauseWorkflow(workflowId);
        }
    }

    public void resumeWorkflow(List<String> workflowIds) {
        ServiceUtils.checkNotNullOrEmpty(workflowIds, "WorkflowIds list cannot be null.");
        ServiceUtils.checkArgument(workflowIds.size() < MAX_REQUEST_ITEMS, String.format("Cannot process more than %s workflows. Please use multiple requests", MAX_REQUEST_ITEMS));
        for (String workflowId : workflowIds) {
            workflowExecutor.resumeWorkflow(workflowId);
        }
    }

    public void restart(List<String> workflowIds) {
        ServiceUtils.checkNotNullOrEmpty(workflowIds, "WorkflowIds list cannot be null.");
        ServiceUtils.checkArgument(workflowIds.size() < MAX_REQUEST_ITEMS, String.format("Cannot process more than %s workflows. Please use multiple requests", MAX_REQUEST_ITEMS));
        for (String workflowId : workflowIds) {
            workflowExecutor.rewind(workflowId);
        }
    }

    public void retry(List<String> workflowIds) {
        ServiceUtils.checkNotNullOrEmpty(workflowIds, "WorkflowIds list cannot be null.");
        ServiceUtils.checkArgument(workflowIds.size() < MAX_REQUEST_ITEMS, String.format("Cannot process more than %s workflows. Please use multiple requests", MAX_REQUEST_ITEMS));
        for (String workflowId : workflowIds) {
            workflowExecutor.retry(workflowId);
        }
    }

    public void terminate(List<String> workflowIds, String reason) {
        ServiceUtils.checkNotNullOrEmpty(workflowIds, "workflowIds list cannot be null.");
        ServiceUtils.checkArgument(workflowIds.size() < MAX_REQUEST_ITEMS, String.format("Cannot process more than %s workflows. Please use multiple requests", MAX_REQUEST_ITEMS));
        for (String workflowId : workflowIds) {
            workflowExecutor.terminateWorkflow(workflowId, reason);
        }
    }
}
