/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.sqlite.dao.metadata;

import java.util.List;
import java.util.Optional;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.dao.MetadataDAO;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SqliteMetadataDAO implements MetadataDAO, EventHandlerDAO {

    private final SqliteTaskMetadataDAO taskMetadataDAO;
    private final SqliteWorkflowMetadataDAO workflowMetadataDAO;
    private final SqliteEventHandlerMetadataDAO eventHandlerMetadataDAO;

    @Override
    public TaskDef createTaskDef(TaskDef taskDef) {
        return taskMetadataDAO.createTaskDef(taskDef);
    }

    @Override
    public TaskDef updateTaskDef(TaskDef taskDef) {
        return taskMetadataDAO.updateTaskDef(taskDef);
    }

    @Override
    public TaskDef getTaskDef(String name) {
        return taskMetadataDAO.getTaskDef(name);
    }

    @Override
    public List<TaskDef> getAllTaskDefs() {
        return taskMetadataDAO.getAllTaskDefs();
    }

    @Override
    public void removeTaskDef(String name) {
        taskMetadataDAO.removeTaskDef(name);
    }

    @Override
    public void createWorkflowDef(WorkflowDef def) {
        workflowMetadataDAO.createWorkflowDef(def);
    }

    @Override
    public void updateWorkflowDef(WorkflowDef def) {
        workflowMetadataDAO.updateWorkflowDef(def);
    }

    @Override
    public Optional<WorkflowDef> getLatestWorkflowDef(String name) {
        return workflowMetadataDAO.getLatestWorkflowDef(name);
    }

    @Override
    public Optional<WorkflowDef> getWorkflowDef(String name, int version) {
        return workflowMetadataDAO.getWorkflowDef(name, version);
    }

    @Override
    public void removeWorkflowDef(String name, Integer version) {
        workflowMetadataDAO.removeWorkflowDef(name, version);
    }

    @Override
    public List<WorkflowDef> getAllWorkflowDefs() {
        return workflowMetadataDAO.getAllWorkflowDefs();
    }

    @Override
    public List<WorkflowDef> getAllWorkflowDefsLatestVersions() {
        return workflowMetadataDAO.getAllWorkflowDefsLatestVersions();
    }

    @Override
    public void addEventHandler(EventHandler eventHandler) {
        eventHandlerMetadataDAO.addEventHandler(eventHandler);
    }

    @Override
    public void updateEventHandler(EventHandler eventHandler) {
        eventHandlerMetadataDAO.updateEventHandler(eventHandler);
    }

    @Override
    public void removeEventHandler(String name) {
        eventHandlerMetadataDAO.removeEventHandler(name);
    }

    @Override
    public List<EventHandler> getAllEventHandlers() {
        return eventHandlerMetadataDAO.getAllEventHandlers();
    }

    @Override
    public List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly) {
        return eventHandlerMetadataDAO.getEventHandlersForEvent(event, activeOnly);
    }

    public List<String> findAll() {
        return workflowMetadataDAO.findAll();
    }

    public List<WorkflowDef> getAllLatest() {
        return workflowMetadataDAO.getAllLatest();
    }

    public List<WorkflowDef> getAllVersions(String name) {
        return workflowMetadataDAO.getAllVersions(name);
    }
}
