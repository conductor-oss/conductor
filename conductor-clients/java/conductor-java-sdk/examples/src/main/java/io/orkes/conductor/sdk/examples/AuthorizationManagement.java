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
package io.orkes.conductor.sdk.examples;

import java.util.List;
import java.util.UUID;

import io.orkes.conductor.client.AuthorizationClient;
import io.orkes.conductor.client.OrkesClients;
import io.orkes.conductor.client.model.AuthorizationRequest;
import io.orkes.conductor.client.model.SubjectRef;
import io.orkes.conductor.client.model.TargetRef;
import io.orkes.conductor.client.model.UpsertGroupRequest;
import io.orkes.conductor.client.model.UpsertUserRequest;
import io.orkes.conductor.sdk.examples.util.ClientUtil;

/**
 * Examples for managing user authorization in Orkes Conductor
 *
 * 1. upsertUser - Add user
 * 2. upsertUser - Add group
 * 3. addUserToGroup - Add user to group
 * 4. removeUserFromGroup - Remove user from group
 * 5. grantPermissions - Grant permission to user via tag or group.
 */
public class AuthorizationManagement {

    private static AuthorizationClient authorizationClient;

    public static void main(String[] a) {
        OrkesClients orkesClients = ClientUtil.getOrkesClients();
        createMetadata();
        authorizationClient = orkesClients.getAuthorizationClient();
        AuthorizationManagement authorizationManagement = new AuthorizationManagement();
        authorizationManagement.userAndGroupOperations();
    }

    private void userAndGroupOperations() {
        // Create users
        String userId = createUser("user1");
        String userId2 = createUser("user2");
        String userId3 = createUser("user3");
        // Create groups
        String group1 = "group1";
        String group2 = "group2";
        createGroup(group1, "group to perform");
        createGroup(group2, "group to perform action");
        // Add users to group 1
        authorizationClient.addUserToGroup(group1, userId);
        authorizationClient.addUserToGroup(group1, userId2);
        authorizationClient.addUserToGroup(group2, userId2);
        authorizationClient.addUserToGroup(group2, userId3);

        // Remove user from group
        authorizationClient.removeUserFromGroup(group1, userId2);

        // Add workflow execution permissions to the group
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setAccess(List.of(AuthorizationRequest.AccessEnum.EXECUTE));
        SubjectRef subjectRef = new SubjectRef();
        subjectRef.setId(userId);
        subjectRef.setType(SubjectRef.TypeEnum.USER);
        // Grant workflow execution permission to user
        authorizationRequest.setSubject(subjectRef);
        TargetRef targetRef = new TargetRef();
        targetRef.setId("org:engineering");
        targetRef.setType(TargetRef.TypeEnum.WORKFLOW_DEF);
        authorizationRequest.setTarget(targetRef);
        authorizationClient.grantPermissions(authorizationRequest);

        // Grant workflow execution permission to tag
        targetRef = new TargetRef();
        targetRef.setId("customer:abc");
        targetRef.setType(TargetRef.TypeEnum.TASK_DEF);
        authorizationRequest.setTarget(targetRef);
        authorizationClient.grantPermissions(authorizationRequest);

        // Add read only permission to tag in group
        authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setAccess(List.of(AuthorizationRequest.AccessEnum.READ));
        subjectRef = new SubjectRef();
        subjectRef.setId(group1);
        subjectRef.setType(SubjectRef.TypeEnum.GROUP);
        authorizationRequest.setSubject(subjectRef);
        targetRef = new TargetRef();
        targetRef.setId("org:engineering");
        targetRef.setType(TargetRef.TypeEnum.WORKFLOW_DEF);
        authorizationRequest.setTarget(targetRef);
        authorizationClient.grantPermissions(authorizationRequest);
    }

    private String createUser(String name) {
        String userId = UUID.randomUUID().toString();
        UpsertUserRequest upsertUserRequest = new UpsertUserRequest();
        upsertUserRequest.setName(name);
        upsertUserRequest.setRoles(List.of(UpsertUserRequest.RolesEnum.USER));
        authorizationClient.upsertUser(upsertUserRequest, userId);
        return userId;
    }

    private void createGroup(String name, String description) {
        UpsertGroupRequest upsertGroupRequest = new UpsertGroupRequest();
        upsertGroupRequest.setDescription(description);
        upsertGroupRequest.setRoles(List.of(UpsertGroupRequest.RolesEnum.USER));
        authorizationClient.upsertGroup(upsertGroupRequest, name);
    }

    private static void createMetadata() {
        MetadataManagement metadataManagement = new MetadataManagement();
        metadataManagement.createTaskDefinitions();
        metadataManagement.createWorkflowDefinitions();
    }
}
