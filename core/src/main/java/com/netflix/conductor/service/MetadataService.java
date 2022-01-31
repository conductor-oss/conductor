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

import java.util.List;
import java.util.Optional;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

@Validated
public interface MetadataService {

    /** @param taskDefinitions Task Definitions to register */
    void registerTaskDef(
            @NotNull(message = "TaskDefList cannot be empty or null")
                    @Size(min = 1, message = "TaskDefList is empty")
                    List<@Valid TaskDef> taskDefinitions);

    /** @param taskDefinition Task Definition to be updated */
    void updateTaskDef(@NotNull(message = "TaskDef cannot be null") @Valid TaskDef taskDefinition);

    /** @param taskType Remove task definition */
    void unregisterTaskDef(@NotEmpty(message = "TaskName cannot be null or empty") String taskType);

    /** @return List of all the registered tasks */
    List<TaskDef> getTaskDefs();

    /**
     * @param taskType Task to retrieve
     * @return Task Definition
     */
    TaskDef getTaskDef(@NotEmpty(message = "TaskType cannot be null or empty") String taskType);

    /** @param def Workflow definition to be updated */
    void updateWorkflowDef(@NotNull(message = "WorkflowDef cannot be null") @Valid WorkflowDef def);

    /** @param workflowDefList Workflow definitions to be updated. */
    void updateWorkflowDef(
            @NotNull(message = "WorkflowDef list name cannot be null or empty")
                    @Size(min = 1, message = "WorkflowDefList is empty")
                    List<@NotNull(message = "WorkflowDef cannot be null") @Valid WorkflowDef>
                            workflowDefList);

    /**
     * @param name Name of the workflow to retrieve
     * @param version Optional. Version. If null, then retrieves the latest
     * @return Workflow definition
     */
    WorkflowDef getWorkflowDef(
            @NotEmpty(message = "Workflow name cannot be null or empty") String name,
            Integer version);

    /**
     * @param name Name of the workflow to retrieve
     * @return Latest version of the workflow definition
     */
    Optional<WorkflowDef> getLatestWorkflow(
            @NotEmpty(message = "Workflow name cannot be null or empty") String name);

    List<WorkflowDef> getWorkflowDefs();

    void registerWorkflowDef(
            @NotNull(message = "WorkflowDef cannot be null") @Valid WorkflowDef workflowDef);

    /**
     * @param name Name of the workflow definition to be removed
     * @param version Version of the workflow definition to be removed
     */
    void unregisterWorkflowDef(
            @NotEmpty(message = "Workflow name cannot be null or empty") String name,
            @NotNull(message = "Version cannot be null") Integer version);

    /**
     * @param eventHandler Event handler to be added. Will throw an exception if an event handler
     *     already exists with the name
     */
    void addEventHandler(
            @NotNull(message = "EventHandler cannot be null") @Valid EventHandler eventHandler);

    /** @param eventHandler Event handler to be updated. */
    void updateEventHandler(
            @NotNull(message = "EventHandler cannot be null") @Valid EventHandler eventHandler);

    /** @param name Removes the event handler from the system */
    void removeEventHandlerStatus(
            @NotEmpty(message = "EventName cannot be null or empty") String name);

    /** @return All the event handlers registered in the system */
    List<EventHandler> getAllEventHandlers();

    /**
     * @param event name of the event
     * @param activeOnly if true, returns only the active handlers
     * @return Returns the list of all the event handlers for a given event
     */
    List<EventHandler> getEventHandlersForEvent(
            @NotEmpty(message = "EventName cannot be null or empty") String event,
            boolean activeOnly);
}
