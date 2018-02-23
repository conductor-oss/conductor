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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    public DeciderOutcome decide(Workflow workflow, WorkflowDef def) throws TerminateWorkflowException {

        workflow.setSchemaVersion(def.getSchemaVersion());
        //In case of a new workflow the list of tasks will be empty
        final List<Task> tasks = workflow.getTasks();
        //In case of a new workflow the list of executedTasks will also be empty
        List<Task> executedTasks = tasks.stream()
                .filter(t -> !t.getStatus().equals(Status.SKIPPED) && !t.getStatus().equals(Status.READY_FOR_RERUN))
                .collect(Collectors.toList());

        List<Task> tasksToBeScheduled = new LinkedList<>();
        if (executedTasks.isEmpty()) {
            //this isSystemTask the flow that the new workflow will go through
            tasksToBeScheduled = startWorkflow(workflow, def);
            if (tasksToBeScheduled == null) {
                tasksToBeScheduled = new LinkedList<>();
            }
        }
        return decide(def, workflow, tasksToBeScheduled);
    }

    private DeciderOutcome decide(final WorkflowDef def, final Workflow workflow, List<Task> preScheduledTasks) throws TerminateWorkflowException {

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

        //Filter the list of tasks and include only tasks that are not retried,
        // marked to be skipped and not part of System tasks that is DECISION, FORK, JOIN
        //This list will be empty for a new workflow being started
        List<Task> pendingTasks = workflow.getTasks()
                .stream()
                .filter(t -> (!t.isRetried() && !t.getStatus().equals(Status.SKIPPED)) || SystemTaskType.isBuiltIn(t.getTaskType()))
                .collect(Collectors.toList());

        //Get all the tasks that are ready to rerun or not marked to be skipped
        //This list will be empty for a new workflow
        Set<String> executedTaskRefNames = workflow.getTasks()
                .stream()
                .filter(t -> !t.getStatus().equals(Status.SKIPPED) && !t.getStatus().equals(Status.READY_FOR_RERUN))
                .map(Task::getReferenceTaskName)
                .collect(Collectors.toSet());

        Map<String, Task> tasksToBeScheduled = new LinkedHashMap<>();

        preScheduledTasks.forEach(pst -> {
            executedTaskRefNames.remove(pst.getReferenceTaskName());
            tasksToBeScheduled.put(pst.getReferenceTaskName(), pst);
        });

        //A new workflow does not enter this code branch
        for (Task pendingTask : pendingTasks) {

            if (SystemTaskType.is(pendingTask.getTaskType()) && !pendingTask.getStatus().isTerminal()) {
                tasksToBeScheduled.put(pendingTask.getReferenceTaskName(), pendingTask);
                executedTaskRefNames.remove(pendingTask.getReferenceTaskName());
            }

            TaskDef taskDefinition = metadataDAO.getTaskDef(pendingTask.getTaskDefName());
            if (taskDefinition != null) {//QQ what happens when the task definition is null at this time ??
                checkForTimeout(taskDefinition, pendingTask);
                // If the task has not been updated for "responseTimeout" then rescheduled it.
                if (checkForResponseTimeout(taskDefinition, pendingTask)) {
                    outcome.tasksToBeRequeued.add(pendingTask);
                }
            }

            if (!pendingTask.getStatus().isSuccessful()) {
                WorkflowTask workflowTask = pendingTask.getWorkflowTask();
                if (workflowTask == null) {
                    workflowTask = def.getTaskByRefName(pendingTask.getReferenceTaskName());
                }
                if (workflowTask != null && workflowTask.isOptional()) {
                    pendingTask.setStatus(Status.COMPLETED_WITH_ERRORS);
                } else {
                    Task rt = retry(taskDefinition, workflowTask, pendingTask, workflow);
                    tasksToBeScheduled.put(rt.getReferenceTaskName(), rt);
                    executedTaskRefNames.remove(rt.getReferenceTaskName());
                    outcome.tasksToBeUpdated.add(pendingTask);
                }
            }

            if (!pendingTask.isRetried() && pendingTask.getStatus().isTerminal()) {
                pendingTask.setRetried(true);
                List<Task> nextTasks = getNextTask(def, workflow, pendingTask);
                nextTasks.forEach(rt -> tasksToBeScheduled.put(rt.getReferenceTaskName(), rt));
                outcome.tasksToBeUpdated.add(pendingTask);
                logger.debug("Scheduling Tasks from {}, next = {}", pendingTask.getTaskDefName(),
                        nextTasks.stream()
                                .map(Task::getTaskDefName)
                                .collect(Collectors.toList()));
            }
        }

        //All the tasks that need to scheduled are added to the outcome, in case of
        List<Task> unScheduledTasks = tasksToBeScheduled.values().stream()
                .filter(tt -> !executedTaskRefNames.contains(tt.getReferenceTaskName()))
                .collect(Collectors.toList());
        if (!unScheduledTasks.isEmpty()) {
            logger.debug("Scheduling Tasks {} ", unScheduledTasks.stream()
                    .map(Task::getTaskDefName)
                    .collect(Collectors.toList()));
            outcome.tasksToBeScheduled.addAll(unScheduledTasks);
        }
        updateOutput(def, workflow);
        if (outcome.tasksToBeScheduled.isEmpty() && checkForWorkflowCompletion(def, workflow)) {
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
                .filter(task -> Status.READY_FOR_RERUN.equals(task.getStatus()))
                .findFirst()
                .map(task -> {
                    task.setStatus(Status.SCHEDULED);
                    task.setRetried(true);
                    task.setRetryCount(0);
                    return task; })
                .orElseThrow(() -> {
                    String reason = String.format("The workflow %s isSystemTask marked for re-run from %s but could not find the starting task",
                            workflow.getWorkflowId(), workflow.getReRunFromWorkflowId());
                    return new TerminateWorkflowException(reason);
                });

        return Arrays.asList(rerunFromTask);

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
            //return getTasksToBeScheduled(def, workflow, taskToSchedule, 0, task.getEndTime());
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
            WorkflowStatus status = task.getStatus().equals(Status.TIMED_OUT) ? WorkflowStatus.TIMED_OUT : WorkflowStatus.FAILED;
            task.setRetried(true);
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
        rescheduled.setStatus(Status.SCHEDULED);
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
    void checkForTimeout(TaskDef taskType, Task task) {

        if (taskType == null) {
            logger.warn("missing task type " + task.getTaskDefName() + ", workflowId=" + task.getWorkflowInstanceId());
            return;
        }
        if (task.getStatus().isTerminal() || taskType.getTimeoutSeconds() <= 0 || !task.getStatus().equals(Status.IN_PROGRESS)) {
            return;
        }

        long timeout = 1000 * taskType.getTimeoutSeconds();
        long now = System.currentTimeMillis();
        long elapsedTime = now - (task.getStartTime() + (task.getStartDelayInSeconds() * 1000));

        if (elapsedTime < timeout) {
            return;
        }

        String reason = "Task timed out after " + elapsedTime + " millisecond.  Timeout configured as " + timeout;
        Monitors.recordTaskTimeout(task.getTaskDefName());

        switch (taskType.getTimeoutPolicy()) {
            case ALERT_ONLY:
                return;
            case RETRY:
                task.setStatus(Status.TIMED_OUT);
                task.setReasonForIncompletion(reason);
                return;
            case TIME_OUT_WF:
                task.setStatus(Status.TIMED_OUT);
                task.setReasonForIncompletion(reason);
                throw new TerminateWorkflowException(reason, WorkflowStatus.TIMED_OUT, task);
        }
    }

    @VisibleForTesting
    boolean checkForResponseTimeout(TaskDef taskType, Task task) {

        if (taskType == null) {
            logger.warn("missing task type " + task.getTaskDefName() + ", workflowId=" + task.getWorkflowInstanceId());
            return false;
        }
        if (task.getStatus().isTerminal() || taskType.getTimeoutSeconds() <= 0 ||
                !task.getStatus().equals(Status.IN_PROGRESS) || taskType.getResponseTimeoutSeconds() == 0) {
            return false;
        }

        long responseTimeout = 1000 * taskType.getResponseTimeoutSeconds();
        long now = System.currentTimeMillis();
        long noResponseTime = now - task.getUpdateTime();

        if (noResponseTime < responseTimeout) {
            return false;
        }
        Monitors.recordTaskResponseTimeout(task.getTaskDefName());

        return true;
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
        String taskId = IDGenerator.generate();
        TaskMapperContext taskMapperContext = new TaskMapperContext(workflowDefinition, workflowInstance, taskToSchedule,
                input, retryCount, retriedTaskId, taskId, this);
        return taskMappers.get(taskType.name()).getMappedTasks(taskMapperContext);
    }


    private boolean isTaskSkipped(WorkflowTask taskToSchedule, Workflow workflow) {
        try {
            boolean retval = false;
            if (taskToSchedule != null) {
                Task t = workflow.getTaskByRefName(taskToSchedule.getTaskReferenceName());
                if (t == null) {
                    retval = false;
                } else if (t.getStatus().equals(Status.SKIPPED)) {
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
