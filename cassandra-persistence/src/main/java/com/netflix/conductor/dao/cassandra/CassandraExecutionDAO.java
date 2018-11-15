/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.dao.cassandra;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.netflix.conductor.cassandra.CassandraConfiguration;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.util.Statements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.netflix.conductor.util.Constants.ENTITY_KEY;
import static com.netflix.conductor.util.Constants.ENTITY_TYPE_TASK;
import static com.netflix.conductor.util.Constants.ENTITY_TYPE_WORKFLOW;
import static com.netflix.conductor.util.Constants.PAYLOAD_KEY;
import static com.netflix.conductor.util.Constants.TOTAL_PARTITIONS_KEY;
import static com.netflix.conductor.util.Constants.TOTAL_TASKS_KEY;
import static com.netflix.conductor.util.Constants.WORKFLOW_ID_KEY;

public class CassandraExecutionDAO extends CassandraBaseDAO implements ExecutionDAO {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraExecutionDAO.class);

    private final PreparedStatement insertWorkflowStatement;
    private final PreparedStatement insertTaskStatement;

    private final PreparedStatement selectTotalStatement;
    private final PreparedStatement selectTaskStatement;
    private final PreparedStatement selectWorkflowStatement;
    private final PreparedStatement selectWorkflowWithTasksStatement;
    private final PreparedStatement selectTaskLookupStatement;

    private final PreparedStatement updateWorkflowStatement;
    private final PreparedStatement updateTotalTasksStatement;
    private final PreparedStatement updateTotalPartitionsStatement;
    private final PreparedStatement updateTaskLookupStatement;

    private final PreparedStatement deleteWorkflowStatement;
    private final PreparedStatement deleteTaskStatement;
    private final PreparedStatement deleteTaskLookupStatement;

    @Inject
    public CassandraExecutionDAO(Session session, ObjectMapper objectMapper, CassandraConfiguration config, Statements statements) {
        super(session, objectMapper, config);

        this.insertWorkflowStatement = session.prepare(statements.getInsertWorkflowStatement().toString());
        this.insertTaskStatement = session.prepare(statements.getInsertTaskStatement().toString());

        this.selectTotalStatement = session.prepare(statements.getSelectTotalStatement().toString());
        this.selectTaskStatement = session.prepare(statements.getSelectTaskStatement().toString());
        this.selectWorkflowStatement = session.prepare(statements.getSelectWorkflowStatement().toString());
        this.selectWorkflowWithTasksStatement = session.prepare(statements.getSelectWorkflowWithTasksStatement().toString());
        this.selectTaskLookupStatement = session.prepare(statements.getSelectTaskFromLookupTableStatement().toString());

        this.updateWorkflowStatement = session.prepare(statements.getUpdateWorkflowStatement().toString());
        this.updateTotalTasksStatement = session.prepare(statements.getUpdateTotalTasksStatement().toString());
        this.updateTotalPartitionsStatement = session.prepare(statements.getUpdateTotalPartitionsStatement().toString());
        this.updateTaskLookupStatement = session.prepare(statements.getUpdateTaskLookupStatement().toString());

        this.deleteWorkflowStatement = session.prepare(statements.getDeleteWorkflowStatement().toString());
        this.deleteTaskStatement = session.prepare(statements.getDeleteTaskStatement().toString());
        this.deleteTaskLookupStatement = session.prepare(statements.getDeleteTaskLookupStatement().toString());
    }

    @Override
    public List<Task> getPendingTasksByWorkflow(String taskName, String workflowId) {
        List<Task> tasks = getTasksForWorkflow(workflowId);
        return tasks.stream()
                .filter(task -> taskName.equals(task.getTaskType()))
                .filter(task -> Task.Status.IN_PROGRESS.equals(task.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Task> getTasks(String taskType, String startKey, int count) {
        return null;
    }

    @Override
    public List<Task> createTasks(List<Task> tasks) {
        validateTasks(tasks);
        String workflowId = tasks.get(0).getWorkflowInstanceId();
        try {
            WorkflowMetadata workflowMetadata = getWorkflowMetadata(workflowId);
            int shardId = 1;
            int totalTasks = workflowMetadata.getTotalTasks() + tasks.size();
            int totalPartitions = 1;
            // TODO: write into multiple shards based on number of tasks

            // update the task_lookup table
            tasks.forEach(task -> {
                task.setScheduledTime(System.currentTimeMillis());
                session.execute(updateTaskLookupStatement.bind(UUID.fromString(workflowId), UUID.fromString(task.getTaskId())));
            });

            // update all the tasks in the workflow using batch
            BatchStatement batchStatement = new BatchStatement();
            tasks.forEach(task -> batchStatement.add(insertTaskStatement.bind(UUID.fromString(workflowId), shardId, task.getTaskId(), toJson(task))));
            batchStatement.add(updateTotalTasksStatement.bind(totalTasks, UUID.fromString(workflowId), shardId));
            session.execute(batchStatement);

            // update the total tasks and partitions for the workflow
            session.execute(updateTotalPartitionsStatement.bind(totalTasks, totalPartitions, UUID.fromString(workflowId)));

            return tasks;
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Error creating %d tasks for workflow: %s", tasks.size(), workflowId);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    }

    @Override
    public void updateTask(Task task) {
        try {
            task.setUpdateTime(System.currentTimeMillis());
            if (task.getStatus().isTerminal() && task.getEndTime() == 0) {
                task.setEndTime(System.currentTimeMillis());
            }
            int shardId = 1;
            // TODO: calculate the shard number the task belongs to
            session.execute(insertTaskStatement.bind(UUID.fromString(task.getWorkflowInstanceId()), shardId, task.getTaskId(), toJson(task)));
        } catch (Exception e) {
            String errorMsg = String.format("Error updating task: %s in workflow: %s", task.getTaskId(), task.getWorkflowInstanceId());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     *
     * @param task which needs to be evaluated whether it is concurrency limited or not
     * @return false (since, not implemented)
     */
    @Override
    public boolean exceedsInProgressLimit(Task task) {
        return false;
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     *
     * @param task which needs to be evaluated whether it is rateLimited or not
     * @return false (since, not implemented)
     */
    @Override
    public boolean exceedsRateLimitPerFrequency(Task task) {
        return false;
    }

    @Override
    public void updateTasks(List<Task> tasks) {
        tasks.forEach(this::updateTask);
    }

    @Override
    public void removeTask(String taskId) {
        Task task = getTask(taskId);
        if (task == null) {
            LOGGER.warn("No such task by id {}", taskId);
            return;
        }
        removeTask(task);
    }

    @Override
    public Task getTask(String taskId) {
        try {
            String workflowId = lookupWorkflowIdFromTaskId(taskId);
            // TODO: implement for query against multiple shards
            int shardId = 1;

            ResultSet resultSet = session.execute(selectTaskStatement.bind(UUID.fromString(workflowId), shardId, taskId));
            return Optional.ofNullable(resultSet.one())
                    .map(row -> readValue(row.getString(PAYLOAD_KEY), Task.class))
                    .orElseThrow(() -> new ApplicationException(ApplicationException.Code.NOT_FOUND, String.format("Task: %s, workflow: %s not found in data store", taskId, workflowId)));
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Error getting task by id: %s", taskId);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg);
        }
    }

    @Override
    public List<Task> getTasks(List<String> taskIds) {
        Preconditions.checkNotNull(taskIds);
        Preconditions.checkArgument(taskIds.size() > 0, "Task ids list cannot be empty");
        String workflowId = lookupWorkflowIdFromTaskId(taskIds.get(0));
        return getWorkflow(workflowId, true).getTasks().stream()
                .filter(task -> taskIds.contains(task.getTaskId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Task> getPendingTasksForTaskType(String taskType) {
        return null;
    }

    @Override
    public List<Task> getTasksForWorkflow(String workflowId) {
        return getWorkflow(workflowId, true).getTasks();
    }

    @Override
    public String createWorkflow(Workflow workflow) {
        try {
            workflow.setCreateTime(System.currentTimeMillis());
            List<Task> tasks = workflow.getTasks();
            workflow.setTasks(new LinkedList<>());
            session.execute(insertWorkflowStatement.bind(UUID.fromString(workflow.getWorkflowId()), 1, "", toJson(workflow), 0, 1));
            workflow.setTasks(tasks);
            return workflow.getWorkflowId();
        } catch (Exception e) {
            String errorMsg = String.format("Error creating workflow: %s", workflow.getWorkflowId());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    }

    @Override
    public String updateWorkflow(Workflow workflow) {
        try {
            workflow.setUpdateTime(System.currentTimeMillis());
            if (workflow.getStatus().isTerminal()) {
                workflow.setEndTime(System.currentTimeMillis());
            }
            List<Task> tasks = workflow.getTasks();
            workflow.setTasks(new LinkedList<>());
            session.execute(updateWorkflowStatement.bind(toJson(workflow), UUID.fromString(workflow.getWorkflowId())));
            workflow.setTasks(tasks);
            return workflow.getWorkflowId();
        } catch (Exception e) {
            String errorMsg = String.format("Failed to update workflow: %s", workflow.getWorkflowId());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg);
        }
    }

    @Override
    public void removeWorkflow(String workflowId) {
        Workflow workflow = getWorkflow(workflowId, true);

        // TODO: calculate number of shards and iterate
        int shardId = 1;

        try {
            session.execute(deleteWorkflowStatement.bind(UUID.fromString(workflowId), shardId));
        } catch (Exception e) {
            String errorMsg = String.format("Failed to remove workflow: %s", workflowId);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg);
        }

        workflow.getTasks().forEach(this::removeTask);
    }

    @Override
    public void removeFromPendingWorkflow(String workflowType, String workflowId) {

    }

    @Override
    public Workflow getWorkflow(String workflowId) {
        return getWorkflow(workflowId, true);
    }

    @Override
    public Workflow getWorkflow(String workflowId, boolean includeTasks) {
        try {
            ResultSet resultSet;
            if (includeTasks) {
                int shardId = 1;
                resultSet = session.execute(selectWorkflowWithTasksStatement.bind(UUID.fromString(workflowId), shardId));
                List<Task> tasks = new ArrayList<>();
                Workflow workflow = null;

                List<Row> rows = resultSet.all();
                if (rows.size() == 0) {
                    throw new ApplicationException(ApplicationException.Code.NOT_FOUND, String.format("Workflow: %s not found in data store", workflowId));
                }
                for (Row row : rows) {
                    if (ENTITY_TYPE_WORKFLOW.equals(row.getString(ENTITY_KEY))) {
                        workflow = readValue(row.getString(PAYLOAD_KEY), Workflow.class);
                    } else if (ENTITY_TYPE_TASK.equals(row.getString(ENTITY_KEY))) {
                        Task task = readValue(row.getString(PAYLOAD_KEY), Task.class);
                        tasks.add(task);
                    } else {
                        throw new ApplicationException(ApplicationException.Code.INTERNAL_ERROR, String.format("Invalid row found in datastore for workflow: %s", workflowId));
                    }
                }

                workflow.setTasks(tasks);
                return workflow;
            } else {
                resultSet = session.execute(selectWorkflowStatement.bind(UUID.fromString(workflowId)));
                return Optional.ofNullable(resultSet.one())
                        .map(row -> readValue(row.getString(PAYLOAD_KEY), Workflow.class))
                        .orElseThrow(() -> new ApplicationException(ApplicationException.Code.NOT_FOUND, String.format("Workflow: %s not found in data store", workflowId)));
            }
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to get workflow: %s", workflowId);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg);
        }
    }

    @Override
    public List<String> getRunningWorkflowIds(String workflowName) {
        return null;
    }

    @Override
    public List<Workflow> getPendingWorkflowsByType(String workflowName) {
        return null;
    }

    @Override
    public long getPendingWorkflowCount(String workflowName) {
        return 0;
    }

    @Override
    public long getInProgressTaskCount(String taskDefName) {
        return 0;
    }

    @Override
    public List<Workflow> getWorkflowsByType(String workflowName, Long startTime, Long endTime) {
        return null;
    }

    @Override
    public List<Workflow> getWorkflowsByCorrelationId(String correlationId, boolean includeTasks) {
        throw new UnsupportedOperationException("This method is not implemented in CassandraExecutionDAO. Please use ExecutionDAOFacade instead.");
    }

    @Override
    public boolean canSearchAcrossWorkflows() {
        return false;
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     *
     * @return false (since, not implemented)
     */
    @Override
    public boolean addEventExecution(EventExecution ee) {
        return false;
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public void updateEventExecution(EventExecution ee) {

    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public void removeEventExecution(EventExecution ee) {

    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     *
     * @return null (since, not implemented)
     */
    @Override
    public List<EventExecution> getEventExecutions(String eventHandlerName, String eventName, String messageId, int max) {
        return null;
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public void updateLastPoll(String taskDefName, String domain, String workerId) {

    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     *
     * @return null (since, not implemented)
     */
    @Override
    public PollData getPollData(String taskDefName, String domain) {
        return null;
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     *
     * @return null (since, not implemented)
     */
    @Override
    public List<PollData> getPollData(String taskDefName) {
        return null;
    }

    private void removeTask(Task task) {
        // TODO: calculate shard number based on seq and maxTasksPerShard
        int shardId = 1;
        try {
            session.execute(deleteTaskLookupStatement.bind(UUID.fromString(task.getTaskId())));
            session.execute(deleteTaskStatement.bind(UUID.fromString(task.getWorkflowInstanceId()), shardId, task.getTaskId()));
        } catch (Exception e) {
            String errorMsg = String.format("Failed to remove task: %s", task.getTaskId());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg);
        }
    }

    @VisibleForTesting
    void validateTasks(List<Task> tasks) {
        try {
            Preconditions.checkNotNull(tasks, "Tasks object cannot be null");
            Preconditions.checkArgument(!tasks.isEmpty(), "Tasks object cannot be empty");
            tasks.forEach(task -> {
                Preconditions.checkNotNull(task, "task object cannot be null");
                Preconditions.checkNotNull(task.getTaskId(), "Task id cannot be null");
                Preconditions.checkNotNull(task.getWorkflowInstanceId(), "Workflow instance id cannot be null");
                Preconditions.checkNotNull(task.getReferenceTaskName(), "Task reference name cannot be null");
            });
        } catch (NullPointerException npe) {
            throw new ApplicationException(ApplicationException.Code.INVALID_INPUT, npe.getMessage(), npe);
        }

        String workflowId = tasks.get(0).getWorkflowInstanceId();
        Optional<Task> optionalTask = tasks.stream()
                .filter(task -> !workflowId.equals(task.getWorkflowInstanceId()))
                .findAny();
        if (optionalTask.isPresent()) {
            throw new ApplicationException(ApplicationException.Code.INTERNAL_ERROR, "Tasks of multiple workflows cannot be created/updated simultaneously");
        }
    }

    private WorkflowMetadata getWorkflowMetadata(String workflowId) {
        ResultSet resultSet = session.execute(selectTotalStatement.bind(UUID.fromString(workflowId)));
        return Optional.ofNullable(resultSet.one())
                .map(row -> {
                    WorkflowMetadata workflowMetadata = new WorkflowMetadata();
                    workflowMetadata.setTotalTasks(row.getInt(TOTAL_TASKS_KEY));
                    workflowMetadata.setTotalPartitions(row.getInt(TOTAL_PARTITIONS_KEY));
                    return workflowMetadata;
                }).orElseThrow(() -> new ApplicationException(ApplicationException.Code.NOT_FOUND, String.format("Workflow with id: %s not found in data store", workflowId)));
    }

    private String lookupWorkflowIdFromTaskId(String taskId) {
        try {
            ResultSet resultSet = session.execute(selectTaskLookupStatement.bind(UUID.fromString(taskId)));
            return Optional.ofNullable(resultSet.one())
                    .map(row -> row.getUUID(WORKFLOW_ID_KEY).toString())
                    .orElseThrow(() -> new ApplicationException(ApplicationException.Code.NOT_FOUND, String.format("Task with id: %s not found in data store", taskId)));
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to lookup workflowId from taskId: %s", taskId);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    }
}
