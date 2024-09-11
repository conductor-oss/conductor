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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import io.orkes.conductor.client.AuthorizationClient;
import io.orkes.conductor.client.OrkesClients;
import io.orkes.conductor.client.model.AccessKeyResponse;
import io.orkes.conductor.client.model.AuthorizationRequest;
import io.orkes.conductor.client.model.ConductorApplication;
import io.orkes.conductor.client.model.ConductorUser;
import io.orkes.conductor.client.model.CreateAccessKeyResponse;
import io.orkes.conductor.client.model.CreateOrUpdateApplicationRequest;
import io.orkes.conductor.client.model.Group;
import io.orkes.conductor.client.model.SubjectRef;
import io.orkes.conductor.client.model.TagObject;
import io.orkes.conductor.client.model.TargetRef;
import io.orkes.conductor.client.model.TargetRef.TypeEnum;
import io.orkes.conductor.client.model.UpsertGroupRequest;
import io.orkes.conductor.client.model.UpsertGroupRequest.RolesEnum;
import io.orkes.conductor.client.model.UpsertUserRequest;
import io.orkes.conductor.client.util.ClientTestUtil;
import io.orkes.conductor.client.util.Commons;

public class AuthorizationClientTests {
    private static AuthorizationClient authorizationClient;
    private static String applicationId;
    private static OrkesMetadataClient metadataClient;

    @BeforeAll
    public static void setup() {
        OrkesClients orkesClients = ClientTestUtil.getOrkesClients();
        authorizationClient = orkesClients.getAuthorizationClient();
        metadataClient = orkesClients.getMetadataClient();
        CreateOrUpdateApplicationRequest request = new CreateOrUpdateApplicationRequest();
        request.setName("test-" + UUID.randomUUID());
        ConductorApplication app = authorizationClient.createApplication(request);
        applicationId = app.getId();
    }

    @AfterAll
    public static void cleanup() {
        if(applicationId != null) {
            authorizationClient.deleteApplication(applicationId);
        }
    }

    @Test
    @DisplayName("auto assign group permission on workflow creation by any group member")
    public void autoAssignWorkflowPermissions() {
        giveApplicationPermissions(applicationId);
        Group group = authorizationClient.upsertGroup(getUpsertGroupRequest(), "sdk-test-group");
        validateGroupPermissions(group.getId());
    }

    @Test
    void testUser() {
        ConductorUser user =
                authorizationClient.upsertUser(getUpserUserRequest(), Commons.USER_EMAIL);
        ConductorUser receivedUser = authorizationClient.getUser(Commons.USER_EMAIL);
        Assertions.assertEquals(user.getName(), receivedUser.getName());
        Assertions.assertEquals(user.getGroups().get(0).getId(), receivedUser.getGroups().get(0).getId());
        Assertions.assertEquals(user.getRoles().get(0).getName(), receivedUser.getRoles().get(0).getName());
        authorizationClient.sendInviteEmail(user.getId());
        Group group = authorizationClient.upsertGroup(getUpsertGroupRequest(), Commons.GROUP_ID);
        Assertions.assertNotNull(group);
        authorizationClient.removeUserFromGroup(Commons.GROUP_ID, user.getId());
        authorizationClient.removePermissions(getAuthorizationRequest());
    }

    @Test
    void testGroup() {
        UpsertGroupRequest request = new UpsertGroupRequest();

        // Default Access for the group. When specified, any new workflow or task
        // created by the
        // members of this group
        // get this default permission inside the group.
        Map<String, List<String>> defaultAccess = new HashMap<>();

        // Grant READ access to the members of the group for any new workflow created by
        // a member of
        // this group
        defaultAccess.put(TypeEnum.WORKFLOW_DEF.getValue(), List.of("READ"));

        // Grant EXECUTE access to the members of the group for any new task created by
        // a member of
        // this group
        defaultAccess.put(TypeEnum.TASK_DEF.getValue(), List.of("EXECUTE"));
        request.setDefaultAccess(defaultAccess);

        request.setDescription("Example group created for testing");
        request.setRoles(Arrays.asList(UpsertGroupRequest.RolesEnum.USER));

        Group group = authorizationClient.upsertGroup(request, Commons.GROUP_ID);
        Assertions.assertNotNull(group);
        Group found = authorizationClient.getGroup(Commons.GROUP_ID);
        Assertions.assertNotNull(found);
        Assertions.assertEquals(group.getId(), found.getId());
        Assertions.assertEquals(group.getDefaultAccess().keySet(), found.getDefaultAccess().keySet());
    }

    @Test
    void testApplication() {
        CreateOrUpdateApplicationRequest request = new CreateOrUpdateApplicationRequest();
        request.setName("Test Application for the testing");

        // WARNING: Application Name is not a UNIQUE value and if called multiple times,
        // it will
        // create a new application
        ConductorApplication application = authorizationClient.createApplication(request);
        Assertions.assertNotNull(application);
        Assertions.assertNotNull(application.getId());

        // Get the list of applications
        List<ConductorApplication> apps = authorizationClient.listApplications();
        Assertions.assertNotNull(apps);
        long found =
                apps.stream()
                        .map(ConductorApplication::getId)
                        .filter(id -> id.equals(application.getId()))
                        .count();
        Assertions.assertEquals(1, found);

        // Create new access key
        CreateAccessKeyResponse accessKey =
                authorizationClient.createAccessKey(application.getId());
        List<AccessKeyResponse> accessKeyResponses =
                authorizationClient.getAccessKeys(application.getId());
        Assertions.assertEquals(1, accessKeyResponses.size());
        authorizationClient.toggleAccessKeyStatus(application.getId(), accessKey.getId());
        authorizationClient.deleteAccessKey(application.getId(), accessKey.getId());
        authorizationClient.setApplicationTags(getTagObject(), application.getId());
        Assertions.assertEquals(getTagObject(), authorizationClient.getApplicationTags(application.getId()));
        authorizationClient.deleteApplicationTags(getTagObject(), application.getId());
        Assertions.assertEquals(0, authorizationClient.getApplicationTags(application.getId()).size());
        accessKeyResponses = authorizationClient.getAccessKeys(application.getId());
        Assertions.assertEquals(0, accessKeyResponses.size());

        authorizationClient.removeRoleFromApplicationUser(
                application.getId(), RolesEnum.ADMIN.getValue());

        String newName = "ansdjansdjna";
        authorizationClient.updateApplication(
                new CreateOrUpdateApplicationRequest().name(newName), application.getId());
        Assertions.assertEquals(newName, authorizationClient.getApplication(application.getId()).getName());

        authorizationClient.deleteApplication(application.getId());
    }

    @Test
    void testGrantPermissionsToGroup() {
        AuthorizationRequest request = new AuthorizationRequest();
        request.access(Arrays.asList(AuthorizationRequest.AccessEnum.READ));
        SubjectRef subject = new SubjectRef();
        subject.setId("worker-test-group31dfe7a4-bd85-4ccc-9571-7c0e018ebc32");
        subject.setType(SubjectRef.TypeEnum.GROUP);
        request.setSubject(subject);
        TargetRef target = new TargetRef();
        target.setId("Test_032");
        target.setType(TargetRef.TypeEnum.WORKFLOW_DEF);
        request.setTarget(target);
        authorizationClient.grantPermissions(request);
    }

    @Test
    void testGrantPermissionsToDomain() {
        AuthorizationRequest request = new AuthorizationRequest();
        request.access(Arrays.asList(AuthorizationRequest.AccessEnum.EXECUTE));
        SubjectRef subject = new SubjectRef();
        subject.setId("conductoruser1@gmail.com");
        subject.setType(SubjectRef.TypeEnum.USER);
        request.setSubject(subject);
        TargetRef target = new TargetRef();
        target.setId("my-domain");
        target.setType(TargetRef.TypeEnum.DOMAIN);
        request.setTarget(target);
        authorizationClient.grantPermissions(request);
    }

    @Test
    @DisplayName("tag a workflows and task")
    public void tagWorkflowsAndTasks() {
        registerWorkflow();
        TagObject tagObject = new TagObject();
        tagObject.setType(TagObject.TypeEnum.METADATA);
        tagObject.setKey("a");
        tagObject.setValue("b");
        metadataClient.addTaskTag(tagObject, Commons.TASK_NAME);
        metadataClient.addWorkflowTag(tagObject, Commons.WORKFLOW_NAME);
    }

    public void registerWorkflow() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(Commons.WORKFLOW_NAME);
        workflowDef.setVersion(Commons.WORKFLOW_VERSION);
        workflowDef.setOwnerEmail(Commons.OWNER_EMAIL);
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName(Commons.TASK_NAME);
        workflowTask.setTaskReferenceName(Commons.TASK_NAME);
        workflowDef.setTasks(List.of(workflowTask));
        metadataClient.updateWorkflowDefs(Arrays.asList(workflowDef));
    }

    @Test
    void testGrantPermissionsToTag() {
        authorizationClient.grantPermissions(getAuthorizationRequest());
    }

    @Test
    void testMethods() {
        try {
            authorizationClient.deleteUser(Commons.USER_EMAIL);
        } catch (ConductorClientException e) {
            if (e.getStatus() != 404) {
                throw e;
            }
        }
        authorizationClient.upsertUser(getUpserUserRequest(), Commons.USER_EMAIL);
        List<ConductorUser> users = authorizationClient.listUsers(false);
        Assertions.assertFalse(users.isEmpty());
        users = authorizationClient.listUsers(true);
        Assertions.assertFalse(users.isEmpty());
        try {
            authorizationClient.deleteGroup(Commons.GROUP_ID);
        } catch (ConductorClientException e) {
            if (e.getStatus() != 404) {
                throw e;
            }
        }
        authorizationClient.upsertGroup(getUpsertGroupRequest(), Commons.GROUP_ID);
        List<Group> groups = authorizationClient.listGroups();
        Assertions.assertFalse(groups.isEmpty());
        authorizationClient.addUserToGroup(Commons.GROUP_ID, Commons.USER_EMAIL);
        boolean found = false;
        for (ConductorUser user : authorizationClient.getUsersInGroup(Commons.GROUP_ID)) {
            if (user.getName().equals(Commons.USER_NAME)) {
                found = true;
            }
        }
        Assertions.assertTrue(found);
        authorizationClient.getPermissions("APPLICATION", applicationId);
        Assertions.assertEquals(authorizationClient.getApplication(applicationId).getId(), applicationId);
        Assertions.assertTrue(
                authorizationClient
                        .getGrantedPermissionsForGroup(Commons.GROUP_ID)
                        .getGrantedAccess()
                        .isEmpty());
        // The user is added just now so it should not have any access.
        Assertions.assertTrue(
                authorizationClient
                        .getGrantedPermissionsForUser(Commons.USER_EMAIL)
                        .getGrantedAccess()
                        .isEmpty());
    }

    void giveApplicationPermissions(String applicationId) {
        authorizationClient.addRoleToApplicationUser(applicationId, RolesEnum.ADMIN.getValue());
    }

    void validateGroupPermissions(String id) {
        Group group = authorizationClient.getGroup(id);
        for (Map.Entry<String, List<String>> entry : group.getDefaultAccess().entrySet()) {
            List<String> expectedList = new ArrayList<>(getAccessListAll());
            List<String> actualList = new ArrayList<>(entry.getValue());
            Collections.sort(expectedList);
            Collections.sort(actualList);
            Assertions.assertEquals(expectedList, actualList);
        }
    }

    UpsertGroupRequest getUpsertGroupRequest() {
        return new UpsertGroupRequest()
                .defaultAccess(
                        Map.of(
                                TypeEnum.WORKFLOW_DEF.getValue(), getAccessListAll(),
                                TypeEnum.TASK_DEF.getValue(), getAccessListAll()))
                .description("Group used for SDK testing")
                .roles(List.of(RolesEnum.ADMIN));
    }

    UpsertUserRequest getUpserUserRequest() {
        UpsertUserRequest request = new UpsertUserRequest();
        request.setName(Commons.USER_NAME);
        request.setGroups(List.of(Commons.GROUP_ID));
        request.setRoles(List.of(UpsertUserRequest.RolesEnum.USER));
        return request;
    }



    List<String> getAccessListAll() {
        return List.of("CREATE", "READ", "UPDATE", "EXECUTE", "DELETE");
    }

    AuthorizationRequest getAuthorizationRequest() {
        AuthorizationRequest request = new AuthorizationRequest();
        request.access(Arrays.asList(AuthorizationRequest.AccessEnum.READ));
        SubjectRef subject = new SubjectRef();
        subject.setId("worker-test-group31dfe7a4-bd85-4ccc-9571-7c0e018ebc32");
        subject.setType(SubjectRef.TypeEnum.GROUP);
        request.setSubject(subject);
        TargetRef target = new TargetRef();
        target.setId("org:accounting");
        target.setType(TargetRef.TypeEnum.TAG);
        request.setTarget(target);
        return request;
    }

    private List<TagObject> getTagObject() {
        TagObject tagObject = new TagObject();
        tagObject.setKey("department");
        tagObject.setValue("accounts");
        return List.of(tagObject);
    }
}
