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

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.ConductorClientRequest;
import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.client.http.ConductorClientResponse;

import io.orkes.conductor.client.model.AccessKeyResponse;
import io.orkes.conductor.client.model.ConductorApplication;
import io.orkes.conductor.client.model.CreateAccessKeyResponse;
import io.orkes.conductor.client.model.CreateOrUpdateApplicationRequest;
import io.orkes.conductor.client.model.TagObject;

import com.fasterxml.jackson.core.type.TypeReference;

class ApplicationResource {

    private final ConductorClient client;

    ApplicationResource(ConductorClient client) {
        this.client = client;
    }

    void addRoleToApplicationUser(String applicationId, String role) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/applications/{applicationId}/roles/{role}")
                .addPathParam("applicationId", applicationId)
                .addPathParam("role", role)
                .build();
        client.execute(request);
    }

    CreateAccessKeyResponse createAccessKey(String applicationId) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/applications/{applicationId}/accessKeys")
                .addPathParam("applicationId", applicationId)
                .build();
        ConductorClientResponse<CreateAccessKeyResponse> response = client.execute(request, new TypeReference<>() {
        });

        return response.getData();
    }

    ConductorApplication upsertApplication(CreateOrUpdateApplicationRequest body, String id) {
        Objects.requireNonNull(body, "CreateOrUpdateApplicationRequest cannot be null");
        ConductorClientRequest.Builder builder = ConductorClientRequest.builder()
                .body(body);

        if (id == null) {
            builder.method(Method.POST).path("/applications");
        } else {
            builder.method(Method.PUT)
                    .path("/applications/{id}")
                    .addPathParam("id", id);
        }

        ConductorClientResponse<ConductorApplication> response = client.execute(builder.build(), new TypeReference<>() {
        });

        return response.getData();
    }

    void deleteAccessKey(String applicationId, String keyId) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/applications/{applicationId}/accessKeys/{keyId}")
                .addPathParam("applicationId", applicationId)
                .addPathParam("keyId", keyId)
                .build();
        client.execute(request);
    }

    void deleteApplication(String applicationId) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/applications/{applicationId}")
                .addPathParam("applicationId", applicationId)
                .build();
        client.execute(request);
    }

    List<AccessKeyResponse> getAccessKeys(String applicationId) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/applications/{applicationId}/accessKeys")
                .addPathParam("applicationId", applicationId)
                .build();
        ConductorClientResponse<List<AccessKeyResponse>> response = client.execute(request, new TypeReference<>() {
        });

        return response.getData();
    }

    ConductorApplication getApplication(String applicationId) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/applications/{applicationId}")
                .addPathParam("applicationId", applicationId)
                .build();
        ConductorClientResponse<ConductorApplication> response = client.execute(request, new TypeReference<>() {
        });

        return response.getData();
    }

    List<ConductorApplication> listApplications() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/applications")
                .build();
        ConductorClientResponse<List<ConductorApplication>> response = client.execute(request, new TypeReference<>() {
        });

        return response.getData();
    }

    void removeRoleFromApplicationUser(String applicationId, String role) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/applications/{applicationId}/roles/{role}")
                .addPathParam("applicationId", applicationId)
                .addPathParam("role", role)
                .build();
        client.execute(request);
    }

    AccessKeyResponse toggleAccessKeyStatus(String applicationId, String keyId) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/applications/{applicationId}/accessKeys/{keyId}/status")
                .addPathParam("applicationId", applicationId)
                .addPathParam("keyId", keyId)
                .build();

        ConductorClientResponse<AccessKeyResponse> response = client.execute(request, new TypeReference<>() {
        });

        return response.getData();
    }

    void putTags(List<TagObject> body, String applicationId) {
        Objects.requireNonNull(body, "List<TagObject> cannot be null");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path("/applications/{id}/tags")
                .addPathParam("applicationId", applicationId)
                .body(body)
                .build();

        client.execute(request);
    }

    List<TagObject> getTags(String applicationId) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/applications/{id}/tags")
                .addPathParam("applicationId", applicationId)
                .build();

        ConductorClientResponse<List<TagObject>> response = client.execute(request, new TypeReference<>() {
        });

        return response.getData();
    }

    void deleteTags(List<TagObject> body, String applicationId) {
        Objects.requireNonNull(body, "List<TagObject> cannot be null");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/applications/{id}/tags")
                .addPathParam("applicationId", applicationId)
                .body(body)
                .build();

        client.execute(request);
    }
}
