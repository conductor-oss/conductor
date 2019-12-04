/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.tests.listener;

import static com.netflix.conductor.common.metadata.tasks.Task.Status.COMPLETED;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(StatusPublisherTestRunner.class)
public class WorkflowStatusPublisherIntegrationTest {

    private String CALLBACK_QUEUE =  "dummy";
    private static final String LINEAR_WORKFLOW_T1_T2 = "junit_test_wf";
    private static final int WORKFLOW_VERSION = 1;
    private static final String INCOMPLETION_REASON = "test reason";

    @Inject
    private ObjectMapper mapper;

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
        String id = startOrLoadWorkflowExecution(LINEAR_WORKFLOW_T1_T2, 1, "testWorkflowTerminatedListener", new HashMap<>(), null, null);
        workflowExecutor.terminateWorkflow(id, INCOMPLETION_REASON);

        List<Message> callbackMessages = queueDAO.pollMessages(CALLBACK_QUEUE, 1, 200);
        queueDAO.ack(CALLBACK_QUEUE, callbackMessages.get(0).getId());

        WorkflowSummary payload = mapper.readValue(callbackMessages.get(0).getPayload(), WorkflowSummary.class);

        assertEquals(id, callbackMessages.get(0).getId());
        assertEquals(LINEAR_WORKFLOW_T1_T2, payload.getWorkflowType());
        assertEquals("testWorkflowTerminatedListener", payload.getCorrelationId());
        assertEquals(Workflow.WorkflowStatus.TERMINATED, payload.getStatus());
        assertEquals(INCOMPLETION_REASON, payload.getReasonForIncompletion());
    }

    @Test
    public void testListenerOnCompletedWorkflow() throws IOException, InterruptedException {
        clearWorkflows();

        WorkflowDef def = new WorkflowDef();
        def.setName(LINEAR_WORKFLOW_T1_T2);
        def.setDescription(def.getName());
        def.setVersion(WORKFLOW_VERSION);
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

        checkIfWorkflowIsCompleted(id);

        List<Message> callbackMessages = queueDAO.pollMessages(CALLBACK_QUEUE, 1, 200);
        queueDAO.ack(CALLBACK_QUEUE, callbackMessages.get(0).getId());

        WorkflowSummary payload = mapper.readValue(callbackMessages.get(0).getPayload(), WorkflowSummary.class);

        assertEquals(id, callbackMessages.get(0).getId());
        assertEquals(LINEAR_WORKFLOW_T1_T2, payload.getWorkflowType());
        assertEquals("testWorkflowCompletedListener", payload.getCorrelationId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, payload.getStatus());
    }



    private void checkIfWorkflowIsCompleted(String id) throws InterruptedException {
        int statusRetrieveAttempts = 0;
        while (workflowExecutor.getWorkflow(id, false).getStatus() != Workflow.WorkflowStatus.COMPLETED) {
            if (statusRetrieveAttempts > 5) {
                break;
            }
            Thread.sleep(100);
            statusRetrieveAttempts++;
        }
    }

    @After
    public void clearWorkflows() {
        List<String> workflows = metadataService.getWorkflowDefs().stream()
                .map(WorkflowDef::getName)
                .collect(Collectors.toList());
        for (String wfName : workflows) {
            List<String> running = workflowExecutionService.getRunningWorkflows(wfName, WORKFLOW_VERSION);
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
