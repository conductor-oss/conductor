/*
 * Copyright 2022 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.test.listener;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;

import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.Task.Status.COMPLETED;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest(
        properties = {
            "conductor.workflow-status-listener.type=queue_publisher",
            "conductor.workflow-status-listener.queue-publisher.successQueue=dummy",
            "conductor.workflow-status-listener.queue-publisher.failureQueue=dummy",
            "conductor.workflow-status-listener.queue-publisher.finalizeQueue=final"
        })
@TestPropertySource(locations = "classpath:application-integrationtest.properties")
public class WorkflowStatusPublisherIntegrationTest {

    private final String CALLBACK_QUEUE = "dummy";
    private final String FINALIZED_QUEUE = "final";
    private static final String LINEAR_WORKFLOW_T1_T2 = "junit_test_wf";
    private static final int WORKFLOW_VERSION = 1;
    private static final String INCOMPLETION_REASON = "test reason";
    private static final String DEFAULT_OWNER_EMAIL = "test@harness.com";

    @Autowired private ObjectMapper objectMapper;

    @Autowired QueueDAO queueDAO;

    @Autowired protected MetadataService metadataService;

    @Autowired protected ExecutionService workflowExecutionService;

    @Autowired protected WorkflowExecutor workflowExecutor;

    @Before
    public void setUp() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("junit_task_1");
        taskDef.setTimeoutSeconds(120);
        taskDef.setResponseTimeoutSeconds(120);
        taskDef.setRetryCount(1);
        taskDef.setOwnerEmail(DEFAULT_OWNER_EMAIL);
        metadataService.registerTaskDef(Collections.singletonList(taskDef));
    }

    @After
    public void cleanUp() {
        List<String> workflows =
                metadataService.getWorkflowDefs().stream()
                        .map(WorkflowDef::getName)
                        .collect(Collectors.toList());
        for (String wfName : workflows) {
            List<String> running =
                    workflowExecutionService.getRunningWorkflows(wfName, WORKFLOW_VERSION);
            for (String wfid : running) {
                workflowExecutor.terminateWorkflow(wfid, "cleanup");
            }
        }
        queueDAO.queuesDetail().keySet().forEach(queueDAO::flush);
    }

    @Test
    public void testListenerOnTerminatedWorkflow() throws IOException {
        String id =
                startOrLoadWorkflowExecution(
                        LINEAR_WORKFLOW_T1_T2,
                        1,
                        "testWorkflowTerminatedListener",
                        new HashMap<>());
        workflowExecutor.terminateWorkflow(id, INCOMPLETION_REASON);

        List<Message> callbackMessages = queueDAO.pollMessages(CALLBACK_QUEUE, 1, 200);
        queueDAO.ack(CALLBACK_QUEUE, callbackMessages.get(0).getId());

        WorkflowSummary payload =
                objectMapper.readValue(callbackMessages.get(0).getPayload(), WorkflowSummary.class);
        assertEquals(id, callbackMessages.get(0).getId());
        assertEquals(LINEAR_WORKFLOW_T1_T2, payload.getWorkflowType());
        assertEquals("testWorkflowTerminatedListener", payload.getCorrelationId());
        assertEquals(Workflow.WorkflowStatus.TERMINATED, payload.getStatus());
        assertEquals(INCOMPLETION_REASON, payload.getReasonForIncompletion());

        // check finalized queue
        callbackMessages = queueDAO.pollMessages(FINALIZED_QUEUE, 1, 200);
        queueDAO.ack(CALLBACK_QUEUE, callbackMessages.get(0).getId());

        payload =
                objectMapper.readValue(callbackMessages.get(0).getPayload(), WorkflowSummary.class);
        assertEquals(id, callbackMessages.get(0).getId());
        assertEquals(LINEAR_WORKFLOW_T1_T2, payload.getWorkflowType());
        assertEquals("testWorkflowTerminatedListener", payload.getCorrelationId());
        assertEquals(Workflow.WorkflowStatus.TERMINATED, payload.getStatus());
        assertEquals(INCOMPLETION_REASON, payload.getReasonForIncompletion());
    }

    @Test
    public void testListenerOnCompletedWorkflow() throws IOException, InterruptedException {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(LINEAR_WORKFLOW_T1_T2);
        workflowDef.setDescription(workflowDef.getName());
        workflowDef.setVersion(WORKFLOW_VERSION);
        workflowDef.setSchemaVersion(2);
        workflowDef.setOwnerEmail(DEFAULT_OWNER_EMAIL);
        workflowDef.setWorkflowStatusListenerEnabled(true);
        LinkedList<WorkflowTask> wftasks = new LinkedList<>();

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("junit_task_1");
        wft1.setTaskReferenceName("t1");

        wftasks.add(wft1);
        workflowDef.setTasks(wftasks);

        metadataService.updateWorkflowDef(Collections.singletonList(workflowDef));

        String id =
                startOrLoadWorkflowExecution(
                        workflowDef.getName(), 1, "testWorkflowCompletedListener", new HashMap<>());

        List<Task> tasks = workflowExecutionService.getTasks("junit_task_1", null, 1);
        tasks.get(0).setStatus(COMPLETED);
        workflowExecutionService.updateTask(new TaskResult(tasks.get(0)));

        checkIfWorkflowIsCompleted(id);

        List<Message> callbackMessages = queueDAO.pollMessages(CALLBACK_QUEUE, 1, 200);
        queueDAO.ack(CALLBACK_QUEUE, callbackMessages.get(0).getId());

        WorkflowSummary payload =
                objectMapper.readValue(callbackMessages.get(0).getPayload(), WorkflowSummary.class);
        assertEquals(id, callbackMessages.get(0).getId());
        assertEquals(LINEAR_WORKFLOW_T1_T2, payload.getWorkflowType());
        assertEquals("testWorkflowCompletedListener", payload.getCorrelationId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, payload.getStatus());

        // check finalized queue
        callbackMessages = queueDAO.pollMessages(FINALIZED_QUEUE, 1, 200);
        queueDAO.ack(CALLBACK_QUEUE, callbackMessages.get(0).getId());

        payload =
                objectMapper.readValue(callbackMessages.get(0).getPayload(), WorkflowSummary.class);
        assertEquals(id, callbackMessages.get(0).getId());
        assertEquals(LINEAR_WORKFLOW_T1_T2, payload.getWorkflowType());
        assertEquals("testWorkflowCompletedListener", payload.getCorrelationId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, payload.getStatus());
    }

    @SuppressWarnings("BusyWait")
    private void checkIfWorkflowIsCompleted(String id) throws InterruptedException {
        int statusRetrieveAttempts = 0;
        while (workflowExecutor.getWorkflow(id, false).getStatus()
                != WorkflowModel.Status.COMPLETED) {
            if (statusRetrieveAttempts > 5) {
                break;
            }
            Thread.sleep(100);
            statusRetrieveAttempts++;
        }
    }

    private String startOrLoadWorkflowExecution(
            String workflowName, int version, String correlationId, Map<String, Object> input) {
        return workflowExecutor.startWorkflow(workflowName, version, correlationId, input, null);
    }
}
