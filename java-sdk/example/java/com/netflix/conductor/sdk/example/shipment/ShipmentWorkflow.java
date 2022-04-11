/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.sdk.example.shipment;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.WorkflowBuilder;
import com.netflix.conductor.sdk.workflow.def.tasks.*;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

public class ShipmentWorkflow {

    private final WorkflowExecutor executor;

    public ShipmentWorkflow(WorkflowExecutor executor) {
        this.executor = executor;
        this.executor.initWorkers(ShipmentWorkflow.class.getPackageName());
    }

    public ConductorWorkflow<Order> createOrderFlow() {
        WorkflowBuilder<Order> builder = new WorkflowBuilder<>(executor);
        builder.name("order_flow")
                .version(1)
                .ownerEmail("user@example.com")
                .timeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF, 60) // 1 day max
                .description("Workflow to track shipment")
                .add(
                        new SimpleTask("calculate_tax_and_total", "calculate_tax_and_total")
                                .input("orderDetail", ConductorWorkflow.input.get("orderDetail")))
                .add(
                        new SimpleTask("charge_payment", "charge_payment")
                                .input(
                                        "billingId",
                                                ConductorWorkflow.input
                                                        .map("userDetails")
                                                        .get("billingId"),
                                        "billingType",
                                                ConductorWorkflow.input
                                                        .map("userDetails")
                                                        .get("billingType"),
                                        "amount", "${calculate_tax_and_total.output.total_amount}"))
                .add(
                        new Switch("shipping_label", "${workflow.input.orderDetail.shippingMethod}")
                                .switchCase(
                                        Order.ShippingMethod.GROUND.toString(),
                                        new SimpleTask(
                                                        "ground_shipping_label",
                                                        "ground_shipping_label")
                                                .input(
                                                        "name",
                                                                ConductorWorkflow.input
                                                                        .map("userDetails")
                                                                        .get("name"),
                                                        "address",
                                                                ConductorWorkflow.input
                                                                        .map("userDetails")
                                                                        .get("addressLine"),
                                                        "orderNo",
                                                                ConductorWorkflow.input
                                                                        .map("orderDetail")
                                                                        .get("orderNumber")))
                                .switchCase(
                                        Order.ShippingMethod.NEXT_DAY_AIR.toString(),
                                        new SimpleTask("air_shipping_label", "air_shipping_label")
                                                .input(
                                                        "name",
                                                                ConductorWorkflow.input
                                                                        .map("userDetails")
                                                                        .get("name"),
                                                        "address",
                                                                ConductorWorkflow.input
                                                                        .map("userDetails")
                                                                        .get("addressLine"),
                                                        "orderNo",
                                                                ConductorWorkflow.input
                                                                        .map("orderDetail")
                                                                        .get("orderNumber")))
                                .switchCase(
                                        Order.ShippingMethod.SAME_DAY.toString(),
                                        new SimpleTask(
                                                        "same_day_shipping_label",
                                                        "same_day_shipping_label")
                                                .input(
                                                        "name",
                                                                ConductorWorkflow.input
                                                                        .map("userDetails")
                                                                        .get("name"),
                                                        "address",
                                                                ConductorWorkflow.input
                                                                        .map("userDetails")
                                                                        .get("addressLine"),
                                                        "orderNo",
                                                                ConductorWorkflow.input
                                                                        .map("orderDetail")
                                                                        .get("orderNumber")))
                                .defaultCase(
                                        new Terminate(
                                                "unsupported_shipping_type",
                                                Workflow.WorkflowStatus.FAILED,
                                                "Unsupported Shipping Method")))
                .add(
                        new SimpleTask("send_email", "send_email")
                                .input(
                                        "name",
                                                ConductorWorkflow.input
                                                        .map("userDetails")
                                                        .get("name"),
                                        "email",
                                                ConductorWorkflow.input
                                                        .map("userDetails")
                                                        .get("email"),
                                        "orderNo",
                                                ConductorWorkflow.input
                                                        .map("orderDetail")
                                                        .get("orderNumber")));
        ConductorWorkflow<Order> conductorWorkflow = builder.build();
        conductorWorkflow.registerWorkflow(true, true);
        return conductorWorkflow;
    }

    public ConductorWorkflow<Shipment> createShipmentWorkflow() {

        WorkflowBuilder<Shipment> builder = new WorkflowBuilder<>(executor);

        SimpleTask getOrderDetails =
                new SimpleTask("get_order_details", "get_order_details")
                        .input("orderNo", ConductorWorkflow.input.get("orderNo"));

        SimpleTask getUserDetails =
                new SimpleTask("get_user_details", "get_user_details")
                        .input("userId", ConductorWorkflow.input.get("userId"));

        ConductorWorkflow<Shipment> conductorWorkflow =
                builder.name("shipment_workflow")
                        .version(1)
                        .ownerEmail("user@example.com")
                        .variables(new ShipmentState())
                        .timeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF, 60) // 30 days
                        .description("Workflow to track shipment")
                        .add(
                                new ForkJoin(
                                        "get_in_parallel",
                                        new Task[] {getOrderDetails},
                                        new Task[] {getUserDetails}))

                        // For all the line items in the order, run in parallel:
                        // (calculate tax, charge payment, set state, prepare shipment, send
                        // shipment, set state)
                        .add(
                                new DynamicFork(
                                        "process_order",
                                        new SimpleTask("generateDynamicFork", "generateDynamicFork")
                                                .input(
                                                        "orderDetails",
                                                        getOrderDetails.taskOutput.get("result"))
                                                .input("userDetails", getUserDetails.taskOutput)))

                        // Update the workflow state with shipped = true
                        .add(new SetVariable("update_state").input("shipped", true))
                        .build();

        conductorWorkflow.registerWorkflow(true, true);

        return conductorWorkflow;
    }

    public static void main(String[] args) {

        String conductorServerURL =
                "http://localhost:8080/api/"; // Change this to your Conductor server
        WorkflowExecutor executor = new WorkflowExecutor(conductorServerURL);

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
            System.exit(0);
        }

        System.out.println("Done");
    }
}
