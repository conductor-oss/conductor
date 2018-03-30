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
import com.google.common.base.Preconditions;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.core.execution.DeciderService.DeciderOutcome;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Viren Workflow services provider interface
 */
@Trace
public class WorkflowExecutor {

    private static Logger logger = LoggerFactory.getLogger(WorkflowExecutor.class);

    private MetadataDAO metadataDAO;

    private ExecutionDAO executionDAO;

    private QueueDAO queueDAO;

    private DeciderService deciderService;

    private Configuration config;

    public static final String deciderQueue = "_deciderQueue";

    private int activeWorkerLastPollnSecs;

    @Inject
    public WorkflowExecutor(DeciderService deciderService, MetadataDAO metadataDAO, ExecutionDAO executionDAO, QueueDAO queueDAO, Configuration config) {
        this.deciderService = deciderService;
        this.metadataDAO = metadataDAO;
        this.executionDAO = executionDAO;
        this.queueDAO = queueDAO;
        this.config = config;
        activeWorkerLastPollnSecs = config.getIntProperty("tasks.active.worker.lastpoll", 10);
    }

    public String startWorkflow(String name, int version, String correlationId, Map<String, Object> input) throws Exception {
        return startWorkflow(name, version, correlationId, input, null);
    }

    public String startWorkflow(String name, int version, String correlationId, Map<String, Object> input, String event) throws Exception {
        return startWorkflow(name, version, input, correlationId, null, null, event);
    }

    public String startWorkflow(String name, int version, String correlationId, Map<String, Object> input, String event, Map<String, String> taskToDomain) throws Exception {
        return startWorkflow(name, version, input, correlationId, null, null, event, taskToDomain);
    }

    public String startWorkflow(String name, int version, Map<String, Object> input, String correlationId, String parentWorkflowId, String parentWorkflowTaskId, String event) throws Exception {
        return startWorkflow(name, version, input, correlationId, parentWorkflowId, parentWorkflowTaskId, event, null);
    }

    private final Predicate<PollData> validateLastPolledTime = pd -> pd.getLastPollTime() > System.currentTimeMillis() - (activeWorkerLastPollnSecs * 1000);

    private final Predicate<Task> isSystemTask = task -> SystemTaskType.is(task.getTaskType());

    private final Predicate<Task> isNonTerminalTask = task -> !task.getStatus().isTerminal();

    public String startWorkflow(String workflowName, int workflowVersion, Map<String, Object> workflowInput,
                                String correlationId, String parentWorkflowId, String parentWorkflowTaskId,
                                String event, Map<String, String> taskToDomain) throws Exception {

        try {
            //Check if the input to the workflow is not null
            //QQ When is the payload of the input validated
            if (workflowInput == null) {
                logger.error("The input for the workflow {} cannot be NULL", workflowName);
                throw new ApplicationException(Code.INVALID_INPUT, "NULL input passed when starting workflow");
            }

            //Check if the workflow definition is valid
            WorkflowDef workflowDefinition = metadataDAO.get(workflowName, workflowVersion);
            if (workflowDefinition == null) {
                logger.error("There is no workflow defined with name {} and version {}", workflowName, workflowVersion);
                throw new ApplicationException(Code.NOT_FOUND, "No such workflow defined. name=" + workflowName + ", version=" + workflowVersion);
            }

            //because everything else is a system defined task
            Set<String> missingTaskDefs = workflowDefinition.all().stream()
                    .filter(wft -> wft.getType().equals(WorkflowTask.Type.SIMPLE.name()))
                    .map(wft2 -> wft2.getName())
                    .filter(task -> metadataDAO.getTaskDef(task) == null)
                    .collect(Collectors.toSet());

            if (!missingTaskDefs.isEmpty()) {
                logger.error("Cannot find the task definitions for the following tasks used in workflow: {}", missingTaskDefs);
                throw new ApplicationException(Code.INVALID_INPUT, "Cannot find the task definitions for the following tasks used in workflow: " + missingTaskDefs);
            }
            //A random UUID is assigned to the work flow instance
            String workflowId = IDGenerator.generate();

            // Persist the Workflow
            Workflow wf = new Workflow();
            wf.setWorkflowId(workflowId);
            wf.setCorrelationId(correlationId);
            wf.setWorkflowType(workflowName);
            wf.setVersion(workflowVersion);
            wf.setInput(workflowInput);
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
            logger.info("A new instance of workflow {} created with workflow id {}", workflowName, workflowId);
            //then decide to see if anything needs to be done as part of the workflow
            decide(workflowId);

            return workflowId;

        } catch (Exception e) {
            Monitors.recordWorkflowStartError(workflowName, WorkflowContext.get().getClientApp());
            throw e;
        }
    }

    public String resetCallbacksForInProgressTasks(String workflowId) throws Exception {
        Workflow workflow = executionDAO.getWorkflow(workflowId, true);
        if (workflow.getStatus().isTerminal()) {
            throw new ApplicationException(Code.CONFLICT, "Workflow is completed.  status=" + workflow.getStatus());
        }

        // Get tasks that are in progress and have callbackAfterSeconds > 0
        // and set the callbackAfterSeconds to 0;
        for (Task t : workflow.getTasks()) {
            if (t.getStatus().equals(Status.IN_PROGRESS) &&
                    t.getCallbackAfterSeconds() > 0) {
                if (queueDAO.setOffsetTime(QueueUtils.getQueueName(t), t.getTaskId(), 0)) {
                    t.setCallbackAfterSeconds(0);
                    executionDAO.updateTask(t);
                }
            }
        }
        ;
        return workflowId;
    }

    public String rerun(RerunWorkflowRequest request) throws Exception {
        Preconditions.checkNotNull(request.getReRunFromWorkflowId(), "reRunFromWorkflowId is missing");
        if (!rerunWF(request.getReRunFromWorkflowId(), request.getReRunFromTaskId(), request.getTaskInput(),
                request.getWorkflowInput(), request.getCorrelationId())) {
            throw new ApplicationException(Code.INVALID_INPUT, "Task " + request.getReRunFromTaskId() + " not found");
        }
        return request.getReRunFromWorkflowId();
    }

    public void rewind(String workflowId) throws Exception {
        Workflow workflow = executionDAO.getWorkflow(workflowId, true);
        if (!workflow.getStatus().isTerminal()) {
            throw new ApplicationException(Code.CONFLICT, "Workflow is still running.  status=" + workflow.getStatus());
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

    public void retry(String workflowId) throws Exception {
        Workflow workflow = executionDAO.getWorkflow(workflowId, true);
        if (!workflow.getStatus().isTerminal()) {
            throw new ApplicationException(Code.CONFLICT, "Workflow is still running.  status=" + workflow.getStatus());
        }
        if (workflow.getTasks().isEmpty()) {
            throw new ApplicationException(Code.CONFLICT, "Workflow has not started yet");
        }

        // First get the failed task and the cancelled task
        Task failedTask = null;
        List<Task> cancelledTasks = new ArrayList<Task>();
        for (Task t : workflow.getTasks()) {
            if (t.getStatus().equals(Status.FAILED)) {
                failedTask = t;
            } else if (t.getStatus().equals(Status.CANCELED)) {
                cancelledTasks.add(t);

            }
        }
        ;
        if (failedTask != null && !failedTask.getStatus().isTerminal()) {
            throw new ApplicationException(Code.CONFLICT,
                    "The last task is still not completed!  I can only retry the last failed task.  Use restart if you want to attempt entire workflow execution again.");
        }
        if (failedTask != null && failedTask.getStatus().isSuccessful()) {
            throw new ApplicationException(Code.CONFLICT,
                    "The last task has not failed!  I can only retry the last failed task.  Use restart if you want to attempt entire workflow execution again.");
        }

        // Below is the situation where currently when the task failure causes
        // workflow to fail, the task's retried flag is not updated. This is to
        // update for these old tasks.
        List<Task> update = workflow.getTasks().stream()
                .filter(task -> !task.isRetried())
                .collect(Collectors.toList());

        update.forEach(task -> task.setRetried(true));
        executionDAO.updateTasks(update);

        List<Task> rescheduledTasks = new ArrayList<Task>();
        // Now reschedule the failed task
        Task retried = failedTask.copy();
        retried.setTaskId(IDGenerator.generate());
        retried.setRetriedTaskId(failedTask.getTaskId());
        retried.setStatus(Status.SCHEDULED);
        retried.setRetryCount(failedTask.getRetryCount() + 1);
        rescheduledTasks.add(retried);

        // Reschedule the cancelled task but if the join is cancelled set that to in progress
        cancelledTasks.forEach(t -> {
            if (t.getTaskType().equalsIgnoreCase(WorkflowTask.Type.JOIN.toString())) {
                t.setStatus(Status.IN_PROGRESS);
                t.setRetried(false);
                executionDAO.updateTask(t);
            } else {
                //executionDAO.removeTask(t.getTaskId());
                Task copy = t.copy();
                copy.setTaskId(IDGenerator.generate());
                copy.setRetriedTaskId(t.getTaskId());
                copy.setStatus(Status.SCHEDULED);
                copy.setRetryCount(t.getRetryCount() + 1);
                rescheduledTasks.add(copy);
            }
        });

        scheduleTask(workflow, rescheduledTasks);

        workflow.setStatus(WorkflowStatus.RUNNING);
        executionDAO.updateWorkflow(workflow);

        decide(workflowId);

    }

    public List<Workflow> getStatusByCorrelationId(String workflowName, String correlationId, boolean includeClosed) throws Exception {
        Preconditions.checkNotNull(correlationId, "correlation id is missing");
        Preconditions.checkNotNull(workflowName, "workflow name is missing");
        List<Workflow> workflows = executionDAO.getWorkflowsByCorrelationId(correlationId);
        List<Workflow> result = new LinkedList<>();
        for (Workflow wf : workflows) {
            if (wf.getWorkflowType().equals(workflowName) && (includeClosed || wf.getStatus().equals(WorkflowStatus.RUNNING))) {
                result.add(wf);
            }
        }

        return result;
    }

    public Task getPendingTaskByWorkflow(String taskReferenceName, String workflowId) {
        return executionDAO.getTasksForWorkflow(workflowId).stream()
                .filter(isNonTerminalTask)
                .filter(task -> task.getReferenceTaskName().equals(taskReferenceName))
                .findFirst() // There can only be one task by a given reference name running at a time.
                .orElse(null);
    }

    public void completeWorkflow(Workflow wf) throws Exception {
        Workflow workflow = executionDAO.getWorkflow(wf.getWorkflowId(), false);

        if (workflow.getStatus().equals(WorkflowStatus.COMPLETED)) {
            executionDAO.removeFromPendingWorkflow(workflow.getWorkflowType(), workflow.getWorkflowId());
            logger.info("Workflow has already been completed.  Current status={}, workflowId= {}", workflow.getStatus(), wf.getWorkflowId());
            return;
        }

        if (workflow.getStatus().isTerminal()) {
            String msg = "Workflow has already been completed.  Current status " + workflow.getStatus();
            throw new ApplicationException(Code.CONFLICT, msg);
        }

        workflow.setStatus(WorkflowStatus.COMPLETED);
        workflow.setOutput(wf.getOutput());
        executionDAO.updateWorkflow(workflow);

        // If the following task, for some reason fails, the sweep will take
        // care of this again!
        if (workflow.getParentWorkflowId() != null) {
            Workflow parent = executionDAO.getWorkflow(workflow.getParentWorkflowId(), false);
            decide(parent.getWorkflowId());
        }
        Monitors.recordWorkflowCompletion(workflow.getWorkflowType(), workflow.getEndTime() - workflow.getStartTime(), wf.getOwnerApp());
        queueDAO.remove(deciderQueue, workflow.getWorkflowId());    //remove from the sweep queue
    }

    public void terminateWorkflow(String workflowId, String reason) throws Exception {
        Workflow workflow = executionDAO.getWorkflow(workflowId, true);
        workflow.setStatus(WorkflowStatus.TERMINATED);
        terminateWorkflow(workflow, reason, null);
    }

    public void terminateWorkflow(Workflow workflow, String reason, String failureWorkflow) throws Exception {

        if (!workflow.getStatus().isTerminal()) {
            workflow.setStatus(WorkflowStatus.TERMINATED);
        }

        String workflowId = workflow.getWorkflowId();
        workflow.setReasonForIncompletion(reason);
        executionDAO.updateWorkflow(workflow);

        List<Task> tasks = workflow.getTasks();
        for (Task task : tasks) {
            if (!task.getStatus().isTerminal()) {
                // Cancel the ones which are not completed yet....
                task.setStatus(Status.CANCELED);
                if (isSystemTask.test(task)) {
                    WorkflowSystemTask stt = WorkflowSystemTask.get(task.getTaskType());
                    stt.cancel(workflow, task, this);
                    //SystemTaskType.valueOf(task.getTaskType()).cancel(workflow, task, this);
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
            Map<String, Object> input = new HashMap<>();
            input.putAll(workflow.getInput());
            input.put("workflowId", workflowId);
            input.put("reason", reason);
            input.put("failureStatus", workflow.getStatus().toString());

            try {

                WorkflowDef latestFailureWorkflow = metadataDAO.getLatest(failureWorkflow);
                String failureWFId = startWorkflow(failureWorkflow, latestFailureWorkflow.getVersion(), input, workflowId, null, null, null);
                workflow.getOutput().put("conductor.failure_workflow", failureWFId);

            } catch (Exception e) {
                logger.error("Failed to start error workflow", e);
                workflow.getOutput().put("conductor.failure_workflow", "Error workflow " + failureWorkflow + " failed to start.  reason: " + e.getMessage());
                Monitors.recordWorkflowStartError(failureWorkflow, WorkflowContext.get().getClientApp());
            }
        }

        queueDAO.remove(deciderQueue, workflow.getWorkflowId());    //remove from the sweep queue
        executionDAO.removeFromPendingWorkflow(workflow.getWorkflowType(), workflow.getWorkflowId());

        // Send to atlas
        Monitors.recordWorkflowTermination(workflow.getWorkflowType(), workflow.getStatus(), workflow.getOwnerApp());
    }

    public void updateTask(TaskResult result) throws Exception {
        if (result == null) {
            logger.info("null task given for update..." + result);
            throw new ApplicationException(Code.INVALID_INPUT, "Task object is null");
        }

        String workflowId = result.getWorkflowInstanceId();
        Workflow workflowInstance = executionDAO.getWorkflow(workflowId);
        Task task = executionDAO.getTask(result.getTaskId());

        logger.debug("Task: {} belonging to Workflow {} being updated", task, workflowInstance);

        String taskQueueName = QueueUtils.getQueueName(task);
        if (workflowInstance.getStatus().isTerminal()) {
            // Workflow is in terminal state
            queueDAO.remove(taskQueueName, result.getTaskId());
            logger.debug("Workflow: {} is in terminal state Task: {} removed from Queue: {} during update task", task, workflowInstance, taskQueueName);
            if (!task.getStatus().isTerminal()) {
                task.setStatus(Status.COMPLETED);
            }
            task.setOutputData(result.getOutputData());
            task.setReasonForIncompletion(result.getReasonForIncompletion());
            task.setWorkerId(result.getWorkerId());
            executionDAO.updateTask(task);
            String msg = String.format("Workflow %s is already completed as %s, task=%s, reason=%s",
                    workflowInstance.getWorkflowId(), workflowInstance.getStatus(), task.getTaskType(), workflowInstance.getReasonForIncompletion());
            logger.info(msg);
            Monitors.recordUpdateConflict(task.getTaskType(), workflowInstance.getWorkflowType(), workflowInstance.getStatus());
            return;
        }

        if (task.getStatus().isTerminal()) {
            // Task was already updated....
            queueDAO.remove(taskQueueName, result.getTaskId());
            logger.debug("Task: {} is in terminal state and is removed from the queue {} ", task, taskQueueName);
            String msg = String.format("Task is already completed as %s@%d, workflow status=%s, workflowId=%s, taskId=%s",
                    task.getStatus(), task.getEndTime(), workflowInstance.getStatus(), workflowInstance.getWorkflowId(), task.getTaskId());
            logger.info(msg);
            Monitors.recordUpdateConflict(task.getTaskType(), workflowInstance.getWorkflowType(), task.getStatus());
            return;
        }

        task.setStatus(Status.valueOf(result.getStatus().name()));
        task.setOutputData(result.getOutputData());
        task.setReasonForIncompletion(result.getReasonForIncompletion());
        task.setWorkerId(result.getWorkerId());
        task.setCallbackAfterSeconds(result.getCallbackAfterSeconds());

        if (task.getStatus().isTerminal()) {
            task.setEndTime(System.currentTimeMillis());
        }

        executionDAO.updateTask(task);

        //If the task has failed update the failed task reference name in the workflow.
        //This gives the ability to look at workflow and see what tasks have failed at a high level.
        if (Status.FAILED.equals(task.getStatus())) {
            workflowInstance.getFailedReferenceTaskNames().add(task.getReferenceTaskName());
            executionDAO.updateWorkflow(workflowInstance);
            logger.debug("Task: {} has a FAILED status and the Workflow has been updated with failed task reference", task);
        }

        result.getLogs().forEach(tl -> tl.setTaskId(task.getTaskId()));
        executionDAO.addTaskExecLog(result.getLogs());

        switch (task.getStatus()) {

            case COMPLETED:
                queueDAO.remove(taskQueueName, result.getTaskId());
                logger.debug("Task: {} removed from taskQueue: {} since the task status is {}", task, taskQueueName, task.getStatus().name());
                break;

            case CANCELED:
                queueDAO.remove(taskQueueName, result.getTaskId());
                logger.debug("Task: {} removed from taskQueue: {} since the task status is {}", task, taskQueueName, task.getStatus().name());
                break;
            case FAILED:
                queueDAO.remove(taskQueueName, result.getTaskId());
                logger.debug("Task: {} removed from taskQueue: {} since the task status is {}", task, taskQueueName, task.getStatus().name());
                break;
            case IN_PROGRESS:
                // put it back in queue based in callbackAfterSeconds
                long callBack = result.getCallbackAfterSeconds();
                queueDAO.remove(taskQueueName, task.getTaskId());
                logger.debug("Task: {} removed from taskQueue: {} since the task status is {}", task, taskQueueName, task.getStatus().name());
                queueDAO.push(taskQueueName, task.getTaskId(), callBack); // Milliseconds
                logger.debug("Task: {} pushed to taskQueue: {} since the task status is {} and callback: {}", task, taskQueueName, task.getStatus().name(), callBack);
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

    public List<Task> getTasks(String taskType, String startKey, int count) throws Exception {
        return executionDAO.getTasks(taskType, startKey, count);
    }

    public List<Workflow> getRunningWorkflows(String workflowName) throws Exception {
        return executionDAO.getPendingWorkflowsByType(workflowName);

    }

    public List<String> getWorkflows(String name, Integer version, Long startTime, Long endTime) {
        List<Workflow> allwf = executionDAO.getWorkflowsByType(name, startTime, endTime);
        return allwf.stream()
                .filter(wf -> wf.getVersion() == version)
                .map(wf -> wf.getWorkflowId())
                .collect(Collectors.toList());

    }

    public List<String> getRunningWorkflowIds(String workflowName) throws Exception {
        return executionDAO.getRunningWorkflowIds(workflowName);
    }

    /**
     *
     * @param workflowId ID of the workflow to evaluate the state for
     * @return true if the workflow has completed (success or failed), false otherwise.
     * @throws Exception If there was an error - caller should retry in this case.
     */
    public boolean decide(String workflowId) throws Exception {

        //If it is a new workflow the tasks will be still empty even though include tasks is true
        Workflow workflow = executionDAO.getWorkflow(workflowId, true);
        //QQ the definition can be null here
        WorkflowDef def = metadataDAO.get(workflow.getWorkflowType(), workflow.getVersion());

        try {
            DeciderOutcome outcome = deciderService.decide(workflow, def);
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
                    WorkflowSystemTask stt = WorkflowSystemTask.get(task.getTaskType());
                    if (!stt.isAsync() && stt.execute(workflow, task, this)) {
                        tasksToBeUpdated.add(task);
                        stateChanged = true;
                    }
                }
            }

            stateChanged = scheduleTask(workflow, tasksToBeScheduled) || stateChanged;

            if (!outcome.tasksToBeUpdated.isEmpty() || !outcome.tasksToBeScheduled.isEmpty()) {
                executionDAO.updateTasks(tasksToBeUpdated);
                executionDAO.updateWorkflow(workflow);
                queueDAO.push(deciderQueue, workflow.getWorkflowId(), config.getSweepFrequency());
            }

            if (stateChanged) {
                decide(workflowId);
            }

        } catch (TerminateWorkflowException tw) {
            logger.debug(tw.getMessage(), tw);
            terminate(def, workflow, tw);
            return true;
        }
        return false;
    }

    public void pauseWorkflow(String workflowId) throws Exception {
        WorkflowStatus status = WorkflowStatus.PAUSED;
        Workflow workflow = executionDAO.getWorkflow(workflowId, false);
        if (workflow.getStatus().isTerminal()) {
            throw new ApplicationException(Code.CONFLICT, "Workflow id " + workflowId + " has ended, status cannot be updated.");
        }
        if (workflow.getStatus().equals(status)) {
            return;        //Already paused!
        }
        workflow.setStatus(status);
        executionDAO.updateWorkflow(workflow);
    }

    public void resumeWorkflow(String workflowId) throws Exception {
        Workflow workflow = executionDAO.getWorkflow(workflowId, false);
        if (!workflow.getStatus().equals(WorkflowStatus.PAUSED)) {
            throw new IllegalStateException("The workflow " + workflowId + " is PAUSED so cannot resume");
        }
        workflow.setStatus(WorkflowStatus.RUNNING);
        executionDAO.updateWorkflow(workflow);
        decide(workflowId);
    }

    public void skipTaskFromWorkflow(String workflowId, String taskReferenceName, SkipTaskRequest skipTaskRequest) throws Exception {

        Workflow wf = executionDAO.getWorkflow(workflowId, true);

        // If the wf is not running then cannot skip any task
        if (!wf.getStatus().equals(WorkflowStatus.RUNNING)) {
            String errorMsg = String.format("The workflow %s is not running so the task referenced by %s cannot be skipped", workflowId, taskReferenceName);
            throw new IllegalStateException(errorMsg);
        }
        // Check if the reference name is as per the workflowdef
        WorkflowDef wfd = metadataDAO.get(wf.getWorkflowType(), wf.getVersion());
        WorkflowTask wft = wfd.getTaskByRefName(taskReferenceName);
        if (wft == null) {
            String errorMsg = String.format("The task referenced by %s does not exist in the WorkflowDefinition %s", taskReferenceName, wf.getWorkflowType());
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
        theTask.setStatus(Status.SKIPPED);
        theTask.setTaskType(wft.getName());
        theTask.setCorrelationId(wf.getCorrelationId());
        if (skipTaskRequest != null) {
            theTask.setInputData(skipTaskRequest.getTaskInput());
            theTask.setOutputData(skipTaskRequest.getTaskOutput());
        }
        executionDAO.createTasks(Arrays.asList(theTask));
        decide(workflowId);
    }

    public Workflow getWorkflow(String workflowId, boolean includeTasks) {
        return executionDAO.getWorkflow(workflowId, includeTasks);
    }


    public void addTaskToQueue(Task task) throws Exception {
        // put in queue
        String taskQueueName = QueueUtils.getQueueName(task);
        queueDAO.remove(taskQueueName, task.getTaskId()); //QQ why do we need to remove the existing task ??
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
            logger.info("Task: {} fetched from execution DAO for TaskId: {}", task, taskId);
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
                    task.setStatus(Status.CANCELED);
                }
                executionDAO.updateTask(task);
                queueDAO.remove(QueueUtils.getQueueName(task), task.getTaskId());
                return;
            }

            if (task.getStatus().equals(Status.SCHEDULED)) {

                if (executionDAO.exceedsInProgressLimit(task)) {
                    logger.warn("Rate limited for {}", task.getTaskDefName());
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
            logger.error(e.getMessage(), e);
        }
    }

    public void setTaskDomains(List<Task> tasks, Workflow wf) {
        Map<String, String> taskToDomain = wf.getTaskToDomain();
        if (taskToDomain != null) {
            // Check if all tasks have the same domain "*"
            String domainstr = taskToDomain.get("*");
            if (domainstr != null) {
                String[] domains = domainstr.split(",");
                tasks.forEach(task -> {
                    // Filter out SystemTask
                    if (!WorkflowTask.Type.isSystemTask(task.getTaskType())) {
                        // Check which domain worker is polling
                        // Set the task domain
                        task.setDomain(getActiveDomain(task.getTaskType(), domains));
                    }
                });

            } else {
                tasks.forEach(task -> {
                    if (!WorkflowTask.Type.isSystemTask(task.getTaskType())) {
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
                .map(PollData::getDomain) //QQ is the domain saved in the executionDAO same as the domain passed in ??
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
    boolean scheduleTask(Workflow workflow, List<Task> tasks) throws Exception {

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

        //Save the tasks in the DAO
        List<Task> created = executionDAO.createTasks(tasks);

        List<Task> createdSystemTasks = created.stream()
                .filter(isSystemTask)
                .collect(Collectors.toList());

        List<Task> toBeQueued = created.stream()
                .filter(isSystemTask.negate())
                .collect(Collectors.toList());

        boolean startedSystemTasks = false;

        //Traverse through all the system tasks, start the sync tasks, in case of async queue the tasks
        for (Task task : createdSystemTasks) {
            WorkflowSystemTask stt = WorkflowSystemTask.get(task.getTaskType());
            if (stt == null) {
                throw new RuntimeException("No system task found by name " + task.getTaskType());
            }
            task.setStartTime(System.currentTimeMillis());
            if (!stt.isAsync()) {
                stt.start(workflow, task, this);
                startedSystemTasks = true;
                executionDAO.updateTask(task);
            } else {
                toBeQueued.add(task);
            }
        }

        addTaskToQueue(toBeQueued);
        return startedSystemTasks;
    }

    private void addTaskToQueue(final List<Task> tasks) throws Exception {
        for (Task t : tasks) {
            addTaskToQueue(t);
        }
    }

    private void terminate(final WorkflowDef def, final Workflow workflow, TerminateWorkflowException tw) throws Exception {

        if (!workflow.getStatus().isTerminal()) {
            workflow.setStatus(tw.workflowStatus);
        }

        String failureWorkflow = def.getFailureWorkflow();
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
                            Map<String, Object> workflowInput, String correlationId) throws Exception {

        // Get the workflow
        Workflow workflow = executionDAO.getWorkflow(workflowId);

        // If the task Id is null it implies that the entire workflow has to be rerun
        if (taskId == null) {
            // remove all tasks
            workflow.getTasks().forEach(t -> executionDAO.removeTask(t.getTaskId()));
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

        // Now iterate thru the tasks and find the "specific" task
        Task theTask = null;
        for (Task t : workflow.getTasks()) {
            if (t.getTaskId().equals(taskId)) {
                theTask = t;
                break;
            } else {
                // If not found look into sub workflows
                if (t.getTaskType().equalsIgnoreCase("SUB_WORKFLOW")) {
                    String subWorkflowId = t.getInputData().get("subWorkflowId").toString();
                    if (rerunWF(subWorkflowId, taskId, taskInput, null, null)) {
                        theTask = t;
                        break;
                    }
                }
            }
        }


        if (theTask != null) {
            // Remove all later tasks from the "theTask"
            for (Task t : workflow.getTasks()) {
                if (t.getSeq() > theTask.getSeq()) {
                    executionDAO.removeTask(t.getTaskId());
                }
            }
            if (theTask.getTaskType().equalsIgnoreCase("SUB_WORKFLOW")) {
                // if task is sub workflow set task as IN_PROGRESS
                theTask.setStatus(Status.IN_PROGRESS);
                executionDAO.updateTask(theTask);
            } else {
                // Set the task to rerun
                theTask.setStatus(Status.SCHEDULED);
                if (taskInput != null) {
                    theTask.setInputData(taskInput);
                }
                theTask.setRetried(false);
                executionDAO.updateTask(theTask);
                addTaskToQueue(theTask);
            }
            // and workflow as RUNNING
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
