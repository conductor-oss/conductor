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
package com.netflix.conductor.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.common.utils.ExternalPayloadStorage.Operation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage.PayloadType;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.dal.ModelMapper;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;

@Trace
@Service
public class ExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionService.class);

    private final WorkflowExecutor workflowExecutor;
    private final ModelMapper modelMapper;
    private final ExecutionDAOFacade executionDAOFacade;
    private final QueueDAO queueDAO;
    private final ExternalPayloadStorage externalPayloadStorage;
    private final SystemTaskRegistry systemTaskRegistry;

    private final long queueTaskMessagePostponeSecs;

    private static final int MAX_POLL_TIMEOUT_MS = 5000;
    private static final int POLL_COUNT_ONE = 1;
    private static final int POLLING_TIMEOUT_IN_MS = 100;

    public ExecutionService(
            WorkflowExecutor workflowExecutor,
            ModelMapper modelMapper,
            ExecutionDAOFacade executionDAOFacade,
            QueueDAO queueDAO,
            ConductorProperties properties,
            ExternalPayloadStorage externalPayloadStorage,
            SystemTaskRegistry systemTaskRegistry) {
        this.workflowExecutor = workflowExecutor;
        this.modelMapper = modelMapper;
        this.executionDAOFacade = executionDAOFacade;
        this.queueDAO = queueDAO;
        this.externalPayloadStorage = externalPayloadStorage;

        this.queueTaskMessagePostponeSecs =
                properties.getTaskExecutionPostponeDuration().getSeconds();
        this.systemTaskRegistry = systemTaskRegistry;
    }

    public Task poll(String taskType, String workerId) {
        return poll(taskType, workerId, null);
    }

    public Task poll(String taskType, String workerId, String domain) {

        List<Task> tasks = poll(taskType, workerId, domain, 1, 100);
        if (tasks.isEmpty()) {
            return null;
        }
        return tasks.get(0);
    }

    public List<Task> poll(String taskType, String workerId, int count, int timeoutInMilliSecond) {
        return poll(taskType, workerId, null, count, timeoutInMilliSecond);
    }

    public List<Task> poll(
            String taskType, String workerId, String domain, int count, int timeoutInMilliSecond) {
        if (timeoutInMilliSecond > MAX_POLL_TIMEOUT_MS) {
            throw new ApplicationException(
                    ApplicationException.Code.INVALID_INPUT,
                    "Long Poll Timeout value cannot be more than 5 seconds");
        }
        String queueName = QueueUtils.getQueueName(taskType, domain, null, null);

        List<String> taskIds = new LinkedList<>();
        List<Task> tasks = new LinkedList<>();
        try {
            taskIds = queueDAO.pop(queueName, count, timeoutInMilliSecond);
        } catch (Exception e) {
            LOGGER.error(
                    "Error polling for task: {} from worker: {} in domain: {}, count: {}",
                    taskType,
                    workerId,
                    domain,
                    count,
                    e);
            Monitors.error(this.getClass().getCanonicalName(), "taskPoll");
            Monitors.recordTaskPollError(taskType, domain, e.getClass().getSimpleName());
        }

        for (String taskId : taskIds) {
            try {
                TaskModel task = executionDAOFacade.getTaskModel(taskId);
                if (task == null || task.getStatus().isTerminal()) {
                    // Remove taskId(s) without a valid Task/terminal state task from the queue
                    queueDAO.remove(queueName, taskId);
                    LOGGER.debug("Removed task: {} from the queue: {}", taskId, queueName);
                    continue;
                }

                if (executionDAOFacade.exceedsInProgressLimit(task)) {
                    // Postpone this message, so that it would be available for poll again.
                    queueDAO.postpone(
                            queueName,
                            taskId,
                            task.getWorkflowPriority(),
                            queueTaskMessagePostponeSecs);
                    LOGGER.debug(
                            "Postponed task: {} in queue: {} by {} seconds",
                            taskId,
                            queueName,
                            queueTaskMessagePostponeSecs);
                    continue;
                }
                TaskDef taskDef =
                        task.getTaskDefinition().isPresent()
                                ? task.getTaskDefinition().get()
                                : null;
                if (task.getRateLimitPerFrequency() > 0
                        && executionDAOFacade.exceedsRateLimitPerFrequency(task, taskDef)) {
                    // Postpone this message, so that it would be available for poll again.
                    queueDAO.postpone(
                            queueName,
                            taskId,
                            task.getWorkflowPriority(),
                            queueTaskMessagePostponeSecs);
                    LOGGER.debug(
                            "RateLimit Execution limited for {}:{}, limit:{}",
                            taskId,
                            task.getTaskDefName(),
                            task.getRateLimitPerFrequency());
                    continue;
                }

                task.setStatus(TaskModel.Status.IN_PROGRESS);
                if (task.getStartTime() == 0) {
                    task.setStartTime(System.currentTimeMillis());
                    Monitors.recordQueueWaitTime(task.getTaskDefName(), task.getQueueWaitTime());
                }
                task.setCallbackAfterSeconds(
                        0); // reset callbackAfterSeconds when giving the task to the worker
                task.setWorkerId(workerId);
                task.incrementPollCount();
                executionDAOFacade.updateTask(task);
                tasks.add(modelMapper.getTask(task));
            } catch (Exception e) {
                // db operation failed for dequeued message, re-enqueue with a delay
                LOGGER.warn(
                        "DB operation failed for task: {}, postponing task in queue", taskId, e);
                Monitors.recordTaskPollError(taskType, domain, e.getClass().getSimpleName());
                queueDAO.postpone(queueName, taskId, 0, queueTaskMessagePostponeSecs);
            }
        }
        executionDAOFacade.updateTaskLastPoll(taskType, domain, workerId);
        Monitors.recordTaskPoll(queueName);
        return tasks;
    }

    public Task getLastPollTask(String taskType, String workerId, String domain) {
        List<Task> tasks = poll(taskType, workerId, domain, POLL_COUNT_ONE, POLLING_TIMEOUT_IN_MS);
        if (tasks.isEmpty()) {
            LOGGER.debug(
                    "No Task available for the poll: /tasks/poll/{}?{}&{}",
                    taskType,
                    workerId,
                    domain);
            return null;
        }
        Task task = tasks.get(0);
        ackTaskReceived(task);
        LOGGER.debug(
                "The Task {} being returned for /tasks/poll/{}?{}&{}",
                task,
                taskType,
                workerId,
                domain);
        return task;
    }

    public List<PollData> getPollData(String taskType) {
        return executionDAOFacade.getTaskPollData(taskType);
    }

    public List<PollData> getAllPollData() {
        try {
            return executionDAOFacade.getAllPollData();
        } catch (UnsupportedOperationException uoe) {
            List<PollData> allPollData = new ArrayList<>();
            Map<String, Long> queueSizes = queueDAO.queuesDetail();
            queueSizes
                    .keySet()
                    .forEach(
                            queueName -> {
                                try {
                                    if (!queueName.contains(QueueUtils.DOMAIN_SEPARATOR)) {
                                        allPollData.addAll(
                                                getPollData(
                                                        QueueUtils.getQueueNameWithoutDomain(
                                                                queueName)));
                                    }
                                } catch (Exception e) {
                                    LOGGER.error("Unable to fetch all poll data!", e);
                                }
                            });
            return allPollData;
        }
    }

    public void terminateWorkflow(String workflowId, String reason) {
        workflowExecutor.terminateWorkflow(workflowId, reason);
    }

    public void updateTask(TaskResult taskResult) {
        workflowExecutor.updateTask(taskResult);
    }

    public List<Task> getTasks(String taskType, String startKey, int count) {
        return executionDAOFacade.getTasksByName(taskType, startKey, count);
    }

    public Task getTask(String taskId) {
        return executionDAOFacade.getTask(taskId);
    }

    public Task getPendingTaskForWorkflow(String taskReferenceName, String workflowId) {
        return executionDAOFacade.getTasksForWorkflow(workflowId).stream()
                .filter(task -> !task.getStatus().isTerminal())
                .filter(task -> task.getReferenceTaskName().equals(taskReferenceName))
                .findFirst() // There can only be one task by a given reference name running at a
                // time.
                .orElse(null);
    }

    /**
     * This method removes the task from the un-acked Queue
     *
     * @param taskId: the taskId that needs to be updated and removed from the unacked queue
     * @return True in case of successful removal of the taskId from the un-acked queue
     */
    public boolean ackTaskReceived(String taskId) {
        return Optional.ofNullable(getTask(taskId)).map(this::ackTaskReceived).orElse(false);
    }

    public boolean ackTaskReceived(Task task) {
        return queueDAO.ack(QueueUtils.getQueueName(task), task.getTaskId());
    }

    public Map<String, Integer> getTaskQueueSizes(List<String> taskDefNames) {
        Map<String, Integer> sizes = new HashMap<>();
        for (String taskDefName : taskDefNames) {
            sizes.put(taskDefName, queueDAO.getSize(taskDefName));
        }
        return sizes;
    }

    public void removeTaskFromQueue(String taskId) {
        Task task = getTask(taskId);
        if (task == null) {
            throw new ApplicationException(
                    ApplicationException.Code.NOT_FOUND,
                    String.format("No such task found by taskId: %s", taskId));
        }
        queueDAO.remove(QueueUtils.getQueueName(task), taskId);
    }

    public int requeuePendingTasks(String taskType) {

        int count = 0;
        List<Task> tasks = getPendingTasksForTaskType(taskType);

        for (Task pending : tasks) {

            if (systemTaskRegistry.isSystemTask(pending.getTaskType())) {
                continue;
            }
            if (pending.getStatus().isTerminal()) {
                continue;
            }

            LOGGER.debug(
                    "Requeuing Task: {} of taskType: {} in Workflow: {}",
                    pending.getTaskId(),
                    pending.getTaskType(),
                    pending.getWorkflowInstanceId());
            boolean pushed = requeue(pending);
            if (pushed) {
                count++;
            }
        }
        return count;
    }

    private boolean requeue(Task pending) {
        long callback = pending.getCallbackAfterSeconds();
        if (callback < 0) {
            callback = 0;
        }
        queueDAO.remove(QueueUtils.getQueueName(pending), pending.getTaskId());
        long now = System.currentTimeMillis();
        callback = callback - ((now - pending.getUpdateTime()) / 1000);
        if (callback < 0) {
            callback = 0;
        }
        return queueDAO.pushIfNotExists(
                QueueUtils.getQueueName(pending),
                pending.getTaskId(),
                pending.getWorkflowPriority(),
                callback);
    }

    public List<Workflow> getWorkflowInstances(
            String workflowName,
            String correlationId,
            boolean includeClosed,
            boolean includeTasks) {

        List<Workflow> workflows =
                executionDAOFacade.getWorkflowsByCorrelationId(workflowName, correlationId, false);
        return workflows.stream()
                .parallel()
                .filter(
                        workflow -> {
                            if (includeClosed
                                    || workflow.getStatus()
                                            .equals(Workflow.WorkflowStatus.RUNNING)) {
                                // including tasks for subset of workflows to increase performance
                                if (includeTasks) {
                                    List<Task> tasks =
                                            executionDAOFacade.getTasksForWorkflow(
                                                    workflow.getWorkflowId());
                                    tasks.sort(Comparator.comparingInt(Task::getSeq));
                                    workflow.setTasks(tasks);
                                }
                                return true;
                            } else {
                                return false;
                            }
                        })
                .collect(Collectors.toList());
    }

    public Workflow getExecutionStatus(String workflowId, boolean includeTasks) {
        return executionDAOFacade.getWorkflow(workflowId, includeTasks);
    }

    public List<String> getRunningWorkflows(String workflowName, int version) {
        return executionDAOFacade.getRunningWorkflowIds(workflowName, version);
    }

    public void removeWorkflow(String workflowId, boolean archiveWorkflow) {
        executionDAOFacade.removeWorkflow(workflowId, archiveWorkflow);
    }

    public SearchResult<WorkflowSummary> search(
            String query, String freeText, int start, int size, List<String> sortOptions) {

        SearchResult<String> result =
                executionDAOFacade.searchWorkflows(query, freeText, start, size, sortOptions);
        List<WorkflowSummary> workflows =
                result.getResults().stream()
                        .parallel()
                        .map(
                                workflowId -> {
                                    try {
                                        return new WorkflowSummary(
                                                executionDAOFacade.getWorkflow(workflowId, false));
                                    } catch (Exception e) {
                                        LOGGER.error(
                                                "Error fetching workflow by id: {}", workflowId, e);
                                        return null;
                                    }
                                })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
        int missing = result.getResults().size() - workflows.size();
        long totalHits = result.getTotalHits() - missing;
        return new SearchResult<>(totalHits, workflows);
    }

    public SearchResult<Workflow> searchV2(
            String query, String freeText, int start, int size, List<String> sortOptions) {

        SearchResult<String> result =
                executionDAOFacade.searchWorkflows(query, freeText, start, size, sortOptions);
        List<Workflow> workflows =
                result.getResults().stream()
                        .parallel()
                        .map(
                                workflowId -> {
                                    try {
                                        return executionDAOFacade.getWorkflow(workflowId, false);
                                    } catch (Exception e) {
                                        LOGGER.error(
                                                "Error fetching workflow by id: {}", workflowId, e);
                                        return null;
                                    }
                                })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
        int missing = result.getResults().size() - workflows.size();
        long totalHits = result.getTotalHits() - missing;
        return new SearchResult<>(totalHits, workflows);
    }

    public SearchResult<WorkflowSummary> searchWorkflowByTasks(
            String query, String freeText, int start, int size, List<String> sortOptions) {
        SearchResult<TaskSummary> taskSummarySearchResult =
                searchTasks(query, freeText, start, size, sortOptions);
        List<WorkflowSummary> workflowSummaries =
                taskSummarySearchResult.getResults().stream()
                        .parallel()
                        .map(
                                taskSummary -> {
                                    try {
                                        String workflowId = taskSummary.getWorkflowId();
                                        return new WorkflowSummary(
                                                executionDAOFacade.getWorkflow(workflowId, false));
                                    } catch (Exception e) {
                                        LOGGER.error(
                                                "Error fetching workflow by id: {}",
                                                taskSummary.getWorkflowId(),
                                                e);
                                        return null;
                                    }
                                })
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
        int missing = taskSummarySearchResult.getResults().size() - workflowSummaries.size();
        long totalHits = taskSummarySearchResult.getTotalHits() - missing;
        return new SearchResult<>(totalHits, workflowSummaries);
    }

    public SearchResult<Workflow> searchWorkflowByTasksV2(
            String query, String freeText, int start, int size, List<String> sortOptions) {
        SearchResult<TaskSummary> taskSummarySearchResult =
                searchTasks(query, freeText, start, size, sortOptions);
        List<Workflow> workflows =
                taskSummarySearchResult.getResults().stream()
                        .parallel()
                        .map(
                                taskSummary -> {
                                    try {
                                        String workflowId = taskSummary.getWorkflowId();
                                        return executionDAOFacade.getWorkflow(workflowId, false);
                                    } catch (Exception e) {
                                        LOGGER.error(
                                                "Error fetching workflow by id: {}",
                                                taskSummary.getWorkflowId(),
                                                e);
                                        return null;
                                    }
                                })
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
        int missing = taskSummarySearchResult.getResults().size() - workflows.size();
        long totalHits = taskSummarySearchResult.getTotalHits() - missing;
        return new SearchResult<>(totalHits, workflows);
    }

    public SearchResult<TaskSummary> searchTasks(
            String query, String freeText, int start, int size, List<String> sortOptions) {

        SearchResult<String> result =
                executionDAOFacade.searchTasks(query, freeText, start, size, sortOptions);
        List<TaskSummary> workflows =
                result.getResults().stream()
                        .parallel()
                        .map(
                                task -> {
                                    try {
                                        return new TaskSummary(executionDAOFacade.getTask(task));
                                    } catch (Exception e) {
                                        LOGGER.error("Error fetching task by id: {}", task, e);
                                        return null;
                                    }
                                })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
        int missing = result.getResults().size() - workflows.size();
        long totalHits = result.getTotalHits() - missing;
        return new SearchResult<>(totalHits, workflows);
    }

    public SearchResult<TaskSummary> getSearchTasks(
            String query,
            String freeText,
            int start,
            /*@Max(value = MAX_SEARCH_SIZE, message = "Cannot return more than {value} workflows." +
            " Please use pagination.")*/ int size,
            String sortString) {
        return searchTasks(query, freeText, start, size, Utils.convertStringToList(sortString));
    }

    public SearchResult<Task> getSearchTasksV2(
            String query, String freeText, int start, int size, String sortString) {
        SearchResult<String> result =
                executionDAOFacade.searchTasks(
                        query, freeText, start, size, Utils.convertStringToList(sortString));
        List<Task> tasks =
                result.getResults().stream()
                        .parallel()
                        .map(
                                task -> {
                                    try {
                                        return executionDAOFacade.getTask(task);
                                    } catch (Exception e) {
                                        LOGGER.error("Error fetching task by id: {}", task, e);
                                        return null;
                                    }
                                })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
        int missing = result.getResults().size() - tasks.size();
        long totalHits = result.getTotalHits() - missing;
        return new SearchResult<>(totalHits, tasks);
    }

    public List<Task> getPendingTasksForTaskType(String taskType) {
        return executionDAOFacade.getPendingTasksForTaskType(taskType);
    }

    public boolean addEventExecution(EventExecution eventExecution) {
        return executionDAOFacade.addEventExecution(eventExecution);
    }

    public void removeEventExecution(EventExecution eventExecution) {
        executionDAOFacade.removeEventExecution(eventExecution);
    }

    public void updateEventExecution(EventExecution eventExecution) {
        executionDAOFacade.updateEventExecution(eventExecution);
    }

    /**
     * @param queue Name of the registered queueDAO
     * @param msg Message
     */
    public void addMessage(String queue, Message msg) {
        executionDAOFacade.addMessage(queue, msg);
    }

    /**
     * Adds task logs
     *
     * @param taskId Id of the task
     * @param log logs
     */
    public void log(String taskId, String log) {
        TaskExecLog executionLog = new TaskExecLog();
        executionLog.setTaskId(taskId);
        executionLog.setLog(log);
        executionLog.setCreatedTime(System.currentTimeMillis());
        executionDAOFacade.addTaskExecLog(Collections.singletonList(executionLog));
    }

    /**
     * @param taskId Id of the task for which to retrieve logs
     * @return Execution Logs (logged by the worker)
     */
    public List<TaskExecLog> getTaskLogs(String taskId) {
        return executionDAOFacade.getTaskExecutionLogs(taskId);
    }

    /**
     * Get external uri for the payload
     *
     * @param operation the type of {@link Operation} to be performed
     * @param payloadType the {@link PayloadType} at the external uri
     * @param path the path for which the external storage location is to be populated
     * @return the external uri at which the payload is stored/to be stored
     */
    public ExternalStorageLocation getExternalStorageLocation(
            Operation operation, PayloadType payloadType, String path) {
        return externalPayloadStorage.getLocation(operation, payloadType, path);
    }
}
