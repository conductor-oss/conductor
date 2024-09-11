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
package com.netflix.conductor.sdk.examples.helloworld;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.netflix.conductor.sdk.examples.helloworld.workflowdef.GreetingsWorkflow;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

import io.orkes.conductor.sdk.examples.util.ClientUtil;

public class Main {

    public static void main(String[] args) throws ExecutionException, InterruptedException, TimeoutException {
        var workflowExecutor = new WorkflowExecutor(ClientUtil.getClient(), 10);
        workflowExecutor.initWorkers("com.netflix.conductor.sdk.examples.helloworld.workers");
        var workflowCreator = new GreetingsWorkflow(workflowExecutor);
        var simpleWorkflow = workflowCreator.create();

        var input = new GreetingsWorkflow.Input();
        input.setName("Conductor User 42");

        var workflowExecution = simpleWorkflow.executeDynamic(input);
        try {
            var workflowRun = workflowExecution.get(10, TimeUnit.SECONDS);
            System.out.println("Workflow execution status: " + workflowRun.getStatus());
        } catch (Exception e) {
            System.out.println("Error while executing workflow: " + e.getMessage());
        }

        // Shutdown workers gracefully
        workflowExecutor.shutdown();
    }
}
