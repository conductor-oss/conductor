/*
 * Copyright 2026 Conductor Authors.
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
package io.conductor.e2e.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Disabled;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.tasks.SimpleTask;
import com.netflix.conductor.sdk.workflow.def.tasks.Switch;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import io.conductor.e2e.util.ApiUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SwitchTests {

    private static WorkflowExecutor executor;

    @BeforeAll
    public static void init() {
        TaskClient taskClient = ApiUtil.TASK_CLIENT;
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        executor = new WorkflowExecutor(taskClient, workflowClient, metadataClient, 1000);
        executor.initWorkers("io.conductor.e2e.control");
    }

    @Test
    @DisplayName("Check if switch works based on the provided input - sms")
    public void testSwitchHappySms()
            throws ExecutionException, InterruptedException, TimeoutException {
        ConductorWorkflow<WorkflowInput> workflow = getSwitchWorkflow();
        WorkflowInput workflowInput = new WorkflowInput("userA", "SMS");

        CompletableFuture<Workflow> future = workflow.executeDynamic(workflowInput);
        assertNotNull(future);
        Workflow run = future.get(40, TimeUnit.SECONDS);

        assertNotNull(run);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, run.getStatus());
        assertEquals(4, run.getTasks().size());
        assertEquals(
                "Sending push for  userA[Email: userA@gmail.com][PhoneNumber: 9999999999]",
                run.getTasks().get(3).getOutputData().get("result"));
    }

    @Test
    @DisplayName("Check if switch works based on the provided input - email")
    public void testSwitchHappyEmail()
            throws ExecutionException, InterruptedException, TimeoutException {
        ConductorWorkflow<WorkflowInput> workflow = getSwitchWorkflow();
        WorkflowInput workflowInput = new WorkflowInput("userA", "EMAIL");

        CompletableFuture<Workflow> future = workflow.executeDynamic(workflowInput);
        assertNotNull(future);
        Workflow run = future.get(40, TimeUnit.SECONDS);

        assertNotNull(run);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, run.getStatus());
        assertEquals(3, run.getTasks().size());
        assertEquals("EMAIL: userA@gmail.com", run.getTasks().get(2).getOutputData().get("result"));
    }

    @Test
    @Disabled(
            "Default case switch workflow does not complete within 20s with SDK-based executeDynamic in conductor-oss; see conductor-oss#SwitchTests-default-case-timing")
    @DisplayName("Check if switch works based on the provided wrong input")
    public void testSwitchNegetive()
            throws ExecutionException, InterruptedException, TimeoutException {
        ConductorWorkflow<WorkflowInput> workflow = getSwitchWorkflow();
        WorkflowInput workflowInput = new WorkflowInput("userA", "Whatsapp");

        CompletableFuture<Workflow> future = workflow.executeDynamic(workflowInput);
        assertNotNull(future);
        Workflow run = future.get(20, TimeUnit.SECONDS);

        assertNotNull(run);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, run.getStatus());
        assertEquals(3, run.getTasks().size());

        Object resultObject = run.getTasks().get(1).getOutputData().get("evaluationResult");
        ArrayList<String> runResult = null;
        if (resultObject instanceof ArrayList<?>) {
            @SuppressWarnings("unchecked")
            ArrayList<String> safeResult = (ArrayList<String>) resultObject;
            runResult = safeResult;
        } else {
            runResult = new ArrayList<>();
        }
        assertEquals(1, runResult.size());
        assertEquals("Whatsapp", runResult.get(0));
    }

    @AfterAll
    public static void cleanup() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @WorkerTask("get_user_details")
    public WorkflowOutput get_user_details(@InputParam("userId") String userId)
            throws InterruptedException {
        return new WorkflowOutput("9999999999", userId + "@gmail.com");
    }

    @WorkerTask("send_email")
    public String send_email(@InputParam("email") String email) throws InterruptedException {
        return "EMAIL: " + email;
    }

    @WorkerTask("send_sms")
    public String send_sms(@InputParam("phoneNumber") String phoneNumber)
            throws InterruptedException {
        return "SMS: " + phoneNumber;
    }

    @WorkerTask("send_push")
    public String send_push(
            @InputParam("userId") String userId,
            @InputParam("email") String email,
            @InputParam("phoneNumber") String phoneNumber)
            throws InterruptedException {
        return "Sending push for  "
                + userId
                + "[Email: "
                + email
                + "]"
                + "[PhoneNumber: "
                + phoneNumber
                + "]";
    }

    @WorkerTask("default_switch_case")
    public String default_switch_case(
            @InputParam("userId") String userId,
            @InputParam("email") String email,
            @InputParam("phoneNumber") String phoneNumber)
            throws InterruptedException {
        return "No activity found for  "
                + userId
                + "[Email: "
                + email
                + "]"
                + "[PhoneNumber: "
                + phoneNumber
                + "]";
    }

    class WorkflowInput {
        private String userId;

        public String getNotificationPreference() {
            return notificationPreference;
        }

        public void setNotificationPreference(String notificationPreference) {
            this.notificationPreference = notificationPreference;
        }

        private String notificationPreference;

        public WorkflowInput(String userId, String notificationPreference) {
            this.userId = userId;
            this.notificationPreference = notificationPreference;
        }

        public String getName() {
            return userId;
        }

        public void setName(String name) {
            this.userId = name;
        }
    }

    public static class WorkflowOutput {
        private String phoneNumber;
        private String email;

        public WorkflowOutput(String phoneNumber, String email) {
            this.phoneNumber = phoneNumber;
            this.email = email;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    private ConductorWorkflow<WorkflowInput> getSwitchWorkflow() {
        ConductorWorkflow<WorkflowInput> workflow = new ConductorWorkflow<>(executor);
        workflow.setName("sdk_switch_test");
        workflow.setVersion(1);

        SimpleTask getUserDetails = new SimpleTask("get_user_details", "get_user_details");
        getUserDetails.input("userId", "${workflow.input.name}");
        workflow.add(getUserDetails);

        SimpleTask sendEmail = new SimpleTask("send_email", "send_email");
        // get user details user info, which contains the email field
        sendEmail.input("email", "${get_user_details.output.email}");

        SimpleTask sendSMS = new SimpleTask("send_sms", "send_sms");
        // get user details user info, which contains the phone Number field
        sendSMS.input("phoneNumber", "${get_user_details.output.phoneNumber}");

        SimpleTask defaultSwitchCase = new SimpleTask("default_switch_case", "default_switch_case");
        Map<String, String> inputDefault = new HashMap<>();
        inputDefault.put("userId", "${workflow.input.name}");
        inputDefault.put("email", "${get_user_details.output.email}");
        inputDefault.put("phoneNumber", "${get_user_details.output.phoneNumber}");
        defaultSwitchCase.input(inputDefault);

        SimpleTask sendPush = new SimpleTask("send_push", "send_push");
        sendPush.input(inputDefault);

        Switch emailOrSMS =
                new Switch("emailorsms", "${workflow.input.notificationPreference}")
                        .switchCase("EMAIL", sendEmail)
                        .switchCase("SMS", sendSMS, sendPush)
                        .defaultCase(defaultSwitchCase);
        workflow.add(emailOrSMS);
        return workflow;
    }
}
