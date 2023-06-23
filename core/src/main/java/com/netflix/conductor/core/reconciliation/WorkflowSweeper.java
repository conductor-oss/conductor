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

import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.netflix.conductor.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.TaskModel.Status;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.core.config.SchedulerConfiguration.SWEEPER_EXECUTOR_NAME;
import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;

@Component
public class WorkflowSweeper {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowSweeper.class);

    private final ConductorProperties properties;
    private final WorkflowExecutor workflowExecutor;
    private final WorkflowRepairService workflowRepairService;
    private final QueueDAO queueDAO;

    private static final String CLASS_NAME = WorkflowSweeper.class.getSimpleName();

    @Autowired
    public WorkflowSweeper(
            WorkflowExecutor workflowExecutor,
            Optional<WorkflowRepairService> workflowRepairService,
            ConductorProperties properties,
            QueueDAO queueDAO) {
        this.properties = properties;
        this.queueDAO = queueDAO;
        this.workflowExecutor = workflowExecutor;
        this.workflowRepairService = workflowRepairService.orElse(null);
        LOGGER.info("WorkflowSweeper initialized.");
    }

    @Async(SWEEPER_EXECUTOR_NAME)
    public CompletableFuture<Void> sweepAsync(String workflowId) {
        sweep(workflowId);
        return CompletableFuture.completedFuture(null);
    }

    public void sweep(String workflowId) {
        WorkflowModel workflow = null;
        try {
            WorkflowContext workflowContext = new WorkflowContext(properties.getAppId());
            WorkflowContext.set(workflowContext);
            LOGGER.debug("Running sweeper for workflow {}", workflowId);

            if (workflowRepairService != null) {
                // Verify and repair tasks in the workflow.
                workflowRepairService.verifyAndRepairWorkflowTasks(workflowId);
            }

            workflow = workflowExecutor.decide(workflowId);
            if (workflow != null && workflow.getStatus().isTerminal()) {
                queueDAO.remove(DECIDER_QUEUE, workflowId);
                return;
            }

        } catch (NotFoundException nfe) {
            queueDAO.remove(DECIDER_QUEUE, workflowId);
            LOGGER.info(
                    "Workflow NOT found for id:{}. Removed it from decider queue", workflowId, nfe);
            return;
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "sweep");
            LOGGER.error("Error running sweep for " + workflowId, e);
        }
        long workflowOffsetTimeout =
                workflowOffsetWithJitter(properties.getWorkflowOffsetTimeout().getSeconds());
        if (workflow != null) {
            long startTime = Instant.now().toEpochMilli();
            unack(workflow, workflowOffsetTimeout);
            long endTime = Instant.now().toEpochMilli();
            Monitors.recordUnackTime(workflow.getWorkflowName(), endTime - startTime);
        } else {
            LOGGER.warn(
                    "Workflow with {} id can not be found. Attempting to unack using the id",
                    workflowId);
            queueDAO.setUnackTimeout(DECIDER_QUEUE, workflowId, workflowOffsetTimeout * 1000);
        }
    }

    @VisibleForTesting
    void unack(WorkflowModel workflowModel, long workflowOffsetTimeout) {
        long postponeDurationSeconds = 0;
        for (TaskModel taskModel : workflowModel.getTasks()) {
            if (taskModel.getStatus() == Status.IN_PROGRESS) {
                if (taskModel.getTaskType().equals(TaskType.TASK_TYPE_WAIT)) {
                    if (taskModel.getWaitTimeout() == 0) {
                        postponeDurationSeconds = workflowOffsetTimeout;
                    } else {
                        long deltaInSeconds =
                                (taskModel.getWaitTimeout() - System.currentTimeMillis()) / 1000;
                        postponeDurationSeconds = (deltaInSeconds > 0) ? deltaInSeconds : 0;
                    }
                } else if (taskModel.getTaskType().equals(TaskType.TASK_TYPE_HUMAN)) {
                    postponeDurationSeconds = workflowOffsetTimeout;
                } else {
                    postponeDurationSeconds =
                            (taskModel.getResponseTimeoutSeconds() != 0)
                                    ? taskModel.getResponseTimeoutSeconds() + 1
                                    : workflowOffsetTimeout;
                }
                break;
            } else if (taskModel.getStatus() == Status.SCHEDULED) {
                Optional<TaskDef> taskDefinition = taskModel.getTaskDefinition();
                if (taskDefinition.isPresent()) {
                    TaskDef taskDef = taskDefinition.get();
                    if (taskDef.getPollTimeoutSeconds() != null
                            && taskDef.getPollTimeoutSeconds() != 0) {
                        postponeDurationSeconds = taskDef.getPollTimeoutSeconds() + 1;
                    } else {
                        postponeDurationSeconds =
                                (workflowModel.getWorkflowDefinition().getTimeoutSeconds() != 0)
                                        ? workflowModel.getWorkflowDefinition().getTimeoutSeconds()
                                                + 1
                                        : workflowOffsetTimeout;
                    }
                } else {
                    postponeDurationSeconds =
                            (workflowModel.getWorkflowDefinition().getTimeoutSeconds() != 0)
                                    ? workflowModel.getWorkflowDefinition().getTimeoutSeconds() + 1
                                    : workflowOffsetTimeout;
                }
                break;
            }
        }
        queueDAO.setUnackTimeout(
                DECIDER_QUEUE, workflowModel.getWorkflowId(), postponeDurationSeconds * 1000);
    }

    /**
     * jitter will be +- (1/3) workflowOffsetTimeout for example, if workflowOffsetTimeout is 45
     * seconds, this function returns values between [30-60] seconds
     *
     * @param workflowOffsetTimeout
     * @return
     */
    @VisibleForTesting
    long workflowOffsetWithJitter(long workflowOffsetTimeout) {
        long range = workflowOffsetTimeout / 3;
        long jitter = new Random().nextInt((int) (2 * range + 1)) - range;
        return workflowOffsetTimeout + jitter;
    }
}
