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

import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Service to orchestrate the data access between the storage layers
 */
@Singleton
public class DataAccessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataAccessor.class);

    private final ExecutionDAO executionDAO;
    private final IndexDAO indexDAO;

    @Inject
    public DataAccessor(ExecutionDAO executionDAO, IndexDAO indexDAO) {
        this.executionDAO = executionDAO;
        this.indexDAO = indexDAO;
    }

    public Workflow getWorkflowById(String workflowId, boolean includeTasks) {
        return executionDAO.getWorkflow(workflowId, includeTasks);
    }

    public List<Workflow> getWorkflowsByCorrelationId(String correlationId, boolean includeTasks) {
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

    public String createWorkflow(Workflow workflow) {
        return executionDAO.createWorkflow(workflow);
    }

    public String updateWorkflow(Workflow workflow) {
        return executionDAO.updateWorkflow(workflow);
    }

    public void removeFromPendingWorkflow(String workflowType, String workflowId) {
        executionDAO.removeFromPendingWorkflow(workflowType, workflowId);
    }

    public void removeWorkflow(String workflowId, boolean archiveWorkflow) {
        executionDAO.removeWorkflow(workflowId, archiveWorkflow);
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

    public void updateTask(Task task) {
        executionDAO.updateTask(task);
    }

    public void updateTasks(List<Task> tasks) {
        executionDAO.updateTasks(tasks);
    }

    public void removeTask(String taskId) {
        executionDAO.removeTask(taskId);
    }

    public void addTaskExecLog(List<TaskExecLog> logs) {
        executionDAO.addTaskExecLog(logs);
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

    public boolean addEventExecution(EventExecution eventExecution) {
        return executionDAO.addEventExecution(eventExecution);
    }

    public void updateEventExecution(EventExecution eventExecution) {
        executionDAO.updateEventExecution(eventExecution);
    }

    public void removeEventExecution(EventExecution eventExecution) {
        executionDAO.removeEventExecution(eventExecution);
    }

    public void addMessage(String queue, Message message) {
        executionDAO.addMessage(queue, message);
    }

    public boolean exceedsInProgressLimit(Task task) {
        return executionDAO.exceedsInProgressLimit(task);
    }

    public boolean exceedsRateLimitPerFrequency(Task task) {
        return executionDAO.exceedsRateLimitPerFrequency(task);
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
