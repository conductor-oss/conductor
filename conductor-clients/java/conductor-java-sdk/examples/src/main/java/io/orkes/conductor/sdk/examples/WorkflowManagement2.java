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
package io.orkes.conductor.sdk.examples;

import java.time.Duration;
import java.util.Map;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.tasks.Http;
import com.netflix.conductor.sdk.workflow.def.tasks.Wait;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

import io.orkes.conductor.sdk.examples.util.ClientUtil;

//TODO review this example
public class WorkflowManagement2 {
    private final WorkflowClient workflowClient;
    private final TaskClient taskClient;
    private final WorkflowExecutor workflowExecutor;

    public static void main(String[] args) {
        new WorkflowManagement2().execute();
    }

    public WorkflowManagement2() {
        ConductorClient client = ClientUtil.getClient();
        this.workflowClient = new WorkflowClient(client);
        this.taskClient = new TaskClient(client);
        this.workflowExecutor = new WorkflowExecutor(client, 100);
    }

    public String startWorkflow() {
        ConductorWorkflow<?> workflow = new ConductorWorkflow<>(workflowExecutor);
        workflow.setName("workflow_signals_demo");
        workflow.setVersion(1);
        Wait waitForTwoSec = new Wait("wait_for_2_sec", Duration.ofSeconds(2));
        Http httpCall = new Http("call_remote_api");
        httpCall.url("https://orkes-api-tester.orkesconductor.com/api");

        Wait waitForSignal = new Wait("wait_for_signal");

        workflow.add(waitForTwoSec);
        workflow.add(waitForSignal);
        workflow.add(httpCall);

        workflow.registerWorkflow(true);
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setVersion(1);
        request.setName(workflow.getName());
        request.setInput(Map.of());

        return workflowClient.startWorkflow(request);
    }

    public void execute() {
        String workflowId = startWorkflow();
        System.out.println("Started workflow with id " + workflowId);

        try {
            Thread.sleep(3000); // Wait for 3 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        Task lastTask = workflow.getTasks().get(workflow.getTasks().size() - 1);
        System.out.println("Workflow status is " + workflow.getStatus() + " and currently running task is " + lastTask.getReferenceTaskName());

        workflowClient.terminateWorkflow(workflowId, "testing termination");

        // Other operations like retry, update tasks, etc.

        // Example of task completion
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(lastTask.getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setOutputData(Map.of("greetings", "hello from Orkes"));
        taskClient.updateTask(taskResult);

        // Handling workflow lifecycle: terminate, restart, pause, resume
        workflowClient.terminateWorkflow(workflowId, "terminating so we can do a restart");
        workflowClient.restart(workflowId, true);
        workflowClient.pauseWorkflow(workflowId);

        try {
            Thread.sleep(3000); // Simulating a wait
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        workflowClient.resumeWorkflow(workflowId);

        // Search workflow examples
        SearchResult<WorkflowSummary> searchResults = workflowClient.search(0, 100, "", "*", "correlationId = 'correlation_123'");
        System.out.println("Found " + searchResults.getTotalHits() + " executions with correlation_id 'correlation_123'");
        workflowExecutor.shutdown();
    }
}
