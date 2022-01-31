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
package com.netflix.conductor.core.reconciliation;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.google.common.annotations.VisibleForTesting;

/**
 * A helper service that tries to keep ExecutionDAO and QueueDAO in sync, based on the task or
 * workflow state.
 *
 * <p>This service expects that the underlying Queueing layer implements {@link
 * QueueDAO#containsMessage(String, String)} method. This can be controlled with <code>
 * conductor.workflow-repair-service.enabled</code> property.
 */
@Service
@ConditionalOnProperty(name = "conductor.workflow-repair-service.enabled", havingValue = "true")
public class WorkflowRepairService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowRepairService.class);
    private final ExecutionDAO executionDAO;
    private final QueueDAO queueDAO;
    private final ConductorProperties properties;
    private SystemTaskRegistry systemTaskRegistry;

    /*
    For system task -> Verify the task isAsync() and not isAsyncComplete() or isAsyncComplete() in SCHEDULED state,
    and in SCHEDULED or IN_PROGRESS state. (Example: SUB_WORKFLOW tasks in SCHEDULED state)
    For simple task -> Verify the task is in SCHEDULED state.
    */
    private final Predicate<TaskModel> isTaskRepairable =
            task -> {
                if (systemTaskRegistry.isSystemTask(task.getTaskType())) { // If system task
                    WorkflowSystemTask workflowSystemTask =
                            systemTaskRegistry.get(task.getTaskType());
                    return workflowSystemTask.isAsync()
                            && (!workflowSystemTask.isAsyncComplete(task)
                                    || (workflowSystemTask.isAsyncComplete(task)
                                            && task.getStatus() == TaskModel.Status.SCHEDULED))
                            && (task.getStatus() == TaskModel.Status.IN_PROGRESS
                                    || task.getStatus() == TaskModel.Status.SCHEDULED);
                } else { // Else if simple task
                    return task.getStatus() == TaskModel.Status.SCHEDULED;
                }
            };

    public WorkflowRepairService(
            ExecutionDAO executionDAO,
            QueueDAO queueDAO,
            ConductorProperties properties,
            SystemTaskRegistry systemTaskRegistry) {
        this.executionDAO = executionDAO;
        this.queueDAO = queueDAO;
        this.properties = properties;
        this.systemTaskRegistry = systemTaskRegistry;
        LOGGER.info("WorkflowRepairService Initialized");
    }

    /**
     * Verify and repair if the workflowId exists in deciderQueue, and then if each scheduled task
     * has relevant message in the queue.
     */
    public boolean verifyAndRepairWorkflow(String workflowId, boolean includeTasks) {
        WorkflowModel workflow = executionDAO.getWorkflow(workflowId, includeTasks);
        AtomicBoolean repaired = new AtomicBoolean(false);
        repaired.set(verifyAndRepairDeciderQueue(workflow));
        if (includeTasks) {
            workflow.getTasks().forEach(task -> repaired.set(verifyAndRepairTask(task)));
        }
        return repaired.get();
    }

    /** Verify and repair tasks in a workflow. */
    public void verifyAndRepairWorkflowTasks(String workflowId) {
        WorkflowModel workflow = executionDAO.getWorkflow(workflowId, true);
        workflow.getTasks().forEach(this::verifyAndRepairTask);
        // repair the parent workflow if needed
        verifyAndRepairWorkflow(workflow.getParentWorkflowId());
    }

    /**
     * Verify and fix if Workflow decider queue contains this workflowId.
     *
     * @return true - if the workflow was queued for repair
     */
    private boolean verifyAndRepairDeciderQueue(WorkflowModel workflow) {
        if (!workflow.getStatus().isTerminal()) {
            return verifyAndRepairWorkflow(workflow.getWorkflowId());
        }
        return false;
    }

    /**
     * Verify if ExecutionDAO and QueueDAO agree for the provided task.
     *
     * @param task the task to be repaired
     * @return true - if the task was queued for repair
     */
    @VisibleForTesting
    boolean verifyAndRepairTask(TaskModel task) {
        if (isTaskRepairable.test(task)) {
            // Ensure QueueDAO contains this taskId
            String taskQueueName = QueueUtils.getQueueName(task);
            if (!queueDAO.containsMessage(taskQueueName, task.getTaskId())) {
                queueDAO.push(taskQueueName, task.getTaskId(), task.getCallbackAfterSeconds());
                LOGGER.info(
                        "Task {} in workflow {} re-queued for repairs",
                        task.getTaskId(),
                        task.getWorkflowInstanceId());
                Monitors.recordQueueMessageRepushFromRepairService(task.getTaskDefName());
                return true;
            }
        }
        return false;
    }

    private boolean verifyAndRepairWorkflow(String workflowId) {
        if (StringUtils.isNotEmpty(workflowId)) {
            String queueName = Utils.DECIDER_QUEUE;
            if (!queueDAO.containsMessage(queueName, workflowId)) {
                queueDAO.push(
                        queueName, workflowId, properties.getWorkflowOffsetTimeout().getSeconds());
                LOGGER.info("Workflow {} re-queued for repairs", workflowId);
                Monitors.recordQueueMessageRepushFromRepairService(queueName);
                return true;
            }
            return false;
        }
        return false;
    }
}
