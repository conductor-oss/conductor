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
package com.netflix.conductor.core.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.metrics.Monitors;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that acts as a facade for accessing execution data from the {@link ExecutionDAO} and {@link IndexDAO} storage layers
 */
@Singleton
public class ExecutionDAOFacade {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionDAOFacade.class);

    private static final String ARCHIVED_FIELD = "archived";
    private static final String RAW_JSON_FIELD = "rawJSON";

    private final ExecutionDAO executionDAO;
    private final IndexDAO indexDAO;
    private final ObjectMapper objectMapper;
    private final Configuration config;

    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    @Inject
    public ExecutionDAOFacade(ExecutionDAO executionDAO, IndexDAO indexDAO, ObjectMapper objectMapper, Configuration config) {
        this.executionDAO = executionDAO;
        this.indexDAO = indexDAO;
        this.objectMapper = objectMapper;
        this.config = config;
        this.scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(4,
            (runnable, executor) -> {
            LOGGER.warn("Request {} to delay updating index dropped in executor {}", runnable, executor);
            Monitors.recordDiscardedIndexingCount("delayQueue");
        });
        this.scheduledThreadPoolExecutor.setRemoveOnCancelPolicy(true);
    }

    @PreDestroy
    public void shutdownExecutorService() {
        try {
            LOGGER.info("Gracefully shutdown executor service");
            scheduledThreadPoolExecutor.shutdown();
            if (scheduledThreadPoolExecutor.awaitTermination(config.getAsyncUpdateDelay(), TimeUnit.SECONDS)) {
                LOGGER.debug("tasks completed, shutting down");
            } else {
                LOGGER.warn("Forcing shutdown after waiting for {} seconds", config.getAsyncUpdateDelay());
                scheduledThreadPoolExecutor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            LOGGER.warn("Shutdown interrupted, invoking shutdownNow on scheduledThreadPoolExecutor for delay queue");
            scheduledThreadPoolExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Fetches the {@link Workflow} object from the data store given the id.
     * Attempts to fetch from {@link ExecutionDAO} first,
     * if not found, attempts to fetch from {@link IndexDAO}.
     *
     * @param workflowId   the id of the workflow to be fetched
     * @param includeTasks if true, fetches the {@link Task} data in the workflow.
     * @return the {@link Workflow} object
     * @throws ApplicationException if
     *                              <ul>
     *                              <li>no such {@link Workflow} is found</li>
     *                              <li>parsing the {@link Workflow} object fails</li>
     *                              </ul>
     */
    public Workflow getWorkflowById(String workflowId, boolean includeTasks) {
        Workflow workflow = executionDAO.getWorkflow(workflowId, includeTasks);
        if (workflow == null) {
            LOGGER.debug("Workflow {} not found in executionDAO, checking indexDAO", workflowId);
            String json = indexDAO.get(workflowId, RAW_JSON_FIELD);
            if (json == null) {
                String errorMsg = String.format("No such workflow found by id: %s", workflowId);
                LOGGER.error(errorMsg);
                throw new ApplicationException(ApplicationException.Code.NOT_FOUND, errorMsg);
            }

            try {
                workflow = objectMapper.readValue(json, Workflow.class);
                if (!includeTasks) {
                    workflow.getTasks().clear();
                }
            } catch (IOException e) {
                String errorMsg = String.format("Error reading workflow: %s", workflowId);
                LOGGER.error(errorMsg);
                throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
            }
        }
        return workflow;
    }

    /**
     * Retrieve all workflow executions with the given correlationId
     * Uses the {@link IndexDAO} to search across workflows if the {@link ExecutionDAO} cannot perform searches across workflows.
     *
     * @param correlationId the correlation id to be queried
     * @param includeTasks  if true, fetches the {@link Task} data within the workflows
     * @return the list of {@link Workflow} executions matching the correlationId
     */
    public List<Workflow> getWorkflowsByCorrelationId(String correlationId, boolean includeTasks) {
        if (!executionDAO.canSearchAcrossWorkflows()) {
            SearchResult<String> result = indexDAO.searchWorkflows("correlationId='" + correlationId + "'", "*", 0, 1000, null);
            return result.getResults().stream()
                .parallel()
                .map(workflowId -> {
                    try {
                        return getWorkflowById(workflowId, includeTasks);
                    } catch (ApplicationException e) {
                        // This might happen when the workflow archival failed and the workflow was removed from primary datastore
                        LOGGER.error("Error getting the workflow: {}  for correlationId: {} from datastore/index", workflowId, correlationId, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }
        return executionDAO.getWorkflowsByCorrelationId(correlationId, includeTasks);
    }

    public List<Workflow> getWorkflowsByName(String workflowName, Long startTime, Long endTime) {
        return executionDAO.getWorkflowsByType(workflowName, startTime, endTime);
    }

    public List<Workflow> getPendingWorkflowsByName(String workflowName, int version) {
        return executionDAO.getPendingWorkflowsByType(workflowName, version);
    }

    public List<String> getRunningWorkflowIds(String workflowName, int version) {
        return executionDAO.getRunningWorkflowIds(workflowName, version);
    }

    public long getPendingWorkflowCount(String workflowName) {
        return executionDAO.getPendingWorkflowCount(workflowName);
    }

    /**
     * Creates a new workflow in the data store
     *
     * @param workflow the workflow to be created
     * @return the id of the created workflow
     */
    public String createWorkflow(Workflow workflow) {
        workflow.setCreateTime(System.currentTimeMillis());
        executionDAO.createWorkflow(workflow);
        if (config.enableAsyncIndexing()) {
            indexDAO.asyncIndexWorkflow(workflow);
        } else {
            indexDAO.indexWorkflow(workflow);
        }
        return workflow.getWorkflowId();
    }

    /**
     * Updates the given workflow in the data store
     *
     * @param workflow the workflow tp be updated
     * @return the id of the updated workflow
     */
    public String updateWorkflow(Workflow workflow) {
        workflow.setUpdateTime(System.currentTimeMillis());
        if (workflow.getStatus().isTerminal()) {
            workflow.setEndTime(System.currentTimeMillis());
        }
        executionDAO.updateWorkflow(workflow);
        if (workflow.getStatus().isTerminal()) {
            if (config.enableAsyncIndexing()) {
                if (workflow.getEndTime() - workflow.getStartTime() < config.getAsyncUpdateShortRunningWorkflowDuration() * 1000) {
                    final String workflowId = workflow.getWorkflowId();
                    DelayWorkflowUpdate delayWorkflowUpdate = new DelayWorkflowUpdate(workflowId);
                    LOGGER.debug("Delayed updating workflow: {} in the index by {} seconds", workflowId, config.getAsyncUpdateDelay());
                    scheduledThreadPoolExecutor.schedule(delayWorkflowUpdate, config.getAsyncUpdateDelay(), TimeUnit.SECONDS);
                    Monitors.recordWorkerQueueSize("delayQueue", scheduledThreadPoolExecutor.getQueue().size());
                } else {
                    indexDAO.asyncIndexWorkflow(workflow);
                }
                workflow.getTasks().forEach(indexDAO::asyncIndexTask);
            } else {
                indexDAO.indexWorkflow(workflow);
                workflow.getTasks().forEach(indexDAO::indexTask);
            }
        }
        return workflow.getWorkflowId();
    }

    public void removeFromPendingWorkflow(String workflowType, String workflowId) {
        executionDAO.removeFromPendingWorkflow(workflowType, workflowId);
    }

    /**
     * Removes the workflow from the data store.
     *
     * @param workflowId      the id of the workflow to be removed
     * @param archiveWorkflow if true, the workflow will be archived in the {@link IndexDAO} after removal from  {@link ExecutionDAO}
     */
    public void removeWorkflow(String workflowId, boolean archiveWorkflow) {
        try {
            Workflow workflow = getWorkflowById(workflowId, true);

            if (archiveWorkflow) {
                if (workflow.getStatus().isTerminal()) {
                    // Only allow archival if workflow is in terminal state
                    // DO NOT archive async, since if archival errors out, workflow data will be lost
                    indexDAO.updateWorkflow(workflowId,
                        new String[]{RAW_JSON_FIELD, ARCHIVED_FIELD},
                        new Object[]{objectMapper.writeValueAsString(workflow), true});
                } else {
                    throw new ApplicationException(Code.INVALID_INPUT, String.format("Cannot archive workflow: %s with status: %s", workflowId, workflow.getStatus()));
                }
            } else {
                // Not archiving, also remove workflow from index
                indexDAO.asyncRemoveWorkflow(workflowId);
            }

            // remove workflow from DAO
            try {
                executionDAO.removeWorkflow(workflowId);
            } catch (Exception ex) {
                Monitors.recordDaoError("executionDao", "removeWorkflow");
                throw ex;
            }
        } catch (ApplicationException ae) {
            throw ae;
        } catch (Exception e) {
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, "Error removing workflow: " + workflowId, e);
        }
    }

    public List<Task> createTasks(List<Task> tasks) {
        return executionDAO.createTasks(tasks);
    }

    public List<Task> getTasksForWorkflow(String workflowId) {
        return executionDAO.getTasksForWorkflow(workflowId);
    }

    public Task getTaskById(String taskId) {
        return executionDAO.getTask(taskId);
    }

    public List<Task> getTasksByName(String taskName, String startKey, int count) {
        return executionDAO.getTasks(taskName, startKey, count);
    }

    public List<Task> getPendingTasksForTaskType(String taskType) {
        return executionDAO.getPendingTasksForTaskType(taskType);
    }

    public long getInProgressTaskCount(String taskDefName) {
        return executionDAO.getInProgressTaskCount(taskDefName);
    }

    /**
     * Sets the update time for the task.
     * Sets the end time for the task (if task is in terminal state and end time is not set).
     * Updates the task in the {@link ExecutionDAO} first, then stores it in the {@link IndexDAO}.
     *
     * @param task the task to be updated in the data store
     * @throws ApplicationException if the dao operations fail
     */
    public void updateTask(Task task) {
        try {
            if (task.getStatus() != null) {
                if (!task.getStatus().isTerminal() || (task.getStatus().isTerminal() && task.getUpdateTime() == 0)) {
                    task.setUpdateTime(System.currentTimeMillis());
                }
                if (task.getStatus().isTerminal() && task.getEndTime() == 0) {
                    task.setEndTime(System.currentTimeMillis());
                }
            }
            executionDAO.updateTask(task);
        } catch (Exception e) {
            String errorMsg = String.format("Error updating task: %s in workflow: %s", task.getTaskId(), task.getWorkflowInstanceId());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    }

    public void updateTasks(List<Task> tasks) {
        tasks.forEach(this::updateTask);
    }

    public void removeTask(String taskId) {
        executionDAO.removeTask(taskId);
    }

    public List<PollData> getTaskPollData(String taskName) {
        return executionDAO.getPollData(taskName);
    }

    public PollData getTaskPollDataByDomain(String taskName, String domain) {
        return executionDAO.getPollData(taskName, domain);
    }

    public void updateTaskLastPoll(String taskName, String domain, String workerId) {
        executionDAO.updateLastPoll(taskName, domain, workerId);
    }

    /**
     * Save the {@link EventExecution} to the data store
     * Saves to {@link ExecutionDAO} first, if this succeeds then saves to the {@link IndexDAO}.
     *
     * @param eventExecution the {@link EventExecution} to be saved
     * @return true if save succeeds, false otherwise.
     */
    public boolean addEventExecution(EventExecution eventExecution) {
        boolean added = executionDAO.addEventExecution(eventExecution);
        if (added) {
            if (config.enableAsyncIndexing()) {
                indexDAO.asyncAddEventExecution(eventExecution);
            } else {
                indexDAO.addEventExecution(eventExecution);
            }
        }
        return added;
    }

    public void updateEventExecution(EventExecution eventExecution) {
        executionDAO.updateEventExecution(eventExecution);
        if (config.enableAsyncIndexing()) {
            indexDAO.asyncAddEventExecution(eventExecution);
        } else {
            indexDAO.addEventExecution(eventExecution);
        }
    }

    public void removeEventExecution(EventExecution eventExecution) {
        executionDAO.removeEventExecution(eventExecution);
    }

    public boolean exceedsInProgressLimit(Task task) {
        return executionDAO.exceedsInProgressLimit(task);
    }

    public boolean exceedsRateLimitPerFrequency(Task task) {
        return executionDAO.exceedsRateLimitPerFrequency(task);
    }

    public void addTaskExecLog(List<TaskExecLog> logs) {
        if (config.enableAsyncIndexing()) {
            indexDAO.asyncAddTaskExecutionLogs(logs);
        } else {
            indexDAO.addTaskExecutionLogs(logs);
        }
    }

    public void addMessage(String queue, Message message) {
        indexDAO.addMessage(queue, message);
    }

    public SearchResult<String> searchWorkflows(String query, String freeText, int start, int count, List<String> sort) {
        return indexDAO.searchWorkflows(query, freeText, start, count, sort);
    }

    public SearchResult<String> searchTasks(String query, String freeText, int start, int count, List<String> sort) {
        return indexDAO.searchTasks(query, freeText, start, count, sort);
    }

    public List<TaskExecLog> getTaskExecutionLogs(String taskId) {
        return indexDAO.getTaskExecutionLogs(taskId);
    }

    class DelayWorkflowUpdate implements Runnable {
        private String workflowId;

        DelayWorkflowUpdate(String workflowId) {
            this.workflowId = workflowId;
        }

        @Override
        public void run() {
            try {
                Workflow workflow = executionDAO.getWorkflow(workflowId, false);
                indexDAO.asyncIndexWorkflow(workflow);
            } catch (Exception e) {
                LOGGER.error("Unable to update workflow: {}", workflowId, e);
            }
        }
    }
}
