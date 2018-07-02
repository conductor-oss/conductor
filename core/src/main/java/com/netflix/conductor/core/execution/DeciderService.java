/**
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.core.execution;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask.Type;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.metrics.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.conductor.common.metadata.tasks.Task.Status.COMPLETED_WITH_ERRORS;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.IN_PROGRESS;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.READY_FOR_RERUN;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.SCHEDULED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.SKIPPED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.TIMED_OUT;

/**
 * @author Viren
 * @author Vikram
 * Decider evaluates the state of the workflow by inspecting the current state along with the blueprint.
 * The result of the evaluation is either to schedule further tasks, complete/fail the workflow or do nothing.
 */
public class DeciderService {

    private static Logger logger = LoggerFactory.getLogger(DeciderService.class);

    private MetadataDAO metadataDAO;

    private ParametersUtils parametersUtils = new ParametersUtils();

    private Map<String, TaskMapper> taskMappers;

    @Inject
    public DeciderService(MetadataDAO metadataDAO, @Named("TaskMappers") Map<String, TaskMapper> taskMappers) {
        this.metadataDAO = metadataDAO;
        this.taskMappers = taskMappers;
    }

    //QQ public method validation of the input params
    public DeciderOutcome decide(Workflow workflow, WorkflowDef workflowDef) throws TerminateWorkflowException {

        workflow.setSchemaVersion(workflowDef.getSchemaVersion());
        //In case of a new workflow the list of tasks will be empty
        final List<Task> tasks = workflow.getTasks();
        //In case of a new workflow the list of executedTasks will also be empty
        List<Task> executedTasks = tasks.stream()
                .filter(t -> !t.getStatus().equals(SKIPPED) && !t.getStatus().equals(READY_FOR_RERUN))
                .collect(Collectors.toList());

        List<Task> tasksToBeScheduled = new LinkedList<>();
        if (executedTasks.isEmpty()) {
            //this is the flow that the new workflow will go through
            tasksToBeScheduled = startWorkflow(workflow, workflowDef);
            if (tasksToBeScheduled == null) {
                tasksToBeScheduled = new LinkedList<>();
            }
        }
        return decide(workflowDef, workflow, tasksToBeScheduled);
    }

    private DeciderOutcome decide(final WorkflowDef workflowDef, final Workflow workflow, List<Task> preScheduledTasks) throws TerminateWorkflowException {

        DeciderOutcome outcome = new DeciderOutcome();

        if (workflow.getStatus().equals(WorkflowStatus.PAUSED)) {
            logger.debug("Workflow " + workflow.getWorkflowId() + " is paused");
            return outcome;
        }

        if (workflow.getStatus().isTerminal()) {
            //you cannot evaluate a terminal workflow
            logger.debug("Workflow " + workflow.getWorkflowId() + " is already finished.  status=" + workflow.getStatus() + ", reason=" + workflow.getReasonForIncompletion());
            return outcome;
        }

        // Filter the list of tasks and include only tasks that are not retried,
        // marked to be skipped and not part of System tasks that is DECISION, FORK, JOIN
        // This list will be empty for a new workflow being started
        List<Task> pendingTasks = workflow.getTasks()
                .stream()
                .filter(task -> (!task.isRetried() && !task.getStatus().equals(SKIPPED)) || SystemTaskType.isBuiltIn(task.getTaskType()))
                .collect(Collectors.toList());

        // Get all the tasks that are ready to rerun or not marked to be skipped
        // This list will be empty for a new workflow
        Set<String> executedTaskRefNames = workflow.getTasks()
                .stream()
                .filter(task -> !task.getStatus().equals(SKIPPED) && !task.getStatus().equals(READY_FOR_RERUN))
                .map(Task::getReferenceTaskName)
                .collect(Collectors.toSet());

        Map<String, Task> tasksToBeScheduled = new LinkedHashMap<>();

        preScheduledTasks.forEach(pst -> {
            executedTaskRefNames.remove(pst.getReferenceTaskName());
            tasksToBeScheduled.put(pst.getReferenceTaskName(), pst);
        });

        // A new workflow does not enter this code branch
        for (Task pendingTask : pendingTasks) {

            if (SystemTaskType.is(pendingTask.getTaskType()) && !pendingTask.getStatus().isTerminal()) {
                tasksToBeScheduled.putIfAbsent(pendingTask.getReferenceTaskName(), pendingTask);
                executedTaskRefNames.remove(pendingTask.getReferenceTaskName());
            }

            TaskDef taskDefinition = metadataDAO.getTaskDef(pendingTask.getTaskDefName());
            if (taskDefinition != null) {
                checkForTimeout(taskDefinition, pendingTask);
                // If the task has not been updated for "responseTimeout" then mark task as TIMED_OUT
                if (isResponseTimedOut(taskDefinition, pendingTask)) {
                    timeoutTask(pendingTask);
                }
            }

            if (!pendingTask.getStatus().isSuccessful()) {
                WorkflowTask workflowTask = pendingTask.getWorkflowTask();
                if (workflowTask == null) {
                    workflowTask = workflowDef.getTaskByRefName(pendingTask.getReferenceTaskName());
                }
                if (workflowTask != null && workflowTask.isOptional()) {
                    pendingTask.setStatus(COMPLETED_WITH_ERRORS);
                } else {
                    Task retryTask = retry(taskDefinition, workflowTask, pendingTask, workflow);
                    tasksToBeScheduled.put(retryTask.getReferenceTaskName(), retryTask);
                    executedTaskRefNames.remove(retryTask.getReferenceTaskName());
                    outcome.tasksToBeUpdated.add(pendingTask);
                }
            }

            if (!pendingTask.isExecuted() && !pendingTask.isRetried() && pendingTask.getStatus().isTerminal()) {
                pendingTask.setExecuted(true);
                List<Task> nextTasks = getNextTask(workflowDef, workflow, pendingTask);
                nextTasks.forEach(nextTask -> tasksToBeScheduled.putIfAbsent(nextTask.getReferenceTaskName(), nextTask));
                outcome.tasksToBeUpdated.add(pendingTask);
                logger.debug("Scheduling Tasks from {}, next = {}", pendingTask.getTaskDefName(),
                        nextTasks.stream()
                                .map(Task::getTaskDefName)
                                .collect(Collectors.toList()));
            }
        }

        //All the tasks that need to scheduled are added to the outcome, in case of
        List<Task> unScheduledTasks = tasksToBeScheduled.values().stream()
                .filter(task -> !executedTaskRefNames.contains(task.getReferenceTaskName()))
                .collect(Collectors.toList());
        if (!unScheduledTasks.isEmpty()) {
            logger.debug("Scheduling Tasks {} ", unScheduledTasks.stream()
                    .map(Task::getTaskDefName)
                    .collect(Collectors.toList()));
            outcome.tasksToBeScheduled.addAll(unScheduledTasks);
        }
        updateOutput(workflowDef, workflow);
        if (outcome.tasksToBeScheduled.isEmpty() && checkForWorkflowCompletion(workflowDef, workflow)) {
            logger.debug("Marking workflow as complete.  workflow=" + workflow.getWorkflowId() + ", tasks=" + workflow.getTasks());
            outcome.isComplete = true;
        }

        return outcome;
    }

    private List<Task> startWorkflow(Workflow workflow, WorkflowDef def) throws TerminateWorkflowException {

        logger.debug("Starting workflow " + def.getName() + "/" + workflow.getWorkflowId());
        //The tasks will be empty in case of new workflow
        List<Task> tasks = workflow.getTasks();
        // Check if the workflow isSystemTask a re-run case or if it isSystemTask a new workflow execution
        if (workflow.getReRunFromWorkflowId() == null || tasks.isEmpty()) {

            if (def.getTasks().isEmpty()) {
                throw new TerminateWorkflowException("No tasks found to be executed", WorkflowStatus.COMPLETED);
            }

            WorkflowTask taskToSchedule = def.getTasks().getFirst(); //Nothing isSystemTask running yet - so schedule the first task
            //Loop until a non-skipped task isSystemTask found
            while (isTaskSkipped(taskToSchedule, workflow)) {
                taskToSchedule = def.getNextTask(taskToSchedule.getTaskReferenceName());
            }

            //In case of a new workflow a the first non-skippable task will be scheduled
            return getTasksToBeScheduled(def, workflow, taskToSchedule, 0);
        }

        // Get the first task to schedule
        Task rerunFromTask = tasks.stream()
                .filter(task -> READY_FOR_RERUN.equals(task.getStatus()))
                .findFirst()
                .map(task -> {
                    task.setStatus(SCHEDULED);
                    task.setRetried(true);
                    task.setRetryCount(0);
                    return task;
                })
                .orElseThrow(() -> {
                    String reason = String.format("The workflow %s isSystemTask marked for re-run from %s but could not find the starting task",
                            workflow.getWorkflowId(), workflow.getReRunFromWorkflowId());
                    return new TerminateWorkflowException(reason);
                });

        return Collections.singletonList(rerunFromTask);

    }

    private void updateOutput(final WorkflowDef def, final Workflow workflow) {

        List<Task> allTasks = workflow.getTasks();
        if (allTasks.isEmpty()) {
            return;
        }

        Task last;
        last = allTasks.get(allTasks.size() - 1);
        Map<String, Object> output = last.getOutputData();

        if (!def.getOutputParameters().isEmpty()) {
            output = parametersUtils.getTaskInput(def.getOutputParameters(), workflow, null, null);
        }
        workflow.setOutput(output);
    }

    private boolean checkForWorkflowCompletion(final WorkflowDef def, final Workflow workflow) throws TerminateWorkflowException {

        List<Task> allTasks = workflow.getTasks();
        if (allTasks.isEmpty()) {
            return false;
        }

        Map<String, Status> taskStatusMap = new HashMap<>();
        workflow.getTasks().forEach(task -> taskStatusMap.put(task.getReferenceTaskName(), task.getStatus()));

        LinkedList<WorkflowTask> wftasks = def.getTasks();
        boolean allCompletedSuccessfully = wftasks.stream().parallel().allMatch(wftask -> {
            Status status = taskStatusMap.get(wftask.getTaskReferenceName());
            return status != null && status.isSuccessful() && status.isTerminal();
        });

        boolean noPendingTasks = taskStatusMap.values()
                .stream()
                .allMatch(Status::isTerminal);

        boolean noPendingSchedule = workflow.getTasks().stream().parallel().filter(wftask -> {
            String next = getNextTasksToBeScheduled(def, workflow, wftask);
            return next != null && !taskStatusMap.containsKey(next);
        }).collect(Collectors.toList()).isEmpty();

        if (allCompletedSuccessfully && noPendingTasks && noPendingSchedule) {
            return true;
        }

        return false;
    }

    @VisibleForTesting
    List<Task> getNextTask(WorkflowDef def, Workflow workflow, Task task) {

        // Get the following task after the last completed task
        if (SystemTaskType.is(task.getTaskType()) && SystemTaskType.DECISION.name().equals(task.getTaskType())) {
            if (task.getInputData().get("hasChildren") != null) {
                return Collections.emptyList();
            }
        }

        String taskReferenceName = task.getReferenceTaskName();
        WorkflowTask taskToSchedule = def.getNextTask(taskReferenceName);
        while (isTaskSkipped(taskToSchedule, workflow)) {
            taskToSchedule = def.getNextTask(taskToSchedule.getTaskReferenceName());
        }
        if (taskToSchedule != null) {
            return getTasksToBeScheduled(def, workflow, taskToSchedule, 0);
        }

        return Collections.emptyList();
    }

    private String getNextTasksToBeScheduled(WorkflowDef def, Workflow workflow, Task task) {

        String taskReferenceName = task.getReferenceTaskName();
        WorkflowTask taskToSchedule = def.getNextTask(taskReferenceName);
        while (isTaskSkipped(taskToSchedule, workflow)) {
            taskToSchedule = def.getNextTask(taskToSchedule.getTaskReferenceName());
        }
        return taskToSchedule == null ? null : taskToSchedule.getTaskReferenceName();
    }

    @VisibleForTesting
    Task retry(TaskDef taskDefinition, WorkflowTask workflowTask, Task task, Workflow workflow) throws TerminateWorkflowException {

        int retryCount = task.getRetryCount();
        if (!task.getStatus().isRetriable() || SystemTaskType.isBuiltIn(task.getTaskType()) || taskDefinition == null || taskDefinition.getRetryCount() <= retryCount) {
            WorkflowStatus status = task.getStatus().equals(TIMED_OUT) ? WorkflowStatus.TIMED_OUT : WorkflowStatus.FAILED;
            throw new TerminateWorkflowException(task.getReasonForIncompletion(), status, task);
        }

        // retry... - but not immediately - put a delay...
        int startDelay = taskDefinition.getRetryDelaySeconds();
        switch (taskDefinition.getRetryLogic()) {
            case FIXED:
                startDelay = taskDefinition.getRetryDelaySeconds();
                break;
            case EXPONENTIAL_BACKOFF:
                startDelay = taskDefinition.getRetryDelaySeconds() * (1 + task.getRetryCount());
                break;
        }

        task.setRetried(true);

        Task rescheduled = task.copy();
        rescheduled.setStartDelayInSeconds(startDelay);
        rescheduled.setCallbackAfterSeconds(startDelay);
        rescheduled.setRetryCount(task.getRetryCount() + 1);
        rescheduled.setRetried(false);
        rescheduled.setTaskId(IDGenerator.generate());
        rescheduled.setRetriedTaskId(task.getTaskId());
        rescheduled.setStatus(SCHEDULED);
        rescheduled.setPollCount(0);
        rescheduled.setInputData(new HashMap<>());
        rescheduled.getInputData().putAll(task.getInputData());
        if (workflowTask != null && workflow.getSchemaVersion() > 1) {
            Map<String, Object> taskInput = parametersUtils.getTaskInputV2(workflowTask.getInputParameters(), workflow, rescheduled.getTaskId(), taskDefinition);
            rescheduled.getInputData().putAll(taskInput);
        }
        //for the schema version 1, we do not have to recompute the inputs
        return rescheduled;
    }

    @VisibleForTesting
    void checkForTimeout(TaskDef taskDef, Task task) {

        if (taskDef == null) {
            logger.warn("missing task type " + task.getTaskDefName() + ", workflowId=" + task.getWorkflowInstanceId());
            return;
        }
        if (task.getStatus().isTerminal() || taskDef.getTimeoutSeconds() <= 0 || !task.getStatus().equals(IN_PROGRESS)) {
            return;
        }

        long timeout = 1000 * taskDef.getTimeoutSeconds();
        long now = System.currentTimeMillis();
        long elapsedTime = now - (task.getStartTime() + (task.getStartDelayInSeconds() * 1000));

        if (elapsedTime < timeout) {
            return;
        }

        String reason = "Task timed out after " + elapsedTime + " millisecond.  Timeout configured as " + timeout;
        Monitors.recordTaskTimeout(task.getTaskDefName());

        switch (taskDef.getTimeoutPolicy()) {
            case ALERT_ONLY:
                return;
            case RETRY:
                task.setStatus(TIMED_OUT);
                task.setReasonForIncompletion(reason);
                return;
            case TIME_OUT_WF:
                task.setStatus(TIMED_OUT);
                task.setReasonForIncompletion(reason);
                throw new TerminateWorkflowException(reason, WorkflowStatus.TIMED_OUT, task);
        }
    }

    @VisibleForTesting
    boolean isResponseTimedOut(TaskDef taskDefinition, Task task) {

        logger.debug("Evaluating responseTimeOut for Task: {}, with Task Definition: {} ", task, taskDefinition);

        if (taskDefinition == null) {
            logger.warn("missing task type : {}, workflowId= {}", task.getTaskDefName(), task.getWorkflowInstanceId());
            return false;
        }
        if (task.getStatus().isTerminal() || !task.getStatus().equals(IN_PROGRESS) || taskDefinition.getResponseTimeoutSeconds() == 0) {
            return false;
        }

        long responseTimeout = 1000 * taskDefinition.getResponseTimeoutSeconds();
        long now = System.currentTimeMillis();
        long noResponseTime = now - task.getUpdateTime();

        if (noResponseTime < responseTimeout) {
            logger.debug("Current responseTime: {} has not exceeded the configured responseTimeout of {} " +
                    "for the Task: {} with Task Definition: {}", noResponseTime, responseTimeout, task, taskDefinition);
            return false;
        }

        Monitors.recordTaskResponseTimeout(task.getTaskDefName());
        return true;
    }

    private void timeoutTask(Task task) {
        String reason = "responseTimeout: " + task.getResponseTimeoutSeconds() + " exceeded for the taskId: " + task.getTaskId() + " with Task Definition: " + task.getTaskDefName();
        logger.debug(reason);
        task.setStatus(TIMED_OUT);
        task.setReasonForIncompletion(reason);
    }

    public List<Task> getTasksToBeScheduled(WorkflowDef def, Workflow workflow,
                                            WorkflowTask taskToSchedule, int retryCount) {
        return getTasksToBeScheduled(def, workflow, taskToSchedule, retryCount, null);
    }

    public List<Task> getTasksToBeScheduled(WorkflowDef workflowDefinition, Workflow workflowInstance,
                                            WorkflowTask taskToSchedule, int retryCount, String retriedTaskId) {

        Map<String, Object> input = parametersUtils.getTaskInput(taskToSchedule.getInputParameters(),
                workflowInstance, null, null);

        Type taskType = Type.USER_DEFINED;
        String type = taskToSchedule.getType();
        if (Type.isSystemTask(type)) {
            taskType = Type.valueOf(type);
        }

        // get in progress tasks for this workflow instance
        List<String> inProgressTasks = workflowInstance.getTasks().stream()
                .filter(pendingTask -> pendingTask.getStatus().equals(Status.IN_PROGRESS))
                .map(Task::getReferenceTaskName)
                .collect(Collectors.toList());

        String taskId = IDGenerator.generate();
        TaskMapperContext taskMapperContext = new TaskMapperContext(workflowDefinition, workflowInstance, taskToSchedule,
                input, retryCount, retriedTaskId, taskId, this);

        // for static forks, each branch of the fork creates a join task upon completion
        // for dynamic forks, a join task is created with the fork and also with each branch of the fork
        // a new task must only be scheduled if a task with the same reference name is not in progress for this workflow instance
        return taskMappers.get(taskType.name()).getMappedTasks(taskMapperContext).stream()
                .filter(task -> !inProgressTasks.contains(task.getReferenceTaskName()))
                .collect(Collectors.toList());
    }


    private boolean isTaskSkipped(WorkflowTask taskToSchedule, Workflow workflow) {
        try {
            boolean retval = false;
            if (taskToSchedule != null) {
                Task t = workflow.getTaskByRefName(taskToSchedule.getTaskReferenceName());
                if (t == null) {
                    retval = false;
                } else if (t.getStatus().equals(SKIPPED)) {
                    retval = true;
                }
            }
            return retval;
        } catch (Exception e) {
            throw new TerminateWorkflowException(e.getMessage());
        }

    }


    public static class DeciderOutcome {

        List<Task> tasksToBeScheduled = new LinkedList<>();

        List<Task> tasksToBeUpdated = new LinkedList<>();

        List<Task> tasksToBeRequeued = new LinkedList<>();

        boolean isComplete;

        private DeciderOutcome() {
        }

    }
}
