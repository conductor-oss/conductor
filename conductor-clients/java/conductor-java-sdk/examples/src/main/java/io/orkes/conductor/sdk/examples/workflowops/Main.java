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
package io.orkes.conductor.sdk.examples.workflowops;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

import io.orkes.conductor.client.http.OrkesWorkflowClient;
import io.orkes.conductor.sdk.examples.util.ClientUtil;
import io.orkes.conductor.sdk.examples.workflowops.workflowdef.GreetingsWorkflow;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;


public class Main {

    public static void main(String[] args) throws ExecutionException, InterruptedException, TimeoutException {
        //Initialise Conductor Client
        var client = ClientUtil.getClient();
        var orkesWorkflowClient = new OrkesWorkflowClient(client);

        //Initialise WorkflowExecutor and Conductor Workers
        var workflowExecutor = new WorkflowExecutor(client, 10);
        workflowExecutor.initWorkers("com.netflix.conductor.sdk.examples.helloworld.workers");

        //Create the workflow with input
        var workflowCreator = new GreetingsWorkflow(workflowExecutor);
        var simpleWorkflow = workflowCreator.create();
        var input = new GreetingsWorkflow.Input();
        input.setName("Orkes User");
        input.setPersons(List.of(Person.builder()
                                .id("1")
                                .name("John")
                                .last("Doe")
                                .email("john@orkes.io")
                                .build(),
                        Person.builder()
                                .id("2")
                                .name("Jane")
                                .last("Doe")
                                .email("jane@orkes.io")
                                .build()));
        var workflowId = simpleWorkflow.startDynamic(input);
        var summary = orkesWorkflowClient.getWorkflowStatusSummary(workflowId, false, false);

        //Workflow Execution Started
        System.out.println("Your workflow started with id " + workflowId);
        System.out.println("Workflow status is " + summary.getStatus());

        var workflow = orkesWorkflowClient.getWorkflow(workflowId, true);
        var lastTask = workflow.getTasks();
        var lastTaskId = lastTask.get(lastTask.size() - 1).getTaskDefName();
        System.out.println("Workflow status is " + workflow.getStatus() + " and current or last task is " + lastTaskId);

        //Test Termination
        orkesWorkflowClient.terminateWorkflow(workflowId, "Testing Termination");
        workflow = orkesWorkflowClient.getWorkflow(workflowId, false);
        System.out.println("Workflow status is " + workflow.getStatus());

        var workflowIds = List.of(workflowId);

        //Restart Workflow
        var bulkResponse = orkesWorkflowClient.restartWorkflow(workflowIds, false);

        workflow = orkesWorkflowClient.getWorkflow(workflowId, false);
        System.out.println("Workflow status is " + workflow.getStatus());

        //Pause Workflow
        orkesWorkflowClient.pauseWorkflow(workflowId);
        workflow = orkesWorkflowClient.getWorkflow(workflowId, true);
        System.out.println("Workflow status is " + workflow.getStatus());

        //Resume Workflow
        orkesWorkflowClient.resumeWorkflow(workflowIds);
        workflow = orkesWorkflowClient.getWorkflow(workflowId, true);
        System.out.println("Workflow status is " + workflow.getStatus());

        //Shutdown workflowClient and taskrunner
        orkesWorkflowClient.close();
        System.exit(0);
    }

    @Builder
    @RequiredArgsConstructor
    @Getter
    public static class Person {
        private final String id;
        private final String name;
        private final String last;
        private final String email;
    }
}
