/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.grpc.server.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.netflix.conductor.grpc.WorkflowServicePb;
import com.netflix.conductor.service.WorkflowService;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class WorkflowServiceImplTest {

    private static final String WORKFLOW_ID = "anyWorkflowId";
    private static final Boolean RESUME_SUBWORKFLOW_TASKS = true;

    @Mock
    private WorkflowService workflowService;

    private WorkflowServiceImpl workflowServiceImpl;

    @Before
    public void init() {
        initMocks(this);
        workflowServiceImpl = new WorkflowServiceImpl(workflowService, 5000);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void givenWorkflowIdWhenRetryWorkflowThenRetriedSuccessfully() {
        // Given
        WorkflowServicePb.RetryWorkflowRequest req = WorkflowServicePb.RetryWorkflowRequest
            .newBuilder()
            .setWorkflowId(WORKFLOW_ID)
            .setResumeSubworkflowTasks(true)
            .build();
        // When
        workflowServiceImpl.retryWorkflow(req, mock(StreamObserver.class));
        // Then
        verify(workflowService).retryWorkflow(WORKFLOW_ID, RESUME_SUBWORKFLOW_TASKS);
    }
}
