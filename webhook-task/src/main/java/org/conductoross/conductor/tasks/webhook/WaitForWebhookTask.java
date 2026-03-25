/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.tasks.webhook;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_WAIT_FOR_WEBHOOK;

/**
 * System task implementation for {@code WAIT_FOR_WEBHOOK}.
 *
 * <p>When a workflow reaches this task it immediately enters {@link TaskModel.Status#IN_PROGRESS}
 * and registers itself with {@link WebhookTaskDAO} so that a matching inbound webhook event can
 * locate and complete it. The task remains in-progress until:
 *
 * <ul>
 *   <li>A matching webhook event arrives and calls {@link #complete}, or
 *   <li>The workflow is cancelled, in which case {@link #cancel} is called.
 * </ul>
 *
 * <p>Hash computation and the inbound webhook REST endpoint are implemented in a separate layer
 * (see {@code IncomingWebhookResource}). This class is responsible only for lifecycle management
 * and DAO registration.
 */
@Component(TASK_TYPE_WAIT_FOR_WEBHOOK)
public class WaitForWebhookTask extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(WaitForWebhookTask.class);

    /** Input parameter key containing the JSONPath match expressions map. */
    public static final String MATCHES_INPUT = "matches";

    private final WebhookTaskDAO webhookTaskDAO;

    public WaitForWebhookTask(WebhookTaskDAO webhookTaskDAO) {
        super(TASK_TYPE_WAIT_FOR_WEBHOOK);
        this.webhookTaskDAO = webhookTaskDAO;
    }

    /**
     * Puts the task {@code IN_PROGRESS} and registers it with {@link WebhookTaskDAO} under the
     * routing hash computed from its resolved {@code matches} input.
     *
     * <p>The hash format is {@code workflowName;version;taskRefName;value1;value2...} (values in
     * sorted JSONPath-key order). It is also stored in the task's input data under {@code
     * __webhookHash} so that {@link #cancel} can deregister the task without recomputing.
     *
     * <p>If the task has no {@code matches} input it is still parked {@code IN_PROGRESS} but
     * nothing is registered — it will never be completed by a webhook event.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        Map<String, Object> matches = (Map<String, Object>) task.getInputData().get(MATCHES_INPUT);
        if (matches != null && !matches.isEmpty()) {
            int version = workflow.getWorkflowDefinition().getVersion();
            String hash =
                    WebhookHashingService.computeTaskRegistrationHash(
                            workflow.getWorkflowName(),
                            version,
                            task.getReferenceTaskName(),
                            matches);
            task.getInputData().put("__webhookHash", hash);
            // Register in DAO BEFORE marking IN_PROGRESS — if the DAO call fails, the task
            // status remains unchanged and the engine can retry.  Matches Orkes Enterprise order.
            webhookTaskDAO.put(hash, task.getTaskId());
            LOGGER.debug(
                    "WAIT_FOR_WEBHOOK task {} registered with hash={} in workflow {}",
                    task.getTaskId(),
                    hash,
                    workflow.getWorkflowId());
        } else {
            LOGGER.warn(
                    "WAIT_FOR_WEBHOOK task {} has no 'matches' input — will never be completed by a webhook event",
                    task.getTaskId());
        }
        task.setStatus(TaskModel.Status.IN_PROGRESS);
    }

    /**
     * Called on every evaluation cycle. Returns false — this task only completes via an inbound
     * webhook event, never by polling.
     */
    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        return false;
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        task.setStatus(TaskModel.Status.CANCELED);
        String hash = (String) task.getInputData().get("__webhookHash");
        if (hash != null) {
            webhookTaskDAO.remove(hash, task.getTaskId());
            LOGGER.debug(
                    "WAIT_FOR_WEBHOOK task {} cancelled and removed from DAO (hash={})",
                    task.getTaskId(),
                    hash);
        }
    }

    /**
     * Completes the task with the inbound webhook payload as output. Called by the webhook REST
     * layer after matching this task to an inbound event.
     *
     * <p>The payload keys are written directly into the task's output data (not wrapped under a
     * {@code "payload"} key), matching Orkes Enterprise behavior so that workflow variable
     * references like {@code ${taskRef.output.event.type}} work as expected.
     *
     * @param task the task to complete
     * @param payload the parsed body of the inbound webhook request
     * @param hash the routing hash, used to deregister the task from the DAO
     */
    public void complete(TaskModel task, Map<String, Object> payload, String hash) {
        task.setStatus(TaskModel.Status.COMPLETED);
        task.getOutputData().putAll(payload);
        webhookTaskDAO.remove(hash, task.getTaskId());
        LOGGER.debug(
                "WAIT_FOR_WEBHOOK task {} completed via webhook (hash={})", task.getTaskId(), hash);
    }

    /**
     * Returns {@code false} — the task parks itself in {@link TaskModel.Status#IN_PROGRESS} and is
     * completed externally by an inbound webhook event. There is no work for the decider to poll.
     * Matches Orkes Enterprise behaviour (no {@code isAsync()} override on their {@code Webhook}
     * system task).
     */
    @Override
    public boolean isAsync() {
        return false;
    }
}
