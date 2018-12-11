package com.netflix.conductor.tests.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.netflix.conductor.common.metadata.tasks.Task.Status.COMPLETED;
import static org.junit.Assert.assertEquals;

@RunWith(StatusListenerTestRunner.class)
public class StatusListenerPublisherIntegrationTest {

    private String CALLBACK_QUEUE = "_callbackQueue";
    private static final String LINEAR_WORKFLOW_T1_T2 = "junit_test_wf";

    private ObjectMapper mapper = new ObjectMapper();

    @Inject
    QueueDAO queueDAO;

    @Inject
    protected MetadataService metadataService;

    @Inject
    protected ExecutionService workflowExecutionService;

    @Inject
    protected WorkflowExecutor workflowExecutor;

    @Before
    public void setUp() {
        TaskDef task = new TaskDef();
        task.setName("junit_task_1");
        task.setTimeoutSeconds(120);
        task.setRetryCount(1);
        metadataService.registerTaskDef(Collections.singletonList(task));
    }

    @Test
    public void testListenerOnTerminatedWorkflow() throws IOException {

        WorkflowDef empty = new WorkflowDef();
        empty.setName("empty_workflow");
        empty.setSchemaVersion(2);
        empty.setWorkflowStatusListenerEnabled(true);
        metadataService.registerWorkflowDef(empty);

        String id = startOrLoadWorkflowExecution(empty.getName(), 1, "testWorkflowTerminatedListener", new HashMap<>(), null, null);

        List<Message> callbackMessages = queueDAO.pollMessages(CALLBACK_QUEUE, 1, 200);
        queueDAO.ack(CALLBACK_QUEUE, callbackMessages.get(0).getId());
        JsonNode payload = mapper.readTree(callbackMessages.get(0).getPayload());

        assertEquals(id, callbackMessages.get(0).getId());
        assertEquals("empty_workflow", payload.get("workflowType").asText());
        assertEquals("testWorkflowTerminatedListener", payload.get("correlationId").asText());
        assertEquals("COMPLETED", payload.get("status").asText());
    }

    @Test
    public void testListenerOnCompletedWorkflow() throws IOException, InterruptedException {

        clearWorkflows();

        WorkflowDef def = new WorkflowDef();
        def.setName(LINEAR_WORKFLOW_T1_T2);
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setSchemaVersion(2);
        def.setWorkflowStatusListenerEnabled(true);
        LinkedList<WorkflowTask> wftasks = new LinkedList<>();

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("junit_task_1");
        wft1.setTaskReferenceName("t1");

        wftasks.add(wft1);
        def.setTasks(wftasks);

        metadataService.updateWorkflowDef(Collections.singletonList(def));

        String id = startOrLoadWorkflowExecution(def.getName(), 1, "testWorkflowCompletedListener", new HashMap<>(), null, null);

        List<Task> tasks = workflowExecutionService.getTasks("junit_task_1", null, 1);
        tasks.get(0).setStatus(COMPLETED);
        workflowExecutionService.updateTask(tasks.get(0));


        while (workflowExecutor.getWorkflow(id, false).getStatus() != Workflow.WorkflowStatus.COMPLETED) {
            Thread.sleep(10);
        }

        List<Message> callbackMessages = queueDAO.pollMessages(CALLBACK_QUEUE, 1, 200);
        queueDAO.ack(CALLBACK_QUEUE, callbackMessages.get(0).getId());
        JsonNode payload = mapper.readTree(callbackMessages.get(0).getPayload());

        assertEquals(id, callbackMessages.get(0).getId());
        assertEquals(LINEAR_WORKFLOW_T1_T2, payload.get("workflowType").asText());
        assertEquals("testWorkflowCompletedListener", payload.get("correlationId").asText());
        assertEquals("COMPLETED", payload.get("status").asText());



    }

    @After
    public void clearWorkflows() {
        List<String> workflows = metadataService.getWorkflowDefs().stream()
                .map(WorkflowDef::getName)
                .collect(Collectors.toList());
        for (String wfName : workflows) {
            List<String> running = workflowExecutionService.getRunningWorkflows(wfName);
            for (String wfid : running) {
                workflowExecutor.terminateWorkflow(wfid, "cleanup");
            }
        }
        queueDAO.queuesDetail().keySet().forEach(queueDAO::flush);
    }

    private String startOrLoadWorkflowExecution(String workflowName, int version, String correlationId, Map<String, Object> input, String event, Map<String, String> taskToDomain) {
        return startOrLoadWorkflowExecution(workflowName, workflowName, version, correlationId, input, event, taskToDomain);
    }

    String startOrLoadWorkflowExecution(String snapshotResourceName, String workflowName, int version, String correlationId, Map<String, Object> input, String event, Map<String, String> taskToDomain) {
        return workflowExecutor.startWorkflow(workflowName, version, correlationId, input, null, event, taskToDomain);
    }


}
