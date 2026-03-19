package io.conductor.e2e.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;
import org.conductoross.conductor.common.model.WorkflowRun;
import org.conductoross.conductor.common.model.WorkflowStateUpdate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class WorkflowUpdateAndUpdateTaskTests {

    static WorkflowClient workflowClient;
    static TaskClient taskClient;
    static MetadataClient metadataClient;
    static String workflowName = "sync_task_variable_updates";
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    public static void init() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
    }

    @Test
    @DisplayName("A workflow with a cross update with a human task with an assignment should be completed")
    @SneakyThrows
    void updateHumanTaskWithAssignment() {
        var wfDef = getWorkflowDef("/metadata/human_task_update_issue_2.json");
        metadataClient.registerWorkflowDef(wfDef);

        var startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(wfDef.getName());
        startWorkflowRequest.setVersion(wfDef.getVersion());
        var workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var workflow = workflowClient.getWorkflow(workflowId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
            var waitTask = workflow.getTasks()
                    .stream()
                    .filter(t -> TaskType.TASK_TYPE_WAIT.equals(t.getTaskType()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(waitTask);
            taskClient.updateTask(workflowId, "wait_ref", TaskResult.Status.COMPLETED, Map.of());
        });

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var workflow = workflowClient.getWorkflow(workflowId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        });
    }

    @Test
    @DisplayName("Check workflow with simple task and restart functionality")
    public void testRestartSimpleWorkflow() throws IOException, InterruptedException {
        registerWorkflow(workflowName);

        StartWorkflowRequest workflowRequest = new StartWorkflowRequest();
        workflowRequest.setName(workflowName);
        workflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(workflowRequest);

        TaskResult taskResult = new TaskResult();
        taskResult.setOutputData(Map.of("a", "b"));

        WorkflowStateUpdate request = new WorkflowStateUpdate();
        request.setTaskReferenceName("wait_task_ref");
        request.setTaskResult(taskResult);
        request.setVariables(Map.of("case", "case1"));

        WorkflowRun workflowRun = workflowClient.updateWorkflow(workflowId, List.of("wait_task_ref_1", "wait_task_ref_2"), 5, request);

        assertEquals(Workflow.WorkflowStatus.RUNNING, workflowRun.getStatus());

        //Thread.sleep(2000);
        request = new WorkflowStateUpdate();
        request.setTaskReferenceName("wait_task_ref_2");
        request.setTaskResult(taskResult);
        workflowRun = workflowClient.updateWorkflow(workflowId, List.of(), 1, request);

        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflowRun.getStatus());
        Set<Task.Status> allTaskStatus = workflowRun.getTasks()
                .stream()
                .map(t -> t.getStatus())
                .collect(Collectors.toSet());
        assertEquals(1, allTaskStatus.size());
        assertEquals(Task.Status.COMPLETED, allTaskStatus.iterator().next());

        System.out.println(workflowRun.getStatus());
        System.out.println(workflowRun.getTasks()
                .stream()
                .map(task -> task.getReferenceTaskName() + ":" + task.getStatus())
                .collect(Collectors.toList()));


        metadataClient.unregisterWorkflowDef(workflowName, 1);
    }

    private WorkflowDef getWorkflowDef(String path) throws IOException {
        InputStream inputStream = WorkflowUpdateAndUpdateTaskTests.class.getResourceAsStream(path);
        if (inputStream == null) {
            throw new IOException("No file found at " + path);
        }
        return objectMapper.readValue(new InputStreamReader(inputStream), WorkflowDef.class);
    }

    protected void registerWorkflow(String wfName) throws IOException {
        WorkflowDef workflowDef = getWorkflowDef("/metadata/" + wfName + ".json");
        metadataClient.registerWorkflowDef(workflowDef);
    }

}
