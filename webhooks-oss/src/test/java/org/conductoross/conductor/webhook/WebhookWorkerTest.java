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
package org.conductoross.conductor.webhook;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.conductoross.conductor.service.webhook.TargetWorkflowCollector;
import org.conductoross.conductor.webhook.dao.memory.InMemoryWebhookDAO;
import org.conductoross.conductor.webhook.dao.memory.InMemoryWebhookTaskService;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.conductoross.conductor.webhook.model.WebhookExecutionHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Worker-internal tests. Uses real in-memory implementations of WebhookDAO, WebhookTaskService,
 * hashing service, and the target-collector — mocks only the deep infra (QueueDAO,
 * WorkflowExecutor, ExecutionDAOFacade, MetadataDAO, ParametersUtils). Per AGENTS.md preference:
 * real beans over mocks.
 *
 * <p>The full register→receive→dispatch end-to-end is covered by {@link WebhooksOssEndToEndTest}.
 * This class focuses on worker-specific semantics: handleMessage edge cases and pollAndExecute ack
 * behavior.
 */
@ExtendWith(MockitoExtension.class)
class WebhookWorkerTest {

    @Mock private QueueDAO queueDAO;
    @Mock private WorkflowExecutor workflowExecutor;
    @Mock private ExecutionDAOFacade executionDAOFacade;
    @Mock private MetadataDAO metadataDAO;
    @Mock private ParametersUtils parametersUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private InMemoryWebhookDAO webhookDAO;
    private InMemoryWebhookTaskService webhookTaskService;
    private TargetWorkflowCollector targetWorkflowCollector;
    private WebhookHashingService hashingService;
    private WebhookWorker worker;

    @BeforeEach
    void setUp() {
        webhookDAO = new InMemoryWebhookDAO(metadataDAO);
        webhookTaskService = new InMemoryWebhookTaskService();
        targetWorkflowCollector =
                new TargetWorkflowCollector(Map.<String, Evaluator>of(), parametersUtils);
        hashingService = new WebhookHashingService();
        WebhookWorkerProperties properties = new WebhookWorkerProperties();
        properties.setThreadCount(0); // disable polling thread
        worker =
                new WebhookWorker(
                        objectMapper,
                        queueDAO,
                        webhookDAO,
                        properties,
                        hashingService,
                        workflowExecutor,
                        webhookTaskService,
                        executionDAOFacade,
                        targetWorkflowCollector);
    }

    // ---------- pollAndExecute ----------

    @Test
    void pollAndExecute_emptyBatch_noop() {
        when(queueDAO.pop(eq(WebhookWorkerProperties.WEBHOOK_QUEUE), anyInt(), anyInt()))
                .thenReturn(List.of());

        worker.pollAndExecute();

        verify(queueDAO, never()).ack(anyString(), anyString());
    }

    @Test
    void pollAndExecute_success_acks() {
        // event exists but webhookConfig doesn't → handleMessage returns cleanly.
        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder().id("ev-success").webhookId("hook-missing").build();
        webhookDAO.createIncomingWebhookEvent("ev-success", event);

        when(queueDAO.pop(eq(WebhookWorkerProperties.WEBHOOK_QUEUE), anyInt(), anyInt()))
                .thenReturn(List.of("ev-success"));

        worker.pollAndExecute();

        verify(queueDAO).ack(WebhookWorkerProperties.WEBHOOK_QUEUE, "ev-success");
    }

    @Test
    void pollAndExecute_handleMessageThrows_doesNotAck() {
        // queueDAO returns a message id whose getWebhookEvent will throw.
        when(queueDAO.pop(eq(WebhookWorkerProperties.WEBHOOK_QUEUE), anyInt(), anyInt()))
                .thenReturn(List.of("ev-poison"));
        InMemoryWebhookDAO failingDao =
                new InMemoryWebhookDAO(metadataDAO) {
                    @Override
                    public IncomingWebhookEvent getWebhookEvent(String id) {
                        throw new RuntimeException("simulated DB blip");
                    }
                };
        WebhookWorker failingWorker =
                new WebhookWorker(
                        objectMapper,
                        queueDAO,
                        failingDao,
                        new WebhookWorkerProperties(),
                        hashingService,
                        workflowExecutor,
                        webhookTaskService,
                        executionDAOFacade,
                        targetWorkflowCollector);

        failingWorker.pollAndExecute();

        // Regression: prior impl acked in a finally block, silently dropping the event.
        verify(queueDAO, never()).ack(anyString(), anyString());
    }

    @Test
    void pollAndExecute_mixedBatch_acksOnlySuccesses() {
        // ev-ok: event present (no config) → handleMessage returns cleanly → ack.
        // ev-bad: getWebhookEvent throws → no ack.
        IncomingWebhookEvent okEvent =
                IncomingWebhookEvent.builder().id("ev-ok").webhookId("hook-missing").build();
        InMemoryWebhookDAO mixedDao =
                new InMemoryWebhookDAO(metadataDAO) {
                    @Override
                    public IncomingWebhookEvent getWebhookEvent(String id) {
                        if ("ev-bad".equals(id)) {
                            throw new RuntimeException("boom");
                        }
                        return super.getWebhookEvent(id);
                    }
                };
        mixedDao.createIncomingWebhookEvent("ev-ok", okEvent);

        when(queueDAO.pop(eq(WebhookWorkerProperties.WEBHOOK_QUEUE), anyInt(), anyInt()))
                .thenReturn(List.of("ev-ok", "ev-bad"));

        WebhookWorker w =
                new WebhookWorker(
                        objectMapper,
                        queueDAO,
                        mixedDao,
                        new WebhookWorkerProperties(),
                        hashingService,
                        workflowExecutor,
                        webhookTaskService,
                        executionDAOFacade,
                        targetWorkflowCollector);
        w.pollAndExecute();

        verify(queueDAO).ack(WebhookWorkerProperties.WEBHOOK_QUEUE, "ev-ok");
        verify(queueDAO, never()).ack(WebhookWorkerProperties.WEBHOOK_QUEUE, "ev-bad");
    }

    // ---------- handleMessage edge cases ----------

    @Test
    void handleMessage_eventNotFound_returnsEarly() {
        // Event was never stored; should return without throwing.
        worker.handleMessage("missing");

        verify(workflowExecutor, never()).startWorkflow(any(StartWorkflowInput.class));
    }

    @Test
    void handleMessage_configNotFound_returnsEarly() {
        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder()
                        .id("ev-1")
                        .webhookId("hook-missing")
                        .body("{}")
                        .build();
        webhookDAO.createIncomingWebhookEvent("ev-1", event);

        worker.handleMessage("ev-1");

        verify(workflowExecutor, never()).startWorkflow(any(StartWorkflowInput.class));
        assertThat(webhookDAO.getWebhookEvent("ev-1")).isNotNull(); // event NOT removed
    }

    @Test
    void handleMessage_workflowsToStart_invokesExecutor() {
        WebhookConfig config = registerConfig("hook-1", "my-hook", "alice", Map.of("wf-a", 1));
        when(metadataDAO.getWorkflowDef("wf-a", 1)).thenReturn(Optional.empty());

        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder()
                        .id("ev-1")
                        .webhookId(config.getId())
                        .body("{\"foo\":\"bar\"}")
                        .requestParams(Map.of())
                        .build();
        webhookDAO.createIncomingWebhookEvent("ev-1", event);

        // Configure workflowsToStart instead of receiverWorkflowNamesToVersions.
        config.setWorkflowsToStart(Map.of("wf-a", 1));
        config.setReceiverWorkflowNamesToVersions(null);

        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class))).thenReturn("wf-id");

        worker.handleMessage("ev-1");

        ArgumentCaptor<StartWorkflowInput> captor =
                ArgumentCaptor.forClass(StartWorkflowInput.class);
        verify(workflowExecutor).startWorkflow(captor.capture());
        StartWorkflowInput input = captor.getValue();
        assertThat(input.getName()).isEqualTo("wf-a");
        assertThat(input.getVersion()).isEqualTo(1);
        assertThat(input.getWorkflowInput()).containsEntry("foo", "bar");
        assertThat(input.getEvent()).isEqualTo("my-hook:ev-1");
        assertThat(webhookDAO.getWebhookEvent("ev-1")).isNull(); // event removed
    }

    @Test
    void handleMessage_workflowsToStart_nonIntegerVersion_skipped() {
        WebhookConfig config = registerConfig("hook-1", "my-hook", null, null);
        config.setWorkflowsToStart(Map.of("wf-a", "not-a-number"));

        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder()
                        .id("ev-1")
                        .webhookId(config.getId())
                        .body("{}")
                        .requestParams(Map.of())
                        .build();
        webhookDAO.createIncomingWebhookEvent("ev-1", event);

        worker.handleMessage("ev-1");

        verify(workflowExecutor, never()).startWorkflow(any(StartWorkflowInput.class));
    }

    @Test
    void handleMessage_matcherHit_completesWaitingTask() {
        WorkflowTask waitTask = new WorkflowTask();
        waitTask.setType("WAIT_FOR_WEBHOOK");
        waitTask.setTaskReferenceName("wait_ref");
        waitTask.setInputParameters(Map.of("matches", Map.of("event", "push")));
        WorkflowDef def = new WorkflowDef();
        def.setName("wf-a");
        def.setVersion(1);
        def.setTasks(List.of(waitTask));
        when(metadataDAO.getWorkflowDef("wf-a", 1)).thenReturn(Optional.of(def));

        WebhookConfig config = registerConfig("hook-1", "my-hook", null, Map.of("wf-a", 1));

        // Register a waiting task with the matching criteria, via the system task path.
        TaskModel waitingTask = new TaskModel();
        waitingTask.setTaskId("task-1");
        waitingTask.setWorkflowType("wf-a");
        waitingTask.setReferenceTaskName("wait_ref");
        waitingTask.setStatus(TaskModel.Status.IN_PROGRESS);
        waitingTask.setWorkflowInstanceId("wf-instance-1");
        waitingTask.setInputData(Map.of("matches", Map.of("event", "push")));
        webhookTaskService.put(waitingTask, 1);
        when(executionDAOFacade.getTaskModel("task-1")).thenReturn(waitingTask);

        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder()
                        .id("ev-1")
                        .webhookId(config.getId())
                        .body("{\"event\":\"push\"}")
                        .requestParams(Map.of())
                        .build();
        webhookDAO.createIncomingWebhookEvent("ev-1", event);

        worker.handleMessage("ev-1");

        ArgumentCaptor<TaskResult> resultCaptor = ArgumentCaptor.forClass(TaskResult.class);
        verify(workflowExecutor).updateTask(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getStatus()).isEqualTo(TaskResult.Status.COMPLETED);
        // Task removed from the hash store after completion.
        assertThat(webhookTaskService.get("wf-a;1;wait_ref;push")).isEmpty();
    }

    @Test
    void handleMessage_matcherHit_terminalTask_skipped() {
        WorkflowTask waitTask = new WorkflowTask();
        waitTask.setType("WAIT_FOR_WEBHOOK");
        waitTask.setTaskReferenceName("wait_ref");
        waitTask.setInputParameters(Map.of("matches", Map.of("event", "push")));
        WorkflowDef def = new WorkflowDef();
        def.setName("wf-a");
        def.setVersion(1);
        def.setTasks(List.of(waitTask));
        when(metadataDAO.getWorkflowDef("wf-a", 1)).thenReturn(Optional.of(def));

        WebhookConfig config = registerConfig("hook-1", "my-hook", null, Map.of("wf-a", 1));

        TaskModel done = new TaskModel();
        done.setTaskId("task-done");
        done.setWorkflowType("wf-a");
        done.setReferenceTaskName("wait_ref");
        done.setStatus(TaskModel.Status.COMPLETED);
        done.setInputData(Map.of("matches", Map.of("event", "push")));
        webhookTaskService.put(done, 1);
        when(executionDAOFacade.getTaskModel("task-done")).thenReturn(done);

        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder()
                        .id("ev-1")
                        .webhookId(config.getId())
                        .body("{\"event\":\"push\"}")
                        .requestParams(Map.of())
                        .build();
        webhookDAO.createIncomingWebhookEvent("ev-1", event);

        worker.handleMessage("ev-1");

        verify(workflowExecutor, never()).updateTask(any(TaskResult.class));
    }

    @Test
    void handleMessage_nonMapBody_inputWrappedUnderRequestKey() {
        WebhookConfig config = registerConfig("hook-1", "my-hook", "alice", null);
        config.setWorkflowsToStart(Map.of("wf-a", 1));

        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder()
                        .id("ev-1")
                        .webhookId(config.getId())
                        .body("[1,2,3]")
                        .requestParams(Map.of())
                        .build();
        webhookDAO.createIncomingWebhookEvent("ev-1", event);

        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class))).thenReturn("wf-id");

        worker.handleMessage("ev-1");

        ArgumentCaptor<StartWorkflowInput> captor =
                ArgumentCaptor.forClass(StartWorkflowInput.class);
        verify(workflowExecutor).startWorkflow(captor.capture());
        assertThat(captor.getValue().getWorkflowInput()).containsKey("request");
        assertThat(captor.getValue().getWorkflowInput().get("request")).isInstanceOf(List.class);
    }

    @Test
    void handleMessage_recordHistory_atCapacity_trimsOldestEntry() {
        WebhookWorkerProperties props = new WebhookWorkerProperties();
        props.setLastRunWorkflowIdSize(2);
        props.setThreadCount(0);
        WebhookWorker smallHistoryWorker =
                new WebhookWorker(
                        objectMapper,
                        queueDAO,
                        webhookDAO,
                        props,
                        hashingService,
                        workflowExecutor,
                        webhookTaskService,
                        executionDAOFacade,
                        targetWorkflowCollector);

        WebhookConfig config = registerConfig("hook-1", "my-hook", null, null);
        WebhookExecutionHistory old1 =
                WebhookExecutionHistory.builder()
                        .eventId("ev-old1")
                        .workflowIds(Set.of())
                        .payload("{}")
                        .build();
        WebhookExecutionHistory old2 =
                WebhookExecutionHistory.builder()
                        .eventId("ev-old2")
                        .workflowIds(Set.of())
                        .payload("{}")
                        .build();
        config.setWebhookExecutionHistory(new java.util.ArrayList<>(List.of(old1, old2)));

        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder()
                        .id("ev-new")
                        .webhookId(config.getId())
                        .body("{}")
                        .requestParams(Map.of())
                        .build();
        webhookDAO.createIncomingWebhookEvent("ev-new", event);

        smallHistoryWorker.handleMessage("ev-new");

        List<WebhookExecutionHistory> hist = config.getWebhookExecutionHistory();
        assertThat(hist).hasSize(2);
        assertThat(hist.get(0).getEventId()).isEqualTo("ev-new");
        assertThat(hist.get(1).getEventId()).isEqualTo("ev-old1");
    }

    private WebhookConfig registerConfig(
            String id, String name, String createdBy, Map<String, Integer> targetWorkflows) {
        WebhookConfig config = new WebhookConfig();
        config.setId(id);
        config.setName(name);
        config.setCreatedBy(createdBy);
        if (targetWorkflows != null) {
            config.setReceiverWorkflowNamesToVersions(targetWorkflows);
        }
        webhookDAO.createWebhook(id, config);
        webhookDAO.createMatchers(config, targetWorkflows);
        return config;
    }

    // Mockito imports
    static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
