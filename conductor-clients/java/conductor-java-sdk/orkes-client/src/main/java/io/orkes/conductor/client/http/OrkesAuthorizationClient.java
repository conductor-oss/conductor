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

import com.netflix.conductor.client.http.ConductorClient;

import io.orkes.conductor.client.AuthorizationClient;
import io.orkes.conductor.client.model.AccessKeyResponse;
import io.orkes.conductor.client.model.AuthorizationRequest;
import io.orkes.conductor.client.model.ConductorApplication;
import io.orkes.conductor.client.model.ConductorUser;
import io.orkes.conductor.client.model.CreateAccessKeyResponse;
import io.orkes.conductor.client.model.CreateOrUpdateApplicationRequest;
import io.orkes.conductor.client.model.GrantedAccessResponse;
import io.orkes.conductor.client.model.Group;
import io.orkes.conductor.client.model.Subject;
import io.orkes.conductor.client.model.TagObject;
import io.orkes.conductor.client.model.UpsertGroupRequest;
import io.orkes.conductor.client.model.UpsertUserRequest;

public class OrkesAuthorizationClient implements AuthorizationClient {

    private final ApplicationResource applicationResource;
    private final AuthorizationResource authorizationResource;
    private final GroupResource groupResource;
    private final UserResource userResource;

    public OrkesAuthorizationClient(ConductorClient apiClient) {
        this.applicationResource = new ApplicationResource(apiClient);
        this.authorizationResource = new AuthorizationResource(apiClient);
        this.groupResource = new GroupResource(apiClient);
        this.userResource = new UserResource(apiClient);
    }

    @Override
    public Map<String, List<Subject>> getPermissions(String type, String id) {
        return authorizationResource.getPermissions(type, id);
    }

    @Override
    public void grantPermissions(AuthorizationRequest authorizationRequest) {
        authorizationResource.grantPermissions(authorizationRequest);
    }

    @Override
    public void removePermissions(AuthorizationRequest authorizationRequest) {
        authorizationResource.removePermissions(authorizationRequest);
    }

    @Override
    public void addUserToGroup(String groupId, String userId) {
        groupResource.addUserToGroup(groupId, userId);
    }

    @Override
    public void deleteGroup(String id) {
        groupResource.deleteGroup(id);
    }

    @Override
    public GrantedAccessResponse getGrantedPermissionsForGroup(String groupId) {
        return groupResource.getGrantedPermissions(groupId);
    }

    @Override
    public Group getGroup(String id) {
        return groupResource.getGroup(id);
    }

    @Override
    public List<ConductorUser> getUsersInGroup(String id) {
        return groupResource.getUsersInGroup(id);
    }

    @Override
    public List<Group> listGroups() {
        return groupResource.listGroups();
    }

    @Override
    public void removeUserFromGroup(String groupId, String userId) {
        groupResource.removeUserFromGroup(groupId, userId);
    }

    @Override
    public Group upsertGroup(UpsertGroupRequest upsertGroupRequest, String id) {
        return groupResource.upsertGroup(upsertGroupRequest, id);
    }

    @Override
    public void deleteUser(String id) {
        userResource.deleteUser(id);
    }

    @Override
    public GrantedAccessResponse getGrantedPermissionsForUser(String userId) {
        return userResource.getGrantedPermissions(userId);
    }

    @Override
    public ConductorUser getUser(String id) {
        return userResource.getUser(id);
    }

    @Override
    public List<ConductorUser> listUsers(Boolean apps) {
        return userResource.listUsers(apps);
    }

    @Override
    public void sendInviteEmail(String email) {
        userResource.sendInviteEmail(email);
    }

    @Override
    public ConductorUser upsertUser(UpsertUserRequest upsertUserRequest, String id) {
        return userResource.upsertUser(upsertUserRequest, id);
    }

    @Override
    public void addRoleToApplicationUser(String applicationId, String role) {
        applicationResource.addRoleToApplicationUser(applicationId, role);
    }

    @Override
    public CreateAccessKeyResponse createAccessKey(String id) {
        return applicationResource.createAccessKey(id);
    }

    @Override
    public ConductorApplication createApplication(CreateOrUpdateApplicationRequest createOrUpdateApplicationRequest) {
        return applicationResource.upsertApplication(createOrUpdateApplicationRequest, null);
    }

    @Override
    public void deleteAccessKey(String applicationId, String keyId) {
        applicationResource.deleteAccessKey(applicationId, keyId);
    }

    @Override
    public void deleteApplication(String id) {
        applicationResource.deleteApplication(id);
    }

    @Override
    public List<AccessKeyResponse> getAccessKeys(String id) {
        return applicationResource.getAccessKeys(id);
    }

    @Override
    public ConductorApplication getApplication(String id) {
        return applicationResource.getApplication(id);
    }

    @Override
    public List<ConductorApplication> listApplications() {
        return applicationResource.listApplications();
    }

    @Override
    public void removeRoleFromApplicationUser(String applicationId, String role) {
        applicationResource.removeRoleFromApplicationUser(applicationId, role);
    }

    @Override
    public AccessKeyResponse toggleAccessKeyStatus(String applicationId, String keyId) {
        return applicationResource.toggleAccessKeyStatus(applicationId, keyId);
    }

    @Override
    public ConductorApplication updateApplication(CreateOrUpdateApplicationRequest createOrUpdateApplicationRequest, String id) {
        return applicationResource.upsertApplication(createOrUpdateApplicationRequest, id);
    }

    @Override
    public void setApplicationTags(List<TagObject> tags, String applicationId) {
        applicationResource.putTags(tags, applicationId);
    }

    @Override
    public List<TagObject> getApplicationTags(String applicationId) {
        return applicationResource.getTags(applicationId);
    }

    @Override
    public void deleteApplicationTags(List<TagObject> tags, String applicationId) {
        applicationResource.deleteTags(tags, applicationId);
    }
}
