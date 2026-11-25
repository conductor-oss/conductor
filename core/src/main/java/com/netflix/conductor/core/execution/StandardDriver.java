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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.utils.ExternalPayloadStorage.Operation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage.PayloadType;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;
import static com.netflix.conductor.model.TaskModel.Status.COMPLETED_WITH_ERRORS;
import static com.netflix.conductor.model.TaskModel.Status.IN_PROGRESS;

/** The driver of a standard workflow: the definition decides the next task. */
@Component
public class StandardDriver implements WorkflowDriver {

    private static final Logger LOGGER = LoggerFactory.getLogger(StandardDriver.class);

    private final ParametersUtils parametersUtils;
    private final ExternalPayloadStorageUtils externalPayloadStorageUtils;
    private final ExecutionDAOFacade executionDAOFacade;
    private final QueueDAO queueDAO;

    public StandardDriver(
            ParametersUtils parametersUtils,
            ExternalPayloadStorageUtils externalPayloadStorageUtils,
            ExecutionDAOFacade executionDAOFacade,
            QueueDAO queueDAO) {
        this.parametersUtils = parametersUtils;
        this.externalPayloadStorageUtils = externalPayloadStorageUtils;
        this.executionDAOFacade = executionDAOFacade;
        this.queueDAO = queueDAO;
    }

    @Override
    public void initialize(WorkflowModel workflow) {}

    @Override
    public List<TaskModel> startWithNoTasks() {
        throw new TerminateWorkflowException(
                "No tasks found to be executed", WorkflowModel.Status.COMPLETED);
    }

    @Override
    public void completeWithErrors(TaskModel task) {
        task.setStatus(COMPLETED_WITH_ERRORS);
    }

    @Override
    public void updateOutput(WorkflowModel workflow, TaskModel task) {
        List<TaskModel> allTasks = workflow.getTasks();
        if (allTasks.isEmpty()) {
            return;
        }

        Map<String, Object> output = new HashMap<>();
        Optional<TaskModel> optionalTask =
                allTasks.stream()
                        .filter(
                                t ->
                                        TaskType.TERMINATE.name().equals(t.getTaskType())
                                                && t.getStatus().isTerminal()
                                                && t.getStatus().isSuccessful())
                        .findFirst();
        if (optionalTask.isPresent()) {
            TaskModel terminateTask = optionalTask.get();
            if (StringUtils.isNotBlank(terminateTask.getExternalOutputPayloadStoragePath())) {
                output =
                        externalPayloadStorageUtils.downloadPayload(
                                terminateTask.getExternalOutputPayloadStoragePath());
                Monitors.recordExternalPayloadStorageUsage(
                        terminateTask.getTaskDefName(),
                        Operation.READ.toString(),
                        PayloadType.TASK_OUTPUT.toString());
            } else if (!terminateTask.getOutputData().isEmpty()) {
                output = terminateTask.getOutputData();
            }
        } else {
            TaskModel last = Optional.ofNullable(task).orElse(allTasks.get(allTasks.size() - 1));
            WorkflowDef workflowDef = workflow.getWorkflowDefinition();
            if (workflowDef.getOutputParameters() != null
                    && !workflowDef.getOutputParameters().isEmpty()) {
                output =
                        parametersUtils.getTaskInput(
                                workflowDef.getOutputParameters(), workflow, null, null);
            } else if (StringUtils.isNotBlank(last.getExternalOutputPayloadStoragePath())) {
                output =
                        externalPayloadStorageUtils.downloadPayload(
                                last.getExternalOutputPayloadStoragePath());
                Monitors.recordExternalPayloadStorageUsage(
                        last.getTaskDefName(),
                        Operation.READ.toString(),
                        PayloadType.TASK_OUTPUT.toString());
            } else {
                output = last.getOutputData();
            }
        }
        workflow.setOutput(output);
    }

    @Override
    public boolean isComplete(WorkflowModel workflow, BooleanSupplier tasksComplete) {
        return tasksComplete.getAsBoolean();
    }

    @Override
    public void continueCode(WorkflowModel workflow) {}

    @Override
    public void postponeDecide(WorkflowModel workflow, Duration offset, Duration timeout) {
        if (offset.getSeconds() != timeout.getSeconds()) {
            // we have a new value, setUnack uses time in millis
            LOGGER.debug(
                    "Pushing the workflow {} into decider queue by {} millis",
                    workflow.getWorkflowId(),
                    offset.getSeconds() * 1000);
            queueDAO.setUnackTimeout(
                    DECIDER_QUEUE, workflow.getWorkflowId(), offset.getSeconds() * 1000);
        }
    }

    /** A running workflow must have at least one task that is not terminal. */
    @Override
    public boolean isStalled(WorkflowModel workflow) {
        return workflow.getTasks().stream().allMatch(task -> task.getStatus().isTerminal());
    }

    @Override
    public WorkflowDef resetForRestart(
            WorkflowModel workflow, WorkflowDef definition, boolean useLatestDefinitions) {
        workflow.setNativeState(null); // the latest definition is not native
        return definition;
    }

    @Override
    public RetryOutcome retry(WorkflowModel workflow) {
        return RetryOutcome.retryFailedTasks();
    }

    @Override
    public void resetForRerun(WorkflowModel workflow) {}

    @Override
    public void checkCanRerunFrom(TaskModel rerunFrom) {}

    @Override
    public boolean rerunsSubWorkflowInPlace() {
        return true;
    }

    @Override
    public List<TaskModel> undoAfter(WorkflowModel workflow, TaskModel rerunFrom) {
        removeTasks(workflow, task -> task.getSeq() > rerunFrom.getSeq());
        return List.of();
    }

    /** Removes the tasks that match from the execution and from the store. */
    void removeTasks(WorkflowModel workflow, Predicate<TaskModel> removed) {
        List<TaskModel> filteredTasks = new ArrayList<>();
        for (TaskModel task : workflow.getTasks()) {
            if (removed.test(task)) {
                executionDAOFacade.removeTask(task.getTaskId());
            } else {
                filteredTasks.add(task);
            }
        }
        workflow.setTasks(filteredTasks);
    }

    @Override
    public void rerunSubWorkflowTask(
            WorkflowModel workflow,
            TaskModel task,
            Map<String, Object> taskInput,
            WorkflowExecutor executor) {
        // if task is sub workflow set task as IN_PROGRESS and reset start time
        task.setStatus(IN_PROGRESS);
        task.setStartTime(System.currentTimeMillis());
    }

    @Override
    public boolean runsAgainOnRerun(TaskModel task) {
        return !task.getStatus().isSuccessful() && task.getStatus().isTerminal();
    }
}
