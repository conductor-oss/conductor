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
package com.netflix.conductor.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.netflix.conductor.common.constraints.OwnerEmailMandatoryConstraint;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDefSummary;
import com.netflix.conductor.common.model.BulkResponse;
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.validations.ValidationContext;

@Service
public class MetadataServiceImpl implements MetadataService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataServiceImpl.class);
    private final MetadataDAO metadataDAO;
    private final EventHandlerDAO eventHandlerDAO;

    public MetadataServiceImpl(
            MetadataDAO metadataDAO,
            EventHandlerDAO eventHandlerDAO,
            ConductorProperties properties) {
        this.metadataDAO = metadataDAO;
        this.eventHandlerDAO = eventHandlerDAO;

        ValidationContext.initialize(metadataDAO);
        OwnerEmailMandatoryConstraint.WorkflowTaskValidValidator.setOwnerEmailMandatory(
                properties.isOwnerEmailMandatory());
    }

    /**
     * @param taskDefinitions Task Definitions to register
     */
    public void registerTaskDef(List<TaskDef> taskDefinitions) {
        for (TaskDef taskDefinition : taskDefinitions) {
            taskDefinition.setCreatedBy(WorkflowContext.get().getClientApp());
            taskDefinition.setCreateTime(System.currentTimeMillis());
            taskDefinition.setUpdatedBy(null);
            taskDefinition.setUpdateTime(null);

            metadataDAO.createTaskDef(taskDefinition);
        }
    }

    @Override
    public void validateWorkflowDef(WorkflowDef workflowDef) {
        // do nothing, WorkflowDef is annotated with @Valid and calling this method will validate it
    }

    /**
     * @param taskDefinition Task Definition to be updated
     */
    public void updateTaskDef(TaskDef taskDefinition) {
        TaskDef existing = metadataDAO.getTaskDef(taskDefinition.getName());
        if (existing == null) {
            throw new NotFoundException("No such task by name %s", taskDefinition.getName());
        }
        taskDefinition.setUpdatedBy(WorkflowContext.get().getClientApp());
        taskDefinition.setUpdateTime(System.currentTimeMillis());
        metadataDAO.updateTaskDef(taskDefinition);
    }

    /**
     * @param taskType Remove task definition
     */
    public void unregisterTaskDef(String taskType) {
        metadataDAO.removeTaskDef(taskType);
    }

    /**
     * @return List of all the registered tasks
     */
    public List<TaskDef> getTaskDefs() {
        return metadataDAO.getAllTaskDefs();
    }

    /**
     * @param taskType Task to retrieve
     * @return Task Definition
     */
    public TaskDef getTaskDef(String taskType) {
        TaskDef taskDef = metadataDAO.getTaskDef(taskType);
        if (taskDef == null) {
            throw new NotFoundException("No such taskType found by name: %s", taskType);
        }
        return taskDef;
    }

    /**
     * @param workflowDef Workflow definition to be updated
     */
    public void updateWorkflowDef(WorkflowDef workflowDef) {
        workflowDef.setUpdateTime(System.currentTimeMillis());
        metadataDAO.updateWorkflowDef(workflowDef);
    }

    /**
     * @param workflowDefList Workflow definitions to be updated.
     */
    public BulkResponse updateWorkflowDef(List<WorkflowDef> workflowDefList) {
        BulkResponse bulkResponse = new BulkResponse();
        for (WorkflowDef workflowDef : workflowDefList) {
            try {
                updateWorkflowDef(workflowDef);
                bulkResponse.appendSuccessResponse(workflowDef.getName());
            } catch (Exception e) {
                LOGGER.error("bulk update workflow def failed, name {} ", workflowDef.getName(), e);
                bulkResponse.appendFailedResponse(workflowDef.getName(), e.getMessage());
            }
        }
        return bulkResponse;
    }

    /**
     * @param name Name of the workflow to retrieve
     * @param version Optional. Version. If null, then retrieves the latest
     * @return Workflow definition
     */
    public WorkflowDef getWorkflowDef(String name, Integer version) {
        Optional<WorkflowDef> workflowDef;
        if (version == null) {
            workflowDef = metadataDAO.getLatestWorkflowDef(name);
        } else {
            workflowDef = metadataDAO.getWorkflowDef(name, version);
        }

        return workflowDef.orElseThrow(
                () ->
                        new NotFoundException(
                                "No such workflow found by name: %s, version: %d", name, version));
    }

    /**
     * @param name Name of the workflow to retrieve
     * @return Latest version of the workflow definition
     */
    public Optional<WorkflowDef> getLatestWorkflow(String name) {
        return metadataDAO.getLatestWorkflowDef(name);
    }

    public List<WorkflowDef> getWorkflowDefs() {
        return metadataDAO.getAllWorkflowDefs();
    }

    public void registerWorkflowDef(WorkflowDef workflowDef) {
        workflowDef.setCreateTime(System.currentTimeMillis());
        metadataDAO.createWorkflowDef(workflowDef);
    }

    /**
     * @param name Name of the workflow definition to be removed
     * @param version Version of the workflow definition to be removed
     */
    public void unregisterWorkflowDef(String name, Integer version) {
        metadataDAO.removeWorkflowDef(name, version);
    }

    /**
     * @param eventHandler Event handler to be added. Will throw an exception if an event handler
     *     already exists with the name
     */
    public void addEventHandler(EventHandler eventHandler) {
        eventHandlerDAO.addEventHandler(eventHandler);
    }

    /**
     * @param eventHandler Event handler to be updated.
     */
    public void updateEventHandler(EventHandler eventHandler) {
        eventHandlerDAO.updateEventHandler(eventHandler);
    }

    /**
     * @param name Removes the event handler from the system
     */
    public void removeEventHandlerStatus(String name) {
        eventHandlerDAO.removeEventHandler(name);
    }

    /**
     * @return All the event handlers registered in the system
     */
    public List<EventHandler> getAllEventHandlers() {
        return eventHandlerDAO.getAllEventHandlers();
    }

    /**
     * @param event name of the event
     * @param activeOnly if true, returns only the active handlers
     * @return Returns the list of all the event handlers for a given event
     */
    public List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly) {
        return eventHandlerDAO.getEventHandlersForEvent(event, activeOnly);
    }

    @Override
    public List<WorkflowDef> getWorkflowDefsLatestVersions() {
        return metadataDAO.getAllWorkflowDefsLatestVersions();
    }

    public Map<String, ? extends Iterable<WorkflowDefSummary>> getWorkflowNamesAndVersions() {
        List<WorkflowDef> workflowDefs = metadataDAO.getAllWorkflowDefs();

        Map<String, TreeSet<WorkflowDefSummary>> retval = new HashMap<>();
        for (WorkflowDef def : workflowDefs) {
            String workflowName = def.getName();
            WorkflowDefSummary summary = fromWorkflowDef(def);

            retval.putIfAbsent(workflowName, new TreeSet<WorkflowDefSummary>());

            TreeSet<WorkflowDefSummary> versions = retval.get(workflowName);
            versions.add(summary);
        }

        return retval;
    }

    private WorkflowDefSummary fromWorkflowDef(WorkflowDef def) {
        WorkflowDefSummary summary = new WorkflowDefSummary();
        summary.setName(def.getName());
        summary.setVersion(def.getVersion());
        summary.setCreateTime(def.getCreateTime());

        return summary;
    }
}
