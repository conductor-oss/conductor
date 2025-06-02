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
package com.netflix.conductor.gettingstarted;

import java.util.List;

import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

public class HelloWorker implements Worker {

    @Override
    public TaskResult execute(Task task) {
        var taskResult = new TaskResult(task);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.getOutputData().put("message", "Hello World!");
        return taskResult;
    }

    @Override
    public String getTaskDefName() {
        return "hello_task";
    }

    public static void main(String[] args) {
        var client = new ConductorClient("http://localhost:8080/api");
        var taskClient = new TaskClient(client);
        var runnerConfigurer = new TaskRunnerConfigurer
                .Builder(taskClient, List.of(new HelloWorker()))
                .withThreadCount(10)
                .build();
        runnerConfigurer.init();
    }
}
