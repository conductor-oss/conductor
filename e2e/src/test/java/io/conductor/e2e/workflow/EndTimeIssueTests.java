package io.conductor.e2e.workflow;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.conductor.e2e.util.TestUtil.getResourceAsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.awaitility.Awaitility.await;

public class EndTimeIssueTests {

    @DisplayName("${task_ref.endTime} should be replaced correctly")
    @Test
    @SneakyThrows
    void endTimeIsReplacedCorrectly() {
        final var start = System.currentTimeMillis();
        var metadataClient = ApiUtil.METADATA_CLIENT;
        var workflowClient = ApiUtil.WORKFLOW_CLIENT;
        var taskClient = ApiUtil.TASK_CLIENT;

        var mapper = new ObjectMapperProvider().getObjectMapper();
        var workflowDef = mapper.readValue(getResourceAsString("metadata/end_time_workflow.json"), WorkflowDef.class);
        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));

        var startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowDef.getName());
        startWorkflowRequest.setVersion(workflowDef.getVersion());
        var workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var task = taskClient.pollTask("end_time_simple", "end-time-worker", null);
            assertNotNull(task);
            assertEquals(workflowId, task.getWorkflowInstanceId());
            assertEquals("simple_ref", task.getReferenceTaskName());
            assertEquals(Task.Status.IN_PROGRESS, task.getStatus());

            var result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            taskClient.updateTask(result);
        });

        for (int i = 2; i <= 3; i++) {
            var n = i;
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                var taskType = "end_time_simple_" + n;
                var task = taskClient.pollTask(taskType, "end-time-worker", null);
                assertNotNull(task);
                assertEquals(workflowId, task.getWorkflowInstanceId());
                assertEquals(Task.Status.IN_PROGRESS, task.getStatus());

                assertNotNull(task.getInputData().get("endTime"));
                var endTime = Long.parseLong(task.getInputData().get("endTime").toString());
                if (endTime < start) {
                    // we want to throw an Exception to break the untilAsserted block
                    throw new RuntimeException(
                            String.format("Invalid value for ${simple_ref.endTime}:%d in task %s", endTime, taskType));
                }

                var result = new TaskResult(task);
                result.setStatus(TaskResult.Status.COMPLETED);
                taskClient.updateTask(result);
            });
        }
    }
}
