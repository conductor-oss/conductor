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
package com.netflix.conductor.sdk.examples.events;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.automator.events.PollCompleted;
import com.netflix.conductor.client.automator.events.PollFailure;
import com.netflix.conductor.client.automator.events.TaskExecutionFailure;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

import io.orkes.conductor.sdk.examples.util.ClientUtil;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Shows how you can create a worker that polls and executes a SIMPLE task
 * and register listeners to expose metrics with micrometer.
 */
@Slf4j
public class EventListenerExample {

    private static final PrometheusMeterRegistry prometheusRegistry =
            new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    public static void main(String[] args) throws IOException {
        var client = ClientUtil.getClient();
        var taskClient = new TaskClient(client);
        var runnerConfigurer = new TaskRunnerConfigurer
                .Builder(taskClient, List.of(new SimpleWorker()))
                .withThreadCount(2)
                .withListener(PollCompleted.class, (e) -> {
                    log.info("Poll Completed {}", e);
                    var timer = prometheusRegistry.timer("poll_completed", "type", e.getTaskType());
                    timer.record(e.getDuration());
                })
                .withListener(PollFailure.class, (e) -> {
                    log.error("Poll Failure {}", e);
                    var counter = prometheusRegistry.counter("poll_failure", "type", e.getTaskType());
                    counter.increment();
                })
                .withListener(TaskExecutionFailure.class, (e) -> {
                    log.error("Task execution Failure {}", e);
                    var counter = prometheusRegistry.counter("execution_failure", "type", e.getTaskType(), "id", e.getTaskId());
                    counter.increment();
                })
                .build();
        runnerConfigurer.init();

        // Expose a /metrics endpoint that will be scrapped by Prometheus
        var server = HttpServer.create(new InetSocketAddress(9991), 0);
        server.createContext("/metrics", (exchange -> {
            var body = prometheusRegistry.scrape();
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.getBytes().length);
            try (var os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        }));

        server.start();
    }

    private static class SimpleWorker implements Worker {

        private static final String TASK_NAME = "simple_task";

        @Override
        public String getTaskDefName() {
            return TASK_NAME;
        }

        @Override
        public TaskResult execute(Task task) {
            // BUSINESS LOGIC GOES HERE
            var result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            return result;
        }

        @Override
        public int getPollingInterval() {
            return 500;
        }
    }
}
