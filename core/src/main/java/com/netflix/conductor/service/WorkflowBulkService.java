/*
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
