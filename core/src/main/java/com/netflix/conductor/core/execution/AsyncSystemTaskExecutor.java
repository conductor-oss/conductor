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
package com.netflix.conductor.core.execution;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.ExecutionLockService;

@Component
public class AsyncSystemTaskExecutor {

    private final ExecutionDAOFacade executionDAOFacade;
    private final QueueDAO queueDAO;
    private final MetadataDAO metadataDAO;
    private final long queueTaskMessagePostponeSecs;
    private final long systemTaskCallbackTime;
    private final WorkflowExecutor workflowExecutor;
    private final ExecutionLockService executionLockService;
    private final ConductorProperties properties;

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncSystemTaskExecutor.class);

    public AsyncSystemTaskExecutor(
            ExecutionDAOFacade executionDAOFacade,
            QueueDAO queueDAO,
            MetadataDAO metadataDAO,
            ConductorProperties conductorProperties,
            WorkflowExecutor workflowExecutor,
            ExecutionLockService executionLockService) {
        this.executionDAOFacade = executionDAOFacade;
        this.queueDAO = queueDAO;
        this.metadataDAO = metadataDAO;
        this.workflowExecutor = workflowExecutor;
        this.executionLockService = executionLockService;
        this.properties = conductorProperties;
        this.systemTaskCallbackTime =
                conductorProperties.getSystemTaskWorkerCallbackDuration().getSeconds();
        this.queueTaskMessagePostponeSecs =
                conductorProperties.getTaskExecutionPostponeDuration().getSeconds();
    }

    /**
     * Executes and persists the results of an async {@link WorkflowSystemTask}.
     *
     * @param systemTask The {@link WorkflowSystemTask} to be executed.
     * @param taskId The id of the {@link TaskModel} object.
     */
    public void execute(WorkflowSystemTask systemTask, String taskId) {
        TaskModel task = loadTaskQuietly(taskId);
        if (task == null) {
            LOGGER.error("TaskId: {} could not be found while executing {}", taskId, systemTask);
            try {
                LOGGER.debug(
                        "Cleaning up dead task from queue message: taskQueue={}, taskId={}",
                        systemTask.getTaskType(),
                        taskId);
                queueDAO.remove(systemTask.getTaskType(), taskId);
            } catch (Exception e) {
                LOGGER.error(
                        "Failed to remove dead task from queue message: taskQueue={}, taskId={}",
                        systemTask.getTaskType(),
                        taskId);
            }
            return;
        }

        LOGGER.debug("Task: {} fetched from execution DAO for taskId: {}", task, taskId);
        String queueName = QueueUtils.getQueueName(task);
        if (task.getStatus().isTerminal()) {
            // Tune the SystemTaskWorkerCoordinator's queues - if the queue size is very big this
            // can happen!
            LOGGER.info("Task {}/{} was already completed.", task.getTaskType(), task.getTaskId());
            queueDAO.remove(queueName, task.getTaskId());
            return;
        }

        if (task.getStatus().equals(TaskModel.Status.SCHEDULED)) {
            if (executionDAOFacade.exceedsInProgressLimit(task)) {
                LOGGER.warn(
                        "Concurrent Execution limited for {}:{}", taskId, task.getTaskDefName());
                postponeQuietly(queueName, task);
                return;
            }
            if (task.getRateLimitPerFrequency() > 0
                    && executionDAOFacade.exceedsRateLimitPerFrequency(
                            task, metadataDAO.getTaskDef(task.getTaskDefName()))) {
                LOGGER.warn(
                        "RateLimit Execution limited for {}:{}, limit:{}",
                        taskId,
                        task.getTaskDefName(),
                        task.getRateLimitPerFrequency());
                postponeQuietly(queueName, task);
                return;
            }
        }

        boolean hasTaskExecutionCompleted = false;
        boolean shouldRemoveTaskFromQueue = false;
        String workflowId = task.getWorkflowInstanceId();
        // if we are here the Task object is updated and needs to be persisted regardless of an
        // exception
        try {
            WorkflowModel workflow =
                    executionDAOFacade.getWorkflowModel(
                            workflowId, systemTask.isTaskRetrievalRequired());

            if (workflow.getStatus().isTerminal()) {
                LOGGER.info(
                        "Workflow {} has been completed for {}/{}",
                        workflow.toShortString(),
                        systemTask,
                        task.getTaskId());
                if (!task.getStatus().isTerminal()) {
                    task.setStatus(TaskModel.Status.CANCELED);
                    task.setReasonForIncompletion(
                            String.format(
                                    "Workflow is in %s state", workflow.getStatus().toString()));
                }
                shouldRemoveTaskFromQueue = true;
                return;
            }

            LOGGER.debug(
                    "Executing {}/{} in {} state",
                    task.getTaskType(),
                    task.getTaskId(),
                    task.getStatus());

            boolean isTaskAsyncComplete = systemTask.isAsyncComplete(task);
            if (task.getStatus() == TaskModel.Status.SCHEDULED || !isTaskAsyncComplete) {
                task.incrementPollCount();
            }

            // Acquire workflow lock for SCHEDULED tasks to prevent concurrent execution
            boolean lockAcquired = false;
            ScheduledFuture<?> leaseRenewalFuture = null;
            ScheduledExecutorService renewalExecutor = null;

            if (properties != null && properties.isWorkflowExecutionLockEnabled()) {
                if (executionLockService != null && task.getStatus() == TaskModel.Status.SCHEDULED) {
                    lockAcquired = executionLockService.acquireLock(workflowId);
                    if (!lockAcquired) {
                        postponeQuietly(queueName, task);
                        return;
                    }

                    // Start lock renewal for long-running async system tasks to prevent expiration
                    if (systemTask.isAsync()) {
                        long leaseTime = properties.getLockLeaseTime().toMillis();
                        long renewalInterval = Math.max(leaseTime / 2, 30000);
                        long renewalTimeout = Math.max(leaseTime / 10, 1000);

                        renewalExecutor = Executors.newSingleThreadScheduledExecutor(
                            r -> {
                                Thread t = new Thread(r, "lock-renewal-" + workflowId + "-" + task.getTaskId());
                                t.setDaemon(true);
                                return t;
                            });

                        leaseRenewalFuture = renewalExecutor.scheduleAtFixedRate(
                            () -> {
                                executionLockService.acquireLock(workflowId, renewalTimeout, leaseTime);
                            },
                            renewalInterval, renewalInterval, TimeUnit.MILLISECONDS
                        );
                    }
                }
            }

            try {
                if (task.getStatus() == TaskModel.Status.SCHEDULED) {
                    task.setStartTime(System.currentTimeMillis());
                    Monitors.recordQueueWaitTime(task.getTaskType(), task.getQueueWaitTime());

                    // Set IN_PROGRESS status for async tasks to prevent redundant status updates
                    boolean isAsyncTask = systemTask.isAsync() && systemTask.isAsyncComplete(task);
                    if (isAsyncTask) {
                        task.setStatus(TaskModel.Status.IN_PROGRESS);
                        task.setWorkerId(Utils.getServerId());
                        executionDAOFacade.updateTask(task);
                    }

                    systemTask.start(workflow, task, workflowExecutor);
                } else if (task.getStatus() == TaskModel.Status.IN_PROGRESS) {
                    systemTask.execute(workflow, task, workflowExecutor);
                }
            } finally {
                // Stop lock renewal and release lock
                if (leaseRenewalFuture != null) {
                    leaseRenewalFuture.cancel(false);
                }
                if (renewalExecutor != null) {
                    renewalExecutor.shutdown();
                    try {
                        if (!renewalExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                            renewalExecutor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        renewalExecutor.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                }
                if (lockAcquired) {
                    executionLockService.releaseLock(workflowId);
                }
            }

            // Update message in Task queue based on Task status
            // Remove asyncComplete system tasks from the queue that are not in SCHEDULED state
            if (isTaskAsyncComplete && task.getStatus() != TaskModel.Status.SCHEDULED) {
                shouldRemoveTaskFromQueue = true;
                hasTaskExecutionCompleted = true;
            } else if (task.getStatus().isTerminal()) {
                task.setEndTime(System.currentTimeMillis());
                shouldRemoveTaskFromQueue = true;
                hasTaskExecutionCompleted = true;
            } else {
                task.setCallbackAfterSeconds(systemTaskCallbackTime);
                systemTask
                        .getEvaluationOffset(task, systemTaskCallbackTime)
                        .ifPresentOrElse(
                                task::setCallbackAfterSeconds,
                                () -> task.setCallbackAfterSeconds(systemTaskCallbackTime));
                queueDAO.postpone(
                        queueName,
                        task.getTaskId(),
                        task.getWorkflowPriority(),
                        task.getCallbackAfterSeconds());
                LOGGER.debug("{} postponed in queue: {}", task, queueName);
            }

            LOGGER.debug(
                    "Finished execution of {}/{}-{}",
                    systemTask,
                    task.getTaskId(),
                    task.getStatus());
        } catch (Exception e) {
            Monitors.error(AsyncSystemTaskExecutor.class.getSimpleName(), "executeSystemTask");
            LOGGER.error("Error executing system task - {}, with id: {}", systemTask, taskId, e);
        } finally {
            executionDAOFacade.updateTask(task);
            if (shouldRemoveTaskFromQueue) {
                queueDAO.remove(queueName, task.getTaskId());
                LOGGER.debug("{} removed from queue: {}", task, queueName);
            }
            // if the current task execution has completed, then the workflow needs to be evaluated
            if (hasTaskExecutionCompleted) {
                workflowExecutor.decide(workflowId);
            }
        }
    }

    private void postponeQuietly(String queueName, TaskModel task) {
        try {
            queueDAO.postpone(
                    queueName,
                    task.getTaskId(),
                    task.getWorkflowPriority(),
                    queueTaskMessagePostponeSecs);
        } catch (Exception e) {
            LOGGER.error("Error postponing task: {} in queue: {}", task.getTaskId(), queueName);
        }
    }

    private TaskModel loadTaskQuietly(String taskId) {
        try {
            return executionDAOFacade.getTaskModel(taskId);
        } catch (Exception e) {
            return null;
        }
    }
}
