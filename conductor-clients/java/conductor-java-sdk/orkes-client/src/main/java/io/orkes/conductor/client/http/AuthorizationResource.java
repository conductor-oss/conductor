/*
 * Copyright 2022 Orkes, Inc.
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
import java.util.Map;
import java.util.Objects;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.ConductorClientRequest;
import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.client.http.ConductorClientResponse;

import io.orkes.conductor.client.model.AuthorizationRequest;
import io.orkes.conductor.client.model.Subject;

import com.fasterxml.jackson.core.type.TypeReference;

class AuthorizationResource {
    
    private final ConductorClient client;

    AuthorizationResource(ConductorClient client) {
        this.client = client;
    }

    Map<String, List<Subject>> getPermissions(String type, String id) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/auth/authorization/{type}/{id}")
                .addPathParam("type", type)
                .addPathParam("id", id)
                .build();

        ConductorClientResponse<Map<String, List<Subject>>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    void grantPermissions(AuthorizationRequest body) {
        Objects.requireNonNull(body, "AuthorizationRequest cannot be null");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/auth/authorization")
                .body(body)
                .build();

        client.execute(request);
    }

    void removePermissions(AuthorizationRequest body) {
        Objects.requireNonNull(body, "AuthorizationRequest cannot be null");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/auth/authorization")
                .body(body)
                .build();

        client.execute(request);
    }
}
