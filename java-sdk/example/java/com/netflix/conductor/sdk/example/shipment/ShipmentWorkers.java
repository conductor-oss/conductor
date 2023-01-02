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

import java.math.BigDecimal;
import java.util.*;

import com.netflix.conductor.sdk.workflow.def.tasks.DynamicForkInput;
import com.netflix.conductor.sdk.workflow.def.tasks.SubWorkflow;
import com.netflix.conductor.sdk.workflow.def.tasks.Task;
import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.OutputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

public class ShipmentWorkers {

    @WorkerTask(value = "generateDynamicFork", threadCount = 3)
    public DynamicForkInput generateDynamicFork(
            @InputParam("orderDetails") List<Order> orderDetails,
            @InputParam("userDetails") User userDetails) {
        DynamicForkInput input = new DynamicForkInput();
        List<Task<?>> tasks = new ArrayList<>();
        Map<String, Object> inputs = new HashMap<>();

        for (int i = 0; i < orderDetails.size(); i++) {
            Order detail = orderDetails.get(i);
            String referenceName = "order_flow_sub_" + i;
            tasks.add(
                    new SubWorkflow(referenceName, "order_flow", null)
                            .input("orderDetail", detail)
                            .input("userDetails", userDetails));
            inputs.put(referenceName, new HashMap<>());
        }
        input.setInputs(inputs);
        input.setTasks(tasks);
        return input;
    }

    @WorkerTask(value = "get_order_details", threadCount = 5)
    public List<Order> getOrderDetails(@InputParam("orderNo") String orderNo) {
        int lineItemCount = new Random().nextInt(10);
        List<Order> orderDetails = new ArrayList<>();
        for (int i = 0; i < lineItemCount; i++) {
            Order orderDetail = new Order(orderNo, "sku_" + i, 2, BigDecimal.valueOf(20.5));
            orderDetail.setOrderNumber(UUID.randomUUID().toString());
            orderDetail.setCountryCode(i % 2 == 0 ? "US" : "CA");
            if (i % 3 == 0) {
                orderDetail.setCountryCode("UK");
            }

            if (orderDetail.getCountryCode().equals("US"))
                orderDetail.setShippingMethod(Order.ShippingMethod.SAME_DAY);
            else if (orderDetail.getCountryCode().equals("CA"))
                orderDetail.setShippingMethod(Order.ShippingMethod.NEXT_DAY_AIR);
            else orderDetail.setShippingMethod(Order.ShippingMethod.GROUND);

            orderDetails.add(orderDetail);
        }
        return orderDetails;
    }

    @WorkerTask("get_user_details")
    public User getUserDetails(@InputParam("userId") String userId) {
        User user =
                new User(
                        "User Name",
                        userId + "@example.com",
                        "1234 forline street",
                        "mountain view",
                        "95030",
                        "US",
                        "Paypal",
                        "biling_001");

        return user;
    }

    @WorkerTask("calculate_tax_and_total")
    public @OutputParam("total_amount") BigDecimal calculateTax(
            @InputParam("orderDetail") Order orderDetails) {
        BigDecimal preTaxAmount =
                orderDetails.getUnitPrice().multiply(new BigDecimal(orderDetails.getQuantity()));
        BigDecimal tax = BigDecimal.valueOf(0.2).multiply(preTaxAmount);
        if (!"US".equals(orderDetails.getCountryCode())) {
            tax = BigDecimal.ZERO;
        }
        return preTaxAmount.add(tax);
    }

    @WorkerTask("ground_shipping_label")
    public @OutputParam("reference_number") String prepareGroundShipping(
            @InputParam("name") String name,
            @InputParam("address") String address,
            @InputParam("orderNo") String orderNo) {

        return "Ground_" + orderNo;
    }

    @WorkerTask("air_shipping_label")
    public @OutputParam("reference_number") String prepareAirShipping(
            @InputParam("name") String name,
            @InputParam("address") String address,
            @InputParam("orderNo") String orderNo) {

        return "Air_" + orderNo;
    }

    @WorkerTask("same_day_shipping_label")
    public @OutputParam("reference_number") String prepareSameDayShipping(
            @InputParam("name") String name,
            @InputParam("address") String address,
            @InputParam("orderNo") String orderNo) {

        return "SameDay_" + orderNo;
    }

    @WorkerTask("charge_payment")
    public @OutputParam("reference") String chargePayment(
            @InputParam("amount") BigDecimal amount,
            @InputParam("billingId") String billingId,
            @InputParam("billingType") String billingType) {

        return UUID.randomUUID().toString();
    }

    @WorkerTask("send_email")
    public void sendEmail(
            @InputParam("name") String name,
            @InputParam("email") String email,
            @InputParam("orderNo") String orderNo) {}
}
