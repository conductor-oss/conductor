/**
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.netflix.conductor.annotations.Service;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.service.utils.ServiceUtils;
import com.netflix.conductor.validations.ValidationContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Optional;

/**
 * @author Viren
 *
 */
@Singleton
@Trace
public class MetadataService {
    private final MetadataDAO metadataDAO;
    private final EventQueues eventQueues;

    @Inject
    public MetadataService(MetadataDAO metadataDAO, EventQueues eventQueues) {
        this.metadataDAO = metadataDAO;
        this.eventQueues = eventQueues;

        ValidationContext.initialize(metadataDAO);
    }

    /**
     * @param taskDefinitions Task Definitions to register
     */
    @Service
    public void registerTaskDef(@NotNull(message = "TaskDefList cannot be empty or null")
                                    @Size(min=1, message = "TaskDefList is empty") List<@Valid TaskDef> taskDefinitions) {
        for (TaskDef taskDefinition : taskDefinitions) {

            taskDefinition.setCreatedBy(WorkflowContext.get().getClientApp());
            taskDefinition.setCreateTime(System.currentTimeMillis());
            taskDefinition.setUpdatedBy(null);
            taskDefinition.setUpdateTime(null);
            metadataDAO.createTaskDef(taskDefinition);
        }
    }

    /**
     * @param taskDefinition Task Definition to be updated
     */
    @Service
    public void updateTaskDef(@NotNull(message = "TaskDef cannot be null") @Valid TaskDef taskDefinition) {
        TaskDef existing = metadataDAO.getTaskDef(taskDefinition.getName());
        if (existing == null) {
            throw new ApplicationException(Code.NOT_FOUND, "No such task by name " + taskDefinition.getName());
        }
        taskDefinition.setUpdatedBy(WorkflowContext.get().getClientApp());
        taskDefinition.setUpdateTime(System.currentTimeMillis());
        metadataDAO.updateTaskDef(taskDefinition);
    }

    /**
     * @param taskType Remove task definition
     */
    @Service
    public void unregisterTaskDef(@NotEmpty String taskType) {
        metadataDAO.removeTaskDef(taskType);
    }

    /**
     * @return List of all the registered tasks
     */
    @Service
    public List<TaskDef> getTaskDefs() {
        return metadataDAO.getAllTaskDefs();
    }

    /**
     * @param taskType Task to retrieve
     * @return Task Definition
     */
    @Service
    public TaskDef getTaskDef(@NotEmpty(message="TaskType cannot be null or empty") String taskType) {
        TaskDef taskDef = metadataDAO.getTaskDef(taskType);
        if (taskDef == null){
            throw new ApplicationException(ApplicationException.Code.NOT_FOUND,
                    String.format("No such taskType found by name: %s", taskType));
        }
        return taskDef;
    }

    /**
     * @param def Workflow definition to be updated
     */
    @Service
    public void updateWorkflowDef(@NotNull(message = "WorkflowDef cannot be null") @Valid WorkflowDef def) {
        Preconditions.checkNotNull(def, "WorkflowDef object cannot be null");
        Preconditions.checkNotNull(def.getName(), "WorkflowDef name cannot be null");
        metadataDAO.update(def);
    }

    /**
     *
     * @param workflowDefList Workflow definitions to be updated.
     */
    @Service
    public void updateWorkflowDef(@NotNull(message = "WorkflowDef list name cannot be null or empty")
                                      @Size(min=1, message = "WorkflowDefList is empty")
                                              List<@NotNull(message = "WorkflowDef cannot be null") @Valid WorkflowDef> workflowDefList) {
       for (WorkflowDef workflowDef : workflowDefList) {
            metadataDAO.update(workflowDef);
        }
    }

    /**
     * @param name    Name of the workflow to retrieve
     * @param version Optional.  Version.  If null, then retrieves the latest
     * @return Workflow definition
     */
    @Service
    public WorkflowDef getWorkflowDef(@NotEmpty(message = "Workflow name cannot be null or empty") String name, Integer version) {
        Optional<WorkflowDef> workflowDef;
        if (version == null) {
            workflowDef = metadataDAO.getLatest(name);
        } else {
            workflowDef =  metadataDAO.get(name, version);
        }

        return workflowDef.orElseThrow(() -> new ApplicationException(Code.NOT_FOUND,
                String.format("No such workflow found by name: %s, version: %d", name, version)));
    }

    /**
     * @param name Name of the workflow to retrieve
     * @return Latest version of the workflow definition
     */
    public Optional<WorkflowDef> getLatestWorkflow(String name) {
        return metadataDAO.getLatest(name);
    }

    public List<WorkflowDef> getWorkflowDefs() {
        return metadataDAO.getAll();
    }

    @Service
    public void registerWorkflowDef(@NotNull(message = "WorkflowDef cannot be null")
                                        @Valid WorkflowDef workflowDef) {
        if (workflowDef.getName().contains(":")) {
            throw new ApplicationException(Code.INVALID_INPUT, "Workflow name cannot contain the following set of characters: ':'");
        }
        if (workflowDef.getSchemaVersion() < 1 || workflowDef.getSchemaVersion() > 2) {
            workflowDef.setSchemaVersion(2);
        }
        metadataDAO.create(workflowDef);
    }

    /**
     *
     * @param name Name of the workflow definition to be removed
     * @param version Version of the workflow definition to be removed
     */
    @Service
    public void unregisterWorkflowDef(@NotEmpty(message = "Workflow name cannot be null or empty") String name,
                                      @NotNull(message = "Version cannot be null") Integer version) {
        metadataDAO.removeWorkflowDef(name, version);
    }

    /**
     * @param eventHandler Event handler to be added.
     *                     Will throw an exception if an event handler already exists with the name
     */
    @Service
    public void addEventHandler(@NotNull(message = "EventHandler cannot be null") @Valid EventHandler eventHandler) {
        eventQueues.getQueue(eventHandler.getEvent());
        metadataDAO.addEventHandler(eventHandler);
    }

    /**
     * @param eventHandler Event handler to be updated.
     */
    @Service
    public void updateEventHandler(@NotNull(message = "EventHandler cannot be null") @Valid EventHandler eventHandler) {
        eventQueues.getQueue(eventHandler.getEvent());
        metadataDAO.updateEventHandler(eventHandler);
    }

    /**
     * @param name Removes the event handler from the system
     */
    public void removeEventHandlerStatus(@NotEmpty(message = "EventName cannot be null or empty") String name) {
        metadataDAO.removeEventHandlerStatus(name);
    }

    /**
     * @return All the event handlers registered in the system
     */
    public List<EventHandler> getEventHandlers() {
        return metadataDAO.getEventHandlers();
    }

    /**
     * @param event      name of the event
     * @param activeOnly if true, returns only the active handlers
     * @return Returns the list of all the event handlers for a given event
     */
    @Service
    public List<EventHandler> getEventHandlersForEvent(@NotEmpty(message = "EventName cannot be null or empty") String event, boolean activeOnly) {
        return metadataDAO.getEventHandlersForEvent(event, activeOnly);
    }

}
