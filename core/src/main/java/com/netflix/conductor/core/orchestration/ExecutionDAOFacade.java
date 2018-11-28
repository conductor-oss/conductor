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
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.metrics.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

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

    @Inject
    public ExecutionDAOFacade(ExecutionDAO executionDAO, IndexDAO indexDAO, ObjectMapper objectMapper) {
        this.executionDAO = executionDAO;
        this.indexDAO = indexDAO;
        this.objectMapper = objectMapper;
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
            List<Workflow> workflows = new LinkedList<>();
            SearchResult<String> result = indexDAO.searchWorkflows("correlationId='" + correlationId + "'", "*", 0, 10000, null);
            result.getResults().forEach(workflowId -> {
                try {
                    Workflow workflow = getWorkflowById(workflowId, includeTasks);
                    workflows.add(workflow);
                } catch (ApplicationException e) {
                    //This might happen when the workflow archival failed and the workflow was removed from dynomite
                    LOGGER.error("Error getting the workflowId: {}  for correlationId: {} from Dynomite/Archival", workflowId, correlationId, e);
                }
            });
            return workflows;
        }
        return executionDAO.getWorkflowsByCorrelationId(correlationId, includeTasks);
    }

    public List<Workflow> getWorkflowsByName(String workflowName, Long startTime, Long endTime) {
        return executionDAO.getWorkflowsByType(workflowName, startTime, endTime);
    }

    public List<Workflow> getPendingWorkflowsByName(String workflowName) {
        return executionDAO.getPendingWorkflowsByType(workflowName);
    }

    public List<String> getRunningWorkflowIdsByName(String workflowName) {
        return executionDAO.getRunningWorkflowIds(workflowName);
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
        executionDAO.createWorkflow(workflow);
        indexDAO.indexWorkflow(workflow);
        return workflow.getWorkflowId();
    }

    /**
     * Updates the given workflow in the data store
     *
     * @param workflow the workflow tp be updated
     * @return the id of the updated workflow
     */
    public String updateWorkflow(Workflow workflow) {
        executionDAO.updateWorkflow(workflow);
        indexDAO.indexWorkflow(workflow);
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

            // remove workflow from ES
            if (archiveWorkflow) {
                //Add to elasticsearch
                indexDAO.updateWorkflow(workflowId,
                        new String[]{RAW_JSON_FIELD, ARCHIVED_FIELD},
                        new Object[]{objectMapper.writeValueAsString(workflow), true});
            } else {
                // Not archiving, also remove workflowId from index
                indexDAO.removeWorkflow(workflowId);
            }

            // remove workflow from DAO
            try {
                executionDAO.removeWorkflow(workflowId);
            } catch (Exception ex) {
                Monitors.recordDaoError("executionDao", "removeWorkflow");
                throw ex;
            }

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
            executionDAO.updateTask(task);
            indexDAO.indexTask(task);
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
            indexDAO.addEventExecution(eventExecution);
        }
        return added;
    }

    public void updateEventExecution(EventExecution eventExecution) {
        executionDAO.updateEventExecution(eventExecution);
        indexDAO.addEventExecution(eventExecution);
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
        indexDAO.addTaskExecutionLogs(logs);
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
}
