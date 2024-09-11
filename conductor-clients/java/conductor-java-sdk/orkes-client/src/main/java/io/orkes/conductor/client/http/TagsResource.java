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
import com.netflix.conductor.client.http.ConductorClientRequest;
import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.client.http.ConductorClientResponse;

import io.orkes.conductor.client.model.TagObject;
import io.orkes.conductor.client.model.TagString;

import com.fasterxml.jackson.core.type.TypeReference;


public class TagsResource {

    private final ConductorClient client;

    public TagsResource(ConductorClient client) {
        this.client = client;
    }

    public void addTaskTag(TagObject tagObject, String taskName) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/metadata/task/{taskName}/tags")
                .addPathParam("taskName", taskName)
                .body(tagObject)
                .build();

        client.execute(request);
    }

    public void addWorkflowTag(TagObject tagObject, String workflow) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/metadata/workflow/{workflow}/tags")
                .addPathParam("workflow", workflow)
                .body(tagObject)
                .build();

        client.execute(request);
    }

    public void deleteTaskTag(TagString tagString, String taskName) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/metadata/task/{taskName}/tags")
                .addPathParam("taskName", taskName)
                .body(tagString)
                .build();

        client.execute(request);
    }

    public void deleteWorkflowTag(TagObject tagObject, String workflow) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/metadata/workflow/{workflow}/tags")
                .addPathParam("workflow", workflow)
                .body(tagObject)
                .build();

        client.execute(request);
    }

    public List<TagObject> getTags() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/metadata/tags")
                .build();

        ConductorClientResponse<List<TagObject>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public List<TagObject> getTaskTags(String taskName) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/metadata/task/{taskName}/tags")
                .addPathParam("taskName", taskName)
                .build();

        ConductorClientResponse<List<TagObject>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public List<TagObject> getWorkflowTags(String name) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/metadata/workflow/{name}/tags")
                .addPathParam("name", name)
                .build();

        ConductorClientResponse<List<TagObject>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public void setTaskTags(List<TagObject> tagObjects, String taskName) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path("/metadata/task/{taskName}/tags")
                .addPathParam("taskName", taskName)
                .body(tagObjects)
                .build();

        client.execute(request);
    }

    public void setWorkflowTags(List<TagObject> tagObjects, String workflow) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/metadata/workflow/{workflow}/tags")
                .addPathParam("workflow", workflow)
                .body(tagObjects)
                .build();

        client.execute(request);
    }
}
