/*
 * Copyright 2022 Conductor Authors.
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
package io.orkes.conductor.client.worker;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;


public class LocalServerWorkflowExecutionTests {
    public static void main(String[] args) {
        ConductorClient client = new ConductorClient("http://localhost:8080/api");
        TaskClient taskClient = new TaskClient(client);
        Iterable<Worker> workers = Arrays.asList(new MyWorker());
        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("simple_task_0", "viren");
        TaskRunnerConfigurer configurer =
                new TaskRunnerConfigurer.Builder(taskClient, workers)
                        .withSleepWhenRetry(100)
                        .withThreadCount(10)
                        .withWorkerNamePrefix("Hello")
                        .withTaskToDomain(taskToDomain)
                        .build();
        configurer.init();
    }

    private static class MyWorker implements Worker {
        @Override
        public String getTaskDefName() {
            return "simple_task_0";
        }

        @Override
        public TaskResult execute(Task task) {
            System.out.println(
                    "Executing "
                            + task.getTaskId()
                            + ":"
                            + task.getPollCount()
                            + "::"
                            + new Date());
            TaskResult result = new TaskResult(task);
            result.getOutputData().put("a", "b");
            if (task.getPollCount() < 2) {
                result.setCallbackAfterSeconds(5);
            } else {
                result.setStatus(TaskResult.Status.COMPLETED);
            }
            return result;
        }
    }
}
