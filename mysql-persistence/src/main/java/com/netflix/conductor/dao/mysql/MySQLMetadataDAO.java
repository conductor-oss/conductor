package com.netflix.conductor.dao.mysql;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.sql2o.Connection;
import org.sql2o.Sql2o;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.dao.MetadataDAO;

class MySQLMetadataDAO extends MySQLBaseDAO implements MetadataDAO {

	private Map<String, TaskDef> taskDefCache = new HashMap<>();

	@Inject
	MySQLMetadataDAO(ObjectMapper om, Sql2o sql2o, Configuration config) {
		super(om, sql2o);
		refreshTaskDefs();
		int cacheRefreshTime = config.getIntProperty("conductor.taskdef.cache.refresh.time.seconds", 60);
		Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(this::refreshTaskDefs, cacheRefreshTime, cacheRefreshTime, TimeUnit.SECONDS);
	}

	@Override
	public String createTaskDef(TaskDef taskDef) {
		validate(taskDef);
		taskDef.setCreateTime(System.currentTimeMillis());
		return insertOrUpdateTaskDef(taskDef);
	}

	@Override
	public String updateTaskDef(TaskDef taskDef) {
		validate(taskDef);
		taskDef.setUpdateTime(System.currentTimeMillis());
		return insertOrUpdateTaskDef(taskDef);
	}

	@Override
	public TaskDef getTaskDef(String name) {
		Preconditions.checkNotNull(name, "TaskDef name cannot be null");
		TaskDef taskDef = taskDefCache.get(name);
		if (taskDef == null) {
			taskDef = getTaskDefFromDB(name);
		}
		return taskDef;
	}

	private TaskDef getTaskDefFromDB(String name) {
		String READ_ONE_TASKDEF_QUERY = "SELECT json_data FROM meta_task_def WHERE name = :name";
		String taskDefJsonStr = getWithTransaction(conn -> conn.createQuery(READ_ONE_TASKDEF_QUERY).addParameter("name", name).executeScalar(String.class));
		return taskDefJsonStr != null ? readValue(taskDefJsonStr, TaskDef.class) : null;
	}

	@Override
	public List<TaskDef> getAllTaskDefs() {
		return getWithTransaction(this::findAllTaskDefs);
	}

	@Override
	public void removeTaskDef(String name) {
		withTransaction(connection -> {
			String DELETE_TASKDEF_QUERY = "DELETE FROM meta_task_def WHERE name = :name";
			int deleted = connection.createQuery(DELETE_TASKDEF_QUERY).addParameter("name", name).executeUpdate().getResult();
			if (deleted != 1) {
				throw new ApplicationException(ApplicationException.Code.NOT_FOUND, "Cannot remove the task - no such task definition");
			}
			refreshTaskDefs(connection);
		});
	}

	@Override
	public void create(WorkflowDef def) {
		validate(def);
		def.setCreateTime(System.currentTimeMillis());
		withTransaction(connection -> {
			if (workflowExists(connection, def)) {
				throw new ApplicationException(ApplicationException.Code.CONFLICT, "Workflow with " + def.key() + " already exists!");
			}
			insertOrUpdateWorkflowDef(connection, def);
		});
	}

	@Override
	public void update(WorkflowDef def) {
		validate(def);
		def.setUpdateTime(System.currentTimeMillis());
		withTransaction(connection -> insertOrUpdateWorkflowDef(connection, def));
	}

	@Override
	public WorkflowDef getLatest(String name) {
		String GET_LATEST_WORKFLOW_DEF_QUERY = "SELECT json_data FROM meta_workflow_def WHERE NAME = :name AND version = latest_version";
		String workflowJsonStr = getWithTransaction(conn -> conn.createQuery(GET_LATEST_WORKFLOW_DEF_QUERY).addParameter("name", name).executeScalar(String.class));
		return (workflowJsonStr != null) ? readValue(workflowJsonStr, WorkflowDef.class) : null;
	}

	@Override
	public WorkflowDef get(String name, int version) {
		String GET_WORKFLOW_DEF_QUERY = "SELECT json_data FROM meta_workflow_def WHERE NAME = :name AND version = :version";
		String workflowJsonStr = getWithTransaction(conn -> conn.createQuery(GET_WORKFLOW_DEF_QUERY).addParameter("name", name).addParameter("version", version).executeScalar(String.class));
		return (workflowJsonStr != null) ? readValue(workflowJsonStr, WorkflowDef.class) : null;
	}

	@Override
	public List<String> findAll() {
		String FIND_ALL_WORKFLOW_DEF_QUERY = "SELECT DISTINCT name FROM meta_workflow_def";
		return getWithTransaction(conn -> conn.createQuery(FIND_ALL_WORKFLOW_DEF_QUERY).executeScalarList(String.class));
	}

	@Override
	public List<WorkflowDef> getAll() {
		String GET_ALL_WORKFLOW_DEF_QUERY = "SELECT json_data FROM meta_workflow_def ORDER BY name, version";
		return getWithTransaction(conn -> conn.createQuery(GET_ALL_WORKFLOW_DEF_QUERY).executeScalarList(String.class)).stream()
				.map(jsonData -> readValue(jsonData, WorkflowDef.class))
				.collect(Collectors.toList());
	}

	@Override
	public List<WorkflowDef> getAllLatest() {
		String GET_ALL_LATEST_WORKFLOW_DEF_QUERY = "SELECT json_data FROM meta_workflow_def WHERE version = latest_version";
		return getWithTransaction(conn -> conn.createQuery(GET_ALL_LATEST_WORKFLOW_DEF_QUERY).executeScalarList(String.class)).stream()
				.map(jsonData -> readValue(jsonData, WorkflowDef.class))
				.collect(Collectors.toList());
	}

	@Override
	public List<WorkflowDef> getAllVersions(String name) {
		String GET_ALL_VERSIONS_WORKFLOW_DEF_QUERY = "SELECT json_data FROM meta_workflow_def WHERE name = :name ORDER BY version";
		return getWithTransaction(conn -> conn.createQuery(GET_ALL_VERSIONS_WORKFLOW_DEF_QUERY).addParameter("name",name).executeScalarList(String.class)).stream()
				.map(jsonData -> readValue(jsonData, WorkflowDef.class))
				.collect(Collectors.toList());
	}

	@Override
	public void addEventHandler(EventHandler eventHandler) {
		Preconditions.checkNotNull(eventHandler.getName(), "EventHandler name cannot be null");
		withTransaction(connection -> {
			if (getEventHandler(connection, eventHandler.getName()) != null) {
				throw new ApplicationException(ApplicationException.Code.CONFLICT, "EventHandler with name " + eventHandler.getName() + " already exists!");
			}
			String INSERT_EVENT_HANDLER_QUERY = "INSERT INTO meta_event_handler (name, event, active, json_data) VALUES (:name, :event, :active, :jsonData)";
			connection.createQuery(INSERT_EVENT_HANDLER_QUERY).bind(eventHandler).addParameter("jsonData", toJson(eventHandler)).executeUpdate();
		});
	}

	@Override
	public void updateEventHandler(EventHandler eventHandler) {
		Preconditions.checkNotNull(eventHandler.getName(), "EventHandler name cannot be null");
		withTransaction(connection -> {
			EventHandler existing = getEventHandler(connection, eventHandler.getName());
			if (existing == null) {
				throw new ApplicationException(ApplicationException.Code.NOT_FOUND, "EventHandler with name " + eventHandler.getName() + " not found!");
			}
			String UPDATE_EVENT_HANDLER_QUERY = "UPDATE meta_event_handler SET event = :event, active = :active, json_data = :jsonData, modified_on = CURRENT_TIMESTAMP WHERE name = :name";
			connection.createQuery(UPDATE_EVENT_HANDLER_QUERY).bind(eventHandler).addParameter("jsonData", toJson(eventHandler)).executeUpdate();
		});
	}

	@Override
	public void removeEventHandlerStatus(String name) {
		withTransaction(connection -> {
			EventHandler existing = getEventHandler(connection, name);
			if (existing == null) {
				throw new ApplicationException(ApplicationException.Code.NOT_FOUND, "EventHandler with name " + name + " not found!");
			}
			String DELETE_EVENT_HANDLER_QUERY = "DELETE FROM meta_event_handler WHERE name = :name";
			connection.createQuery(DELETE_EVENT_HANDLER_QUERY).addParameter("name", name).executeUpdate();
		});
	}

	private EventHandler getEventHandler(Connection connection, String name) {
		String READ_ONE_EVENT_HANDLER_QUERY = "SELECT json_data FROM meta_event_handler WHERE name = :name";
		String eventHandlerStr = connection.createQuery(READ_ONE_EVENT_HANDLER_QUERY).addParameter("name", name).executeScalar(String.class);
		return eventHandlerStr != null ? readValue(eventHandlerStr, EventHandler.class) : null;
	}

	@Override
	public List<EventHandler> getEventHandlers() {
		String READ_ALL_EVENT_HANDLER_QUERY = "SELECT json_data FROM meta_event_handler";
		return getWithTransaction(conn -> conn.createQuery(READ_ALL_EVENT_HANDLER_QUERY).executeScalarList(String.class)).stream()
				.map(jsonData -> readValue(jsonData, EventHandler.class))
				.collect(Collectors.toList());
	}

	@Override
	public List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly) {
		String READ_ALL_EVENT_HANDLER_BY_EVENT_QUERY = "SELECT json_data FROM meta_event_handler WHERE event = :event";
		return getWithTransaction(conn -> conn.createQuery(READ_ALL_EVENT_HANDLER_BY_EVENT_QUERY).addParameter("event", event).executeScalarList(String.class)).stream()
				.map(jsonData -> readValue(jsonData, EventHandler.class))
				.filter(eventHandler -> (!activeOnly || eventHandler.isActive()))
				.collect(Collectors.toList());
	}

	private void refreshTaskDefs(Connection connection) {
		Map<String, TaskDef> map = new HashMap<>();
		findAllTaskDefs(connection).forEach(taskDef -> map.put(taskDef.getName(), taskDef));
		this.taskDefCache = map;
	}

	private void refreshTaskDefs() {
		withTransaction(this::refreshTaskDefs);
	}

	private void validate(TaskDef taskDef) {
		Preconditions.checkNotNull(taskDef, "TaskDef object cannot be null");
		Preconditions.checkNotNull(taskDef.getName(), "TaskDef name cannot be null");
	}

	private void validate(WorkflowDef def) {
		Preconditions.checkNotNull(def, "WorkflowDef object cannot be null");
		Preconditions.checkNotNull(def.getName(), "WorkflowDef name cannot be null");
	}

	private String insertOrUpdateTaskDef(TaskDef taskDef) {
		withTransaction(connection -> {
			String UPDATE_TASKDEF_QUERY = "UPDATE meta_task_def SET json_data = :jsonData, modified_on = CURRENT_TIMESTAMP WHERE name = :name";
			int result = connection.createQuery(UPDATE_TASKDEF_QUERY).bind(taskDef).addParameter("jsonData", toJson(taskDef)).executeUpdate().getResult();
			if (result == 0) {
				String INSERT_TASKDEF_QUERY = "INSERT INTO meta_task_def (name, json_data) VALUES (:name, :jsonData)";
				connection.createQuery(INSERT_TASKDEF_QUERY).bind(taskDef).addParameter("jsonData", toJson(taskDef)).executeUpdate();
			}
			refreshTaskDefs(connection);
		});
		return taskDef.getName();
	}

	private void insertOrUpdateWorkflowDef(Connection connection, WorkflowDef def) {

		String GET_LATEST_WORKFLOW_DEF_VERSION = "SELECT max(version) AS version FROM meta_workflow_def WHERE name = :name";
		Integer latestVersion = connection.createQuery(GET_LATEST_WORKFLOW_DEF_VERSION).bind(def).executeScalar(Integer.class);

		if (latestVersion == null || latestVersion < def.getVersion()) {
			String INSERT_WORKFLOW_DEF_QUERY = "INSERT INTO meta_workflow_def (name, version, json_data) VALUES (:name, :version, :jsonData)";
			connection.createQuery(INSERT_WORKFLOW_DEF_QUERY).bind(def).addParameter("jsonData", toJson(def)).executeUpdate();
			latestVersion = def.getVersion();
		} else {
			String UPDATE_WORKFLOW_DEF_QUERY = "UPDATE meta_workflow_def SET json_data = :jsonData, modified_on = CURRENT_TIMESTAMP WHERE name = :name AND version = :version";
			connection.createQuery(UPDATE_WORKFLOW_DEF_QUERY).bind(def).addParameter("jsonData", toJson(def)).executeUpdate();
		}

		String UPDATE_WORKFLOW_DEF_LATEST_VERSION_QUERY = "UPDATE meta_workflow_def SET latest_version = :latest_version WHERE name = :name";
		connection.createQuery(UPDATE_WORKFLOW_DEF_LATEST_VERSION_QUERY).bind(def).addParameter("latest_version", latestVersion).executeUpdate();
	}

	private Boolean workflowExists(Connection connection, WorkflowDef def) {
		String CHECK_WORKFLOW_DEF_EXISTS_QUERY = "SELECT COUNT(*) FROM meta_workflow_def WHERE name = :name AND version = :version";
		return connection.createQuery(CHECK_WORKFLOW_DEF_EXISTS_QUERY).bind(def).executeScalar(Boolean.class);
	}

	private List<TaskDef> findAllTaskDefs(Connection connection) {
		String READ_ALL_TASKDEF_QUERY = "SELECT json_data FROM meta_task_def";
		return connection.createQuery(READ_ALL_TASKDEF_QUERY).executeScalarList(String.class)
				.stream()
				.map(jsonData -> readValue(jsonData, TaskDef.class))
				.collect(Collectors.toList());
	}
}
