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
import com.netflix.conductor.annotations.Service;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.core.execution.WorkflowExecutor;

import javax.inject.Inject;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
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

    @Service
    public void pauseWorkflow(@NotEmpty(message = "WorkflowIds list cannot be null.") List<
                              @Size(max=MAX_REQUEST_ITEMS, message = "Cannot process more than {max} workflows. Please use multiple requests.") String> workflowIds) {
        for (String workflowId : workflowIds) {
            workflowExecutor.pauseWorkflow(workflowId);
        }
    }

    @Service
    public void resumeWorkflow(@NotEmpty(message = "WorkflowIds list cannot be null.") List<
                               @Size(max=MAX_REQUEST_ITEMS, message = "Cannot process more than {max} workflows. Please use multiple requests.") String> workflowIds) {
        for (String workflowId : workflowIds) {
            workflowExecutor.resumeWorkflow(workflowId);
        }
    }

    @Service
    public void restart(@NotEmpty(message = "WorkflowIds list cannot be null.") List<
                        @Size(max=MAX_REQUEST_ITEMS, message = "Cannot process more than {max} workflows. Please use multiple requests.") String> workflowIds, boolean useLatestDefinitions) {
        for (String workflowId : workflowIds) {
            workflowExecutor.rewind(workflowId, useLatestDefinitions);
        }
    }

    @Service
    public void retry(@NotEmpty(message = "WorkflowIds list cannot be null.") List<
                      @Size(max=MAX_REQUEST_ITEMS, message = "Cannot process more than {max} workflows. Please use multiple requests.") String> workflowIds) {
        for (String workflowId : workflowIds) {
            workflowExecutor.retry(workflowId);
        }
    }

    @Service
    public void terminate(@NotEmpty(message = "WorkflowIds list cannot be null.") List<
                          @Size(max=MAX_REQUEST_ITEMS, message = "Cannot process more than {max} workflows. Please use multiple requests.") String> workflowIds,
                          String reason) {
        for (String workflowId : workflowIds) {
            workflowExecutor.terminateWorkflow(workflowId, reason);
        }
    }
}
