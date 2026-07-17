/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.ai.a2a;

import java.util.HashMap;
import java.util.Map;

import org.conductoross.conductor.ai.model.A2ACallRequest;
import org.conductoross.conductor.ai.model.A2ACallResult;
import org.conductoross.conductor.ai.model.A2ACancelRequest;
import org.conductoross.conductor.ai.model.A2ACancelResult;
import org.conductoross.conductor.ai.tasks.worker.A2AWorkers;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Helpers that model the engine persisting a worker result before the next poll. */
public final class A2AWorkerTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperProvider().getObjectMapper();

    private A2AWorkerTestSupport() {}

    public static Task task(Map<String, Object> input) {
        Task task = new Task();
        task.setTaskId("conductor-task-1");
        task.setInputData(new HashMap<>(input));
        task.setOutputData(new HashMap<>());
        task.setStatus(Task.Status.SCHEDULED);
        return task;
    }

    public static TaskResult invoke(A2AWorkers workers, Task task) {
        return invokeAgent(workers, task).taskResult();
    }

    public static A2ACallResult invokeOutput(A2AWorkers workers, Task task) {
        return invokeAgent(workers, task).output();
    }

    private static Invocation<A2ACallResult> invokeAgent(A2AWorkers workers, Task task) {
        TaskContext.set(task);
        try {
            A2ACallRequest request =
                    OBJECT_MAPPER.convertValue(task.getInputData(), A2ACallRequest.class);
            A2ACallResult output = workers.agent(request);
            TaskResult taskResult = TaskContext.get().getTaskResult();
            apply(task, taskResult);
            return new Invocation<>(output, taskResult);
        } finally {
            TaskContext.clear();
        }
    }

    public static TaskResult invokeCancel(A2AWorkers workers, Task task) {
        return invokeCancelWorker(workers, task).taskResult();
    }

    public static A2ACancelResult invokeCancelOutput(A2AWorkers workers, Task task) {
        return invokeCancelWorker(workers, task).output();
    }

    private static Invocation<A2ACancelResult> invokeCancelWorker(A2AWorkers workers, Task task) {
        TaskContext.set(task);
        try {
            A2ACancelRequest request =
                    OBJECT_MAPPER.convertValue(task.getInputData(), A2ACancelRequest.class);
            A2ACancelResult output = workers.cancelAgent(request);
            TaskResult taskResult = TaskContext.get().getTaskResult();
            apply(task, taskResult);
            return new Invocation<>(output, taskResult);
        } finally {
            TaskContext.clear();
        }
    }

    public static void apply(Task task, TaskResult result) {
        task.setOutputData(new HashMap<>(result.getOutputData()));
        task.setStatus(Task.Status.valueOf(result.getStatus().name()));
        task.setReasonForIncompletion(result.getReasonForIncompletion());
        task.setCallbackAfterSeconds(result.getCallbackAfterSeconds());
        task.setSubWorkflowId(result.getSubWorkflowId());
    }

    private record Invocation<T>(T output, TaskResult taskResult) {}
}
