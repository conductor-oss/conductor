package io.conductor.e2e.workflow;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import io.conductor.e2e.util.ApiUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ClearContextTest {

    static WorkflowClient workflowClient;
    static TaskClient taskClient;
    static MetadataClient metadataClient;

    @BeforeAll
    public static void init() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
    }

    @Test
    @DisplayName("Context is not polluted by update task calls")
    public void testContextNotPolluted() throws Exception {
        // Step 1: Register a test task definition
        String taskName = "clear_context_test_task";
        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@orkes.io");
        metadataClient.registerTaskDefs(java.util.Collections.singletonList(taskDef));

        // Step 2: Register a workflow definition using this task
        String workflowName = "clear_context_test_workflow";
        WorkflowTask workflowTask = new com.netflix.conductor.common.metadata.workflow.WorkflowTask();
        workflowTask.setName(taskName);
        workflowTask.setTaskReferenceName(taskName);
        workflowTask.setWorkflowTaskType(com.netflix.conductor.common.metadata.tasks.TaskType.SIMPLE);
        workflowTask.setTaskDefinition(taskDef);
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setOwnerEmail("test@orkes.io");
        workflowDef.setVersion(1);
        workflowDef.setTasks(java.util.Collections.singletonList(workflowTask));
        metadataClient.registerWorkflowDef(workflowDef);

        // Step 3: Start a thread that constantly calls updateTaskDef
        Thread updater = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                try {
                    metadataClient.updateTaskDef(taskDef);
                } catch (Exception ignored) {}
            }
        });
        updater.start();

        // Step 4: Start a workflow while the updater thread is running
        com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest startWorkflowRequest = new com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);
        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        // Step 5: Fetch the workflow and assert ownerApp is empty
        com.netflix.conductor.common.run.Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        org.junit.jupiter.api.Assertions.assertTrue(
            workflow.getOwnerApp() == null || workflow.getOwnerApp().isEmpty(),
            "ownerApp should be empty but was: " + workflow.getOwnerApp()
        );
        updater.interrupt();

    }
}
