package com.netflix.conductor.sqlite.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.dao.ConcurrentExecutionLimitDAO;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.RateLimitingDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.sqlite.util.ExecutorsUtil;
import org.springframework.retry.support.RetryTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class SqliteExecutionDAO extends SqliteBaseDAO
        implements ExecutionDAO, RateLimitingDAO, ConcurrentExecutionLimitDAO {

    private final ScheduledExecutorService scheduledExecutorService;

    public SqliteExecutionDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
        this.scheduledExecutorService =
                Executors.newSingleThreadScheduledExecutor(
                        ExecutorsUtil.newNamedThreadFactory("postgres-execution-"));
    }

    @Override
    public boolean exceedsLimit(TaskModel task) {
        return false;
    }

    @Override
    public List<TaskModel> getPendingTasksByWorkflow(String taskName, String workflowId) {
        return List.of();
    }

    @Override
    public List<TaskModel> getTasks(String taskType, String startKey, int count) {
        return List.of();
    }

    @Override
    public List<TaskModel> createTasks(List<TaskModel> tasks) {
        return List.of();
    }

    @Override
    public void updateTask(TaskModel task) {

    }

    @Override
    public boolean removeTask(String taskId) {
        return false;
    }

    @Override
    public TaskModel getTask(String taskId) {
        return null;
    }

    @Override
    public List<TaskModel> getTasks(List<String> taskIds) {
        return List.of();
    }

    @Override
    public List<TaskModel> getPendingTasksForTaskType(String taskType) {
        return List.of();
    }

    @Override
    public List<TaskModel> getTasksForWorkflow(String workflowId) {
        return List.of();
    }

    @Override
    public String createWorkflow(WorkflowModel workflow) {
        return "";
    }

    @Override
    public String updateWorkflow(WorkflowModel workflow) {
        return "";
    }

    @Override
    public boolean removeWorkflow(String workflowId) {
        return false;
    }

    @Override
    public boolean removeWorkflowWithExpiry(String workflowId, int ttlSeconds) {
        return false;
    }

    @Override
    public void removeFromPendingWorkflow(String workflowType, String workflowId) {

    }

    @Override
    public WorkflowModel getWorkflow(String workflowId) {
        return null;
    }

    @Override
    public WorkflowModel getWorkflow(String workflowId, boolean includeTasks) {
        return null;
    }

    @Override
    public List<String> getRunningWorkflowIds(String workflowName, int version) {
        return List.of();
    }

    @Override
    public List<WorkflowModel> getPendingWorkflowsByType(String workflowName, int version) {
        return List.of();
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
    public List<WorkflowModel> getWorkflowsByType(String workflowName, Long startTime, Long endTime) {
        return List.of();
    }

    @Override
    public List<WorkflowModel> getWorkflowsByCorrelationId(String workflowName, String correlationId, boolean includeTasks) {
        return List.of();
    }

    @Override
    public boolean canSearchAcrossWorkflows() {
        return false;
    }

    @Override
    public boolean addEventExecution(EventExecution eventExecution) {
        return false;
    }

    @Override
    public void updateEventExecution(EventExecution eventExecution) {

    }

    @Override
    public void removeEventExecution(EventExecution eventExecution) {

    }

    @Override
    public boolean exceedsRateLimitPerFrequency(TaskModel task, TaskDef taskDef) {
        return false;
    }
}
