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
package com.netflix.conductor.sdk.examples.taskdomains;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.sdk.workflow.executor.task.AnnotatedWorkerExecutor;
import com.netflix.conductor.sdk.workflow.executor.task.WorkerConfiguration;

import io.orkes.conductor.sdk.examples.util.ClientUtil;

public class Main {

    public static void main(String[] args) throws IOException {
        setSystemProperties();

        ConductorClient client = ClientUtil.getClient();
        TaskClient taskClient = new TaskClient(client);
        AnnotatedWorkerExecutor workerExecutor = new AnnotatedWorkerExecutor(
                taskClient, new WorkerConfiguration()
        );
        workerExecutor.initWorkers("com.netflix.conductor.sdk.examples.taskdomains");
        workerExecutor.startPolling();
        workerExecutor.shutdown();

        startTaskRunnerWorkers(taskClient);
    }

    private static void startTaskRunnerWorkers(TaskClient taskClient) {
        List<Worker> workers = List.of(new Workers.TaskWorker());
        TaskRunnerConfigurer.Builder builder = new TaskRunnerConfigurer.Builder(taskClient, workers);

        // test-domain-common should be picked up as Task Domain if conductor.worker.all.domain is populated
        // For test-domain-runner to be picked up, conductor.worker.all.domain shouldn't be populated
        Map<String, String> taskToDomains = new HashMap<>();
        taskToDomains.put("task-domain-runner-simple-task", "test-domain-runner");
        Map<String, Integer> taskThreadCount = new HashMap<>();

        TaskRunnerConfigurer taskRunner =
                builder.withThreadCount(2)
                        .withTaskToDomain(taskToDomains)
                        .withTaskThreadCount(taskThreadCount)
                        .withSleepWhenRetry(500)
                        .withWorkerNamePrefix("task-domain")
                        .withUpdateRetryCount(3)
                        .build();

        // Start Polling for tasks and execute them
        taskRunner.init();

        // Optionally, use the shutdown method to stop polling
        // taskRunner.shutdown();
    }


    public static void setSystemProperties() {
        // This is in lieu of setting properties in application.properties
        System.setProperty("conductor.worker.task-domain-property-simple-task.domain", "test-domain-prop");
        // If below line is un-commented, test-domain-common trumps test-domain-runner and test-domain-annotated
        // System.setProperty("conductor.worker.all.domain", "test-domain-common");
    }
}
