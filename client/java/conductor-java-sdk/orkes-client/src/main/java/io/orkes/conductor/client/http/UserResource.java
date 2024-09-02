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

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.ConductorClientRequest;
import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.client.http.ConductorClientResponse;

import io.orkes.conductor.client.model.ConductorUser;
import io.orkes.conductor.client.model.GrantedAccessResponse;
import io.orkes.conductor.client.model.UpsertUserRequest;

import com.fasterxml.jackson.core.type.TypeReference;


class UserResource {
    private final ConductorClient client;

    public UserResource(ConductorClient client) {
        this.client = client;
    }

    public void deleteUser(String id) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/users/{id}")
                .addPathParam("id", id)
                .build();

        client.execute(request);
    }

    public GrantedAccessResponse getGrantedPermissions(String userId) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/users/{userId}/permissions")
                .addPathParam("userId", userId)
                .build();

        ConductorClientResponse<GrantedAccessResponse> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public ConductorUser getUser(String id) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/users/{id}")
                .addPathParam("id", id)
                .build();

        ConductorClientResponse<ConductorUser> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }


    public List<ConductorUser> listUsers(Boolean apps) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/users")
                .addQueryParam("apps", apps)
                .build();

        ConductorClientResponse<List<ConductorUser>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public void sendInviteEmail(String email) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/users/{email}/sendInviteEmail")
                .addPathParam("email", email)
                .build();

        client.execute(request);
    }

    public ConductorUser upsertUser(UpsertUserRequest upsertUserRequest, String id) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path("/users/{id}")
                .addPathParam("id", id)
                .body(upsertUserRequest)
                .build();

        ConductorClientResponse<ConductorUser> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }
}
