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
package org.conductoross.conductor.core.execution.tasks;

import java.util.List;

import org.springframework.stereotype.Component;

import com.netflix.conductor.sdk.workflow.executor.task.AnnotatedWorkerExecutor;
import com.netflix.conductor.sdk.workflow.executor.task.WorkerConfiguration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ServerTaskWorker {

    private final ServerTaskClient serverTaskClient;

    private final List<AnnotatedSystemTaskWorker> annotatedSystemTaskWorkers;

    private final WorkerConfiguration workerConfiguration;

    public ServerTaskWorker(
            ServerTaskClient serverTaskClient,
            List<AnnotatedSystemTaskWorker> annotatedSystemTaskWorkers,
            SystemTaskWorkerConfiguration workerConfiguration) {
        this.workerConfiguration = workerConfiguration;
        log.info(
                "ServerTaskWorker initialized with annotatedSystemTaskWorkers = {}",
                annotatedSystemTaskWorkers);
        this.serverTaskClient = serverTaskClient;
        this.annotatedSystemTaskWorkers = annotatedSystemTaskWorkers;
    }

    @PostConstruct
    public void run() {
        log.info("ServerTaskWorker::run");
        AnnotatedWorkerExecutor workerExecutor =
                new AnnotatedWorkerExecutor(serverTaskClient, workerConfiguration);
        annotatedSystemTaskWorkers.forEach(workerExecutor::addBean);
        workerExecutor.startPolling();
    }
}
