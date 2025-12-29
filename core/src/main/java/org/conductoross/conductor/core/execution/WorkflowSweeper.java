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
package org.conductoross.conductor.core.execution;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.core.LifecycleAwareComponent;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import static com.netflix.conductor.core.config.SchedulerConfiguration.SWEEPER_EXECUTOR_NAME;
import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;

@Component
@Slf4j
@ConditionalOnProperty(
        name = "conductor.app.sweeper.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class WorkflowSweeper extends LifecycleAwareComponent {

    private final QueueDAO queueDAO;
    private final SweeperProperties sweeperProperties;
    private final WorkflowExecutor workflowExecutor;
    private final ExecutionDAO executionDAO;
    private final Duration worflowOffsetTimeout;
    private final Executor sweeperExecutor;
    private final ConductorProperties properties;
    private final ObjectMapper objectMapper;
    private SystemTaskRegistry systemTaskRegistry;
    private final Clock clock = Clock.systemDefaultZone();
    private AtomicBoolean stop = new AtomicBoolean(false);

    public WorkflowSweeper(
            @Qualifier(SWEEPER_EXECUTOR_NAME) Executor sweeperExecutor,
            QueueDAO queueDAO,
            WorkflowExecutor workflowExecutor,
            ExecutionDAO executionDAO,
            ConductorProperties properties,
            SweeperProperties sweeperProperties,
            SystemTaskRegistry systemTaskRegistry,
            ObjectMapper objectMapper) {
        this.queueDAO = queueDAO;
        this.executionDAO = executionDAO;
        this.sweeperProperties = sweeperProperties;
        this.workflowExecutor = workflowExecutor;
        this.worflowOffsetTimeout = properties.getWorkflowOffsetTimeout();
        this.sweeperExecutor = sweeperExecutor;
        this.properties = properties;
        this.systemTaskRegistry = systemTaskRegistry;
        this.objectMapper = objectMapper;
        log.info("Initializing sweeper with {} threads", properties.getSweeperThreadCount());
        for (int i = 0; i < properties.getSweeperThreadCount(); i++) {
            sweeperExecutor.execute(this::pollAndSweep);
        }
    }

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
                } else { // Else if simple task or wait task
                    return (task.getStatus() == TaskModel.Status.SCHEDULED
                            || (!task.getStatus().isTerminal()
                                    && task.getWaitTimeout() > 0
                                    && (clock.millis() - task.getWaitTimeout() > 1000)));
                }
            };

    private void pollAndSweep() {
        try {
            while (true) {
                if (stop.get()) {
                    return;
                }
                try {
                    if (!isRunning()) {
                        log.trace("Component stopped, skip workflow sweep");
                    } else {
                        List<String> workflowIds =
                                queueDAO.pop(
                                        DECIDER_QUEUE,
                                        sweeperProperties.getSweepBatchSize(),
                                        sweeperProperties.getQueuePopTimeout());
                        log.trace("Found {} workflows to sweep", workflowIds.size());
                        workflowIds.stream()
                                .parallel()
                                .forEach(
                                        workflowId ->
                                                Monitors.getTimer("workflowSweeper")
                                                        .record(() -> sweep(workflowId)));
                    }
                } catch (Throwable e) {
                    log.warn("Error while running sweeper {}", e.getMessage(), e);
                }
            }
        } catch (Throwable e) {
            log.error("Error polling for sweep entries {}", e.getMessage(), e);
        }
    }

    public void sweep(String workflowId) {
        log.info("Running sweeper for workflow {}", workflowId);

        try {
            WorkflowModel workflow = workflowExecutor.getWorkflow(workflowId, true);
            if (workflow == null || workflow.getStatus().isTerminal()) {
                queueDAO.remove(DECIDER_QUEUE, workflowId);
                return;
            }

            String tasks =
                    workflow.getTasks().stream()
                            .map(t -> t.getReferenceTaskName() + ":" + t.getStatus())
                            .toList()
                            .toString();
            workflow = workflowExecutor.decideWithLock(workflow);
            if (workflow == null) {
                // couldn't get a lock
                // Let's try again... with the lockTime timeout / 2
                int backoff = (int) (properties.getLockLeaseTime().toMillis() / 2);
                log.info("can't get a lock on  {}, will try after {} ms", workflowId, backoff);
                queueDAO.push(DECIDER_QUEUE, workflowId, 0, Duration.ofMillis(backoff).toSeconds());
                return;
            }
            if (workflow.getStatus().isTerminal()) {
                queueDAO.remove(DECIDER_QUEUE, workflow.getWorkflowId());
                return;
            }

            String tasksAfterDecide =
                    workflow.getTasks().stream()
                            .map(t -> t.getReferenceTaskName() + ":" + t.getStatus())
                            .toList()
                            .toString();

            // Workflow has not completed and decide did not change the status of the tasks
            // Every task that is running MUST be in the queue
            if (tasks.equals(tasksAfterDecide)) {
                workflow.getTasks().forEach(this::verifyAndRepairTask);
            }

            // Workflow is in running status, there MUST be at-least one task that is not terminal
            // (scheduled, in progress)
            boolean hasRunningTasks =
                    workflow.getTasks().stream().anyMatch(task -> !task.getStatus().isTerminal());
            if (!hasRunningTasks) {
                // Workflow is in RUNNING status but there are no tasks that are running
                // This can happen in case of the database failures where the task scheduling failed
                // after the last task was completed
                // To fix, we reset the executed flag of the last task and re-run decide
                forceSetLastTaskAsNotExecuted(workflow);
                workflow = workflowExecutor.decideWithLock(workflow);
            }

            // If parent workflow exists, call repair on that too - meaning ensure the parent is in
            // the decider queue
            if (workflow != null && StringUtils.isNotBlank(workflow.getParentWorkflowId())) {
                ensureWorkflowExistsInDecider(workflow.getParentWorkflowId());
            }
        } catch (Throwable e) {
            log.error("Error running sweep for {}, error = {}", workflowId, e.getMessage(), e);
        }
    }

    void verifyAndRepairTask(TaskModel task) {
        if (isTaskRepairable.test(task)) {
            // Ensure QueueDAO contains this taskId
            String taskQueueName = QueueUtils.getQueueName(task);
            if (!queueDAO.containsMessage(taskQueueName, task.getTaskId())) {
                queueDAO.push(taskQueueName, task.getTaskId(), task.getCallbackAfterSeconds());
                log.info(
                        "Task {} in workflow {} re-queued for repairs",
                        task.getTaskId(),
                        task.getWorkflowInstanceId());
                Monitors.recordQueueMessageRepushFromRepairService(task.getTaskDefName());
            }
        }
        if (task.getTaskType().equals(TaskType.TASK_TYPE_SUB_WORKFLOW)
                && task.getStatus() == TaskModel.Status.IN_PROGRESS) {
            WorkflowModel subWorkflow = executionDAO.getWorkflow(task.getSubWorkflowId(), false);
            if (subWorkflow.getStatus().isTerminal()) {
                log.info(
                        "Repairing sub workflow task {} for sub workflow {} in workflow {}",
                        task.getTaskId(),
                        task.getSubWorkflowId(),
                        task.getWorkflowInstanceId());
                repairSubWorkflowTask(task, subWorkflow);
            }
        }
    }

    private void repairSubWorkflowTask(TaskModel task, WorkflowModel subWorkflow) {
        switch (subWorkflow.getStatus()) {
            case COMPLETED:
                task.setStatus(TaskModel.Status.COMPLETED);
                break;
            case FAILED:
                task.setStatus(TaskModel.Status.FAILED);
                break;
            case TERMINATED:
                task.setStatus(TaskModel.Status.CANCELED);
                break;
            case TIMED_OUT:
                task.setStatus(TaskModel.Status.TIMED_OUT);
                break;
        }
        task.addOutput(subWorkflow.getOutput());
        executionDAO.updateTask(task);
    }

    private void forceSetLastTaskAsNotExecuted(WorkflowModel workflow) {
        if (workflow.getTasks() != null && !workflow.getTasks().isEmpty()) {
            TaskModel taskModel = workflow.getTasks().getLast();
            log.warn(
                    "Force setting isExecuted to false for last task - {} - {} - {} - {} for workflow {}",
                    taskModel.getTaskId(),
                    taskModel.getReferenceTaskName(),
                    taskModel.getStatus(),
                    taskModel.getTaskDefName(),
                    taskModel.getWorkflowInstanceId());
            try {
                log.debug(
                        "workflow {} JSON {}",
                        workflow.getWorkflowId(),
                        objectMapper.writeValueAsString(workflow));
            } catch (Exception e) {
                log.error("Could not warn about workflow {}", workflow.getWorkflowId(), e);
            }
            taskModel.setExecuted(false);
            executionDAO.updateWorkflow(workflow);
        }
    }

    private void ensureWorkflowExistsInDecider(String workflowId) {
        String queueName = Utils.DECIDER_QUEUE;
        if (!queueDAO.containsMessage(queueName, workflowId)) {
            queueDAO.push(queueName, workflowId, worflowOffsetTimeout.getSeconds());
            Monitors.recordQueueMessageRepushFromRepairService(queueName);
        }
    }

    @Override
    public void doStop() {
        stop.set(true);
        ((ExecutorService) this.sweeperExecutor).shutdownNow();
    }
}
