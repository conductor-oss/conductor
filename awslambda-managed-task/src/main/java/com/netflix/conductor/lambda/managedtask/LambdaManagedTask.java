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
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.managed.ManagedTask;
import com.netflix.conductor.lambda.config.LambdaProperties;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component(LambdaManagedTask.AWS_LAMBDA_MANAGED_TASK)
public class LambdaManagedTask extends ManagedTask {

    public static final String AWS_LAMBDA_MANAGED_TASK = "AWS_LAMBDA";
    private final AWSLambdaAsync client;

    @Autowired
    public LambdaManagedTask(WorkflowExecutor workflowExecutor, LambdaProperties properties) {
        super(workflowExecutor);
        this.client = AWSLambdaAsyncClientBuilder.standard().withRegion(properties.getRegion()).build();
    }

    @Override
    protected String getTaskType() {
        return AWS_LAMBDA_MANAGED_TASK;
    }

    @Override
    protected void invoke(WorkflowModel workflow, TaskModel task) {
        client.invokeAsync(
                new InvokeRequest()
                        .withFunctionName(task.getFunctionName()));
    }

    @Override
    public TaskResult callback() {
        // read output here
        // get task status
        String taskId = "";
        String workflowId = "";
        TaskResult taskResult = new TaskResult();
        taskResult.setTaskId(taskId);
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setOutputData(new HashMap<>());
        return taskResult;
    }
}
