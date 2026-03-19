package io.conductor.e2e.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.StateChangeEvent;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import io.conductor.e2e.util.ApiUtil;
import io.conductor.e2e.util.TestUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.awaitility.Awaitility.await;


public class OnStateChangeTests {
    static WorkflowClient workflowClient;
    static MetadataClient metadataClient;
    static TaskClient taskClient;

    private final String WORKFLOW_NAME = "test_wf";
    private final String TASK_NAME = "test_task_on_change";
    private static final String EVENT_TASK_NAME = "test_123_abc";

    @BeforeAll
    public static void init() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
        List<Task> tasks = new ArrayList<>();
        do {
            tasks = taskClient.batchPollTasksByTaskType(EVENT_TASK_NAME, "", 10, 200);
            for (Task task : tasks) {
                TaskResult result = new TaskResult(task);
                result.setStatus(TaskResult.Status.COMPLETED);
                taskClient.updateTask(result);
            }
        } while (tasks.size() > 0);
    }

    @AfterEach
    public void cleanup() {
        SearchResult<WorkflowSummary> found = workflowClient.search("workflowType IN (" + WORKFLOW_NAME + ") AND status IN (RUNNING)");
        found.getResults().forEach(workflowSummary -> {
            try {
                workflowClient.terminateWorkflow(workflowSummary.getWorkflowId(), "terminate - onstatechange test");
                System.out.println("[OnStateChangeTest] Going to terminate " + workflowSummary.getWorkflowId());
            } catch (Exception e) {
            }
        });
    }

    @Test
    public void testOnSuccess() {
        register(Set.of("onSuccess"));

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(WORKFLOW_NAME);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task task = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.SCHEDULED, task.getStatus());
        });

        // complete the task
        taskClient.updateTask(workflowId, TASK_NAME, TaskResult.Status.COMPLETED, Map.of());

        try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); };

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task task = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.COMPLETED, task.getStatus());
        });

        // poll event task
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Task task = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(task);
            Assertions.assertEquals("COMPLETED", task.getInputData().get("00status00"));
        });
    }

    @Test
    public void testOnFailed() {
        register(Set.of("onFailed"));

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(WORKFLOW_NAME);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task task = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.SCHEDULED, task.getStatus());
        });

        // update the task
        taskClient.updateTask(workflowId, TASK_NAME, TaskResult.Status.FAILED_WITH_TERMINAL_ERROR, Map.of());

        try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); };

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task task = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        });

        // poll for the event task
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Task task = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(task);
            Assertions.assertEquals("FAILED_WITH_TERMINAL_ERROR", task.getInputData().get("00status00"));
        });
    }

    @Test
    public void testOnScheduled() {
        register(Set.of("onScheduled"));

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(WORKFLOW_NAME);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task task = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.SCHEDULED, task.getStatus());
        });

        // poll for the event task
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledTask = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(polledTask);
            Assertions.assertEquals("SCHEDULED", polledTask.getInputData().get("00status00"));
        });

        // update the task
        taskClient.updateTask(workflowId, TASK_NAME, TaskResult.Status.FAILED_WITH_TERMINAL_ERROR, Map.of());

        try { Thread.sleep(10000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); };

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task task = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        });
    }

    @Test
    public void testOnStart() {
        register(Set.of("onStart"));

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(WORKFLOW_NAME);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Assertions.assertNotEquals(0, workflow.getTasks().size());
            Task task = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.SCHEDULED, task.getStatus());
        });

        Task polledTask = taskClient.pollTask(TASK_NAME, "", null);
        Assertions.assertNotNull(polledTask);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledEventTask = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(polledEventTask);
            Assertions.assertEquals("IN_PROGRESS", polledEventTask.getInputData().get("00status00"));
        });

        taskClient.updateTask(workflowId, TASK_NAME, TaskResult.Status.COMPLETED, Map.of());
    }

    @Test
    public void testOnCancelled() {
        register(Set.of("onCancelled"));

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(WORKFLOW_NAME);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task task = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.SCHEDULED, task.getStatus());
        });

        // terminate workflow for task cancellation
        workflowClient.terminateWorkflow(workflowId, "testing purpose");


        // poll for the event task
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Task task = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(task);
            Assertions.assertEquals("CANCELED", task.getInputData().get("00status00"));
        });
    }

    @Test
    public void testMultipleEventsTogetherWhenSuccess() {
        register(Set.of("onScheduled", "onStart", "onSuccess"));

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(WORKFLOW_NAME);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task task = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.SCHEDULED, task.getStatus());
        });

        // poll for the event task
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledTask = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(polledTask);
            Assertions.assertEquals("SCHEDULED", polledTask.getInputData().get("00status00"));
        });

        // poll for the actual task
        Task polledWfTask = taskClient.pollTask(TASK_NAME, "", null);
        Assertions.assertNotNull(polledWfTask);

        // poll for the event task
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledTask = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(polledTask);
            Assertions.assertEquals("IN_PROGRESS", polledTask.getInputData().get("00status00"));
        });

        //taskClient.updateTask(workflowId, TASK_NAME, TaskResult.Status.COMPLETED, Map.of());
        taskClient.updateTaskSync(workflowId, TASK_NAME, TaskResult.Status.COMPLETED, Map.of());

        // poll for the event task
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledTask = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(polledTask);
            Assertions.assertEquals("COMPLETED", polledTask.getInputData().get("00status00"));
        });
    }

    @Test
    public void testMultipleEventsTogetherWhenFailed() {
        register(Set.of("onScheduled", "onStart", "onFailed"));

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(WORKFLOW_NAME);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task task = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.SCHEDULED, task.getStatus());
        });

        // poll for the event task
        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledTask = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(polledTask);
            Assertions.assertEquals("SCHEDULED", polledTask.getInputData().get("00status00"));
        });

        // poll for the actual task
        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledWfTask = taskClient.pollTask(TASK_NAME, "", null);
            Assertions.assertNotNull(polledWfTask);
        });

        // poll for the event task
        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledTask = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(polledTask);
            Assertions.assertEquals("IN_PROGRESS", polledTask.getInputData().get("00status00"));
        });

        taskClient.updateTask(workflowId, TASK_NAME, TaskResult.Status.FAILED_WITH_TERMINAL_ERROR, Map.of());

        // poll for the event task
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledTask = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(polledTask);
            Assertions.assertEquals("FAILED_WITH_TERMINAL_ERROR", polledTask.getInputData().get("00status00"));
        });
    }

    @Test
    @SneakyThrows
    public void testWaitFix() {
        ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();
        WorkflowDef wf = mapper.readValue(TestUtil.getResourceAsString("metadata/onstate_fix_test.json"), WorkflowDef.class);
        wf.setName(WORKFLOW_NAME);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(WORKFLOW_NAME);
        startWorkflowRequest.setWorkflowDef(wf);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task task = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.SCHEDULED, task.getStatus());
        });

        //simple task emits scheduled
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledEventTask = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(polledEventTask);
            Assertions.assertEquals("SCHEDULED", polledEventTask.getInputData().get("00status00"));
        });

        Task polledTask = taskClient.pollTask(TASK_NAME, "", null);
        Assertions.assertNotNull(polledTask);

        //simple task in progress
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledEventTask = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(polledEventTask);
            Assertions.assertEquals("IN_PROGRESS", polledEventTask.getInputData().get("00status00"));
        });

        taskClient.updateTask(workflowId, TASK_NAME, TaskResult.Status.COMPLETED, Map.of());

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Assertions.assertNotEquals(0, workflow.getTasks().size());
            Task task = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.COMPLETED, task.getStatus());
            task = workflow.getTasks().get(1);
            Assertions.assertEquals(Task.Status.IN_PROGRESS, task.getStatus());
        });

        Map<String, String> statusMap = new HashMap<>();
        //simple task completed
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledEventTask = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(polledEventTask);
            statusMap.put(polledEventTask.getReferenceTaskName(), (String) polledEventTask.getInputData().get("00status00"));
            //Assertions.assertEquals("COMPLETED", polledEventTask.getInputData().get("00status00"));
        });

        //signal wait in progress
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledEventTask = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(polledEventTask);
            statusMap.put(polledEventTask.getReferenceTaskName(), (String) polledEventTask.getInputData().get("00status00"));
            //Assertions.assertEquals("IN_PROGRESS", polledEventTask.getInputData().get("00status00"));
        });

        assertEquals(2, statusMap.size());
        assertEquals("COMPLETED", statusMap.get("test_task_on_change_onSuccess_test_123_abc"));
        assertEquals("IN_PROGRESS", statusMap.get("wait_ref_onStart_test_123_abc"));
        taskClient.updateTask(workflowId, "wait_ref", TaskResult.Status.COMPLETED, Map.of());

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task task = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.COMPLETED, task.getStatus());
            task = workflow.getTasks().get(1);
            Assertions.assertEquals(Task.Status.COMPLETED, task.getStatus());
        });

        statusMap.clear();
        //signal wait completed
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledEventTask = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(polledEventTask);
            statusMap.put(polledEventTask.getReferenceTaskName(), (String) polledEventTask.getInputData().get("00status00"));
            //Assertions.assertEquals("COMPLETED", polledEventTask.getInputData().get("00status00"));
        });

        //duration wait in progress
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledEventTask = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(polledEventTask);
            statusMap.put(polledEventTask.getReferenceTaskName(), (String) polledEventTask.getInputData().get("00status00"));
            //Assertions.assertEquals("IN_PROGRESS", polledEventTask.getInputData().get("00status00"));
        });

        assertEquals(2, statusMap.size());
        assertEquals("COMPLETED", statusMap.get("wait_ref_onSuccess_test_123_abc"));
        assertEquals("IN_PROGRESS", statusMap.get("wait_ref_1_onStart_test_123_abc"));

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task task = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.COMPLETED, task.getStatus());
            task = workflow.getTasks().get(1);
            Assertions.assertEquals(Task.Status.COMPLETED, task.getStatus());
            task = workflow.getTasks().get(2);
            Assertions.assertEquals(Task.Status.COMPLETED, task.getStatus());
        });

        //duration wait completed
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Task polledEventTask = taskClient.pollTask(EVENT_TASK_NAME, "", null);
            Assertions.assertNotNull(polledEventTask);
            Assertions.assertEquals("COMPLETED", polledEventTask.getInputData().get("00status00"));
        });

    }

    @Test
    @SneakyThrows
    @DisplayName("When subworkflow task completes, onSuccess should be published")
    public void testSubworkflowTaskOnSuccessEvent() {
        var mapper = new ObjectMapperProvider().getObjectMapper();

        var parentWf = mapper.readValue(TestUtil.getResourceAsString("metadata/e2e_on_state_change_tests.json"), WorkflowDef.class);
        var expectedEventType = getEventType(parentWf);
        metadataClient.registerWorkflowDef(parentWf);

        var subWf = mapper.readValue(TestUtil.getResourceAsString("metadata/e2e_on_state_change_tests_sub.json"), WorkflowDef.class);
        metadataClient.registerWorkflowDef(subWf);

        var startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(parentWf.getName());
        startWorkflowRequest.setVersion(1);
        var workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        System.out.println("Started " + workflowId);
        var subworkflowTaskInParentHolder = new Task[1];
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var workflow = workflowClient.getWorkflow(workflowId, true);
            var subworkflowTaskInParent = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.IN_PROGRESS, subworkflowTaskInParent.getStatus());
            subworkflowTaskInParentHolder[0] = subworkflowTaskInParent;
        });

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var polledEventTask = taskClient.pollTask(expectedEventType, "", null);
            Assertions.assertNotNull(polledEventTask);
            //Assertions.assertEquals(workflowId, polledEventTask.getWorkflowInstanceId());
            Assertions.assertEquals(workflowId, polledEventTask.getInputData().get("parentWorkflowId"));
            Assertions.assertEquals(String.format("sub_workflow_ref_onStart_%s", expectedEventType), polledEventTask.getReferenceTaskName());
        });

        // check for duplicates
        var secondPoll = taskClient.pollTask(expectedEventType, "", null);
        Assertions.assertNull(secondPoll, "Detected duplicate event");

        try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); };
        taskClient.updateTask(subworkflowTaskInParentHolder[0].getSubWorkflowId(), "wait_ref", TaskResult.Status.COMPLETED, Map.of());

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            var workflow = workflowClient.getWorkflow(workflowId, true);
            Assertions.assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        });

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var polledEventTask = taskClient.pollTask(expectedEventType, "", null);
            Assertions.assertNotNull(polledEventTask);
            Assertions.assertEquals(workflowId, polledEventTask.getInputData().get("parentWorkflowId"));
            Assertions.assertEquals(String.format("sub_workflow_ref_onSuccess_%s", expectedEventType), polledEventTask.getReferenceTaskName());

        });

        // check for duplicates
        secondPoll = taskClient.pollTask(expectedEventType, "", null);
        Assertions.assertNull(secondPoll, "Detected duplicate event");
    }

    @Test
    @SneakyThrows
    @DisplayName("When subworkflow task completes, onSuccess should be published - Subworkflow with single sync task")
    public void testSubworkflowWithSingleSyncTaskOnSuccessEvent() {
        var mapper = new ObjectMapperProvider().getObjectMapper();

        var parentWf = mapper.readValue(TestUtil.getResourceAsString("metadata/e2e_on_state_change_tests.json"), WorkflowDef.class);
        var expectedEventType = getEventType(parentWf);
        metadataClient.registerWorkflowDef(parentWf);

        var subWf = mapper.readValue(TestUtil.getResourceAsString("metadata/e2e_on_state_change_tests_sub_set_variable.json"), WorkflowDef.class);
        metadataClient.registerWorkflowDef(subWf);

        var startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(parentWf.getName());
        startWorkflowRequest.setVersion(1);
        var workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var workflow = workflowClient.getWorkflow(workflowId, true);
            Assertions.assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());

            var subworkflowTaskInParent = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.COMPLETED, subworkflowTaskInParent.getStatus());
        });

        var polledTasks = new ArrayList<Task>();
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            var polledEventTasks = taskClient.batchPollTasksByTaskType(expectedEventType, "", 2, 2000);
            Assertions.assertNotNull(polledEventTasks);
            polledTasks.addAll(polledEventTasks);
            Assertions.assertEquals(2, polledTasks.size());
        });

        assertTrue(polledTasks.stream().allMatch(it -> workflowId.equals(it.getInputData().get("parentWorkflowId"))));
        assertTrue(polledTasks.stream().anyMatch(it -> String.format("sub_workflow_ref_onScheduled_%s", expectedEventType).equals(it.getReferenceTaskName())));
        assertTrue(polledTasks.stream().anyMatch(it -> String.format("sub_workflow_ref_onSuccess_%s", expectedEventType).equals(it.getReferenceTaskName())));

        try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); };
        // check for duplicates
        var polledEventTask = taskClient.pollTask(expectedEventType, "", null);
        Assertions.assertNull(polledEventTask);
    }

    @Test
    @SneakyThrows
    @DisplayName("When subworkflow task fails, onFailed should be published")
    public void testSubworkflowTaskOnFailedEvent() {
        var mapper = new ObjectMapperProvider().getObjectMapper();

        var parentWf = mapper.readValue(TestUtil.getResourceAsString("metadata/e2e_on_state_change_tests.json"), WorkflowDef.class);
        var expectedEventType = getEventType(parentWf);
        metadataClient.registerWorkflowDef(parentWf);

        var subWf = mapper.readValue(TestUtil.getResourceAsString("metadata/e2e_on_state_change_tests_sub.json"), WorkflowDef.class);
        metadataClient.registerWorkflowDef(subWf);

        var startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(parentWf.getName());
        startWorkflowRequest.setVersion(1);
        var workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        var subworkflowTaskInParentHolder = new Task[1];
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var workflow = workflowClient.getWorkflow(workflowId, true);
            var subworkflowTaskInParent = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.IN_PROGRESS, subworkflowTaskInParent.getStatus());
            subworkflowTaskInParentHolder[0] = subworkflowTaskInParent;
        });

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var polledEventTask = taskClient.pollTask(expectedEventType, "", null);
            Assertions.assertNotNull(polledEventTask);
            Assertions.assertEquals(workflowId, polledEventTask.getInputData().get("parentWorkflowId"));
            Assertions.assertEquals(String.format("sub_workflow_ref_onStart_%s", expectedEventType), polledEventTask.getReferenceTaskName());
        });

        // check for duplicates
        var secondPoll = taskClient.pollTask(expectedEventType, "", null);
        Assertions.assertNull(secondPoll, "Detected duplicate event");

        try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); };
        taskClient.updateTask(subworkflowTaskInParentHolder[0].getSubWorkflowId(), "wait_ref", TaskResult.Status.FAILED, Map.of());

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var workflow = workflowClient.getWorkflow(workflowId, true);
            Assertions.assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
        });

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var polledEventTask = taskClient.pollTask(expectedEventType, "", null);
            Assertions.assertNotNull(polledEventTask);
            Assertions.assertEquals(workflowId, polledEventTask.getInputData().get("parentWorkflowId"));
            Assertions.assertEquals(String.format("sub_workflow_ref_onFailed_%s", expectedEventType), polledEventTask.getReferenceTaskName());
        });

        // check for duplicates
        secondPoll = taskClient.pollTask(expectedEventType, "", null);
        Assertions.assertNull(secondPoll, "Detected duplicate event");
    }

    @Test
    @SneakyThrows
    @DisplayName("When subworkflow task is cancelled, OnCancelled should be published")
    public void testSubworkflowTaskOnCancelledEvent() {
        var mapper = new ObjectMapperProvider().getObjectMapper();

        var parentWf = mapper.readValue(TestUtil.getResourceAsString("metadata/e2e_on_state_change_tests.json"), WorkflowDef.class);
        var expectedEventType = getEventType(parentWf);
        metadataClient.registerWorkflowDef(parentWf);

        var subWf = mapper.readValue(TestUtil.getResourceAsString("metadata/e2e_on_state_change_tests_sub.json"), WorkflowDef.class);
        metadataClient.registerWorkflowDef(subWf);

        var startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(parentWf.getName());
        startWorkflowRequest.setVersion(1);
        var workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        var subworkflowTaskInParentHolder = new Task[1];
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var workflow = workflowClient.getWorkflow(workflowId, true);
            var subworkflowTaskInParent = workflow.getTasks().get(0);
            Assertions.assertEquals(Task.Status.IN_PROGRESS, subworkflowTaskInParent.getStatus());
            subworkflowTaskInParentHolder[0] = subworkflowTaskInParent;
        });

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var polledEventTask = taskClient.pollTask(expectedEventType, "", null);
            Assertions.assertNotNull(polledEventTask);
            Assertions.assertEquals(workflowId, polledEventTask.getInputData().get("parentWorkflowId"));
            Assertions.assertEquals(String.format("sub_workflow_ref_onStart_%s", expectedEventType), polledEventTask.getReferenceTaskName());
        });

        // check for duplicates
        var secondPoll = taskClient.pollTask(expectedEventType, "", null);
        Assertions.assertNull(secondPoll, "Detected duplicate event");

        try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); };
        workflowClient.terminateWorkflow(subworkflowTaskInParentHolder[0].getSubWorkflowId(), "terminated for testing purposes");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var workflow = workflowClient.getWorkflow(workflowId, true);
            Assertions.assertEquals(Workflow.WorkflowStatus.TERMINATED, workflow.getStatus());
        });

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var polledEventTask = taskClient.pollTask(expectedEventType, "", null);
            Assertions.assertNotNull(polledEventTask);
            Assertions.assertEquals(workflowId, polledEventTask.getInputData().get("parentWorkflowId"));
            Assertions.assertEquals(String.format("sub_workflow_ref_onCancelled_%s", expectedEventType), polledEventTask.getReferenceTaskName());
        });

        // check for duplicates
        secondPoll = taskClient.pollTask(expectedEventType, "", null);
        Assertions.assertNull(secondPoll, "Detected duplicate event");
    }

    private static String getEventType(WorkflowDef wf) {
        List<StateChangeEvent> stateChangeEvents = wf.getTasks()
                .get(0)
                .getOnStateChange()
                .get("onSuccess,onFailed,onScheduled,onCancelled,onStart");
        return stateChangeEvents.get(0).getType();
    }

    private void register(Set<String> stateNames) {
        TaskDef taskDef = new TaskDef();
        taskDef.setName(TASK_NAME);
        taskDef.setOwnerEmail("test@orkes.io");
        metadataClient.registerTaskDefs(List.of(taskDef));

        StateChangeEvent event = new StateChangeEvent();
        event.setType(EVENT_TASK_NAME);
        event.setPayload(Map.of(
                "00status00", "${test_task_on_change.status}"
        ));

        Map<String, List<StateChangeEvent>> eventMaps = new HashMap<>();
        stateNames.forEach(stateName -> eventMaps.put(stateName, List.of(event)));

        WorkflowTask wfTask = new WorkflowTask();
        wfTask.setName(TASK_NAME);
        wfTask.setTaskReferenceName(TASK_NAME);
        wfTask.setWorkflowTaskType(TaskType.SIMPLE);
        wfTask.setTaskDefinition(taskDef);
        wfTask.setOnStateChange(eventMaps);

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(WORKFLOW_NAME);
        workflowDef.setOwnerEmail("test@orkes.io");
        workflowDef.setTasks(List.of(wfTask));
        metadataClient.registerWorkflowDef(workflowDef);
    }
}
