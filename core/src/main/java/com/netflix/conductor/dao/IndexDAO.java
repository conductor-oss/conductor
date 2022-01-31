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
package com.netflix.conductor.dao;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.events.queue.Message;

/** DAO to index the workflow and task details for searching. */
public interface IndexDAO {

    /** Setup method in charge or initializing/populating the index. */
    void setup() throws Exception;

    /**
     * This method should return an unique identifier of the indexed doc
     *
     * @param workflow Workflow to be indexed
     */
    void indexWorkflow(WorkflowSummary workflow);

    /**
     * This method should return an unique identifier of the indexed doc
     *
     * @param workflow Workflow to be indexed
     * @return CompletableFuture of type void
     */
    CompletableFuture<Void> asyncIndexWorkflow(WorkflowSummary workflow);

    /** @param task Task to be indexed */
    void indexTask(TaskSummary task);

    /**
     * @param task Task to be indexed asynchronously
     * @return CompletableFuture of type void
     */
    CompletableFuture<Void> asyncIndexTask(TaskSummary task);

    /**
     * @param query SQL like query for workflow search parameters.
     * @param freeText Additional query in free text. Lucene syntax
     * @param start start start index for pagination
     * @param count count # of workflow ids to be returned
     * @param sort sort options
     * @return List of workflow ids for the matching query
     */
    SearchResult<String> searchWorkflows(
            String query, String freeText, int start, int count, List<String> sort);

    /**
     * @param query SQL like query for task search parameters.
     * @param freeText Additional query in free text. Lucene syntax
     * @param start start start index for pagination
     * @param count count # of task ids to be returned
     * @param sort sort options
     * @return List of workflow ids for the matching query
     */
    SearchResult<String> searchTasks(
            String query, String freeText, int start, int count, List<String> sort);

    /**
     * Remove the workflow index
     *
     * @param workflowId workflow to be removed
     */
    void removeWorkflow(String workflowId);

    /**
     * Remove the workflow index
     *
     * @param workflowId workflow to be removed
     * @return CompletableFuture of type void
     */
    CompletableFuture<Void> asyncRemoveWorkflow(String workflowId);

    /**
     * Updates the index
     *
     * @param workflowInstanceId id of the workflow
     * @param keys keys to be updated
     * @param values values. Number of keys and values MUST match.
     */
    void updateWorkflow(String workflowInstanceId, String[] keys, Object[] values);

    /**
     * Updates the index
     *
     * @param workflowInstanceId id of the workflow
     * @param keys keys to be updated
     * @param values values. Number of keys and values MUST match.
     * @return CompletableFuture of type void
     */
    CompletableFuture<Void> asyncUpdateWorkflow(
            String workflowInstanceId, String[] keys, Object[] values);

    /**
     * Retrieves a specific field from the index
     *
     * @param workflowInstanceId id of the workflow
     * @param key field to be retrieved
     * @return value of the field as string
     */
    String get(String workflowInstanceId, String key);

    /** @param logs Task Execution logs to be indexed */
    void addTaskExecutionLogs(List<TaskExecLog> logs);

    /**
     * @param logs Task Execution logs to be indexed
     * @return CompletableFuture of type void
     */
    CompletableFuture<Void> asyncAddTaskExecutionLogs(List<TaskExecLog> logs);

    /**
     * @param taskId Id of the task for which to fetch the execution logs
     * @return Returns the task execution logs for given task id
     */
    List<TaskExecLog> getTaskExecutionLogs(String taskId);

    /** @param eventExecution Event Execution to be indexed */
    void addEventExecution(EventExecution eventExecution);

    List<EventExecution> getEventExecutions(String event);

    /**
     * @param eventExecution Event Execution to be indexed
     * @return CompletableFuture of type void
     */
    CompletableFuture<Void> asyncAddEventExecution(EventExecution eventExecution);

    /**
     * Adds an incoming external message into the index
     *
     * @param queue Name of the registered queue
     * @param msg Message
     */
    void addMessage(String queue, Message msg);

    /**
     * Adds an incoming external message into the index
     *
     * @param queue Name of the registered queue
     * @param message {@link Message}
     * @return CompletableFuture of type Void
     */
    CompletableFuture<Void> asyncAddMessage(String queue, Message message);

    List<Message> getMessages(String queue);

    /**
     * Search for Workflows completed or failed beyond archiveTtlDays
     *
     * @param indexName Name of the index to search
     * @param archiveTtlDays Archival Time to Live
     * @return List of worlflow Ids matching the pattern
     */
    List<String> searchArchivableWorkflows(String indexName, long archiveTtlDays);

    /**
     * Get total workflow counts that matches the query
     *
     * @param query SQL like query for workflow search parameters.
     * @param freeText Additional query in free text. Lucene syntax
     * @return Number of matches for the query
     */
    long getWorkflowCount(String query, String freeText);
}
