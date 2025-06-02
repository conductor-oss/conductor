/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.gettingstarted;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

public class StartWorkflow {

    public static void main(String[] args) {
        var client = new ConductorClient("http://localhost:8080/api");
        var workflowClient = new WorkflowClient(client);
        var workflowId = workflowClient.startWorkflow(new StartWorkflowRequest()
                .withName("hello_workflow")
                .withVersion(1));

        System.out.println("Started workflow " + workflowId);
    }
}
