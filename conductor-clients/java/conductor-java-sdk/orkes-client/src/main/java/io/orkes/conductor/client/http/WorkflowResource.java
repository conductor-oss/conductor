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
import java.util.Map;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.ConductorClientRequest;
import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.client.http.ConductorClientResponse;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.UpgradeWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;

import io.orkes.conductor.client.enums.Consistency;
import io.orkes.conductor.client.enums.ReturnStrategy;
import io.orkes.conductor.client.model.*;

import com.fasterxml.jackson.core.type.TypeReference;


class WorkflowResource {
    private final ConductorClient client;

    WorkflowResource(ConductorClient client) {
        this.client = client;
    }

    WorkflowRun executeWorkflow(StartWorkflowRequest req,
                                String name,
                                Integer version,
                                String waitUntilTaskRef,
                                String requestId) {
        return executeWorkflow(req, name, version, waitUntilTaskRef, requestId, null);
    }

    WorkflowRun executeWorkflow(StartWorkflowRequest req,
                                String name,
                                Integer version,
                                String waitUntilTaskRef,
                                String requestId,
                                Integer waitForSeconds) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow/execute/{name}/{version}")
                .addPathParam("name", name)
                .addPathParam("version", version)
                .addQueryParam("requestId", requestId)
                .addQueryParam("waitUntilTaskRef", waitUntilTaskRef)
                .addQueryParam("waitForSeconds", waitForSeconds)
                .body(req)
                .build();

        ConductorClientResponse<WorkflowRun> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    WorkflowRun executeWorkflow(StartWorkflowRequest req,
                                String name,
                                Integer version,
                                String waitUntilTaskRef,
                                String requestId,
                                Integer waitForSeconds,
                                Consistency consistency) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow/execute/{name}/{version}")
                .addPathParam("name", name)
                .addPathParam("version", version)
                .addQueryParam("requestId", requestId)
                .addQueryParam("waitUntilTaskRef", waitUntilTaskRef)
                .addQueryParam("waitForSeconds", waitForSeconds)
                .addQueryParam("consistency", consistency.name())
                .body(req)
                .build();

        ConductorClientResponse<WorkflowRun> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    WorkflowRun executeWorkflow(StartWorkflowRequest req,
                                String name,
                                Integer version,
                                String waitUntilTaskRef,
                                String requestId,
                                Integer waitForSeconds,
                                Consistency consistency,
                                ReturnStrategy returnStrategy) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow/execute/{name}/{version}")
                .addPathParam("name", name)
                .addPathParam("version", version)
                .addQueryParam("requestId", requestId)
                .addQueryParam("waitUntilTaskRef", waitUntilTaskRef)
                .addQueryParam("waitForSeconds", waitForSeconds)
                .addQueryParam("consistency", consistency.name())
                .addQueryParam("returnStrategy", returnStrategy.name())
                .body(req)
                .build();

        ConductorClientResponse<WorkflowRun> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    WorkflowStatus getWorkflowStatusSummary(String workflowId, Boolean includeOutput, Boolean includeVariables) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/workflow/{workflowId}/status")
                .addPathParam("workflowId", workflowId)
                .addQueryParam("includeOutput", includeOutput)
                .addQueryParam("includeVariables", includeVariables)
                .build();

        ConductorClientResponse<WorkflowStatus> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    Map<String, List<Workflow>> getWorkflowsByNamesAndCorrelationIds(CorrelationIdsSearchRequest searchRequest,
                                                                     Boolean includeClosed,
                                                                     Boolean includeTasks) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow/correlated/batch")
                .addQueryParam("includeClosed", includeClosed)
                .addQueryParam("includeTasks", includeTasks)
                .body(searchRequest)
                .build();

        ConductorClientResponse<Map<String, List<Workflow>>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    void uploadCompletedWorkflows() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow/document-store/upload")
                .build();

        client.execute(request);
    }

    Workflow updateVariables(String workflowId, Map<String, Object> variables) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow/{workflowId}/variables")
                .addPathParam("workflowId", workflowId)
                .body(variables)
                .build();

        ConductorClientResponse<Workflow> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();

    }

    void upgradeRunningWorkflow(UpgradeWorkflowRequest body, String workflowId) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow/{workflowId}/upgrade")
                .addPathParam("workflowId", workflowId)
                .body(body)
                .build();

        client.execute(request);
    }

    WorkflowRun updateWorkflowState(WorkflowStateUpdate updateRequest,
                                    String requestId,
                                    String workflowId,
                                    String waitUntilTaskRef,
                                    Integer waitForSeconds) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow/{workflowId}/state")
                .addPathParam("workflowId", workflowId)
                .addQueryParam("requestId", requestId)
                .addQueryParam("waitUntilTaskRef", waitUntilTaskRef)
                .addQueryParam("waitForSeconds", waitForSeconds)
                .body(updateRequest)
                .build();

        ConductorClientResponse<WorkflowRun> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public void terminateWithAReason(String workflowId, String reason, boolean triggerFailureWorkflow) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.DELETE)
                .path("/workflow/{workflowId}")
                .addPathParam("workflowId", workflowId)
                .addQueryParam("reason", reason)
                .addQueryParam("triggerFailureWorkflow", triggerFailureWorkflow)
                .build();

        client.execute(request);
    }

    SignalResponse executeWorkflowWithReturnStrategy(StartWorkflowRequest req,
                                                     String name,
                                                     Integer version,
                                                     List<String> waitUntilTaskRef,
                                                     String requestId,
                                                     Integer waitForSeconds,
                                                     Consistency consistency,
                                                     ReturnStrategy returnStrategy) {

        String waitUntilTaskRefStr = null;
        if (waitUntilTaskRef != null && !waitUntilTaskRef.isEmpty()) {
            waitUntilTaskRefStr = String.join(",", waitUntilTaskRef);
        }

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow/execute/{name}/{version}")
                .addPathParam("name", name)
                .addPathParam("version", version)
                .addQueryParam("requestId", requestId)
                .addQueryParam("waitUntilTaskRef", waitUntilTaskRefStr)
                .addQueryParam("waitForSeconds", waitForSeconds)
                .addQueryParam("consistency", consistency.name())
                .addQueryParam("returnStrategy", returnStrategy.name())
                .body(req)
                .build();

        ConductorClientResponse<SignalResponse> resp = client.execute(request, new TypeReference<SignalResponse>() {
        });

        return resp.getData();
    }
}
