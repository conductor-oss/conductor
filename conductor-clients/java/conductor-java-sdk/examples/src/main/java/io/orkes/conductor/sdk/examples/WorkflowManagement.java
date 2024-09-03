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
package io.orkes.conductor.sdk.examples;

import java.util.Arrays;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

import io.orkes.conductor.client.http.OrkesWorkflowClient;
import io.orkes.conductor.sdk.examples.util.ClientUtil;

import static io.orkes.conductor.sdk.examples.MetadataManagement.workflowDef;

/**
 * Examples for managing Workflow operations in Conductor
 *
 * 1. startWorkflow - Start a new workflow
 * 2. getWorkflow - Get workflow execution status
 * 3. pauseWorkflow - Pause workflow
 * 4. resumeWorkflow - Resume workflow
 * 5. terminateWorkflow - Terminate workflow
 * 6. deleteWorkflow - Delete workflow
 *
 */
public class WorkflowManagement {

    private static OrkesWorkflowClient workflowClient;

    public static void main(String[] args) {
        ConductorClient client = ClientUtil.getClient();
        createMetadata();
        WorkflowManagement workflowManagement = new WorkflowManagement();
        workflowClient = new OrkesWorkflowClient(client);
        workflowManagement.workflowOperations();
        client.shutdown();
    }

    private static void createMetadata() {
        MetadataManagement metadataManagement = new MetadataManagement();
        metadataManagement.createTaskDefinitions();
        metadataManagement.createWorkflowDefinitions();
    }

    private void workflowOperations() {
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowDef.getName());
        startWorkflowRequest.setVersion(workflowDef.getVersion());
        startWorkflowRequest.setCorrelationId("test_workflow");

        // Start the workflow
        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        // Get the workflow execution status
        workflowClient.getWorkflow(workflowId, true);
        // Pause the workflow
        workflowClient.pauseWorkflow(workflowId);
        // Resume the workflow
        workflowClient.resumeWorkflow(workflowId);
        // Terminate the workflow
        workflowClient.terminateWorkflow(workflowId, "Terminated");
        // Retry workflow
        workflowClient.retryWorkflow(Arrays.asList(workflowId));
        // Terminate the workflow
        workflowClient.terminateWorkflow(workflowId, "Terminated");
        // Restart workflow
        workflowClient.restartWorkflow(Arrays.asList(workflowId), false);
        // Terminate the workflow
        workflowClient.terminateWorkflow(workflowId, "Terminated");
        // Restart workflow using latest workflow definitions
        workflowClient.restartWorkflow(Arrays.asList(workflowId), true);
        // Terminate the workflow
        workflowClient.terminateWorkflow(workflowId, "Terminated");
        // Delete the workflow without archiving
        workflowClient.deleteWorkflow(workflowId, false);
        // Delete the workflow with archiving to persistent store.
        workflowClient.deleteWorkflow(workflowId, true);
    }
}
