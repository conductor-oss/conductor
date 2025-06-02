/*
 * Copyright 2022 Conductor Authors.
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
package io.orkes.conductor.client.http;

import java.util.List;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import io.orkes.conductor.client.model.TagObject;
import io.orkes.conductor.client.model.TagString;


public class OrkesMetadataClient {

    private final MetadataResource metadataResource;
    private final TagsResource tagsResource;

    private final MetadataClient metadataClient;

    public OrkesMetadataClient(ConductorClient client) {
        this.metadataResource = new MetadataResource(client);
        this.tagsResource = new TagsResource(client);
        this.metadataClient = new MetadataClient(client);
    }

    public void registerWorkflowDef(WorkflowDef workflowDef) {
        metadataResource.registerWorkflowDef(workflowDef, true);
    }

    public void registerWorkflowDef(WorkflowDef workflowDef, boolean overwrite) {
        metadataResource.registerWorkflowDef(workflowDef, overwrite);
    }

    public void updateWorkflowDefs(List<WorkflowDef> workflowDefs) {
        metadataResource.updateWorkflows(workflowDefs, true);
    }

    public void updateWorkflowDefs(List<WorkflowDef> workflowDefs, boolean overwrite) {
        metadataResource.updateWorkflows(workflowDefs, overwrite);
    }

    public WorkflowDef getWorkflowDef(String name, Integer version) {
        return metadataResource.getWorkflow(name, version, false);
    }

    public WorkflowDef getWorkflowDefWithMetadata(String name, Integer version) {
        return metadataResource.getWorkflow(name, version, true);
    }

    public void unregisterWorkflowDef(String name, Integer version) {
        metadataClient.unregisterWorkflowDef(name, version);
    }

    public List<TaskDef> getAllTaskDefs() {
        return metadataClient.getAllTaskDefs();
    }

    public void registerTaskDefs(List<TaskDef> taskDefs) {
        metadataResource.registerTaskDef(taskDefs);
    }

    public void updateTaskDef(TaskDef taskDef) {
        metadataClient.updateTaskDef(taskDef);
    }

    public TaskDef getTaskDef(String taskType) {
        return metadataResource.getTaskDef(taskType, true);
    }

    public void unregisterTaskDef(String taskType) {
        metadataClient.unregisterTaskDef(taskType);
    }

    public void addTaskTag(TagObject tagObject, String taskName) {
        tagsResource.addTaskTag(tagObject, taskName);
    }

    public void addWorkflowTag(TagObject tagObject, String name) {
        tagsResource.addWorkflowTag(tagObject, name);
    }

    public void deleteTaskTag(TagString tagString, String taskName) {
        tagsResource.deleteTaskTag(tagString, taskName);
    }

    public void deleteWorkflowTag(TagObject tagObject, String name) {
        tagsResource.deleteWorkflowTag(tagObject, name);
    }

    public List<TagObject> getTags() {
        return tagsResource.getTags();
    }

    public List<TagObject> getTaskTags(String taskName) {
        return tagsResource.getTaskTags(taskName);
    }

    public List<TagObject> getWorkflowTags(String name) {
        return tagsResource.getWorkflowTags(name);
    }

    public void setTaskTags(List<TagObject> tagObjects, String taskName) {
        tagsResource.setTaskTags(tagObjects, taskName);
    }

    public void setWorkflowTags(List<TagObject> tagObjects, String name) {
        tagsResource.setWorkflowTags(tagObjects, name);
    }

    public List<WorkflowDef> getAllWorkflowDefs() {
        return metadataResource.getAllWorkflows(null, false, null, null);
    }

}
