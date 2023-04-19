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
package com.netflix.conductor.core.index;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.IndexDAO;

/**
 * Dummy implementation of {@link IndexDAO} which does nothing. Nothing is ever indexed, and no
 * results are ever returned.
 */
public class NoopIndexDAO implements IndexDAO {

    @Override
    public void setup() {}

    @Override
    public void indexWorkflow(WorkflowSummary workflowSummary) {}

    @Override
    public CompletableFuture<Void> asyncIndexWorkflow(WorkflowSummary workflowSummary) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void indexTask(TaskSummary taskSummary) {}

    @Override
    public CompletableFuture<Void> asyncIndexTask(TaskSummary taskSummary) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public SearchResult<String> searchWorkflows(
            String query, String freeText, int start, int count, List<String> sort) {
        return new SearchResult<>(0, Collections.emptyList());
    }

    @Override
    public SearchResult<WorkflowSummary> searchWorkflowSummary(
            String query, String freeText, int start, int count, List<String> sort) {
        return new SearchResult<>(0, Collections.emptyList());
    }

    @Override
    public SearchResult<String> searchTasks(
            String query, String freeText, int start, int count, List<String> sort) {
        return new SearchResult<>(0, Collections.emptyList());
    }

    @Override
    public SearchResult<TaskSummary> searchTaskSummary(
            String query, String freeText, int start, int count, List<String> sort) {
        return new SearchResult<>(0, Collections.emptyList());
    }

    @Override
    public void removeWorkflow(String workflowId) {}

    @Override
    public CompletableFuture<Void> asyncRemoveWorkflow(String workflowId) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void updateWorkflow(String workflowInstanceId, String[] keys, Object[] values) {}

    @Override
    public CompletableFuture<Void> asyncUpdateWorkflow(
            String workflowInstanceId, String[] keys, Object[] values) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void removeTask(String workflowId, String taskId) {}

    @Override
    public CompletableFuture<Void> asyncRemoveTask(String workflowId, String taskId) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void updateTask(String workflowId, String taskId, String[] keys, Object[] values) {}

    @Override
    public CompletableFuture<Void> asyncUpdateTask(
            String workflowId, String taskId, String[] keys, Object[] values) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String get(String workflowInstanceId, String key) {
        return null;
    }

    @Override
    public void addTaskExecutionLogs(List<TaskExecLog> logs) {}

    @Override
    public CompletableFuture<Void> asyncAddTaskExecutionLogs(List<TaskExecLog> logs) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public List<TaskExecLog> getTaskExecutionLogs(String taskId) {
        return Collections.emptyList();
    }

    @Override
    public void addEventExecution(EventExecution eventExecution) {}

    @Override
    public List<EventExecution> getEventExecutions(String event) {
        return Collections.emptyList();
    }

    @Override
    public CompletableFuture<Void> asyncAddEventExecution(EventExecution eventExecution) {
        return null;
    }

    @Override
    public void addMessage(String queue, Message msg) {}

    @Override
    public CompletableFuture<Void> asyncAddMessage(String queue, Message message) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public List<Message> getMessages(String queue) {
        return Collections.emptyList();
    }

    @Override
    public List<String> searchArchivableWorkflows(String indexName, long archiveTtlDays) {
        return Collections.emptyList();
    }

    @Override
    public long getWorkflowCount(String query, String freeText) {
        return 0;
    }
}
