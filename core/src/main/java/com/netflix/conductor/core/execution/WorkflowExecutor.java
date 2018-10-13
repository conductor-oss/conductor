/*
 * Copyright 2016 Netflix, Inc.
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
import com.google.common.base.Preconditions;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.netflix.conductor.common.metadata.tasks.Task.Status.CANCELED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.COMPLETED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.FAILED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.FAILED_WITH_TERMINAL_ERROR;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.IN_PROGRESS;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.SCHEDULED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.SKIPPED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.valueOf;
import static com.netflix.conductor.core.execution.ApplicationException.Code.CONFLICT;
import static com.netflix.conductor.core.execution.ApplicationException.Code.INVALID_INPUT;
import static com.netflix.conductor.core.execution.ApplicationException.Code.NOT_FOUND;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.maxBy;

/**
 * @author Viren Workflow services provider interface
 */
@Trace
public class WorkflowExecutor {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowExecutor.class);

    private final MetadataDAO metadataDAO;

    private final ExecutionDAO executionDAO;

    private final QueueDAO queueDAO;

    private final DeciderService deciderService;

    private final Configuration config;

    private final MetadataMapperService metadataMapperService;
    private final ParametersUtils parametersUtils;

    private int activeWorkerLastPollInSecs;

    public static final String DECIDER_QUEUE = "_deciderQueue";


    @Inject
    public WorkflowExecutor(
            DeciderService deciderService,
            MetadataDAO metadataDAO,
            ExecutionDAO executionDAO,
            QueueDAO queueDAO,
            MetadataMapperService metadataMapperService,
            ParametersUtils parametersUtils,
            Configuration config
    ) {
        this.deciderService = deciderService;
        this.metadataDAO = metadataDAO;
        this.executionDAO = executionDAO;
        this.queueDAO = queueDAO;
        this.config = config;
        this.metadataMapperService = metadataMapperService;
        this.activeWorkerLastPollInSecs = config.getIntProperty("tasks.active.worker.lastpoll", 10);
        this.parametersUtils = parametersUtils;
    }

    /**
     * @throws ApplicationException
     */
    public String startWorkflow(String name, Integer version, String correlationId, Map<String, Object> input, String externalInputPayloadStoragePath) {
        return startWorkflow(name, version, correlationId, input, externalInputPayloadStoragePath, null);
    }

    /**
     * @throws ApplicationException
     */
    public String startWorkflow(String name, Integer version, String correlationId, Map<String, Object> input, String externalInputPayloadStoragePath, String event) {
        return startWorkflow(
                name,
                version,
                input,
                externalInputPayloadStoragePath,
                correlationId,
                null,
                null,
                event
        );
    }

    /**
     * @throws ApplicationException
     */
    public String startWorkflow(
            String name,
            Integer version,
            String correlationId,
            Map<String, Object> input,
            String externalInputPayloadStoragePath,
            String event,
            Map<String, String> taskToDomain
    ) {
        return startWorkflow(
                name,
                version,
                input,
                externalInputPayloadStoragePath,
                correlationId,
                null,
                null,
                event,
                taskToDomain
        );
    }

    /**
     * @throws ApplicationException
     */
    public String startWorkflow(
            String name,
            Integer version,
            Map<String, Object> input,
            String externalInputPayloadStoragePath,
            String correlationId,
            String parentWorkflowId,
            String parentWorkflowTaskId,
            String event
    ) {
        return startWorkflow(
                name,
                version,
                input,
                externalInputPayloadStoragePath,
                correlationId,
                parentWorkflowId,
                parentWorkflowTaskId,
                event,
                null
        );
    }

    /**
     * @throws ApplicationException
     */
    public String startWorkflow(
            WorkflowDef workflowDefinition,
            Map<String, Object> workflowInput,
            String externalInputPayloadStoragePath,
            String correlationId,
            String event,
            Map<String, String> taskToDomain
    ) {
        return startWorkflow(
                workflowDefinition,
                workflowInput,
                externalInputPayloadStoragePath,
                correlationId,
                null,
                null,
                event,
                taskToDomain
        );
    }

    /**
     * @throws ApplicationException
     */
    public String startWorkflow(
            String name,
            Integer version,
            Map<String, Object> workflowInput,
            String externalInputPayloadStoragePath,
            String correlationId,
            String parentWorkflowId,
            String parentWorkflowTaskId,
            String event,
            Map<String, String> taskToDomain
    ) {
        WorkflowDef workflowDefinition = metadataMapperService.lookupForWorkflowDefinition(name, version);

        return startWorkflow(
                workflowDefinition,
                workflowInput,
                externalInputPayloadStoragePath,
                correlationId,
                parentWorkflowId,
                parentWorkflowTaskId,
                event,
                taskToDomain
        );
    }

    private final Predicate<PollData> validateLastPolledTime = pd -> pd.getLastPollTime() > System.currentTimeMillis() - (activeWorkerLastPollInSecs * 1000);

    private final Predicate<Task> isSystemTask = task -> SystemTaskType.is(task.getTaskType());

    private final Predicate<Task> isNonTerminalTask = task -> !task.getStatus().isTerminal();

    /**
     * @throws ApplicationException
     */
    public String startWorkflow(
            WorkflowDef workflowDefinition,
            Map<String, Object> workflowInput,
            String externalInputPayloadStoragePath,
            String correlationId,
            String parentWorkflowId,
            String parentWorkflowTaskId,
            String event,
            Map<String, String> taskToDomain
    ) {
        workflowDefinition = metadataMapperService.populateTaskDefinitions(workflowDefinition);

        // perform validations
        validateWorkflow(workflowDefinition, workflowInput, externalInputPayloadStoragePath);

        //A random UUID is assigned to the work flow instance
        String workflowId = IDGenerator.generate();

        // Persist the Workflow
        Workflow wf = new Workflow();
        wf.setWorkflowId(workflowId);
        wf.setCorrelationId(correlationId);
        wf.setWorkflowDefinition(workflowDefinition);
        wf.setInput(workflowInput);
        wf.setExternalInputPayloadStoragePath(externalInputPayloadStoragePath);
        wf.setStatus(WorkflowStatus.RUNNING);
        wf.setParentWorkflowId(parentWorkflowId);
        wf.setParentWorkflowTaskId(parentWorkflowTaskId);
        wf.setOwnerApp(WorkflowContext.get().getClientApp());
        wf.setCreateTime(System.currentTimeMillis());
        wf.setUpdatedBy(null);
        wf.setUpdateTime(null);
        wf.setEvent(event);
        wf.setTaskToDomain(taskToDomain);

        executionDAO.createWorkflow(wf);
        logger.info("A new instance of workflow {} created with workflow id {}", wf.getWorkflowName(), workflowId);

        //then decide to see if anything needs to be done as part of the workflow
        decide(workflowId);

        return workflowId;
    }

    /**
     * Performs validations for starting a workflow
     *
     * @throws ApplicationException if the validation fails
     */
    private void validateWorkflow(WorkflowDef workflowDef, Map<String, Object> workflowInput, String externalStoragePath) {
        try {
            //Check if the input to the workflow is not null
            if (workflowInput == null && StringUtils.isBlank(externalStoragePath)) {
                logger.error("The input for the workflow '{}' cannot be NULL", workflowDef.getName());
                throw new ApplicationException(INVALID_INPUT, "NULL input passed when starting workflow");
            }
        } catch (Exception e) {
            Monitors.recordWorkflowStartError(workflowDef.getName(), WorkflowContext.get().getClientApp());
            throw e;
        }
    }


    /**
     * @param workflowId
     * @return
     * @throws ApplicationException
     */
    public String resetCallbacksForInProgressTasks(String workflowId) {
        Workflow workflow = executionDAO.getWorkflow(workflowId, true);
        if (workflow.getStatus().isTerminal()) {
            throw new ApplicationException(CONFLICT, "Workflow is completed.  status=" + workflow.getStatus());
        }

        // Get tasks that are in progress and have callbackAfterSeconds > 0
        // and set the callbackAfterSeconds to 0;
        for (Task t : workflow.getTasks()) {
            if (t.getStatus().equals(IN_PROGRESS) &&
                    t.getCallbackAfterSeconds() > 0) {
                if (queueDAO.setOffsetTime(QueueUtils.getQueueName(t), t.getTaskId(), 0)) {
                    t.setCallbackAfterSeconds(0);
                    executionDAO.updateTask(t);
                }
            }
        }
        return workflowId;
    }

    public String rerun(RerunWorkflowRequest request) {
        Preconditions.checkNotNull(request.getReRunFromWorkflowId(), "reRunFromWorkflowId is missing");
        if (!rerunWF(request.getReRunFromWorkflowId(), request.getReRunFromTaskId(), request.getTaskInput(),
                request.getWorkflowInput(), request.getCorrelationId())) {
            throw new ApplicationException(INVALID_INPUT, "Task " + request.getReRunFromTaskId() + " not found");
        }
        return request.getReRunFromWorkflowId();
    }

    /**
     * @param workflowId the id of the workflow to be restarted
     * @throws ApplicationException in the following cases:
     *                              <ul>
     *                              <li>Workflow is not in a terminal state</li>
     *                              <li>Workflow definition is not found</li>
     *                              <li>Workflow is deemed non-restartable as per workflow definition</li>
     *                              </ul>
     */
    public void rewind(String workflowId) {
        Workflow workflow = executionDAO.getWorkflow(workflowId, true);
        if (!workflow.getStatus().isTerminal()) {
            throw new ApplicationException(CONFLICT, "Workflow is still running.  status=" + workflow.getStatus());
        }

        WorkflowDef workflowDef = Optional.ofNullable(workflow.getWorkflowDefinition())
                .orElseGet(() -> metadataDAO.get(workflow.getWorkflowName(), workflow.getWorkflowVersion())
                        .orElseThrow(() -> new ApplicationException(NOT_FOUND, String.format("Unable to find definition for %s", workflowId)))
                );

        if (!workflowDef.isRestartable() && workflow.getStatus().equals(WorkflowStatus.COMPLETED)) { // Can only restart non-completed workflows when the configuration is set to false
            throw new ApplicationException(CONFLICT, String.format("WorkflowId: %s is an instance of WorkflowDef: %s and version: %d and is non restartable",
                    workflowId, workflowDef.getName(), workflowDef.getVersion()));
        }

        // Remove all the tasks...
        workflow.getTasks().forEach(t -> executionDAO.removeTask(t.getTaskId()));
        workflow.getTasks().clear();
        workflow.setReasonForIncompletion(null);
        workflow.setStartTime(System.currentTimeMillis());
        workflow.setEndTime(0);
        // Change the status to running
        workflow.setStatus(WorkflowStatus.RUNNING);
        executionDAO.updateWorkflow(workflow);
        decide(workflowId);
    }

    /**
     * Gets the last instance of each failed task and reschedule each
     * Gets all cancelled tasks and schedule all of them except JOIN (join should change status to INPROGRESS)
     * Switch workflow back to RUNNING status and aall decider.
     *
     * @param workflowId
     */
    public void retry(String workflowId) {
        Workflow workflow = executionDAO.getWorkflow(workflowId, true);
        if (!workflow.getStatus().isTerminal()) {
            throw new ApplicationException(CONFLICT, "Workflow is still running.  status=" + workflow.getStatus());
        }
        if (workflow.getTasks().isEmpty()) {
            throw new ApplicationException(CONFLICT, "Workflow has not started yet");
        }

        List<Task> failedTasks = getFailedTasksToRetry(workflow);

        List<Task> cancelledTasks = workflow.getTasks().stream()
                .filter(t -> CANCELED.equals(t.getStatus()))
                .collect(Collectors.toList());

        if (failedTasks.isEmpty()) {
            throw new ApplicationException(CONFLICT,
                    "There are no failed tasks! Use restart if you want to attempt entire workflow execution again.");
        }
        List<Task> rescheduledTasks = new ArrayList<>();
        failedTasks.forEach(failedTask -> {
            rescheduledTasks.add(taskToBeRescheduled(failedTask));
        });

        // Reschedule the cancelled task but if the join is cancelled set that to in progress
        cancelledTasks.forEach(task -> {
            if (task.getTaskType().equalsIgnoreCase(TaskType.JOIN.toString())) {
                task.setStatus(IN_PROGRESS);
                executionDAO.updateTask(task);
            } else {
                rescheduledTasks.add(taskToBeRescheduled(task));
            }
        });

        scheduleTask(workflow, rescheduledTasks);

        workflow.setStatus(WorkflowStatus.RUNNING);
        executionDAO.updateWorkflow(workflow);
        executionDAO.updateTasks(workflow.getTasks());

        decide(workflowId);
    }

    /**
     * Get all failed and cancelled tasks.
     * for failed tasks - get one for each task reference name(latest failed using seq id)
     *
     * @param workflow
     * @return list of latest failed tasks, one for each task reference reference type.
     */
    @VisibleForTesting
    List<Task> getFailedTasksToRetry(Workflow workflow) {
        return workflow.getTasks().stream()
                .filter(x -> FAILED.equals(x.getStatus()))
                .collect(groupingBy(Task::getReferenceTaskName, maxBy(comparingInt(Task::getSeq))))
                .values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Reschedule a task
     *
     * @param task failed or cancelled task
     * @return new instance of a task with "SCHEDULED" status
     */
    private Task taskToBeRescheduled(Task task) {
        Task taskToBeRetried = task.copy();
        taskToBeRetried.setTaskId(IDGenerator.generate());
        taskToBeRetried.setRetriedTaskId(task.getTaskId());
        taskToBeRetried.setStatus(SCHEDULED);
        taskToBeRetried.setRetryCount(task.getRetryCount() + 1);

        // update the failed task in the DAO
        task.setRetried(true);
        executionDAO.updateTask(task);
        return taskToBeRetried;
    }

    public Task getPendingTaskByWorkflow(String taskReferenceName, String workflowId) {
        return executionDAO.getTasksForWorkflow(workflowId).stream()
                .filter(isNonTerminalTask)
                .filter(task -> task.getReferenceTaskName().equals(taskReferenceName))
                .findFirst() // There can only be one task by a given reference name running at a time.
                .orElse(null);
    }


    /**
     * @param wf
     * @throws ApplicationException
     */
    @VisibleForTesting
    void completeWorkflow(Workflow wf) {
        logger.debug("Completing workflow execution for {}", wf.getWorkflowId());
        Workflow workflow = executionDAO.getWorkflow(wf.getWorkflowId(), false);

        if (workflow.getStatus().equals(WorkflowStatus.COMPLETED)) {
            executionDAO.removeFromPendingWorkflow(workflow.getWorkflowName(), workflow.getWorkflowId());
            logger.info("Workflow has already been completed.  Current status={}, workflowId= {}", workflow.getStatus(), wf.getWorkflowId());
            return;
        }

        if (workflow.getStatus().isTerminal()) {
            String msg = "Workflow has already been completed.  Current status " + workflow.getStatus();
            throw new ApplicationException(CONFLICT, msg);
        }

        // FIXME Backwards compatibility for legacy workflows already running.
        // This code will be removed in a future version.
        if (workflow.getWorkflowDefinition() == null) {
            workflow = metadataMapperService.populateWorkflowWithDefinitions(workflow);
        }
        deciderService.updateWorkflowOutput(wf, null);

        workflow.setStatus(WorkflowStatus.COMPLETED);
        workflow.setOutput(wf.getOutput());
        workflow.setExternalOutputPayloadStoragePath(wf.getExternalOutputPayloadStoragePath());
        executionDAO.updateWorkflow(workflow);
        logger.debug("Completed workflow execution for {}", wf.getWorkflowId());
        executionDAO.updateTasks(wf.getTasks());

        // If the following task, for some reason fails, the sweep will take care of this again!
        if (workflow.getParentWorkflowId() != null) {
            Workflow parent = executionDAO.getWorkflow(workflow.getParentWorkflowId(), false);
            WorkflowDef parentDef = Optional.ofNullable(parent.getWorkflowDefinition())
                    .orElseGet(() -> metadataDAO.get(parent.getWorkflowName(), parent.getWorkflowVersion())
                            .orElseThrow(() -> new ApplicationException(NOT_FOUND, String.format("Unable to find parent workflow definition for %s", wf.getWorkflowId())))
                    );
            logger.debug("Completed sub-workflow {}, deciding parent workflow {}", wf.getWorkflowId(), wf.getParentWorkflowId());

            Task parentWorkflowTask = executionDAO.getTask(workflow.getParentWorkflowTaskId());
            // If parent is FAILED and the sub workflow task in parent is FAILED, we want to resume them
            if (StringUtils.isBlank(parentDef.getFailureWorkflow()) && parent.getStatus() == WorkflowStatus.FAILED && parentWorkflowTask.getStatus() == FAILED) {
                parentWorkflowTask.setStatus(IN_PROGRESS);
                executionDAO.updateTask(parentWorkflowTask);
                parent.setStatus(WorkflowStatus.RUNNING);
                executionDAO.updateWorkflow(parent);
            }
            decide(parent.getWorkflowId());
        }
        Monitors.recordWorkflowCompletion(workflow.getWorkflowName(), workflow.getEndTime() - workflow.getStartTime(), wf.getOwnerApp());
        queueDAO.remove(DECIDER_QUEUE, workflow.getWorkflowId());    //remove from the sweep queue
        logger.debug("Removed workflow {} from decider queue", wf.getWorkflowId());
    }

    public void terminateWorkflow(String workflowId, String reason) {
        Workflow workflow = executionDAO.getWorkflow(workflowId, true);
        workflow.setStatus(WorkflowStatus.TERMINATED);
        terminateWorkflow(workflow, reason, null);
    }

    /**
     * @param workflow
     * @param reason
     * @param failureWorkflow
     * @throws ApplicationException
     */
    public void terminateWorkflow(Workflow workflow, String reason, String failureWorkflow) {
        if (!workflow.getStatus().isTerminal()) {
            workflow.setStatus(WorkflowStatus.TERMINATED);
        }

        // FIXME Backwards compatibility for legacy workflows already running.
        // This code will be removed in a future version.
        if (workflow.getWorkflowDefinition() == null) {
            workflow = metadataMapperService.populateWorkflowWithDefinitions(workflow);
        }
        deciderService.updateWorkflowOutput(workflow, null);

        String workflowId = workflow.getWorkflowId();
        workflow.setReasonForIncompletion(reason);
        executionDAO.updateWorkflow(workflow);

        List<Task> tasks = workflow.getTasks();
        for (Task task : tasks) {
            if (!task.getStatus().isTerminal()) {
                // Cancel the ones which are not completed yet....
                task.setStatus(CANCELED);
                if (isSystemTask.test(task)) {
                    WorkflowSystemTask stt = WorkflowSystemTask.get(task.getTaskType());
                    try {
                        stt.cancel(workflow, task, this);
                    } catch (Exception e) {
                        throw new ApplicationException(
                                Code.INTERNAL_ERROR,
                                String.format("Error canceling systems task: %s", stt.getName()),
                                e
                        );
                    }
                }
                executionDAO.updateTask(task);
            }
            // And remove from the task queue if they were there
            queueDAO.remove(QueueUtils.getQueueName(task), task.getTaskId());
        }

        // If the following lines, for some reason fails, the sweep will take
        // care of this again!
        if (workflow.getParentWorkflowId() != null) {
            Workflow parent = executionDAO.getWorkflow(workflow.getParentWorkflowId(), false);
            decide(parent.getWorkflowId());
        }

        if (!StringUtils.isBlank(failureWorkflow)) {
            Map<String, Object> input = new HashMap<>(workflow.getInput());
            input.put("workflowId", workflowId);
            input.put("reason", reason);
            input.put("failureStatus", workflow.getStatus().toString());

            try {
                WorkflowDef latestFailureWorkflow = metadataDAO.getLatest(failureWorkflow)
                        .orElseThrow(() ->
                                new RuntimeException("Failure Workflow Definition not found for: " + failureWorkflow)
                        );

                String failureWFId = startWorkflow(
                        latestFailureWorkflow,
                        input,
                        null,
                        workflowId,
                        null,
                        null
                );

                workflow.getOutput().put("conductor.failure_workflow", failureWFId);
            } catch (Exception e) {
                logger.error("Failed to start error workflow", e);
                workflow.getOutput().put("conductor.failure_workflow", "Error workflow " + failureWorkflow + " failed to start.  reason: " + e.getMessage());
                Monitors.recordWorkflowStartError(failureWorkflow, WorkflowContext.get().getClientApp());
            }
        }

        queueDAO.remove(DECIDER_QUEUE, workflow.getWorkflowId());    //remove from the sweep queue
        executionDAO.removeFromPendingWorkflow(workflow.getWorkflowName(), workflow.getWorkflowId());

        // Send to atlas
        Monitors.recordWorkflowTermination(workflow.getWorkflowName(), workflow.getStatus(), workflow.getOwnerApp());
    }

    /**
     * @param taskResult the task result to be updated
     * @throws ApplicationException
     */
    public void updateTask(TaskResult taskResult) {
        if (taskResult == null) {
            logger.info("null task given for update");
            throw new ApplicationException(Code.INVALID_INPUT, "Task object is null");
        }

        String workflowId = taskResult.getWorkflowInstanceId();
        Workflow workflowInstance = executionDAO.getWorkflow(workflowId);

        // FIXME Backwards compatibility for legacy workflows already running.
        // This code will be removed in a future version.
        if (workflowInstance.getWorkflowDefinition() == null) {
            workflowInstance = metadataMapperService.populateWorkflowWithDefinitions(workflowInstance);
        }

        Task task = executionDAO.getTask(taskResult.getTaskId());

        logger.debug("Task: {} belonging to Workflow {} being updated", task, workflowInstance);

        String taskQueueName = QueueUtils.getQueueName(task);
        if (workflowInstance.getStatus().isTerminal()) {
            // Workflow is in terminal state
            queueDAO.remove(taskQueueName, taskResult.getTaskId());
            logger.debug("Workflow: {} is in terminal state Task: {} removed from Queue: {} during update task", workflowInstance, task, taskQueueName);
            if (!task.getStatus().isTerminal()) {
                task.setStatus(COMPLETED);
            }
            task.setOutputData(taskResult.getOutputData());
            task.setOutputMessage(taskResult.getOutputMessage());
            task.setExternalOutputPayloadStoragePath(taskResult.getExternalOutputPayloadStoragePath());
            task.setReasonForIncompletion(taskResult.getReasonForIncompletion());
            task.setWorkerId(taskResult.getWorkerId());
            executionDAO.updateTask(task);
            String msg = String.format("Workflow %s is already completed as %s, task=%s, reason=%s",
                    workflowInstance.getWorkflowId(), workflowInstance.getStatus(), task.getTaskType(), workflowInstance.getReasonForIncompletion());
            logger.info(msg);
            Monitors.recordUpdateConflict(task.getTaskType(), workflowInstance.getWorkflowName(), workflowInstance.getStatus());
            return;
        }

        if (task.getStatus().isTerminal()) {
            // Task was already updated....
            queueDAO.remove(taskQueueName, taskResult.getTaskId());
            logger.debug("Task: {} is in terminal state and is removed from the queue {} ", task, taskQueueName);
            String msg = String.format("Task is already completed as %s@%d, workflow status=%s, workflowId=%s, taskId=%s",
                    task.getStatus(), task.getEndTime(), workflowInstance.getStatus(), workflowInstance.getWorkflowId(), task.getTaskId());
            logger.info(msg);
            Monitors.recordUpdateConflict(task.getTaskType(), workflowInstance.getWorkflowName(), task.getStatus());
            return;
        }

        task.setStatus(valueOf(taskResult.getStatus().name()));
        task.setOutputData(taskResult.getOutputData());
        task.setOutputMessage(taskResult.getOutputMessage());
        task.setExternalOutputPayloadStoragePath(taskResult.getExternalOutputPayloadStoragePath());
        task.setReasonForIncompletion(taskResult.getReasonForIncompletion());
        task.setWorkerId(taskResult.getWorkerId());
        task.setCallbackAfterSeconds(taskResult.getCallbackAfterSeconds());

        if (task.getStatus().isTerminal()) {
            task.setEndTime(System.currentTimeMillis());
        }

        executionDAO.updateTask(task);

        //If the task has failed update the failed task reference name in the workflow.
        //This gives the ability to look at workflow and see what tasks have failed at a high level.
        if (FAILED.equals(task.getStatus()) || FAILED_WITH_TERMINAL_ERROR.equals(task.getStatus())) {
            workflowInstance.getFailedReferenceTaskNames().add(task.getReferenceTaskName());
            executionDAO.updateWorkflow(workflowInstance);
            logger.debug("Task: {} has a {} status and the Workflow has been updated with failed task reference", task, task.getStatus());
        }

        taskResult.getLogs().forEach(taskExecLog -> taskExecLog.setTaskId(task.getTaskId()));
        executionDAO.addTaskExecLog(taskResult.getLogs());

        switch (task.getStatus()) {
            case COMPLETED:
            case CANCELED:
            case FAILED:
            case FAILED_WITH_TERMINAL_ERROR:
                queueDAO.remove(taskQueueName, taskResult.getTaskId());
                logger.debug("Task: {} removed from taskQueue: {} since the task status is {}", task, taskQueueName, task.getStatus().name());
                break;
            case IN_PROGRESS:
                // put it back in queue based on callbackAfterSeconds
                long callBack = taskResult.getCallbackAfterSeconds();
                queueDAO.remove(taskQueueName, task.getTaskId());
                logger.debug("Task: {} removed from taskQueue: {} since the task status is {}", task, taskQueueName, task.getStatus().name());
                queueDAO.push(taskQueueName, task.getTaskId(), callBack); // Milliseconds
                logger.debug("Task: {} pushed back to taskQueue: {} since the task status is {} with callbackAfterSeconds: {}", task, taskQueueName, task.getStatus().name(), callBack);
                break;
            default:
                break;
        }

        decide(workflowId);

        if (task.getStatus().isTerminal()) {
            long duration = getTaskDuration(0, task);
            long lastDuration = task.getEndTime() - task.getStartTime();
            Monitors.recordTaskExecutionTime(task.getTaskDefName(), duration, true, task.getStatus());
            Monitors.recordTaskExecutionTime(task.getTaskDefName(), lastDuration, false, task.getStatus());
        }
    }

    public Task getTask(String taskId) {
        return Optional.ofNullable(executionDAO.getTask(taskId))
            .map(metadataMapperService::populateTaskWithDefinition)
            .orElse(null);
    }

    public List<Task> getTasks(String taskType, String startKey, int count) {
        return executionDAO.getTasks(taskType, startKey, count);
    }

    public List<Workflow> getRunningWorkflows(String workflowName) {
        return executionDAO.getPendingWorkflowsByType(workflowName);

    }

    public List<String> getWorkflows(String name, Integer version, Long startTime, Long endTime) {
        List<Workflow> workflowsByType = executionDAO.getWorkflowsByType(name, startTime, endTime);
        return workflowsByType.stream()
                .filter(workflow -> workflow.getWorkflowVersion() == version)
                .map(Workflow::getWorkflowId)
                .collect(Collectors.toList());

    }

    public List<String> getRunningWorkflowIds(String workflowName) {
        return executionDAO.getRunningWorkflowIds(workflowName);
    }

    /**
     * @param workflowId ID of the workflow to evaluate the state for
     * @return true if the workflow has completed (success or failed), false otherwise.
     * @throws ApplicationException If there was an error - caller should retry in this case.
     */
    public boolean decide(String workflowId) {

        // If it is a new workflow, the tasks will be still empty even though include tasks is true
        Workflow workflow = executionDAO.getWorkflow(workflowId, true);

        // FIXME Backwards compatibility for legacy workflows already running.
        // This code will be removed in a future version.
        workflow = metadataMapperService.populateWorkflowWithDefinitions(workflow);

        try {
            DeciderService.DeciderOutcome outcome = deciderService.decide(workflow);
            if (outcome.isComplete) {
                completeWorkflow(workflow);
                return true;
            }

            List<Task> tasksToBeScheduled = outcome.tasksToBeScheduled;
            setTaskDomains(tasksToBeScheduled, workflow);
            List<Task> tasksToBeUpdated = outcome.tasksToBeUpdated;
            List<Task> tasksToBeRequeued = outcome.tasksToBeRequeued;
            boolean stateChanged = false;

            if (!tasksToBeRequeued.isEmpty()) {
                addTaskToQueue(tasksToBeRequeued);
            }
            workflow.getTasks().addAll(tasksToBeScheduled);

            for (Task task : tasksToBeScheduled) {
                if (isSystemTask.and(isNonTerminalTask).test(task)) {
                    WorkflowSystemTask workflowSystemTask = WorkflowSystemTask.get(task.getTaskType());

                    try {
                        if (!workflowSystemTask.isAsync() && workflowSystemTask.execute(workflow, task, this)) {
                            tasksToBeUpdated.add(task);
                            stateChanged = true;
                        }
                    } catch (Exception e) {
                        throw new ApplicationException(
                                Code.INTERNAL_ERROR,
                                String.format("Unable to start system task: %s", workflowSystemTask.getName()),
                                e
                        );
                    }
                }
            }

            stateChanged = scheduleTask(workflow, tasksToBeScheduled) || stateChanged;

            if (!outcome.tasksToBeUpdated.isEmpty()) {
                for (Task task : tasksToBeUpdated) {
                    if (task.getStatus() != null && (!task.getStatus().equals(Task.Status.IN_PROGRESS)
                            || !task.getStatus().equals(Task.Status.SCHEDULED))) {
                        queueDAO.remove(QueueUtils.getQueueName(task), task.getTaskId());
                    }
                }
            }

            if (!outcome.tasksToBeUpdated.isEmpty() || !outcome.tasksToBeScheduled.isEmpty()) {
                executionDAO.updateTasks(tasksToBeUpdated);
                executionDAO.updateWorkflow(workflow);
                queueDAO.push(DECIDER_QUEUE, workflow.getWorkflowId(), config.getSweepFrequency());
            }

            if (stateChanged) {
                decide(workflowId);
            }

        } catch (TerminateWorkflowException twe) {
            logger.info("Execution terminated of workflow: {} of type: {}", workflowId, workflow.getWorkflowDefinition().getName(), twe);
            terminate(workflow, twe);
            return true;
        } catch (RuntimeException e) {
            logger.error("Error deciding workflow: {}", workflowId, e);
            throw e;
        }
        return false;
    }

    /**
     * @throws ApplicationException
     */
    public void pauseWorkflow(String workflowId) {
        WorkflowStatus status = WorkflowStatus.PAUSED;
        Workflow workflow = executionDAO.getWorkflow(workflowId, false);
        if (workflow.getStatus().isTerminal()) {
            throw new ApplicationException(CONFLICT, "Workflow id " + workflowId + " has ended, status cannot be updated.");
        }
        if (workflow.getStatus().equals(status)) {
            return;        //Already paused!
        }
        workflow.setStatus(status);
        executionDAO.updateWorkflow(workflow);
    }

    /**
     * @param workflowId
     * @throws IllegalStateException
     */
    public void resumeWorkflow(String workflowId) {
        Workflow workflow = executionDAO.getWorkflow(workflowId, false);
        if (!workflow.getStatus().equals(WorkflowStatus.PAUSED)) {
            throw new IllegalStateException("The workflow " + workflowId + " is not PAUSED so cannot resume. " +
                    "Current status is " + workflow.getStatus().name());
        }
        workflow.setStatus(WorkflowStatus.RUNNING);
        executionDAO.updateWorkflow(workflow);
        decide(workflowId);
    }

    /**
     * @param workflowId
     * @param taskReferenceName
     * @param skipTaskRequest
     * @throws IllegalStateException
     */
    public void skipTaskFromWorkflow(String workflowId, String taskReferenceName, SkipTaskRequest skipTaskRequest) {

        Workflow wf = executionDAO.getWorkflow(workflowId, true);

        // FIXME Backwards compatibility for legacy workflows already running.
        // This code will be removed in a future version.
        wf = metadataMapperService.populateWorkflowWithDefinitions(wf);

        // If the wf is not running then cannot skip any task
        if (!wf.getStatus().equals(WorkflowStatus.RUNNING)) {
            String errorMsg = String.format("The workflow %s is not running so the task referenced by %s cannot be skipped", workflowId, taskReferenceName);
            throw new IllegalStateException(errorMsg);
        }
        // Check if the reference name is as per the workflowdef
        WorkflowTask wft = wf.getWorkflowDefinition().getTaskByRefName(taskReferenceName);
        if (wft == null) {
            String errorMsg = String.format("The task referenced by %s does not exist in the WorkflowDefinition %s", taskReferenceName, wf.getWorkflowName());
            throw new IllegalStateException(errorMsg);
        }
        // If the task is already started the again it cannot be skipped
        wf.getTasks().forEach(task -> {
            if (task.getReferenceTaskName().equals(taskReferenceName)) {
                String errorMsg = String.format("The task referenced %s has already been processed, cannot be skipped", taskReferenceName);
                throw new IllegalStateException(errorMsg);
            }
        });
        // Now create a "SKIPPED" task for this workflow
        Task theTask = new Task();
        theTask.setTaskId(IDGenerator.generate());
        theTask.setReferenceTaskName(taskReferenceName);
        theTask.setWorkflowInstanceId(workflowId);
        theTask.setStatus(SKIPPED);
        theTask.setTaskType(wft.getName());
        theTask.setCorrelationId(wf.getCorrelationId());
        if (skipTaskRequest != null) {
            theTask.setInputData(skipTaskRequest.getTaskInput());
            theTask.setOutputData(skipTaskRequest.getTaskOutput());
            theTask.setInputMessage(skipTaskRequest.getTaskInputMessage());
            theTask.setOutputMessage(skipTaskRequest.getTaskOutputMessage());
        }
        executionDAO.createTasks(Collections.singletonList(theTask));
        decide(workflowId);
    }

    public Workflow getWorkflow(String workflowId, boolean includeTasks) {
        return executionDAO.getWorkflow(workflowId, includeTasks);
    }

    public void addTaskToQueue(Task task) {
        // put in queue
        String taskQueueName = QueueUtils.getQueueName(task);
        queueDAO.remove(taskQueueName, task.getTaskId());
        if (task.getCallbackAfterSeconds() > 0) {
            queueDAO.push(taskQueueName, task.getTaskId(), task.getCallbackAfterSeconds());
        } else {
            queueDAO.push(taskQueueName, task.getTaskId(), 0);
        }
        logger.debug("Added task {} to queue {} with call back seconds {}", task, taskQueueName, task.getCallbackAfterSeconds());
    }

    //Executes the async system task
    public void executeSystemTask(WorkflowSystemTask systemTask, String taskId, int unackTimeout) {
        try {
            Task task = executionDAO.getTask(taskId);
            logger.info("Task: {} fetched from execution DAO for taskId: {}", task, taskId);
            if (task.getStatus().isTerminal()) {
                //Tune the SystemTaskWorkerCoordinator's queues - if the queue size is very big this can happen!
                logger.info("Task {}/{} was already completed.", task.getTaskType(), task.getTaskId());
                queueDAO.remove(QueueUtils.getQueueName(task), task.getTaskId());
                return;
            }

            String workflowId = task.getWorkflowInstanceId();
            Workflow workflow = executionDAO.getWorkflow(workflowId, true);

            if (task.getStartTime() == 0) {
                task.setStartTime(System.currentTimeMillis());
                Monitors.recordQueueWaitTime(task.getTaskDefName(), task.getQueueWaitTime());
            }

            if (workflow.getStatus().isTerminal()) {
                logger.warn("Workflow {} has been completed for {}/{}", workflow.getWorkflowId(), systemTask.getName(), task.getTaskId());
                if (!task.getStatus().isTerminal()) {
                    task.setStatus(CANCELED);
                }
                executionDAO.updateTask(task);
                queueDAO.remove(QueueUtils.getQueueName(task), task.getTaskId());
                return;
            }

            if (task.getStatus().equals(SCHEDULED)) {
                if (executionDAO.exceedsInProgressLimit(task)) {
                    //to do add a metric to record this
                    logger.warn("Concurrent Execution limited for {}:{}", taskId, task.getTaskDefName());
                    return;
                }
                if (task.getRateLimitPerFrequency() > 0 && executionDAO.exceedsRateLimitPerFrequency(task)) {
                    logger.warn("RateLimit Execution limited for {}:{}, limit:{}", taskId, task.getTaskDefName(), task.getRateLimitPerFrequency());
                    return;
                }
            }

            logger.info("Executing {}/{}-{}", task.getTaskType(), task.getTaskId(), task.getStatus());

            queueDAO.setUnackTimeout(QueueUtils.getQueueName(task), task.getTaskId(), systemTask.getRetryTimeInSecond() * 1000);
            task.setPollCount(task.getPollCount() + 1);
            executionDAO.updateTask(task);

            switch (task.getStatus()) {
                case SCHEDULED:
                    systemTask.start(workflow, task, this);
                    break;

                case IN_PROGRESS:
                    systemTask.execute(workflow, task, this);
                    break;
                default:
                    break;
            }

            if (!task.getStatus().isTerminal()) {
                task.setCallbackAfterSeconds(unackTimeout);
            }

            updateTask(new TaskResult(task));
            logger.info("Done Executing {}/{}-{} op={}", task.getTaskType(), task.getTaskId(), task.getStatus(), task.getOutputData().toString());

        } catch (Exception e) {
            logger.error("Error executing system task - {}, with id: {}", systemTask, taskId, e);
        }
    }

    private void setTaskDomains(List<Task> tasks, Workflow wf) {
        Map<String, String> taskToDomain = wf.getTaskToDomain();
        if (taskToDomain != null) {
            // Check if all tasks have the same domain "*"
            String domainstr = taskToDomain.get("*");
            if (domainstr != null) {
                String[] domains = domainstr.split(",");
                tasks.forEach(task -> {
                    // Filter out SystemTask
                    if (!TaskType.isSystemTask(task.getTaskType())) {
                        // Check which domain worker is polling
                        // Set the task domain
                        task.setDomain(getActiveDomain(task.getTaskType(), domains));
                    }
                });

            } else {
                tasks.forEach(task -> {
                    if (!TaskType.isSystemTask(task.getTaskType())) {
                        String taskDomainstr = taskToDomain.get(task.getTaskType());
                        if (taskDomainstr != null) {
                            task.setDomain(getActiveDomain(task.getTaskType(), taskDomainstr.split(",")));
                        }
                    }
                });
            }
        }
    }

    private String getActiveDomain(String taskType, String[] domains) {
        // The domain list has to be ordered.
        // In sequence check if any worker has polled for last 30 seconds, if so that isSystemTask the Active domain
        return Arrays.stream(domains)
                .map(domain -> executionDAO.getPollData(taskType, domain.trim()))
                .filter(Objects::nonNull)
                .filter(validateLastPolledTime)
                .findFirst()
                .map(PollData::getDomain)
                .orElse(null);
    }

    private long getTaskDuration(long s, Task task) {
        long duration = task.getEndTime() - task.getStartTime();
        s += duration;
        if (task.getRetriedTaskId() == null) {
            return s;
        }
        return s + getTaskDuration(s, executionDAO.getTask(task.getRetriedTaskId()));
    }

    @VisibleForTesting
    boolean scheduleTask(Workflow workflow, List<Task> tasks) {

        if (tasks == null || tasks.isEmpty()) {
            return false;
        }

        // Get the highest seq number
        int count = workflow.getTasks().stream()
                .mapToInt(Task::getSeq)
                .max()
                .orElse(0);

        for (Task task : tasks) {
            if (task.getSeq() == 0) { // Set only if the seq was not set
                task.setSeq(++count);
            }
        }

        // Save the tasks in the DAO
        List<Task> created = executionDAO.createTasks(tasks);

        List<Task> createdSystemTasks = created.stream()
                .filter(isSystemTask)
                .collect(Collectors.toList());

        List<Task> tasksToBeQueued = created.stream()
                .filter(isSystemTask.negate())
                .collect(Collectors.toList());

        boolean startedSystemTasks = false;

        // Traverse through all the system tasks, start the sync tasks, in case of async queue the tasks
        for (Task task : createdSystemTasks) {
            WorkflowSystemTask workflowSystemTask = WorkflowSystemTask.get(task.getTaskType());
            if (workflowSystemTask == null) {
                throw new ApplicationException(NOT_FOUND, "No system task found by name " + task.getTaskType());
            }
            task.setStartTime(System.currentTimeMillis());
            if (!workflowSystemTask.isAsync()) {
                try {
                    workflowSystemTask.start(workflow, task, this);
                } catch (Exception e) {
                    String message = String.format(
                            "Unable to start task {id: %s, name: %s}",
                            task.getTaskId(),
                            task.getTaskDefName()
                    );
                    throw new ApplicationException(Code.INTERNAL_ERROR, message, e);
                }
                startedSystemTasks = true;
                executionDAO.updateTask(task);
            } else {
                tasksToBeQueued.add(task);
            }
        }

        addTaskToQueue(tasksToBeQueued);
        return startedSystemTasks;
    }

    private void addTaskToQueue(final List<Task> tasks) {
        for (Task task : tasks) {
            addTaskToQueue(task);
        }
    }

    private void terminate(final Workflow workflow, TerminateWorkflowException tw) {
        if (!workflow.getStatus().isTerminal()) {
            workflow.setStatus(tw.workflowStatus);
        }

        String failureWorkflow = workflow.getWorkflowDefinition().getFailureWorkflow();
        if (failureWorkflow != null) {
            if (failureWorkflow.startsWith("$")) {
                String[] paramPathComponents = failureWorkflow.split("\\.");
                String name = paramPathComponents[2]; // name of the input parameter
                failureWorkflow = (String) workflow.getInput().get(name);
            }
        }
        if (tw.task != null) {
            executionDAO.updateTask(tw.task);
        }
        terminateWorkflow(workflow, tw.getMessage(), failureWorkflow);
    }

    private boolean rerunWF(String workflowId, String taskId, Map<String, Object> taskInput,
                            Map<String, Object> workflowInput, String correlationId) {

        // Get the workflow
        Workflow workflow = executionDAO.getWorkflow(workflowId);

        // If the task Id is null it implies that the entire workflow has to be rerun
        if (taskId == null) {
            // remove all tasks
            workflow.getTasks().forEach(task -> executionDAO.removeTask(task.getTaskId()));
            // Set workflow as RUNNING
            workflow.setStatus(WorkflowStatus.RUNNING);
            if (correlationId != null) {
                workflow.setCorrelationId(correlationId);
            }
            if (workflowInput != null) {
                workflow.setInput(workflowInput);
            }

            executionDAO.updateWorkflow(workflow);

            decide(workflowId);
            return true;
        }

        // Now iterate through the tasks and find the "specific" task
        Task rerunFromTask = null;
        for (Task task : workflow.getTasks()) {
            if (task.getTaskId().equals(taskId)) {
                rerunFromTask = task;
                break;
            } else {
                // If not found look into sub workflows
                if (task.getTaskType().equalsIgnoreCase(SubWorkflow.NAME)) {
                    String subWorkflowId = task.getInputData().get(SubWorkflow.SUB_WORKFLOW_ID).toString();
                    if (rerunWF(subWorkflowId, taskId, taskInput, null, null)) {
                        rerunFromTask = task;
                        break;
                    }
                }
            }
        }

        if (rerunFromTask != null) {
            // Remove all tasks after the "rerunFromTask"
            for (Task task : workflow.getTasks()) {
                if (task.getSeq() > rerunFromTask.getSeq()) {
                    executionDAO.removeTask(task.getTaskId());
                }
            }
            if (rerunFromTask.getTaskType().equalsIgnoreCase(SubWorkflow.NAME)) {
                // if task is sub workflow set task as IN_PROGRESS
                rerunFromTask.setStatus(IN_PROGRESS);
            } else {
                // Set the task to rerun as SCHEDULED
                rerunFromTask.setStatus(SCHEDULED);
                if (taskInput != null) {
                    rerunFromTask.setInputData(taskInput);
                }
                addTaskToQueue(rerunFromTask);
            }
            rerunFromTask.setExecuted(false);
            executionDAO.updateTask(rerunFromTask);

            // and set workflow as RUNNING
            workflow.setStatus(WorkflowStatus.RUNNING);
            if (correlationId != null) {
                workflow.setCorrelationId(correlationId);
            }
            if (workflowInput != null) {
                workflow.setInput(workflowInput);
            }
            executionDAO.updateWorkflow(workflow);
            decide(workflowId);
            return true;
        }
        return false;
    }

}
