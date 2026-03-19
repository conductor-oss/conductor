package io.conductor.e2e.task;

import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;
import io.conductor.e2e.util.ApiUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "API_ORCHESTRATION_ENABLED", matches = "true")
public class HttpTaskHostAndIPBlockedTest {

    @SuppressWarnings("unchecked")
    @Test
    public void returns403OnBlockedIp() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setVersion(1);
        startWorkflowRequest.setName("http_blocked_ip");
        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        await().pollInterval(5, TimeUnit.SECONDS).atMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            assertNotNull(workflow);
            assertEquals(1, workflow.getTasks().size());
            assertEquals(Task.Status.FAILED, workflow.getTasks().getFirst().getStatus());
            Map<String, Object> response = (Map<String, Object>) workflow.getTasks().getFirst().getOutputData().getOrDefault("response", null);
            assertNotNull(response);
            assertEquals(403, response.getOrDefault("statusCode", 500));
        });
    }

}
