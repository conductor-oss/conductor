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
/**
 * 
 */
package com.netflix.conductor.service;

import com.google.common.base.Preconditions;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.dao.MetadataDAO;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * @author Viren 
 * 
 */
@Singleton
@Trace
public class MetadataService {

    private MetadataDAO metadataDAO;

    private RateLimitingService rateLimitingService;

    @Inject
    public MetadataService(MetadataDAO metadataDAO, RateLimitingService rateLimitingService) {
        this.metadataDAO = metadataDAO;
        this.rateLimitingService = rateLimitingService;
    }

    /**
     *
     * @param taskDefinitions Task Definitions to register
     */
    public void registerTaskDef(List<TaskDef> taskDefinitions) {
        for (TaskDef taskDefinition : taskDefinitions) {
            taskDefinition.setCreatedBy(WorkflowContext.get().getClientApp());
            taskDefinition.setCreateTime(System.currentTimeMillis());
            taskDefinition.setUpdatedBy(null);
            taskDefinition.setUpdateTime(null);
            metadataDAO.createTaskDef(taskDefinition);
            if(taskDefinition.getRateLimitPerSecond() != 0) {
                rateLimitingService.updateRateLimitRules(taskDefinition);
            }
        }
    }

    /**
     *
     * @param taskDefinition Task Definition to be updated
     */
    public void updateTaskDef(TaskDef taskDefinition) {
        TaskDef existing = metadataDAO.getTaskDef(taskDefinition.getName());
        if (existing == null) {
            throw new ApplicationException(Code.NOT_FOUND, "No such task by name " + taskDefinition.getName());
        }
        taskDefinition.setUpdatedBy(WorkflowContext.get().getClientApp());
        taskDefinition.setUpdateTime(System.currentTimeMillis());
        metadataDAO.updateTaskDef(taskDefinition);
        if(taskDefinition.getRateLimitPerSecond() != 0) {
            rateLimitingService.updateRateLimitRules(taskDefinition);
        }
    }

    /**
     *
     * @param taskType Remove task definition
     */
    public void unregisterTaskDef(String taskType) {
        metadataDAO.removeTaskDef(taskType);
    }

    /**
     *
     * @return List of all the registered tasks
     */
    public List<TaskDef> getTaskDefs() {
        return metadataDAO.getAllTaskDefs();
    }

    /**
     *
     * @param taskType Task to retrieve
     * @return Task Definition
     */
    public TaskDef getTaskDef(String taskType) {
        return metadataDAO.getTaskDef(taskType);
    }

    /**
     *
     * @param def Workflow definition to be updated
     */
    public void updateWorkflowDef(WorkflowDef def) {
        metadataDAO.update(def);
    }

    /**
     *
     * @param wfs Workflow definitions to be updated.
     */
    public void updateWorkflowDef(List<WorkflowDef> wfs) {
        for (WorkflowDef wf : wfs) {
            metadataDAO.update(wf);
        }
    }

    /**
     *
     * @param name Name of the workflow to retrieve
     * @param version Optional.  Version.  If null, then retrieves the latest
     * @return Workflow definition
     */
    public WorkflowDef getWorkflowDef(String name, Integer version) {
        if (version == null) {
            return metadataDAO.getLatest(name);
        }
        return metadataDAO.get(name, version);
    }

    /**
     *
     * @param name Name of the workflow to retrieve
     * @return Latest version of the workflow definition
     */
    public WorkflowDef getLatestWorkflow(String name) {
        return metadataDAO.getLatest(name);
    }

    public List<WorkflowDef> getWorkflowDefs() {
        return metadataDAO.getAll();
    }

    public void registerWorkflowDef(WorkflowDef def) {
        if(def.getName().contains(":")) {
            throw new ApplicationException(Code.INVALID_INPUT, "Workflow name cannot contain the following set of characters: ':'");
        }
        if(def.getSchemaVersion() < 1 || def.getSchemaVersion() > 2) {
            def.setSchemaVersion(2);
        }
        metadataDAO.create(def);
    }

    /**
     *
     * @param name Name of the workflow definition to be removed
     * @param version Version of the workflow definition.
     */
    public void unregisterWorkflowDef(String name, Integer version) {
        if (name == null) {
            throw new ApplicationException(Code.INVALID_INPUT, "Workflow name cannot be null");
        }

        if (version == null) {
            throw new ApplicationException(Code.INVALID_INPUT, "Version is not valid");
        }

        metadataDAO.removeWorkflowDef(name, version);
    }

    /**
     *
     * @param eventHandler Event handler to be added.
     * Will throw an exception if an event handler already exists with the name
     */
    public void addEventHandler(EventHandler eventHandler) {
        validateEvent(eventHandler);
        metadataDAO.addEventHandler(eventHandler);
    }

    /**
     *
     * @param eventHandler Event handler to be updated.
     */
    public void updateEventHandler(EventHandler eventHandler) {
        validateEvent(eventHandler);
        metadataDAO.updateEventHandler(eventHandler);
    }

    /**
     *
     * @param name Removes the event handler from the system
     */
    public void removeEventHandlerStatus(String name) {
        metadataDAO.removeEventHandlerStatus(name);
    }

    /**
     *
     * @return All the event handlers registered in the system
     */
    public List<EventHandler> getEventHandlers() {
        return metadataDAO.getEventHandlers();
    }

    /**
     *
     * @param event name of the event
     * @param activeOnly if true, returns only the active handlers
     * @return Returns the list of all the event handlers for a given event
     */
    public List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly) {
        return metadataDAO.getEventHandlersForEvent(event, activeOnly);
    }

    private void validateEvent(EventHandler eh) {
        Preconditions.checkNotNull(eh.getName(), "Missing event handler name");
        Preconditions.checkNotNull(eh.getEvent(), "Missing event location");
        Preconditions.checkNotNull(eh.getActions().isEmpty(), "No actions specified.  Please specify at-least one action");
        String event = eh.getEvent();
        EventQueues.getQueue(event, true);
    }
}
