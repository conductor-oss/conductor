/*
 * Copyright 2022 Orkes, Inc.
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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

import io.orkes.conductor.client.http.OrkesAuthentication;

public class Workers {

    private static final Logger LOGGER = LoggerFactory.getLogger(Workers.class);

    private final List<Worker> workers = new ArrayList<>();
    private String rootUri;
    private boolean started = false;
    private String keyId;
    private String secret;
    private ConductorClient client;

    public Workers register(String name, WorkerFn workerFn) {
        workers.add(
                new Worker() {
                    @Override
                    public String getTaskDefName() {
                        return name;
                    }

                    @Override
                    public TaskResult execute(Task task) {
                        return workerFn.execute(task);
                    }

                    @Override
                    public int getPollingInterval() {
                        return 100;
                    }
                });
        return this;
    }

    public Workers rootUri(String rootUri) {
        this.rootUri = rootUri;
        return this;
    }

    public Workers keyId(String keyId) {
        this.keyId = keyId;
        return this;
    }

    public Workers secret(String secret) {
        this.secret = secret;
        return this;
    }

    public Workers apiClient(ConductorClient apiClient) {
        this.client = apiClient;
        return this;
    }

    public Workers startAll() {
        if (rootUri == null) {
            throw new IllegalStateException("RootUri is null");
        }

        if (!started) {
            LOGGER.info("Conductor Server URL: {}", rootUri);
            LOGGER.info("Starting workers : {}", workers);

            if (this.client != null) {
                this.client = new ConductorClient.Builder()
                        .basePath(rootUri)
                        .addHeaderSupplier(new OrkesAuthentication(keyId, secret))
                        .build();
            }

            TaskClient taskClient = new TaskClient(client);
            TaskRunnerConfigurer runnerConfigurer = new TaskRunnerConfigurer.Builder(taskClient, workers)
                    .withThreadCount(Math.max(1, workers.size()))
                    .build();
            runnerConfigurer.init();
            started = true;
        } else {
            LOGGER.warn("Workers have already been started");
        }

        return this;
    }

    public void start(String name, WorkerFn workerFn) {
        workers.add(
                new Worker() {
                    @Override
                    public String getTaskDefName() {
                        return name;
                    }

                    @Override
                    public TaskResult execute(Task task) {
                        return workerFn.execute(task);
                    }
                });
        startAll();
    }
}
