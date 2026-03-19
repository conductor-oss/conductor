package io.conductor.e2e.task;

import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static io.conductor.e2e.util.TestUtil.getResourceAsString;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class PollTimeoutTests {

    private static final String TASK_NAME = "let_it_poll_timeout";

    @Test
    @SneakyThrows
    public void testPollTimeout() {
        var workflowClient = ApiUtil.WORKFLOW_CLIENT;
        var taskClient = ApiUtil.TASK_CLIENT;

        var taskInQueue = taskClient.getQueueSizeForTask(TASK_NAME);
        assertEquals(0, taskInQueue, "Task queue size should be zero but it was " + taskInQueue);

        var mapper = new ObjectMapperProvider().getObjectMapper();
        var wf = mapper.readValue(getResourceAsString("metadata/timed-out-tasks-not-removed.json"), WorkflowDef.class);

        var startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(wf.getName());
        startWorkflowRequest.setWorkflowDef(wf);

        var workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        assertNotNull(workflowId);
        await().untilAsserted(() -> assertTrue(taskClient.getQueueSizeForTask(TASK_NAME) > 0));
        for (int i = 0; i < 5; i++) {
            Thread.sleep(3_000);
            workflowClient.runDecider(workflowId);
        }

        taskInQueue = taskClient.getQueueSizeForTask(TASK_NAME);
        assertEquals(0, taskInQueue, "Task queue size should be zero but it was " + taskInQueue);
    }

}
