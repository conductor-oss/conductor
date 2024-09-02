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
import io.orkes.conductor.client.model.Group;
import io.orkes.conductor.client.model.UpsertGroupRequest;

import com.fasterxml.jackson.core.type.TypeReference;

class GroupResource {

    private final ConductorClient client;

    GroupResource(ConductorClient client) {
        this.client = client;
    }

    public void addUserToGroup(String groupId, String userId) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/groups/{groupId}/users/{userId}")
                .addPathParam("groupId", groupId)
                .addPathParam("userId", userId)
                .build();

        client.execute(request);
    }

    public void deleteGroup(String id) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/groups/{id}")
                .addPathParam("id", id)
                .build();

        client.execute(request);
    }

    public GrantedAccessResponse getGrantedPermissions(String groupId) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/groups/{groupId}/permissions")
                .addPathParam("groupId", groupId)
                .build();

        ConductorClientResponse<GrantedAccessResponse> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public Group getGroup(String id) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/groups/{id}")
                .addPathParam("id", id)
                .build();

        ConductorClientResponse<Group> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public List<ConductorUser> getUsersInGroup(String id) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/groups/{id}/users")
                .addPathParam("id", id)
                .build();

        ConductorClientResponse<List<ConductorUser>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public List<Group> listGroups() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/groups")
                .build();

        ConductorClientResponse<List<Group>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public void removeUserFromGroup(String groupId, String userId) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/groups/{groupId}/users/{userId}")
                .addPathParam("groupId", groupId)
                .addPathParam("userId", userId)
                .build();

        client.execute(request);
    }

    public Group upsertGroup(UpsertGroupRequest upsertGroupRequest, String id) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path("/groups/{id}")
                .addPathParam("id", id)
                .body(upsertGroupRequest)
                .build();

        ConductorClientResponse<Group> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }
}
