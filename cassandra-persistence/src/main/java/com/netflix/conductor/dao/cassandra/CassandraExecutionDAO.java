/*
 * Copyright 2020 Netflix, Inc.
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

import static com.netflix.conductor.common.metadata.tasks.Task.Status.IN_PROGRESS;
import static com.netflix.conductor.util.Constants.DEFAULT_SHARD_ID;
import static com.netflix.conductor.util.Constants.DEFAULT_TOTAL_PARTITIONS;
import static com.netflix.conductor.util.Constants.ENTITY_KEY;
import static com.netflix.conductor.util.Constants.ENTITY_TYPE_TASK;
import static com.netflix.conductor.util.Constants.ENTITY_TYPE_WORKFLOW;
import static com.netflix.conductor.util.Constants.PAYLOAD_KEY;
import static com.netflix.conductor.util.Constants.TASK_ID_KEY;
import static com.netflix.conductor.util.Constants.TOTAL_PARTITIONS_KEY;
import static com.netflix.conductor.util.Constants.TOTAL_TASKS_KEY;
import static com.netflix.conductor.util.Constants.WORKFLOW_ID_KEY;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.cassandra.CassandraConfiguration;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.RetryUtil;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.util.Statements;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Trace
public class CassandraExecutionDAO extends CassandraBaseDAO implements ExecutionDAO, PollDataDAO {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraExecutionDAO.class);
    private static final String CLASS_NAME = CassandraExecutionDAO.class.getSimpleName();

    private final PreparedStatement insertWorkflowStatement;
    private final PreparedStatement insertTaskStatement;
    private final PreparedStatement insertEventExecutionStatement;

    private final PreparedStatement selectTotalStatement;
    private final PreparedStatement selectTaskStatement;
    private final PreparedStatement selectWorkflowStatement;
    private final PreparedStatement selectWorkflowWithTasksStatement;
    private final PreparedStatement selectTaskLookupStatement;
    private final PreparedStatement selectTasksFromTaskDefLimitStatement;
    private final PreparedStatement selectEventExecutionsStatement;

    private final PreparedStatement updateWorkflowStatement;
    private final PreparedStatement updateTotalTasksStatement;
    private final PreparedStatement updateTotalPartitionsStatement;
    private final PreparedStatement updateTaskLookupStatement;
    private final PreparedStatement updateTaskDefLimitStatement;
    private final PreparedStatement updateEventExecutionStatement;

    private final PreparedStatement deleteWorkflowStatement;
    private final PreparedStatement deleteTaskStatement;
    private final PreparedStatement deleteTaskLookupStatement;
    private final PreparedStatement deleteTaskDefLimitStatement;
    private final PreparedStatement deleteEventExecutionStatement;

    private final int eventExecutionsTTL;

    @Inject
    public CassandraExecutionDAO(Session session, ObjectMapper objectMapper, CassandraConfiguration config, Statements statements) {
        super(session, objectMapper, config);

        eventExecutionsTTL = config.getEventExecutionPersistenceTTL();

        this.insertWorkflowStatement = session.prepare(statements.getInsertWorkflowStatement()).setConsistencyLevel(config.getWriteConsistencyLevel());
        this.insertTaskStatement = session.prepare(statements.getInsertTaskStatement()).setConsistencyLevel(config.getWriteConsistencyLevel());
        this.insertEventExecutionStatement = session.prepare(statements.getInsertEventExecutionStatement()).setConsistencyLevel(config.getWriteConsistencyLevel());

        this.selectTotalStatement = session.prepare(statements.getSelectTotalStatement()).setConsistencyLevel(config.getReadConsistencyLevel());
        this.selectTaskStatement = session.prepare(statements.getSelectTaskStatement()).setConsistencyLevel(config.getReadConsistencyLevel());
        this.selectWorkflowStatement = session.prepare(statements.getSelectWorkflowStatement()).setConsistencyLevel(config.getReadConsistencyLevel());
        this.selectWorkflowWithTasksStatement = session.prepare(statements.getSelectWorkflowWithTasksStatement()).setConsistencyLevel(config.getReadConsistencyLevel());
        this.selectTaskLookupStatement = session.prepare(statements.getSelectTaskFromLookupTableStatement()).setConsistencyLevel(config.getReadConsistencyLevel());
        this.selectTasksFromTaskDefLimitStatement = session.prepare(statements.getSelectTasksFromTaskDefLimitStatement()).setConsistencyLevel(config.getReadConsistencyLevel());
        this.selectEventExecutionsStatement = session.prepare(statements.getSelectAllEventExecutionsForMessageFromEventExecutionsStatement()).setConsistencyLevel(config.getReadConsistencyLevel());

        this.updateWorkflowStatement = session.prepare(statements.getUpdateWorkflowStatement()).setConsistencyLevel(config.getWriteConsistencyLevel());
        this.updateTotalTasksStatement = session.prepare(statements.getUpdateTotalTasksStatement()).setConsistencyLevel(config.getWriteConsistencyLevel());
        this.updateTotalPartitionsStatement = session.prepare(statements.getUpdateTotalPartitionsStatement()).setConsistencyLevel(config.getWriteConsistencyLevel());
        this.updateTaskLookupStatement = session.prepare(statements.getUpdateTaskLookupStatement()).setConsistencyLevel(config.getWriteConsistencyLevel());
        this.updateTaskDefLimitStatement = session.prepare(statements.getUpdateTaskDefLimitStatement()).setConsistencyLevel(config.getWriteConsistencyLevel());
        this.updateEventExecutionStatement = session.prepare(statements.getUpdateEventExecutionStatement()).setConsistencyLevel(config.getWriteConsistencyLevel());

        this.deleteWorkflowStatement = session.prepare(statements.getDeleteWorkflowStatement()).setConsistencyLevel(config.getWriteConsistencyLevel());
        this.deleteTaskStatement = session.prepare(statements.getDeleteTaskStatement()).setConsistencyLevel(config.getWriteConsistencyLevel());
        this.deleteTaskLookupStatement = session.prepare(statements.getDeleteTaskLookupStatement()).setConsistencyLevel(config.getWriteConsistencyLevel());
        this.deleteTaskDefLimitStatement = session.prepare(statements.getDeleteTaskDefLimitStatement()).setConsistencyLevel(config.getWriteConsistencyLevel());
        this.deleteEventExecutionStatement = session.prepare(statements.getDeleteEventExecutionsStatement()).setConsistencyLevel(config.getWriteConsistencyLevel());
    }

    @Override
    public List<Task> getPendingTasksByWorkflow(String taskName, String workflowId) {
        List<Task> tasks = getTasksForWorkflow(workflowId);
        return tasks.stream()
                .filter(task -> taskName.equals(task.getTaskType()))
                .filter(task -> IN_PROGRESS.equals(task.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public List<Task> getTasks(String taskType, String startKey, int count) {
        throw new UnsupportedOperationException("This method is not implemented in CassandraExecutionDAO. Please use ExecutionDAOFacade instead.");
    }

    /**
     * Inserts tasks into the Cassandra datastore.
     * <b>Note:</b>
     * Creates the task_id to workflow_id mapping in the task_lookup table first.
     * Once this succeeds, inserts the tasks into the workflows table. Tasks belonging to the same shard are created using batch statements.
     *
     * @param tasks tasks to be created
     */
    @Override
    public List<Task> createTasks(List<Task> tasks) {
        validateTasks(tasks);
        String workflowId = tasks.get(0).getWorkflowInstanceId();
        try {
            WorkflowMetadata workflowMetadata = getWorkflowMetadata(workflowId);
            int totalTasks = workflowMetadata.getTotalTasks() + tasks.size();
            // TODO: write into multiple shards based on number of tasks

            // update the task_lookup table
            tasks.forEach(task -> {
                task.setScheduledTime(System.currentTimeMillis());
                session.execute(updateTaskLookupStatement.bind(UUID.fromString(workflowId), UUID.fromString(task.getTaskId())));
            });

            // update all the tasks in the workflow using batch
            BatchStatement batchStatement = new BatchStatement();
            tasks.forEach(task -> {
                String taskPayload = toJson(task);
                batchStatement.add(insertTaskStatement.bind(UUID.fromString(workflowId), DEFAULT_SHARD_ID, task.getTaskId(), taskPayload));
                recordCassandraDaoRequests("createTask", task.getTaskType(), task.getWorkflowType());
                recordCassandraDaoPayloadSize("createTask", taskPayload.length(), task.getTaskType(), task.getWorkflowType());
            });
            batchStatement.add(updateTotalTasksStatement.bind(totalTasks, UUID.fromString(workflowId), DEFAULT_SHARD_ID));
            session.execute(batchStatement);

            // update the total tasks and partitions for the workflow
            session.execute(updateTotalPartitionsStatement.bind(DEFAULT_TOTAL_PARTITIONS, totalTasks, UUID.fromString(workflowId)));

            return tasks;
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "createTasks");
            String errorMsg = String.format("Error creating %d tasks for workflow: %s", tasks.size(), workflowId);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg, e);
        }
    }

    @Override
    public void updateTask(Task task) {
        try {
            // TODO: calculate the shard number the task belongs to
            String taskPayload = toJson(task);
            recordCassandraDaoRequests("updateTask", task.getTaskType(), task.getWorkflowType());
            recordCassandraDaoPayloadSize("updateTask", taskPayload.length(), task.getTaskType(), task.getWorkflowType());
            session.execute(insertTaskStatement.bind(UUID.fromString(task.getWorkflowInstanceId()), DEFAULT_SHARD_ID, task.getTaskId(), taskPayload));
            if (task.getTaskDefinition().isPresent() && task.getTaskDefinition().get().concurrencyLimit() > 0) {
                updateTaskDefLimit(task, false);
            }
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "updateTask");
            String errorMsg = String.format("Error updating task: %s in workflow: %s", task.getTaskId(), task.getWorkflowInstanceId());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg, e);
        }
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public boolean exceedsInProgressLimit(Task task) {
        Optional<TaskDef> taskDefinition = task.getTaskDefinition();
        if(!taskDefinition.isPresent()) {
            return false;
        }
        int limit = taskDefinition.get().concurrencyLimit();
        if(limit <= 0) {
            return false;
        }

        try {
            recordCassandraDaoRequests("selectTaskDefLimit", task.getTaskType(), task.getWorkflowType());
            ResultSet resultSet = session.execute(selectTasksFromTaskDefLimitStatement.bind(task.getTaskDefName()));
            List<String> taskIds = resultSet.all().stream()
                .map(row -> row.getUUID(TASK_ID_KEY).toString())
                .collect(Collectors.toList());
            long current = taskIds.size();

            if(!taskIds.contains(task.getTaskId()) && current >= limit) {
                LOGGER.info("Task execution count limited. task - {}:{}, limit: {}, current: {}", task.getTaskId(), task.getTaskDefName(), limit, current);
                Monitors.recordTaskConcurrentExecutionLimited(task.getTaskDefName(), limit);
                return true;
            }
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "exceedsInProgressLimit");
            String errorMsg = String.format("Failed to get in progress limit - %s:%s in workflow :%s",
                task.getTaskDefName(), task.getTaskId(), task.getWorkflowInstanceId());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg);
        }
        return false;
    }

    @Override
    public boolean removeTask(String taskId) {
        Task task = getTask(taskId);
        if (task == null) {
            LOGGER.warn("No such task found by id {}", taskId);
            return false;
        }
        return removeTask(task);
    }

    @Override
    public Task getTask(String taskId) {
        try {
            String workflowId = lookupWorkflowIdFromTaskId(taskId);
            if (workflowId == null) {
                return null;
            }
            // TODO: implement for query against multiple shards

            ResultSet resultSet = session
                .execute(selectTaskStatement.bind(UUID.fromString(workflowId), DEFAULT_SHARD_ID, taskId));
            return Optional.ofNullable(resultSet.one())
                .map(row -> {
                    Task task = readValue(row.getString(PAYLOAD_KEY), Task.class);
                    recordCassandraDaoRequests("getTask", task.getTaskType(), task.getWorkflowType());
                    recordCassandraDaoPayloadSize("getTask", toJson(task).length(), task.getTaskType(),
                        task.getWorkflowType());
                    return task;
                })
                .orElse(null);
        } catch (ApplicationException ae) {
            throw ae;
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "getTask");
            String errorMsg = String.format("Error getting task by id: %s", taskId);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg);
        }
    }

    @Override
    public List<Task> getTasks(List<String> taskIds) {
        Preconditions.checkNotNull(taskIds);
        Preconditions.checkArgument(taskIds.size() > 0, "Task ids list cannot be empty");
        String workflowId = lookupWorkflowIdFromTaskId(taskIds.get(0));
        if (workflowId == null) {
            return null;
        }
        return getWorkflow(workflowId, true).getTasks().stream()
                .filter(task -> taskIds.contains(task.getTaskId()))
                .collect(Collectors.toList());
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public List<Task> getPendingTasksForTaskType(String taskType) {
        throw new UnsupportedOperationException("This method is not implemented in CassandraExecutionDAO. Please use ExecutionDAOFacade instead.");
    }

    @Override
    public List<Task> getTasksForWorkflow(String workflowId) {
        return getWorkflow(workflowId, true).getTasks();
    }

    @Override
    public String createWorkflow(Workflow workflow) {
        try {
            List<Task> tasks = workflow.getTasks();
            workflow.setTasks(new LinkedList<>());
            String payload = toJson(workflow);

            recordCassandraDaoRequests("createWorkflow", "n/a", workflow.getWorkflowName());
            recordCassandraDaoPayloadSize("createWorkflow", payload.length(), "n/a", workflow.getWorkflowName());
            session.execute(insertWorkflowStatement.bind(UUID.fromString(workflow.getWorkflowId()), 1, "", payload, 0, 1));

            workflow.setTasks(tasks);
            return workflow.getWorkflowId();
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "createWorkflow");
            String errorMsg = String.format("Error creating workflow: %s", workflow.getWorkflowId());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg, e);
        }
    }

    @Override
    public String updateWorkflow(Workflow workflow) {
        try {
            List<Task> tasks = workflow.getTasks();
            workflow.setTasks(new LinkedList<>());
            String payload = toJson(workflow);
            recordCassandraDaoRequests("updateWorkflow", "n/a", workflow.getWorkflowName());
            recordCassandraDaoPayloadSize("updateWorkflow", payload.length(), "n/a", workflow.getWorkflowName());
            session.execute(updateWorkflowStatement.bind(payload, UUID.fromString(workflow.getWorkflowId())));
            workflow.setTasks(tasks);
            return workflow.getWorkflowId();
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "updateWorkflow");
            String errorMsg = String.format("Failed to update workflow: %s", workflow.getWorkflowId());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg);
        }
    }

    @Override
    public boolean removeWorkflow(String workflowId) {
        Workflow workflow = getWorkflow(workflowId, true);
        boolean removed = false;
        // TODO: calculate number of shards and iterate
        if (workflow != null) {
            try {
                recordCassandraDaoRequests("removeWorkflow", "n/a", workflow.getWorkflowName());
                ResultSet resultSet = session.execute(deleteWorkflowStatement.bind(UUID.fromString(workflowId), DEFAULT_SHARD_ID));
                removed = resultSet.wasApplied();
            } catch (Exception e) {
                Monitors.error(CLASS_NAME, "removeWorkflow");
                String errorMsg = String.format("Failed to remove workflow: %s", workflowId);
                LOGGER.error(errorMsg, e);
                throw new ApplicationException(Code.BACKEND_ERROR, errorMsg);
            }
            workflow.getTasks().forEach(this::removeTaskLookup);
        }
        return removed;
    }

    /**
     * This is a dummy implementation and this feature is not yet implemented
     * for Cassandra backed Conductor
     */
    @Override
    public boolean removeWorkflowWithExpiry(String workflowId, int ttlSeconds) {
        throw new UnsupportedOperationException("This method is not currently implemented in CassandraExecutionDAO. Please use RedisDAO mode instead now for using TTLs.");
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public void removeFromPendingWorkflow(String workflowType, String workflowId) {
        throw new UnsupportedOperationException("This method is not implemented in CassandraExecutionDAO. Please use ExecutionDAOFacade instead.");
    }

    @Override
    public Workflow getWorkflow(String workflowId) {
        return getWorkflow(workflowId, true);
    }

    @Override
    public Workflow getWorkflow(String workflowId, boolean includeTasks) {
        Workflow workflow = null;
        try {
            ResultSet resultSet;
            if (includeTasks) {
                resultSet = session.execute(selectWorkflowWithTasksStatement.bind(UUID.fromString(workflowId), DEFAULT_SHARD_ID));
                List<Task> tasks = new ArrayList<>();

                List<Row> rows = resultSet.all();
                if (rows.size() == 0) {
                    LOGGER.info("Workflow {} not found in datastore", workflowId);
                    return null;
                }
                for (Row row : rows) {
                    String entityKey = row.getString(ENTITY_KEY);
                    if (ENTITY_TYPE_WORKFLOW.equals(entityKey)) {
                        workflow = readValue(row.getString(PAYLOAD_KEY), Workflow.class);
                    } else if (ENTITY_TYPE_TASK.equals(entityKey)) {
                        Task task = readValue(row.getString(PAYLOAD_KEY), Task.class);
                        tasks.add(task);
                    } else {
                        throw new ApplicationException(ApplicationException.Code.INTERNAL_ERROR, String.format("Invalid row with entityKey: %s found in datastore for workflow: %s", entityKey, workflowId));
                    }
                }

                if (workflow != null) {
                    recordCassandraDaoRequests("getWorkflow", "n/a", workflow.getWorkflowName());
                    tasks.sort(Comparator.comparingInt(Task::getSeq));
                    workflow.setTasks(tasks);
                }
            } else {
                resultSet = session.execute(selectWorkflowStatement.bind(UUID.fromString(workflowId)));
                workflow = Optional.ofNullable(resultSet.one())
                        .map(row -> {
                            Workflow wf = readValue(row.getString(PAYLOAD_KEY), Workflow.class);
                            recordCassandraDaoRequests("getWorkflow", "n/a", wf.getWorkflowName());
                            return wf;
                        })
                        .orElse(null);
            }
            return workflow;
        } catch (ApplicationException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            Monitors.error(CLASS_NAME, "getWorkflow");
            String errorMsg = String.format("Invalid workflow id: %s", workflowId);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.INVALID_INPUT, errorMsg, e);
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "getWorkflow");
            String errorMsg = String.format("Failed to get workflow: %s", workflowId);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg);
        }
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public List<String> getRunningWorkflowIds(String workflowName, int version) {
        throw new UnsupportedOperationException("This method is not implemented in CassandraExecutionDAO. Please use ExecutionDAOFacade instead.");
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public List<Workflow> getPendingWorkflowsByType(String workflowName, int version) {
        throw new UnsupportedOperationException("This method is not implemented in CassandraExecutionDAO. Please use ExecutionDAOFacade instead.");
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public long getPendingWorkflowCount(String workflowName) {
        throw new UnsupportedOperationException("This method is not implemented in CassandraExecutionDAO. Please use ExecutionDAOFacade instead.");
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public long getInProgressTaskCount(String taskDefName) {
        throw new UnsupportedOperationException("This method is not implemented in CassandraExecutionDAO. Please use ExecutionDAOFacade instead.");
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public List<Workflow> getWorkflowsByType(String workflowName, Long startTime, Long endTime) {
        throw new UnsupportedOperationException("This method is not implemented in CassandraExecutionDAO. Please use ExecutionDAOFacade instead.");
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public List<Workflow> getWorkflowsByCorrelationId(String workflowName, String correlationId, boolean includeTasks) {
        throw new UnsupportedOperationException("This method is not implemented in CassandraExecutionDAO. Please use ExecutionDAOFacade instead.");
    }

    @Override
    public boolean canSearchAcrossWorkflows() {
        return false;
    }

    @Override
    public boolean addEventExecution(EventExecution eventExecution) {
        try {
            String jsonPayload = toJson(eventExecution);
            recordCassandraDaoEventRequests("addEventExecution", eventExecution.getEvent());
            recordCassandraDaoPayloadSize("addEventExecution", jsonPayload.length(), eventExecution.getEvent(), "n/a");
            return session.execute(insertEventExecutionStatement
                .bind(eventExecution.getMessageId(), eventExecution.getName(), eventExecution.getId(), jsonPayload)).wasApplied();
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "addEventExecution");
            String errorMsg = String.format("Failed to add event execution for event: %s, handler: %s",
                eventExecution.getEvent(), eventExecution.getName());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg);
        }
    }

    @Override
    public void updateEventExecution(EventExecution eventExecution) {
        try {
            String jsonPayload = toJson(eventExecution);
            recordCassandraDaoEventRequests("updateEventExecution", eventExecution.getEvent());
            recordCassandraDaoPayloadSize("updateEventExecution", jsonPayload.length(), eventExecution.getEvent(), "n/a");
            session.execute(updateEventExecutionStatement.bind(eventExecutionsTTL, jsonPayload,
                eventExecution.getMessageId(), eventExecution.getName(), eventExecution.getId()));
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "updateEventExecution");
            String errorMsg = String.format("Failed to update event execution for event: %s, handler: %s",
                eventExecution.getEvent(), eventExecution.getName());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg);
        }
    }

    @Override
    public void removeEventExecution(EventExecution eventExecution) {
        try {
            recordCassandraDaoEventRequests("removeEventExecution", eventExecution.getEvent());
            session.execute(deleteEventExecutionStatement.bind(eventExecution.getMessageId(), eventExecution.getName(), eventExecution.getId()));
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "removeEventExecution");
            String errorMsg = String.format("Failed to remove event execution for event: %s, handler: %s",
                eventExecution.getEvent(), eventExecution.getName());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg);
        }
    }

    @VisibleForTesting
    List<EventExecution> getEventExecutions(String eventHandlerName, String eventName, String messageId) {
        try {
            return session.execute(selectEventExecutionsStatement.bind(messageId, eventHandlerName)).all().stream()
                .filter(row -> !row.isNull(PAYLOAD_KEY))
                .map(row -> readValue(row.getString(PAYLOAD_KEY), EventExecution.class))
                .collect(Collectors.toList());
        } catch (Exception e) {
            String errorMsg = String.format("Failed to fetch event executions for event: %s, handler: %s", eventName, eventHandlerName);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg);
        }
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public void updateLastPollData(String taskDefName, String domain, String workerId) {
        throw new UnsupportedOperationException("This method is not implemented in CassandraExecutionDAO. Please use ExecutionDAOFacade instead.");
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public PollData getPollData(String taskDefName, String domain) {
        throw new UnsupportedOperationException("This method is not implemented in CassandraExecutionDAO. Please use ExecutionDAOFacade instead.");
    }

    /**
     * This is a dummy implementation and this feature is not implemented
     * for Cassandra backed Conductor
     */
    @Override
    public List<PollData> getPollData(String taskDefName) {
        throw new UnsupportedOperationException("This method is not implemented in CassandraExecutionDAO. Please use ExecutionDAOFacade instead.");
    }

    private boolean removeTask(Task task) {
        // TODO: calculate shard number based on seq and maxTasksPerShard
        try {
            // get total tasks for this workflow
            WorkflowMetadata workflowMetadata = getWorkflowMetadata(task.getWorkflowInstanceId());
            int totalTasks = workflowMetadata.getTotalTasks();

            // remove from task_lookup table
            removeTaskLookup(task);

            recordCassandraDaoRequests("removeTask", task.getTaskType(), task.getWorkflowType());
            // delete task from workflows table and decrement total tasks by 1
            BatchStatement batchStatement = new BatchStatement();
            batchStatement.add(deleteTaskStatement.bind(UUID.fromString(task.getWorkflowInstanceId()), DEFAULT_SHARD_ID, task.getTaskId()));
            batchStatement.add(updateTotalTasksStatement.bind(totalTasks - 1, UUID.fromString(task.getWorkflowInstanceId()), DEFAULT_SHARD_ID));
            ResultSet resultSet = session.execute(batchStatement);
            if (task.getTaskDefinition().isPresent() && task.getTaskDefinition().get().concurrencyLimit() > 0) {
                updateTaskDefLimit(task, true);
            }
            return resultSet.wasApplied();
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "removeTask");
            String errorMsg = String.format("Failed to remove task: %s", task.getTaskId());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg);
        }
    }

    private void removeTaskLookup(Task task) {
        try {
            recordCassandraDaoRequests("removeTaskLookup", task.getTaskType(), task.getWorkflowType());
            if (task.getTaskDefinition().isPresent() && task.getTaskDefinition().get().concurrencyLimit() > 0) {
                updateTaskDefLimit(task, true);
            }
            session.execute(deleteTaskLookupStatement.bind(UUID.fromString(task.getTaskId())));
        } catch (ApplicationException ae) {
            // no-op
        }catch (Exception e) {
            Monitors.error(CLASS_NAME, "removeTaskLookup");
            String errorMsg = String.format("Failed to remove task lookup: %s", task.getTaskId());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg);
        }
    }

    @VisibleForTesting
    void validateTasks(List<Task> tasks) {
        Preconditions.checkNotNull(tasks, "Tasks object cannot be null");
        Preconditions.checkArgument(!tasks.isEmpty(), "Tasks object cannot be empty");
        tasks.forEach(task -> {
            Preconditions.checkNotNull(task, "task object cannot be null");
            Preconditions.checkNotNull(task.getTaskId(), "Task id cannot be null");
            Preconditions.checkNotNull(task.getWorkflowInstanceId(), "Workflow instance id cannot be null");
            Preconditions.checkNotNull(task.getReferenceTaskName(), "Task reference name cannot be null");
        });

        String workflowId = tasks.get(0).getWorkflowInstanceId();
        Optional<Task> optionalTask = tasks.stream()
                .filter(task -> !workflowId.equals(task.getWorkflowInstanceId()))
                .findAny();
        if (optionalTask.isPresent()) {
            throw new ApplicationException(Code.INTERNAL_ERROR, "Tasks of multiple workflows cannot be created/updated simultaneously");
        }
    }

    @VisibleForTesting
    WorkflowMetadata getWorkflowMetadata(String workflowId) {
        ResultSet resultSet = session.execute(selectTotalStatement.bind(UUID.fromString(workflowId)));
        recordCassandraDaoRequests("getWorkflowMetadata");
        return Optional.ofNullable(resultSet.one())
                .map(row -> {
                    WorkflowMetadata workflowMetadata = new WorkflowMetadata();
                    workflowMetadata.setTotalTasks(row.getInt(TOTAL_TASKS_KEY));
                    workflowMetadata.setTotalPartitions(row.getInt(TOTAL_PARTITIONS_KEY));
                    return workflowMetadata;
                }).orElseThrow(() -> new ApplicationException(Code.NOT_FOUND, String.format("Workflow with id: %s not found in data store", workflowId)));
    }

    @VisibleForTesting
    String lookupWorkflowIdFromTaskId(String taskId) {
        try {
            ResultSet resultSet = session.execute(selectTaskLookupStatement.bind(UUID.fromString(taskId)));
            return Optional.ofNullable(resultSet.one())
                .map(row -> row.getUUID(WORKFLOW_ID_KEY).toString())
                .orElse(null);
        } catch (IllegalArgumentException iae) {
            Monitors.error(CLASS_NAME, "lookupWorkflowIdFromTaskId");
            String errorMsg = String.format("Invalid task id: %s", taskId);
            LOGGER.error(errorMsg, iae);
            throw new ApplicationException(Code.INVALID_INPUT, errorMsg, iae);
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "lookupWorkflowIdFromTaskId");
            String errorMsg = String.format("Failed to lookup workflowId from taskId: %s", taskId);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg, e);
        }
    }

    @VisibleForTesting
    void updateTaskDefLimit(Task task, boolean forceRemove) {
        try {
            if (task.getStatus().isTerminal() || forceRemove) {
                recordCassandraDaoRequests("removeTaskDefLimit", task.getTaskType(), task.getWorkflowType());
                session.execute(deleteTaskDefLimitStatement.bind(task.getTaskDefName(), UUID.fromString(task.getTaskId())));
                new RetryUtil<>().retryOnException(
                    () -> session.execute(deleteTaskDefLimitStatement.bind(task.getTaskDefName(), UUID.fromString(task.getTaskId()))),
                    null,
                    null,
                    3,
                    "Deleting taskDefLimit",
                    "removeTaskDefLimit"
                );
            } else if (task.getStatus().equals(IN_PROGRESS)) {
                recordCassandraDaoRequests("addTaskDefLimit", task.getTaskType(), task.getWorkflowType());
                new RetryUtil<>().retryOnException(
                    () -> session.execute(updateTaskDefLimitStatement.bind(UUID.fromString(task.getWorkflowInstanceId()), task.getTaskDefName(), UUID.fromString(task.getTaskId()))),
                    null,
                    null,
                    3,
                    "Adding taskDefLimit",
                    "addTaskDefLimit"
                );
            }
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "updateTaskDefLimit");
            String errorMsg = String.format("Error updating taskDefLimit for task - %s:%s in workflow: %s", task.getTaskDefName(), task.getTaskId(), task.getWorkflowInstanceId());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg, e);
        }
    }
}
