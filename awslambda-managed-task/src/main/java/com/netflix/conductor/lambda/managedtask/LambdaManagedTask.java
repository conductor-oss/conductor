/*
 *  Copyright 2022 Netflix, Inc.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.lambda.managedtask;

import com.amazonaws.services.lambda.AWSLambdaAsync;
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.netflix.conductor.core.execution.managed.ManagedTask;
import com.netflix.conductor.lambda.config.LambdaProperties;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import org.springframework.stereotype.Component;

@Component(LambdaManagedTask.MANAGED_TASK_TYPE)
public class LambdaManagedTask implements ManagedTask {

    public static final String MANAGED_TASK_TYPE = "AWS_LAMBDA";

    private final AWSLambdaAsync client;

    public LambdaManagedTask(LambdaProperties properties) {
        client = AWSLambdaAsyncClientBuilder.standard().withRegion(properties.getRegion()).build();
    }

    @Override
    public String getTaskType() {
        return MANAGED_TASK_TYPE;
    }

    @Override
    public void invoke(WorkflowModel workflow, TaskModel task) {
        client.invokeAsync(
                new InvokeRequest()
                        .withFunctionName(task.getFunctionName()));
    }
}
