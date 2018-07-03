/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.dao.dynomite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.metrics.Monitors;

@Singleton
@Trace
public class RedisMetadataDAO extends BaseDynoDAO implements MetadataDAO {

	// Keys Families
	private final static String ALL_TASK_DEFS = "TASK_DEFS";
	private final static String WORKFLOW_DEF_NAMES = "WORKFLOW_DEF_NAMES";
	private final static String WORKFLOW_DEF = "WORKFLOW_DEF";
	private final static String EVENT_HANDLERS = "EVENT_HANDLERS";
	private final static String EVENT_HANDLERS_BY_EVENT = "EVENT_HANDLERS_BY_EVENT";
	private final static String LATEST = "latest";

	private Map<String, TaskDef> taskDefCache = new HashMap<>();
	private static final String className = RedisMetadataDAO.class.getSimpleName();
	@Inject
	public RedisMetadataDAO(DynoProxy dynoClient, ObjectMapper objectMapper, Configuration config) {
		super(dynoClient, objectMapper, config);
		refreshTaskDefs();
		int cacheRefreshTime = config.getIntProperty("conductor.taskdef.cache.refresh.time.seconds", 60);
		Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(()->refreshTaskDefs(), cacheRefreshTime, cacheRefreshTime, TimeUnit.SECONDS);
	}

	@Override
	public String createTaskDef(TaskDef taskDef) {
		taskDef.setCreateTime(System.currentTimeMillis());
		return insertOrUpdateTaskDef(taskDef);
	}

	@Override
	public String updateTaskDef(TaskDef taskDef) {
		taskDef.setUpdateTime(System.currentTimeMillis());
		return insertOrUpdateTaskDef(taskDef);
	}

	private String insertOrUpdateTaskDef(TaskDef taskDef) {

		Preconditions.checkNotNull(taskDef, "TaskDef object cannot be null");
		Preconditions.checkNotNull(taskDef.getName(), "TaskDef name cannot be null");

		// Store all task def in under one key
		String payload = toJson(taskDef);
		dynoClient.hset(nsKey(ALL_TASK_DEFS), taskDef.getName(), payload);
		recordRedisDaoRequests("storeTaskDef");
		recordRedisDaoPayloadSize("storeTaskDef", payload.length(), taskDef.getName(), "n/a");
		refreshTaskDefs();
		return taskDef.getName();
	}

	private void refreshTaskDefs() {
        try {
            Map<String, TaskDef> map = new HashMap<>();
            getAllTaskDefs().forEach(taskDef -> map.put(taskDef.getName(), taskDef));
            this.taskDefCache = map;
            logger.debug("Refreshed task defs " + this.taskDefCache.size());
        } catch (Exception e){
			Monitors.error(className, "refreshTaskDefs");
            logger.error("refresh TaskDefs failed ", e);
        }
	}
	
	@Override
	public TaskDef getTaskDef(String name) {
		TaskDef taskDef = taskDefCache.get(name);
		if(taskDef == null) {
			taskDef = getTaskDefFromDB(name);
		}
		return taskDef;
	}
	
	private TaskDef getTaskDefFromDB(String name) {
		Preconditions.checkNotNull(name, "TaskDef name cannot be null");
		
		TaskDef taskDef = null;
		String taskDefJsonStr = dynoClient.hget(nsKey(ALL_TASK_DEFS), name);
		if (taskDefJsonStr != null) {
			taskDef = readValue(taskDefJsonStr, TaskDef.class);
			recordRedisDaoRequests("getTaskDef");
			recordRedisDaoPayloadSize("getTaskDef", taskDefJsonStr.length(), taskDef.getName(), "n/a");
		}	
		return taskDef;
	}

	@Override
	public List<TaskDef> getAllTaskDefs() {
		List<TaskDef> allTaskDefs = new LinkedList<TaskDef>();

		recordRedisDaoRequests("getAllTaskDefs");
		Map<String, String> taskDefs = dynoClient.hgetAll(nsKey(ALL_TASK_DEFS));
		int size = 0;
		if (taskDefs.size() > 0) {
			for (String taskDefJsonStr : taskDefs.values()) {
				if (taskDefJsonStr != null) {
					allTaskDefs.add(readValue(taskDefJsonStr, TaskDef.class));
					size += taskDefJsonStr.length();
				}
			}
			recordRedisDaoPayloadSize("getAllTaskDefs", size, "n/a", "n/a");
		}

		return allTaskDefs;
	}

	@Override
	public void removeTaskDef(String name) {
		Preconditions.checkNotNull(name, "TaskDef name cannot be null");
		Long result = dynoClient.hdel(nsKey(ALL_TASK_DEFS), name);
		if (!result.equals(1L)) {
			throw new ApplicationException(Code.NOT_FOUND, "Cannot remove the task - no such task definition");
		}
		recordRedisDaoRequests("removeTaskDef");
		refreshTaskDefs();
	}

	@Override
	public void create(WorkflowDef def) {
		if (dynoClient.hexists(nsKey(WORKFLOW_DEF, def.getName()), String.valueOf(def.getVersion()))) {
			throw new ApplicationException(Code.CONFLICT, "Workflow with " + def.key() + " already exists!");
		}
		def.setCreateTime(System.currentTimeMillis());
		_createOrUpdate(def);
	}

	@Override
	public void update(WorkflowDef def) {
		def.setUpdateTime(System.currentTimeMillis());
		_createOrUpdate(def);
	}

	@Override
	public WorkflowDef getLatest(String name) {
		Preconditions.checkNotNull(name, "WorkflowDef name cannot be null");
		WorkflowDef def = null;

		String workflowDefJsonString = dynoClient.hget(nsKey(WORKFLOW_DEF, name), LATEST);
		if (workflowDefJsonString != null) {
			def = readValue(workflowDefJsonString, WorkflowDef.class);
			recordRedisDaoRequests("getWorkflowDef");
			recordRedisDaoPayloadSize("getWorkflowDef", workflowDefJsonString.length(), "n/a", def.getName());
		}

		return def;
	}

	public List<WorkflowDef> getAllVersions(String name) {
		Preconditions.checkNotNull(name, "WorkflowDef name cannot be null");
		List<WorkflowDef> workflows = new LinkedList<WorkflowDef>();

		recordRedisDaoRequests("getAllWorkflowDefsByName");
		Map<String, String> workflowDefs = dynoClient.hgetAll(nsKey(WORKFLOW_DEF, name));
		int size = 0;
		for (String key : workflowDefs.keySet()) {
			if (key.equals(LATEST)) {
				continue;
			}
			String workflowDef = workflowDefs.get(key);
			workflows.add(readValue(workflowDef, WorkflowDef.class));
			size += workflowDef.length();
		}
		recordRedisDaoPayloadSize("getAllWorkflowDefsByName", size, "n/a", name);

		return workflows;
	}

	@Override
	public WorkflowDef get(String name, int version) {
		Preconditions.checkNotNull(name, "WorkflowDef name cannot be null");
		WorkflowDef def = null;

		recordRedisDaoRequests("getWorkflowDef");
		String workflowDefJsonString = dynoClient.hget(nsKey(WORKFLOW_DEF, name), String.valueOf(version));
		if (workflowDefJsonString != null) {
			def = readValue(workflowDefJsonString, WorkflowDef.class);
			recordRedisDaoPayloadSize("getWorkflowDef", workflowDefJsonString.length(), "n/a", name);
		}
		return def;
	}

	@Override
	public List<String> findAll() {
		Set<String> wfNames = dynoClient.smembers(nsKey(WORKFLOW_DEF_NAMES));
		return new ArrayList<>(wfNames);
	}

	@Override
	public List<WorkflowDef> getAll() {
		List<WorkflowDef> workflows = new LinkedList<WorkflowDef>();

		// Get all from WORKFLOW_DEF_NAMES
		recordRedisDaoRequests("getAllWorkflowDefs");
		Set<String> wfNames = dynoClient.smembers(nsKey(WORKFLOW_DEF_NAMES));
		int size = 0;
		for (String wfName : wfNames) {
			Map<String, String> workflowDefs = dynoClient.hgetAll(nsKey(WORKFLOW_DEF, wfName));
			for (String key : workflowDefs.keySet()) {
				if (key.equals(LATEST)) {
					continue;
				}
				String workflowDef = workflowDefs.get(key);
				workflows.add(readValue(workflowDef, WorkflowDef.class));
				size += workflowDef.length();
			}
		}
		recordRedisDaoPayloadSize("getAllWorkflowDefs", size, "n/a", "n/a");
		return workflows;
	}

	@Override
	public List<WorkflowDef> getAllLatest() {
		List<WorkflowDef> workflows = new LinkedList<WorkflowDef>();
		int size = 0;

		// Get all from WORKFLOW_DEF_NAMES
		recordRedisDaoRequests("getAllLatestWorkflowDefs");
		Set<String> workflowNames = dynoClient.smembers(nsKey(WORKFLOW_DEF_NAMES));
		for (String workflowName : workflowNames) {
			String workflowDefJsonString = dynoClient.hget(nsKey(WORKFLOW_DEF, workflowName), LATEST);
			workflows.add(readValue(workflowDefJsonString, WorkflowDef.class));
			size += workflowDefJsonString.length();
		}
		recordRedisDaoPayloadSize("getAllLatestWorkflowDefs", size, "n/a", "n/a");

		return workflows;
	}

	//Event Handler APIs

	@Override
	public void addEventHandler(EventHandler eventHandler) {
		Preconditions.checkNotNull(eventHandler.getName(), "Missing Name");
		if(getEventHandler(eventHandler.getName()) != null) {
			throw new ApplicationException(Code.CONFLICT, "EventHandler with name " + eventHandler.getName() + " already exists!");
		}
		index(eventHandler);
		dynoClient.hset(nsKey(EVENT_HANDLERS), eventHandler.getName(), toJson(eventHandler));
		recordRedisDaoRequests("addEventHandler");
	}

	@Override
	public void updateEventHandler(EventHandler eventHandler) {
		Preconditions.checkNotNull(eventHandler.getName(), "Missing Name");
		EventHandler existing = getEventHandler(eventHandler.getName());
		if(existing == null) {
			throw new ApplicationException(Code.NOT_FOUND, "EventHandler with name " + eventHandler.getName() + " not found!");
		}
		index(eventHandler);
		dynoClient.hset(nsKey(EVENT_HANDLERS), eventHandler.getName(), toJson(eventHandler));
		recordRedisDaoRequests("updateEventHandler");
	}

	@Override
	public void removeEventHandlerStatus(String name) {
		EventHandler existing = getEventHandler(name);
		if(existing == null) {
			throw new ApplicationException(Code.NOT_FOUND, "EventHandler with name " + name + " not found!");
		}
		dynoClient.hdel(nsKey(EVENT_HANDLERS), name);
		recordRedisDaoRequests("removeEventHandler");
		removeIndex(existing);
	}

	@Override
	public List<EventHandler> getEventHandlers() {
		Map<String, String> all = dynoClient.hgetAll(nsKey(EVENT_HANDLERS));
		List<EventHandler> handlers = new LinkedList<>();
		all.entrySet().forEach(e -> {
			String json = e.getValue();
			EventHandler eh = readValue(json, EventHandler.class);
			handlers.add(eh);
		});
		recordRedisDaoRequests("getAllEventHandlers");
		return handlers;
	}
	
	private void index(EventHandler eh) {
		String event = eh.getEvent();
		String key = nsKey(EVENT_HANDLERS_BY_EVENT, event);
		dynoClient.sadd(key, eh.getName());
	}
	
	private void removeIndex(EventHandler eh) {
		String event = eh.getEvent();
		String key = nsKey(EVENT_HANDLERS_BY_EVENT, event);
		dynoClient.srem(key, eh.getName());
	}
	
	@Override
	public List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly) {
		String key = nsKey(EVENT_HANDLERS_BY_EVENT, event);
		Set<String> names = dynoClient.smembers(key);
		List<EventHandler> handlers = new LinkedList<>();
		for(String name : names) {
			try {
				EventHandler eventHandler = getEventHandler(name);
				recordRedisDaoEventRequests("getEventHandler", event);
				if(eventHandler.getEvent().equals(event) && (!activeOnly || eventHandler.isActive())) {
					handlers.add(eventHandler);
				}
			} catch (ApplicationException ae) {
				if(ae.getCode() == Code.NOT_FOUND) {}
				throw ae;
			}
		}
		return handlers;
	}
	
	private EventHandler getEventHandler(String name) {
		EventHandler eventHandler = null;
		String json = dynoClient.hget(nsKey(EVENT_HANDLERS), name);
		if (json != null) {
			eventHandler = readValue(json, EventHandler.class);
		}
		return eventHandler;

	}
	
	
	private void _createOrUpdate(WorkflowDef workflowDef) {
		Preconditions.checkNotNull(workflowDef, "WorkflowDef object cannot be null");
		Preconditions.checkNotNull(workflowDef.getName(), "WorkflowDef name cannot be null");

		// First set the workflow def
		dynoClient.hset(nsKey(WORKFLOW_DEF, workflowDef.getName()), String.valueOf(workflowDef.getVersion()), toJson(workflowDef));

		// If it is getting created then make sure the latest field is updated
		// and WORKFLOW_DEF_NAMES is updated
		List<Integer> versions = new ArrayList<Integer>();
		dynoClient.hkeys(nsKey(WORKFLOW_DEF, workflowDef.getName())).forEach(key -> {
			if (!key.equals(LATEST)) {
				versions.add(Integer.valueOf(key));
			}
		});
		Collections.sort(versions);
		String latestKey = versions.get(versions.size() - 1).toString();
		String latestdata = dynoClient.hget(nsKey(WORKFLOW_DEF, workflowDef.getName()), latestKey);
		dynoClient.hset(nsKey(WORKFLOW_DEF, workflowDef.getName()), LATEST, latestdata);
		dynoClient.sadd(nsKey(WORKFLOW_DEF_NAMES), workflowDef.getName());
		recordRedisDaoRequests("storeWorkflowDef", "n/a", workflowDef.getName());
	}
}
