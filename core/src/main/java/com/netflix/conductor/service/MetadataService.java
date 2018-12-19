/**
 * Copyright 2018 Netflix, Inc.
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

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import java.util.List;
import java.util.Optional;


public interface MetadataService {

    /**
     * @param taskDefinitions Task Definitions to register
     */
    void registerTaskDef(List<TaskDef> taskDefinitions);

    /**
     * @param taskDefinition Task Definition to be updated
     */
    void updateTaskDef(TaskDef taskDefinition);

    /**
     * @param taskType Remove task definition
     */
    void unregisterTaskDef(String taskType);

    /**
     * @return List of all the registered tasks
     */
    List<TaskDef> getTaskDefs();

    /**
     * @param taskType Task to retrieve
     * @return Task Definition
     */
    TaskDef getTaskDef(String taskType);

    /**
     * @param def Workflow definition to be updated
     */
    void updateWorkflowDef(WorkflowDef def);

    /**
     *
     * @param workflowDefList Workflow definitions to be updated.
     */
    void updateWorkflowDef(List<WorkflowDef> workflowDefList);

    /**
     * @param name    Name of the workflow to retrieve
     * @param version Optional.  Version.  If null, then retrieves the latest
     * @return Workflow definition
     */
    WorkflowDef getWorkflowDef(String name, Integer version);

    /**
     * @param name Name of the workflow to retrieve
     * @return Latest version of the workflow definition
     */
    Optional<WorkflowDef> getLatestWorkflow(String name);

    List<WorkflowDef> getWorkflowDefs();

    void registerWorkflowDef(WorkflowDef workflowDef);

    /**
     *
     * @param name Name of the workflow definition to be removed
     * @param version Version of the workflow definition to be removed
     */
    void unregisterWorkflowDef(String name, Integer version);
    /**
     * @param eventHandler Event handler to be added.
     *                     Will throw an exception if an event handler already exists with the name
     */
    void addEventHandler(EventHandler eventHandler);

    /**
     * @param eventHandler Event handler to be updated.
     */
    void updateEventHandler(EventHandler eventHandler);
    /**
     * @param name Removes the event handler from the system
     */
    void removeEventHandlerStatus(String name);
    /**
     * @return All the event handlers registered in the system
     */
    List<EventHandler> getEventHandlers();

    /**
     * @param event      name of the event
     * @param activeOnly if true, returns only the active handlers
     * @return Returns the list of all the event handlers for a given event
     */
    List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly);
}
