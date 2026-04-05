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
     * Registers the task with {@link WebhookTaskDAO} so an inbound webhook can find and complete
     * it. Hash computation is deferred to the REST layer; this method stores the task ID under the
     * hash once computed.
     *
     * <p>NOTE: The hash is computed externally (by {@code WebhookHashUtils}) and passed back via
     * task input under {@code __webhookHash}. If the hash is not yet present (i.e., the REST layer
     * hasn't been called yet), the task simply waits.
     */
    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        LOGGER.debug(
                "WAIT_FOR_WEBHOOK task {} started in workflow {}",
                task.getTaskId(),
                workflow.getWorkflowId());
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
     * @param task the task to complete
     * @param webhookPayload the parsed body of the inbound webhook request
     * @param hash the routing hash, used to deregister the task from the DAO
     */
    public void complete(TaskModel task, Object webhookPayload, String hash) {
        task.setStatus(TaskModel.Status.COMPLETED);
        task.getOutputData().put("payload", webhookPayload);
        webhookTaskDAO.remove(hash, task.getTaskId());
        LOGGER.debug(
                "WAIT_FOR_WEBHOOK task {} completed via webhook (hash={})", task.getTaskId(), hash);
    }

    @Override
    public boolean isAsync() {
        return true;
    }
}
