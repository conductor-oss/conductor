/*
 * Copyright 2024 Orkes, Inc.
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
package com.netflix.conductor.sdk.examples.shipment;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

import io.orkes.conductor.sdk.examples.util.ClientUtil;

public class Main {
    public static void main(String[] args) {
        ConductorClient client = ClientUtil.getClient();
        WorkflowExecutor executor = new WorkflowExecutor(client, 100);

        // Create the new shipment workflow
        ShipmentWorkflow shipmentWorkflow = new ShipmentWorkflow(executor);

        // Create two workflows

        // 1. Order flow that ships an individual order
        // 2. Shipment Workflow that tracks multiple orders in a shipment
        shipmentWorkflow.createOrderFlow();
        ConductorWorkflow<Shipment> workflow = shipmentWorkflow.createShipmentWorkflow();

        // Execute the workflow and wait for it to complete
        try {
            Shipment workflowInput = new Shipment("userA", "order123");

            // Execute returns a completable future.
            CompletableFuture<Workflow> executionFuture = workflow.execute(workflowInput);

            // Wait for a maximum of a minute for the workflow to complete.
            Workflow run = executionFuture.get(1, TimeUnit.MINUTES);

            System.out.println("Workflow Id: " + run);
            System.out.println("Workflow Status: " + run.getStatus());
            System.out.println("Workflow Output: " + run.getOutput());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }

        System.out.println("Done");
    }
}
