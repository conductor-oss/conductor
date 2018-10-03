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
package com.netflix.conductor.dao;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import java.util.List;
import java.util.Optional;

/**
 * @author Viren
 * Data access layer for the workflow metadata - task definitions and workflow definitions
 */
public interface MetadataDAO {

    /**
     * @param taskDef task definition to be created
     * @return name of the task definition
     */
    String createTaskDef(TaskDef taskDef);

    /**
     * @param taskDef task definition to be updated.
     * @return name of the task definition
     */
    String updateTaskDef(TaskDef taskDef);

    /**
     * @param name Name of the task
     * @return Task Definition
     */
    TaskDef getTaskDef(String name);

    /**
     * @return All the task definitions
     */
    List<TaskDef> getAllTaskDefs();

    /**
     * @param name Name of the task
     */
    void removeTaskDef(String name);

    /**
     * @param def workflow definition
     */
    void create(WorkflowDef def);

    /**
     * @param def workflow definition
     */
    void update(WorkflowDef def);

    /**
     * @param name Name of the workflow
     * @return Workflow Definition
     */
    Optional<WorkflowDef> getLatest(String name);

    /**
     * @param name Name of the workflow
     * @param version version
     * @return workflow definition
     */
    Optional<WorkflowDef> get(String name, int version);

    /**
     * @param name Name of the workflow definition to be removed
     * @param version Version of the workflow definition to be removed
     */
    void removeWorkflowDef(String name, Integer version);

    /**
     * @return Names of all the workflows
     */
    List<String> findAll();

    /**
     * @return List of all the workflow definitions
     */
    List<WorkflowDef> getAll();

    /**
     * @param name name of the workflow
     * @return List of all the workflow definitions
     */
    List<WorkflowDef> getAllVersions(String name);

    /**
     * @param eventHandler Event handler to be added. Will throw an exception if an event handler already exists with
     *                     the name
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
