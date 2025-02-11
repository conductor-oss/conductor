package com.netflix.conductor.sqlite.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.sqlite.config.SqliteProperties;
import com.netflix.conductor.sqlite.util.ExecutorsUtil;
import org.springframework.retry.support.RetryTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SqliteMetadataDAO extends SqliteBaseDAO implements MetadataDAO, EventHandlerDAO {

    private final ConcurrentHashMap<String, TaskDef> taskDefCache = new ConcurrentHashMap<>();
    private static final String CLASS_NAME = SqliteMetadataDAO.class.getSimpleName();

    private final ScheduledExecutorService scheduledExecutorService;

    public SqliteMetadataDAO(
            RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource,
            SqliteProperties properties) {
        super(retryTemplate, objectMapper, dataSource);

        long cacheRefreshTime = properties.getTaskDefCacheRefreshInterval().getSeconds();
        this.scheduledExecutorService =
                Executors.newSingleThreadScheduledExecutor(
                        ExecutorsUtil.newNamedThreadFactory("sqlite-metadata-"));
        this.scheduledExecutorService.scheduleWithFixedDelay(
                this::refreshTaskDefs, cacheRefreshTime, cacheRefreshTime, TimeUnit.SECONDS);
    }

    @Override
    public TaskDef createTaskDef(TaskDef taskDef) {
        return null;
    }

    @Override
    public TaskDef updateTaskDef(TaskDef taskDef) {
        return null;
    }

    @Override
    public TaskDef getTaskDef(String name) {
        return null;
    }

    @Override
    public List<TaskDef> getAllTaskDefs() {
        return List.of();
    }

    @Override
    public void removeTaskDef(String name) {

    }

    @Override
    public void createWorkflowDef(WorkflowDef def) {

    }

    @Override
    public void updateWorkflowDef(WorkflowDef def) {

    }

    @Override
    public Optional<WorkflowDef> getLatestWorkflowDef(String name) {
        return Optional.empty();
    }

    @Override
    public Optional<WorkflowDef> getWorkflowDef(String name, int version) {
        return Optional.empty();
    }

    @Override
    public void removeWorkflowDef(String name, Integer version) {

    }

    @Override
    public List<WorkflowDef> getAllWorkflowDefs() {
        return List.of();
    }

    @Override
    public List<WorkflowDef> getAllWorkflowDefsLatestVersions() {
        return List.of();
    }

    @Override
    public void addEventHandler(EventHandler eventHandler) {

    }

    @Override
    public void updateEventHandler(EventHandler eventHandler) {

    }

    @Override
    public void removeEventHandler(String name) {

    }

    @Override
    public List<EventHandler> getAllEventHandlers() {
        return List.of();
    }

    @Override
    public List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly) {
        return List.of();
    }

    private void refreshTaskDefs() {
        logger.info("Refreshing task definitions cache");
    }
}
