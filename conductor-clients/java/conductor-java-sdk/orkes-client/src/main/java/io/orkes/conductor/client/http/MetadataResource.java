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
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.ConductorClientRequest;
import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.client.http.ConductorClientResponse;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import com.fasterxml.jackson.core.type.TypeReference;

public class MetadataResource {

    private final ConductorClient client;

    public MetadataResource(ConductorClient client) {
        this.client = client;
    }

    public void registerWorkflowDef(WorkflowDef workflowDef, Boolean overwrite) {
        Objects.requireNonNull(workflowDef, "WorkflowDef cannot be null");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/metadata/workflow")
                .addQueryParam("overwrite", overwrite)
                .body(workflowDef)
                .build();

        client.execute(request);
    }

    public WorkflowDef getWorkflow(String name, Integer version, Boolean metadata) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/metadata/workflow/{name}")
                .addPathParam("name", name)
                .addQueryParam("version", version)
                .addQueryParam("metadata", metadata)
                .build();

        ConductorClientResponse<WorkflowDef> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public List<WorkflowDef> getAllWorkflows(
            String access, Boolean metadata, String tagKey, String tagValue) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/metadata/workflow")
                .addQueryParam("access", access)
                .addQueryParam("metadata", metadata)
                .addQueryParam("tagKey", tagKey)
                .addQueryParam("tagValue", tagValue)
                .build();

        ConductorClientResponse<List<WorkflowDef>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public TaskDef getTaskDef(String taskType, Boolean metadata) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/metadata/taskdefs/{taskType}")
                .addPathParam("taskType", taskType)
                .addQueryParam("metadata", metadata)
                .build();

        ConductorClientResponse<TaskDef> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public List<TaskDef> getTaskDefs(String access, Boolean metadata, String tagKey, String tagValue) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/metadata/taskdefs")
                .addQueryParam("access", access)
                .addQueryParam("metadata", metadata)
                .addQueryParam("tagKey", tagKey)
                .addQueryParam("tagValue", tagValue)
                .build();

        ConductorClientResponse<List<TaskDef>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public void registerTaskDef(List<TaskDef> taskDefs) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/metadata/taskdefs")
                .body(taskDefs)
                .build();

        client.execute(request);
    }

    public void unregisterTaskDef(String taskType) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/metadata/taskdefs/{taskType}")
                .addPathParam("taskType", taskType)
                .build();

        client.execute(request);
    }

    public void unregisterWorkflowDef(String name, Integer version) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("name cannot be null or blank");
        }

        Objects.requireNonNull(version, "version cannot be null");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/metadata/workflow/{name}/{version}")
                .addPathParam("name", name)
                .addPathParam("version", Integer.toString(version))
                .build();

        client.execute(request);
    }

    public void updateWorkflows(List<WorkflowDef> workflowDefs, Boolean overwrite) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path("/metadata/workflow")
                .addQueryParam("overwrite", overwrite)
                .body(workflowDefs)
                .build();

        client.execute(request);
    }

    public void updateTaskDef(TaskDef taskDef) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path("/metadata/taskdefs")
                .body(taskDef)
                .build();

        client.execute(request);
    }
}
