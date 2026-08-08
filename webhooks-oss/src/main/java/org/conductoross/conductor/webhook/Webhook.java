/*
 * Copyright 2022 Conductor Authors.
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

import org.conductoross.conductor.service.webhook.WebhookTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.conductoross.conductor.service.webhook.WebhookTaskService.Constants.WAIT_FOR_WEBHOOK;

@Component(WAIT_FOR_WEBHOOK)
public class Webhook extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(Webhook.class);

    private final WebhookTaskService webhookTaskService;

    public Webhook(WebhookTaskService webhookTaskService) {
        super(WAIT_FOR_WEBHOOK);
        this.webhookTaskService = webhookTaskService;
    }

    @Override
    public void start(
            WorkflowModel workflow, TaskModel taskModel, WorkflowExecutor workflowExecutor) {
        webhookTaskService.put(taskModel, workflow.getWorkflowVersion());
        taskModel.setStatus(TaskModel.Status.IN_PROGRESS);
        LOGGER.debug("Task {} is put in the queue", taskModel.getTaskId());
    }

    /**
     * Mirror of {@link #start}'s put — remove the correlation entry when the task is cancelled
     * (workflow terminate, parent-cancel, etc.) so the hash set doesn't accumulate orphan task ids.
     *
     * <p>Without this the only cleanup path is {@code WebhookWorker.handleEvent} firing on an
     * incoming event match. Workflows terminated before a match would otherwise leave their taskId
     * in the correlation set indefinitely. See orkes-io/orkes-conductor#3663.
     */
    @Override
    public void cancel(
            WorkflowModel workflow, TaskModel taskModel, WorkflowExecutor workflowExecutor) {
        try {
            webhookTaskService.remove(taskModel, workflow.getWorkflowVersion());
            LOGGER.debug(
                    "Task {} removed from webhook correlation set on cancel",
                    taskModel.getTaskId());
        } catch (Exception e) {
            LOGGER.warn(
                    "Failed to remove webhook correlation for task {} (workflow {}); orphan entry may remain",
                    taskModel.getTaskId(),
                    workflow.getWorkflowId(),
                    e);
        }
    }
}
