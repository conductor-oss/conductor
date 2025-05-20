package io.orkes.conductor.client;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

public class ApiClientTest {
    private static final String workflowName = "test_api_client_wf";
    private static final String taskName = "http_task";
    private static WorkflowClient workflowClient;
    private static MetadataClient metadataClient;

    @BeforeAll
    public static void init() {
        // Initialize the API client and other necessary components
        var apiClient = new ApiClient();
        metadataClient = new MetadataClient(apiClient);
        workflowClient = new WorkflowClient(apiClient);

        // Register the workflow definition
        registerWorkflowDef();
    }

    @AfterAll
    public static void cleanUp() {
        // Clean up resources
        metadataClient.unregisterWorkflowDef(workflowName, 1);
    }

    static void registerWorkflowDef() {
        WorkflowTask httpTask = new WorkflowTask();
        httpTask.setDescription("HTTP Task");
        httpTask.setWorkflowTaskType(TaskType.HTTP);
        httpTask.setTaskReferenceName(taskName);
        httpTask.setName(taskName);
        httpTask.setInputParameters(Map.of(
                "uri", "http://httpBin:8081/api/hello?name=apiClientTest",
                "method", "GET"
        ));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setOwnerEmail("test@orkes.io");
        workflowDef.setDescription("Workflow to test hedging");
        workflowDef.setTasks(List.of(httpTask));
        metadataClient.registerWorkflowDef(workflowDef);
    }

    @Test
    void testApiClient() {
        var workflowId = workflowClient.startWorkflow(new StartWorkflowRequest()
                .withName(workflowName)
                .withVersion(1));

        System.out.println("Started workflow " + workflowId);
        await().atMost(30, SECONDS).pollInterval(1, SECONDS).until(() -> {
            var wf = workflowClient.getWorkflow(workflowId, true);
            return wf.getStatus().isTerminal();
        });
        var workflow = workflowClient.getWorkflow(workflowId, true);

        assertNotNull(workflowId);
        assertEquals(workflowName, workflow.getWorkflowName());
        assertEquals(1, workflow.getWorkflowVersion());
        assertEquals(workflowId, workflow.getWorkflowId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
    }
}
