/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.service.ExecutionService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real {@link AgentService#getStatus(String)} / {@link AgentService#respond(String,
 * Map)} logic to pin down the WAITING/resume contract, in particular that a {@code
 * PULL_WORKFLOW_MESSAGES} wait surfaced by {@code getStatus} can actually be resumed by {@code
 * respond}.
 *
 * <p>Uses hand-written fakes/stubs only (project convention: no mock frameworks). {@link
 * WorkflowExecutor} is stubbed via a JDK dynamic proxy that records {@code decide} calls; {@link
 * ExecutionService} is subclassed with a scripted workflow; {@link WorkflowMessageQueueDAO} is a
 * hand-written recording fake.
 */
class AgentServiceRespondTest {

    private static final String EXEC_ID = "wf-123";

    // ── Hand-written fakes ────────────────────────────────────────────

    /** Records pushes and decide() nudges; pops/size/delete are unused here. */
    private static final class RecordingMessageQueueDAO implements WorkflowMessageQueueDAO {
        final List<String> pushedWorkflowIds = new ArrayList<>();
        final List<WorkflowMessage> pushedMessages = new ArrayList<>();

        @Override
        public void push(String workflowId, WorkflowMessage message) {
            pushedWorkflowIds.add(workflowId);
            pushedMessages.add(message);
        }

        @Override
        public List<WorkflowMessage> pop(String workflowId, int maxCount) {
            return List.of();
        }

        @Override
        public long size(String workflowId) {
            return 0;
        }

        @Override
        public void delete(String workflowId) {}
    }

    /** Serves a scripted workflow and records HUMAN-task completions. */
    private static final class ScriptedExecutionService extends ExecutionService {
        private final Workflow workflow;
        final List<TaskResult> updatedTasks = new ArrayList<>();

        ScriptedExecutionService(Workflow workflow) {
            super(null, null, null, new ConductorProperties(), null, null, null, null, null);
            this.workflow = workflow;
        }

        @Override
        public Workflow getExecutionStatus(String workflowId, boolean includeTasks) {
            return workflow;
        }

        @Override
        public com.netflix.conductor.model.TaskModel updateTask(TaskResult taskResult) {
            updatedTasks.add(taskResult);
            return null;
        }
    }

    /** JDK-proxy stub of WorkflowExecutor that records decide() targets. */
    private static WorkflowExecutor recordingWorkflowExecutor(List<String> decidedWorkflowIds) {
        return (WorkflowExecutor)
                Proxy.newProxyInstance(
                        AgentServiceRespondTest.class.getClassLoader(),
                        new Class<?>[] {WorkflowExecutor.class},
                        (proxy, method, args) -> {
                            if ("decide".equals(method.getName()) && args != null) {
                                decidedWorkflowIds.add((String) args[0]);
                            }
                            return null;
                        });
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static Workflow workflowWithPendingTask(String taskType) {
        Task task = new Task();
        task.setTaskType(taskType);
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setReferenceTaskName(taskType.toLowerCase() + "_ref");
        task.setTaskId("task-1");
        task.setInputData(new java.util.HashMap<>(Map.of("tool_name", "pull_workflow_messages")));

        Workflow workflow = new Workflow();
        workflow.setWorkflowId(EXEC_ID);
        workflow.setStatus(Workflow.WorkflowStatus.RUNNING);
        workflow.setTasks(new ArrayList<>(List.of(task)));
        return workflow;
    }

    private static AgentService newAgentService(
            ExecutionService executionService,
            WorkflowExecutor workflowExecutor,
            WorkflowMessageQueueDAO queueDAO) {
        AgentService service =
                new AgentService(
                        null, // agentCompiler
                        null, // normalizerRegistry
                        null, // executionDAO
                        null, // metadataDAO
                        workflowExecutor,
                        null, // workflowService
                        null, // streamRegistry
                        executionService,
                        null, // providerValidator
                        null, // skillRegistryService
                        null, // idGenerator
                        null); // metadataService
        service.workflowMessageQueueDAO = queueDAO;
        return service;
    }

    // ── Tests ─────────────────────────────────────────────────────────

    @Test
    void getStatus_reportsWaiting_forPullWorkflowMessagesTask() {
        Workflow workflow = workflowWithPendingTask("PULL_WORKFLOW_MESSAGES");
        AgentService service =
                newAgentService(
                        new ScriptedExecutionService(workflow),
                        recordingWorkflowExecutor(new ArrayList<>()),
                        new RecordingMessageQueueDAO());

        Map<String, Object> status = service.getStatus(EXEC_ID);

        assertEquals(Boolean.TRUE, status.get("isWaiting"));
        @SuppressWarnings("unchecked")
        Map<String, Object> pendingTool = (Map<String, Object>) status.get("pendingTool");
        assertEquals("pull_workflow_messages_ref", pendingTool.get("taskRefName"));
    }

    @Test
    void respond_pullWorkflowMessages_deliversResponseViaMessageQueue() {
        // getStatus advertises this wait; respond must be able to satisfy it.
        Workflow workflow = workflowWithPendingTask("PULL_WORKFLOW_MESSAGES");
        ScriptedExecutionService execService = new ScriptedExecutionService(workflow);
        RecordingMessageQueueDAO queueDAO = new RecordingMessageQueueDAO();
        List<String> decided = new ArrayList<>();
        AgentService service =
                newAgentService(execService, recordingWorkflowExecutor(decided), queueDAO);

        service.respond(EXEC_ID, Map.of("result", "the answer"));

        // The response is delivered through the message queue (the pull task's real
        // input channel), NOT by force-completing the task via updateTask.
        assertEquals(1, queueDAO.pushedMessages.size(), "expected exactly one message pushed");
        assertEquals(EXEC_ID, queueDAO.pushedWorkflowIds.get(0));
        WorkflowMessage pushed = queueDAO.pushedMessages.get(0);
        assertEquals(EXEC_ID, pushed.getWorkflowId());
        assertEquals("the answer", pushed.getPayload().get("result"));
        assertTrue(execService.updatedTasks.isEmpty(), "pull task must not be force-completed");
        assertEquals(List.of(EXEC_ID), decided, "waiting task should be nudged via decide()");
    }

    @Test
    void respond_human_completesTaskViaUpdateTask() {
        Workflow workflow = workflowWithPendingTask("HUMAN");
        ScriptedExecutionService execService = new ScriptedExecutionService(workflow);
        RecordingMessageQueueDAO queueDAO = new RecordingMessageQueueDAO();
        AgentService service =
                newAgentService(
                        execService, recordingWorkflowExecutor(new ArrayList<>()), queueDAO);

        service.respond(EXEC_ID, Map.of("approved", true));

        assertEquals(1, execService.updatedTasks.size(), "HUMAN task completed via updateTask");
        TaskResult result = execService.updatedTasks.get(0);
        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals(Boolean.TRUE, result.getOutputData().get("approved"));
        assertTrue(queueDAO.pushedMessages.isEmpty(), "HUMAN path must not touch the queue");
    }

    @Test
    void respond_pullWorkflowMessages_withoutQueueEnabled_throwsInformativeError() {
        Workflow workflow = workflowWithPendingTask("PULL_WORKFLOW_MESSAGES");
        AgentService service =
                newAgentService(
                        new ScriptedExecutionService(workflow),
                        recordingWorkflowExecutor(new ArrayList<>()),
                        null); // WMQ feature disabled

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> service.respond(EXEC_ID, Map.of("result", "x")));
        assertTrue(
                ex.getMessage().contains("/messages"),
                "error should point to the message-push API; was: " + ex.getMessage());
    }

    @Test
    void respond_noPendingTask_throws() {
        Workflow workflow = new Workflow();
        workflow.setWorkflowId(EXEC_ID);
        workflow.setStatus(Workflow.WorkflowStatus.RUNNING);
        workflow.setTasks(new ArrayList<>());
        AgentService service =
                newAgentService(
                        new ScriptedExecutionService(workflow),
                        recordingWorkflowExecutor(new ArrayList<>()),
                        new RecordingMessageQueueDAO());

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> service.respond(EXEC_ID, Map.of("result", "x")));
        assertTrue(ex.getMessage().contains("No pending"));
    }
}
