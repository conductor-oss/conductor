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
package com.netflix.conductor.client.metrics.prometheus;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.netflix.conductor.client.events.task.TaskPayloadUsedEvent;
import com.netflix.conductor.client.events.task.TaskResultPayloadSizeEvent;
import com.netflix.conductor.client.events.taskrunner.PollCompleted;
import com.netflix.conductor.client.events.taskrunner.PollFailure;
import com.netflix.conductor.client.events.taskrunner.PollStarted;
import com.netflix.conductor.client.events.taskrunner.TaskExecutionCompleted;
import com.netflix.conductor.client.events.taskrunner.TaskExecutionFailure;
import com.netflix.conductor.client.events.taskrunner.TaskExecutionStarted;
import com.netflix.conductor.client.events.workflow.WorkflowInputPayloadSizeEvent;
import com.netflix.conductor.client.events.workflow.WorkflowPayloadUsedEvent;
import com.netflix.conductor.client.events.workflow.WorkflowStartedEvent;
import com.netflix.conductor.client.metrics.MetricsCollector;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

public class PrometheusMetricsCollector implements MetricsCollector {

    private static final PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    private static final int DEFAULT_PORT = 9991;

    private static final String DEFAULT_ENDPOINT = "/metrics";

    public  void startServer() throws IOException {
        startServer(DEFAULT_PORT, DEFAULT_ENDPOINT);
    }

    public void startServer(int port, String endpoint) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(endpoint, (exchange -> {
            var body = prometheusRegistry.scrape();
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.getBytes().length);
            try (var os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        }));
        server.start();
    }

    @Override
    public void consume(PollFailure e) {
        var timer = prometheusRegistry.timer("poll_failure", "type", e.getTaskType());
        timer.record(e.getDuration());
    }

    @Override
    public void consume(PollCompleted e) {
        var timer = prometheusRegistry.timer("poll_success", "type", e.getTaskType());
        timer.record(e.getDuration());
    }

    @Override
    public void consume(PollStarted e) {
        var counter = prometheusRegistry.counter("poll_started", "type", e.getTaskType());
        counter.increment();
    }

    @Override
    public void consume(TaskExecutionStarted e) {
        var counter = prometheusRegistry.counter("task_execution_started", "type", e.getTaskType());
        counter.increment();
    }

    @Override
    public void consume(TaskExecutionCompleted e) {
        var timer = prometheusRegistry.timer("task_execution_completed", "type", e.getTaskType());
        timer.record(e.getDuration());
    }

    @Override
    public void consume(TaskExecutionFailure e) {
        var timer = prometheusRegistry.timer("task_execution_failure", "type", e.getTaskType());
        timer.record(e.getDuration());
    }

    @Override
    public void consume(TaskPayloadUsedEvent e) {
        //TODO implement
    }

    @Override
    public void consume(TaskResultPayloadSizeEvent e) {
        //TODO implement
    }

    @Override
    public void consume(WorkflowPayloadUsedEvent event) {
        //TODO implement
    }

    @Override
    public void consume(WorkflowInputPayloadSizeEvent event) {
        //TODO implement
    }

    @Override
    public void consume(WorkflowStartedEvent event) {
        //TODO implement
    }
}
