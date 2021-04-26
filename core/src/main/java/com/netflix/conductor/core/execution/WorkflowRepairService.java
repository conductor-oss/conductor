/*
 * Copyright 2020 Netflix, Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * A helper service that tries to keep ExecutionDAO and QueueDAO in sync, based on the
 * task or workflow state.
 *
 * This service expects that the underlying Queueing layer implements QueueDAO.containsMessage method. This can be controlled
 * with {@link com.netflix.conductor.core.config.Configuration#isWorkflowRepairServiceEnabled()} property.
 */
@Singleton
public class WorkflowRepairService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowRepairService.class);

    private final ExecutionDAO executionDAO;
    private final QueueDAO queueDAO;
    private final Configuration configuration;

    // For system task -> Verify the task isAsync(), not isAsyncComplete() and in SCHEDULED or IN_PROGRESS state
    // For simple task -> Verify the task is in SCHEDULED state
    private final Predicate<Task> isTaskRepairable = task -> {
        if (WorkflowSystemTask.is(task.getTaskType())) {    // If system task
            WorkflowSystemTask workflowSystemTask = WorkflowSystemTask.get(task.getTaskType());
            return workflowSystemTask.isAsync() && !workflowSystemTask.isAsyncComplete(task) &&
                    (task.getStatus() == Task.Status.IN_PROGRESS || task.getStatus() == Task.Status.SCHEDULED);
        } else {    // Else if simple task
            return task.getStatus() == Task.Status.SCHEDULED;
        }
    };

    @Inject
    public WorkflowRepairService(
            ExecutionDAO executionDAO,
            QueueDAO queueDAO,
            Configuration configuration
    ) {
        this.executionDAO = executionDAO;
        this.queueDAO = queueDAO;
        this.configuration = configuration;
    }

    /**
     * Verify and repair if the workflowId exists in deciderQueue, and then if each scheduled task has relevant message
     * in the queue.
     * @param workflowId
     * @param includeTasks
     * @return
     */
    public boolean verifyAndRepairWorkflow(String workflowId, boolean includeTasks) {
        Workflow workflow = executionDAO.getWorkflow(workflowId, includeTasks);
        AtomicBoolean repaired = new AtomicBoolean(false);
        repaired.set(verifyAndRepairDeciderQueue(workflow));
        if (includeTasks) {
            workflow.getTasks().forEach(task -> {
                repaired.set(verifyAndRepairTask(task));
            });
        }
        return repaired.get();
    }

    /**
     * Verify and repair tasks in a workflow
     * @param workflowId
     */
    public void verifyAndRepairWorkflowTasks(String workflowId) {
        Workflow workflow = executionDAO.getWorkflow(workflowId, true);
        if (workflow != null) {
            workflow.getTasks().forEach(task -> verifyAndRepairTask(task));
        }
    }

    /**
     * Verify and fix if Workflow decider queue contains this workflowId.
     * @param workflow
     * @return
     */
    private boolean verifyAndRepairDeciderQueue(Workflow workflow) {
        if (!workflow.getStatus().isTerminal()) {
            String queueName = WorkflowExecutor.DECIDER_QUEUE;
            if (!queueDAO.containsMessage(queueName, workflow.getWorkflowId())) {
                queueDAO.push(queueName, workflow.getWorkflowId(), configuration.getSweepFrequency());
                LOGGER.info("Workflow {} re-queued for repairs", workflow.getWorkflowId());
                Monitors.recordQueueMessageRepushFromRepairService(queueName);
                return true;
            }
        }
        return false;
    }

    /**
     * Verify if ExecutionDAO and QueueDAO agree for the provided task.
     * @param task
     * @return
     */
    @VisibleForTesting
    protected boolean verifyAndRepairTask(Task task) {
        if (isTaskRepairable.test(task)) {
            // Ensure QueueDAO contains this taskId
            String taskQueueName = QueueUtils.getQueueName(task);
            if (!queueDAO.containsMessage(taskQueueName, task.getTaskId())) {
                queueDAO.push(taskQueueName, task.getTaskId(), task.getCallbackAfterSeconds());
                LOGGER.info("Task {} in workflow {} re-queued for repairs", task.getTaskId(), task.getWorkflowInstanceId());
                Monitors.recordQueueMessageRepushFromRepairService(task.getTaskDefName());
                return true;
            }
        }
        return false;
    }
}
