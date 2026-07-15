/*
 * Copyright 2026 Conductor Authors.
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
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.service.TaskService;
import com.netflix.conductor.service.WorkflowService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link AgentService#respond} and its symmetry with {@link AgentService#getStatus}:
 * every wait {@code getStatus()} reports ({@code isWaiting=true} for pending HUMAN or
 * PULL_WORKFLOW_MESSAGES tasks) must be answerable through {@code respond()}. HUMAN waits complete
 * via {@code taskService.updateTask}; message-pull waits are delivered through the workflow message
 * queue and woken with a {@code decide} (mirroring {@code WorkflowMessageQueueResource}).
 *
 * <p>No mock frameworks (per AGENTS.md): collaborators are hand-written fakes — small JDK proxies
 * for the wide {@code WorkflowService}/{@code TaskService}/{@code WorkflowExecutor} interfaces and
 * a direct {@link WorkflowMessageQueueDAO} implementation.
 */
class AgentServiceRespondTest {

    // ── Hand-written collaborators ─────────────────────────────────────

    private static Workflow workflowWithPendingTask(String taskType) {
        Task task = new Task();
        task.setTaskId("task-1");
        task.setTaskType(taskType);
        task.setReferenceTaskName("waiting_ref");
        task.setStatus(Task.Status.IN_PROGRESS);

        Workflow workflow = new Workflow();
        workflow.setWorkflowId("wf-123");
        workflow.setStatus(Workflow.WorkflowStatus.RUNNING);
        workflow.setTasks(new ArrayList<>(List.of(task)));
        return workflow;
    }

    /** WorkflowService stub: only getExecutionStatus is used by respond()/getStatus(). */
    private static WorkflowService workflowService(Workflow workflow) {
        return (WorkflowService)
                Proxy.newProxyInstance(
                        AgentServiceRespondTest.class.getClassLoader(),
                        new Class<?>[] {WorkflowService.class},
                        (proxy, method, args) -> {
                            if ("getExecutionStatus".equals(method.getName())) {
                                return workflow;
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
    }

    /** TaskService stub recording updateTask calls. */
    private static TaskService taskService(List<TaskResult> updates) {
        return (TaskService)
                Proxy.newProxyInstance(
                        AgentServiceRespondTest.class.getClassLoader(),
                        new Class<?>[] {TaskService.class},
                        (proxy, method, args) -> {
                            if ("updateTask".equals(method.getName())) {
                                updates.add((TaskResult) args[0]);
                                return null;
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
    }

    /** WorkflowExecutor stub recording decide calls. */
    private static WorkflowExecutor workflowExecutor(List<String> decided) {
        return (WorkflowExecutor)
                Proxy.newProxyInstance(
                        AgentServiceRespondTest.class.getClassLoader(),
                        new Class<?>[] {WorkflowExecutor.class},
                        (proxy, method, args) -> {
                            if ("decide".equals(method.getName())) {
                                decided.add((String) args[0]);
                                return null;
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
    }

    private static final class RecordingMessageQueueDAO implements WorkflowMessageQueueDAO {
        String lastWorkflowId;
        WorkflowMessage lastMessage;

        @Override
        public void push(String workflowId, WorkflowMessage message) {
            this.lastWorkflowId = workflowId;
            this.lastMessage = message;
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

    private static AgentService newAgentService(
            WorkflowService workflowService,
            TaskService taskService,
            WorkflowExecutor workflowExecutor,
            WorkflowMessageQueueDAO queueDAO) {
        AgentService service =
                new AgentService(
                        null, // agentCompiler
                        null, // normalizerRegistry
                        null, // executionDAO
                        null, // metadataDAO
                        workflowService,
                        taskService,
                        workflowExecutor,
                        null, // streamRegistry
                        null, // skillRegistryService
                        null); // metadataService
        service.workflowMessageQueueDAO = queueDAO;
        return service;
    }

    // ── Tests ─────────────────────────────────────────────────────────

    // getStatus() reports the pull wait — proving the wait is visible on the status surface.
    @Test
    void getStatus_reportsWaiting_forPullWorkflowMessagesTask() {
        Workflow workflow = workflowWithPendingTask("PULL_WORKFLOW_MESSAGES");
        AgentService service =
                newAgentService(
                        workflowService(workflow),
                        taskService(new ArrayList<>()),
                        workflowExecutor(new ArrayList<>()),
                        new RecordingMessageQueueDAO());

        Map<String, Object> status = service.getStatus("wf-123");

        assertEquals(Boolean.TRUE, status.get("isWaiting"));
    }

    // ...and respond() must be able to answer it: delivered via the queue + a decide, never
    // force-completed with updateTask (the pull task pops its input from the queue).
    @Test
    void respond_pullWorkflowMessages_deliversResponseViaMessageQueue() {
        Workflow workflow = workflowWithPendingTask("PULL_WORKFLOW_MESSAGES");
        List<TaskResult> updates = new ArrayList<>();
        List<String> decided = new ArrayList<>();
        RecordingMessageQueueDAO queue = new RecordingMessageQueueDAO();
        AgentService service =
                newAgentService(
                        workflowService(workflow),
                        taskService(updates),
                        workflowExecutor(decided),
                        queue);

        service.respond("wf-123", Map.of("result", "Tuesday 3pm"));

        assertEquals("wf-123", queue.lastWorkflowId);
        assertEquals(Map.of("result", "Tuesday 3pm"), queue.lastMessage.getPayload());
        assertEquals(List.of("wf-123"), decided);
        assertTrue(updates.isEmpty(), "a pull wait must not be completed via updateTask");
    }

    // HUMAN waits keep the existing channel: completed directly via updateTask.
    @Test
    void respond_human_completesTaskViaUpdateTask() {
        Workflow workflow = workflowWithPendingTask("HUMAN");
        List<TaskResult> updates = new ArrayList<>();
        RecordingMessageQueueDAO queue = new RecordingMessageQueueDAO();
        AgentService service =
                newAgentService(
                        workflowService(workflow),
                        taskService(updates),
                        workflowExecutor(new ArrayList<>()),
                        queue);

        service.respond("wf-123", Map.of("answer", "yes"));

        assertEquals(1, updates.size());
        assertEquals("task-1", updates.get(0).getTaskId());
        assertEquals(TaskResult.Status.COMPLETED, updates.get(0).getStatus());
        assertEquals("yes", updates.get(0).getOutputData().get("answer"));
        assertNull(queue.lastMessage, "a HUMAN wait must not go through the message queue");
    }

    // A pull wait with the queue disabled fails with an error pointing at the queue API instead
    // of the misleading "No pending HUMAN task" message.
    @Test
    void respond_pullWorkflowMessages_withoutQueueEnabled_throwsInformativeError() {
        Workflow workflow = workflowWithPendingTask("PULL_WORKFLOW_MESSAGES");
        AgentService service =
                newAgentService(
                        workflowService(workflow),
                        taskService(new ArrayList<>()),
                        workflowExecutor(new ArrayList<>()),
                        null); // queue not enabled

        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> service.respond("wf-123", Map.of("result", "x")));

        assertTrue(
                e.getMessage().contains("/api/workflow/{workflowId}/messages"),
                "error should point to the message-push API; was: " + e.getMessage());
    }

    // No pending wait at all still throws.
    @Test
    void respond_noPendingTask_throws() {
        Workflow workflow = workflowWithPendingTask("HUMAN");
        workflow.getTasks().get(0).setStatus(Task.Status.COMPLETED); // nothing pending
        AgentService service =
                newAgentService(
                        workflowService(workflow),
                        taskService(new ArrayList<>()),
                        workflowExecutor(new ArrayList<>()),
                        new RecordingMessageQueueDAO());

        assertThrows(
                IllegalStateException.class, () -> service.respond("wf-123", Map.of("x", "y")));
    }
}
