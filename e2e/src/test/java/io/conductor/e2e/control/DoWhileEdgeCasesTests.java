package io.conductor.e2e.control;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.awaitility.Awaitility.await;

@Slf4j
public class DoWhileEdgeCasesTests {

    private static final List<String> workflowIdsToTerminate = new ArrayList<>();
    private static WorkflowClient workflowClient;
    private static TaskClient taskClient;
    private static final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private static final TypeReference<WorkflowDef> WORKFLOW_DEF = new TypeReference<>() {
    };

    @SneakyThrows
    @BeforeAll
    public static void beforeAll() {
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;

        InputStream resource = DoWhileEdgeCasesTests.class.getResourceAsStream("/metadata/vialtodoowhile.json");
        assert resource != null;
        WorkflowDef workflowDef = objectMapper.readValue(new InputStreamReader(resource), WORKFLOW_DEF);
        metadataClient.registerWorkflowDef(workflowDef);
        log.info("Registered workflow definition: {}", workflowDef.getName());

        resource = DoWhileEdgeCasesTests.class.getResourceAsStream("/metadata/vialtodoowhile3.json");
        assert resource != null;
        workflowDef = objectMapper.readValue(new InputStreamReader(resource), WORKFLOW_DEF);
        metadataClient.registerWorkflowDef(workflowDef);
        log.info("Registered workflow definition: {}", workflowDef.getName());

        resource = DoWhileEdgeCasesTests.class.getResourceAsStream("/metadata/stackoverflower.json");
        assert resource != null;
        workflowDef = objectMapper.readValue(new InputStreamReader(resource), WORKFLOW_DEF);
        metadataClient.registerWorkflowDef(workflowDef);
        log.info("Registered workflow definition: {}", workflowDef.getName());
    }

    @AfterAll
    public static void cleanup() {
        try {
            workflowIdsToTerminate.forEach(id -> {
                workflowClient.terminateWorkflow(id, String.format("Terminated by cleanup in %s", DoWhileEdgeCasesTests.class.getSimpleName()));
            });
        } catch (Exception e) {
            if (!e.getMessage().contains("Cannot terminate a COMPLETED workflow.")) {
                log.error("Error while cleaning up in {} : {}", DoWhileEdgeCasesTests.class.getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Test
    public void testVialtoDoWhile() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("vialtodoowhile");
        String workflowId = workflowClient.startWorkflow(request);
        log.info("Started workflow {}", workflowId);
        workflowIdsToTerminate.add(workflowId);

        // Update the wait task 10 times: 9 times with "b" and 1 time with "a"
        for (int i = 0; i < 10; i++) {
            final int iteration = i;
            // Poll every second to check if the workflow is in the wait task
            await().pollInterval(1, TimeUnit.SECONDS)
                    .atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                        assertNotNull(workflow);

                        // Find the wait task in the current iteration
                        Task waitTask = workflow.getTasks().stream()
                                .filter(t -> t.getTaskType().equals("WAIT") &&
                                           t.getStatus() == Task.Status.IN_PROGRESS)
                                .findFirst()
                                .orElse(null);

                        assertNotNull(waitTask, "Wait task should be in progress at iteration " + iteration);
                    });

            // Get the workflow and find the wait task
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task waitTask = workflow.getTasks().stream()
                    .filter(t -> t.getTaskType().equals("WAIT") &&
                               t.getStatus() == Task.Status.IN_PROGRESS)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Wait task not found"));

            // Update the wait task with the appropriate result
            TaskResult taskResult = new TaskResult();
            taskResult.setTaskId(waitTask.getTaskId());
            taskResult.setStatus(TaskResult.Status.COMPLETED);
            taskResult.setWorkflowInstanceId(workflowId);

            if (i < 9) {
                // First 9 times: update with "b"
                taskResult.setOutputData(Map.of("result", "b"));
                log.info("Updating wait task iteration {} with result 'b'", i);
            } else {
                // 10th time: update with "a"
                taskResult.setOutputData(Map.of("result", "a"));
                log.info("Updating wait task iteration {} with result 'a'", i);
            }

            taskClient.updateTask(taskResult);
        }

        await().pollInterval(1, TimeUnit.SECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                    assertNotNull(workflow);
                    assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus(),
                            "Workflow should be completed");
                });

        // Assert that the do_while iterated 10 times
        Workflow finalWorkflow = workflowClient.getWorkflow(workflowId, true);
        Task doWhileTask = finalWorkflow.getTaskByRefName("do_while_ref");
        assertNotNull(doWhileTask, "do_while task should exist");

        // Check the iteration count in the output
        Object iteration = doWhileTask.getOutputData().get("iteration");
        assertNotNull(iteration, "iteration field should exist in do_while task output");
        assertEquals(10, iteration, "do_while should have iterated 10 times");

        log.info("Test completed successfully. Do-while iterated {} times", iteration);
    }

    @Test
    public void testVialtoDoWhile3() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("vialtodoowhile3");
        String workflowId = workflowClient.startWorkflow(request);
        log.info("Started workflow {}", workflowId);
        workflowIdsToTerminate.add(workflowId);

        // Await for the workflow to finish
        // This workflow runs automatically and stops when iteration > 25
        await().pollInterval(1, TimeUnit.SECONDS)
                .atMost(2, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                    assertNotNull(workflow);
                    assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus(),
                            "Workflow should be completed");
                });

        // Assert that the do_while iterated 26 times
        Workflow finalWorkflow = workflowClient.getWorkflow(workflowId, true);
        Task doWhileTask = finalWorkflow.getTaskByRefName("do_while_ref");
        assertNotNull(doWhileTask, "do_while task should exist");

        Object iteration = doWhileTask.getOutputData().get("iteration");
        assertNotNull(iteration, "iteration field should exist in do_while task output");
        assertEquals(26, iteration, "do_while should have iterated 26 times");

        log.info("Test completed successfully. Do-while iterated {} times", iteration);
    }

    @Test
    public void testStackoverflower() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("stackoverflower");
        request.setInput(Map.of("n", 999));
        String workflowId = workflowClient.startWorkflow(request);
        log.info("Started workflow {}", workflowId);
        workflowIdsToTerminate.add(workflowId);

        await().pollInterval(1, TimeUnit.SECONDS)
                .atMost(2, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                    assertNotNull(workflow);
                    assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus(),
                            "Workflow should be completed");
                });

        Workflow finalWorkflow = workflowClient.getWorkflow(workflowId, true);
        Task loopTask = finalWorkflow.getTaskByRefName("loop_ref");
        assertNotNull(loopTask, "loop task should exist");

        Object iteration = loopTask.getOutputData().get("iteration");
        assertNotNull(iteration, "iteration field should exist in loop task output");
        assertEquals(999, iteration, "loop should have iterated 999 times");

        log.info("Test completed successfully. Loop iterated {} times", iteration);
    }
}
