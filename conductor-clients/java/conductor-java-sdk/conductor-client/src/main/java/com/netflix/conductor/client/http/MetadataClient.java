/*
 * Copyright 2020 Orkes, Inc.
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
package com.netflix.conductor.client.http;

import java.util.List;

import org.apache.commons.lang3.Validate;

import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import com.fasterxml.jackson.core.type.TypeReference;


public final class MetadataClient {

    private ConductorClient client;

    /** Creates a default metadata client */
    public MetadataClient() {
    }

    public MetadataClient(ConductorClient client) {
        this.client = client;
    }

    /**
     * Kept only for backwards compatibility
     *
     * @param rootUri basePath for the ApiClient
     */
    @Deprecated
    public void setRootURI(String rootUri) {
        if (client != null) {
            client.shutdown();
        }
        client = new ConductorClient(rootUri);
    }

    /**
     * Register a workflow definition with the server
     *
     * @param workflowDef the workflow definition
     */
    public void registerWorkflowDef(WorkflowDef workflowDef) {
        Validate.notNull(workflowDef, "WorkflowDef cannot be null");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/metadata/workflow")
                .body(workflowDef)
                .build();

        client.execute(request);
    }

    /**
     * Updates a list of existing workflow definitions
     *
     * @param workflowDefs List of workflow definitions to be updated
     */
    public void updateWorkflowDefs(List<WorkflowDef> workflowDefs) {
        Validate.notEmpty(workflowDefs, "Workflow definitions cannot be null or empty");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path("/metadata/workflow")
                .body(workflowDefs)
                .build();

        client.execute(request);
    }

    /**
     * Retrieve the workflow definition
     *
     * @param name the name of the workflow
     * @param version the version of the workflow def
     * @return Workflow definition for the given workflow and version
     */
    public WorkflowDef getWorkflowDef(String name, Integer version) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/metadata/workflow/{name}")
                .addPathParam("name", name)
                .addQueryParam("version", version)
                .build();

        ConductorClientResponse<WorkflowDef> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public List<WorkflowDef> getAllWorkflowsWithLatestVersions() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("metadata/workflow/latest-versions")
                .build();

        ConductorClientResponse<List<WorkflowDef>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    /**
     * Removes the workflow definition of a workflow from the conductor server. It does not remove
     * associated workflows. Use with caution.
     *
     * @param name Name of the workflow to be unregistered.
     * @param version Version of the workflow definition to be unregistered.
     */
    public void unregisterWorkflowDef(String name, Integer version) {
        Validate.notBlank(name, "Name cannot be blank");
        Validate.notNull(version, "version cannot be null");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/metadata/workflow/{name}/{version}")
                .addPathParam("name", name)
                .addPathParam("version", Integer.toString(version))
                .build();

        client.execute(request);
    }

    // Task Metadata Operations

    /**
     * Registers a list of task types with the conductor server
     *
     * @param taskDefs List of task types to be registered.
     */
    public void registerTaskDefs(List<TaskDef> taskDefs) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/metadata/taskdefs")
                .body(taskDefs)
                .build();

        client.execute(request);
    }

    /**
     * Updates an existing task definition
     *
     * @param taskDef the task definition to be updated
     */
    public void updateTaskDef(TaskDef taskDef) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path("/metadata/taskdefs")
                .body(taskDef)
                .build();

        client.execute(request);
    }

    /**
     * Retrieve the task definition of a given task type
     *
     * @param taskType type of task for which to retrieve the definition
     * @return Task Definition for the given task type
     */
    public TaskDef getTaskDef(String taskType) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/metadata/taskdefs/{taskType}")
                .addPathParam("taskType", taskType)
                .build();

        ConductorClientResponse<TaskDef> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    /**
     * Removes the task definition of a task type from the conductor server. Use with caution.
     *
     * @param taskType Task type to be unregistered.
     */
    public void unregisterTaskDef(String taskType) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/metadata/taskdefs/{taskType}")
                .addPathParam("taskType", taskType)
                .build();

        client.execute(request);
    }

    /**
     *
     * @return All the registered task definitions
     */
    public List<TaskDef> getAllTaskDefs() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/metadata/taskdefs")
                .build();

        ConductorClientResponse<List<TaskDef>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }
}
