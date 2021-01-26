package com.netflix.conductor.grpc.server.service;


import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.grpc.WorkflowServicePb;
import com.netflix.conductor.service.WorkflowService;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class WorkflowServiceImplTest {

    private static final String WORKFLOW_ID = "anyWorkflowId";
    private static final Boolean RESUME_SUBWORKFLOW_TASKS = true;

    @Mock
    private WorkflowService workflowService;

    @Mock
    private Configuration configuration;

    private WorkflowServiceImpl workflowServiceImpl;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        Mockito.when(configuration.getIntProperty("workflow.max.search.size", 5_000)).thenReturn(5_000);
        workflowServiceImpl = new WorkflowServiceImpl(workflowService, configuration);
    }

    @Test
    public void givenWorkflowIdWhenRetryWorkflowThenRetriedSuccessfully() {
        // Given
        WorkflowServicePb.RetryWorkflowRequest req = WorkflowServicePb.RetryWorkflowRequest
                .newBuilder()
                .setWorkflowId(WORKFLOW_ID)
                .setResumeSubworkflowTasks(true)
                .build();
        // When
        workflowServiceImpl.retryWorkflow(req, Mockito.mock(StreamObserver.class));
        // Then
        Mockito.verify(workflowService).retryWorkflow(WORKFLOW_ID, RESUME_SUBWORKFLOW_TASKS);
    }
}
