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
package com.netflix.conductor.test.integration.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.core.events.DefaultEventProcessor;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end test, through the <b>real engine</b>, of the {@code start_agent} {@link
 * com.netflix.conductor.common.metadata.events.EventHandler.Action.Type} — an {@link
 * com.netflix.conductor.common.metadata.events.EventHandler} configured with a {@code start_agent}
 * action, triggered by a real {@link DefaultEventProcessor#handle} call, starting a registered
 * "hello world" agent (a Conductor-native {@link com.netflix.conductor.common.metadata.workflow.WorkflowDef}
 * flagged {@code isAgent}) via {@code SimpleActionProcessor.startAgent()} →
 * {@code WorkflowExecutor.startAgentExecution()}.
 *
 * <p>Reuses the deterministic, LLM-free agent-registration pattern from {@code
 * ConductorAgentEndToEndTest}, but triggers {@code startAgentExecution} through the EventHandler
 * action path instead of the {@code AGENT} task type — so unlike that class, this one does not need
 * {@code conductor.integrations.ai.enabled}: {@code WorkflowExecutorOps.startAgentExecution} is pure
 * {@code core} logic with no dependency on the {@code ai}/agentspan module.
 */
@SpringBootTest(classes = ConductorTestApp.class)
@TestPropertySource(
        locations = "classpath:application-integrationtest.properties",
        properties = {
            "conductor.db.type=redis_standalone",
            "conductor.queue.type=redis_standalone",
            "conductor.app.sweeperThreadCount=1",
            "conductor.app.sweeper.sweepBatchSize=10",
            "conductor.app.sweeper.queuePopTimeout=10"
        })
class EventHandlerStartAgentEndToEndTest {

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:6.2-alpine"))
                    .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("conductor.redis.availability-zone", () -> "us-east-1c");
        registry.add("conductor.redis.data-center-region", () -> "us-east-1");
        registry.add(
                "conductor.redis.workflow-namespace-prefix", () -> "event-handler-start-agent-e2e");
        registry.add(
                "conductor.redis.queue-namespace-prefix", () -> "event-handler-start-agent-e2e");
        registry.add(
                "conductor.redis.hosts",
                () -> "localhost:" + REDIS.getFirstMappedPort() + ":us-east-1c");
        registry.add(
                "conductor.redis-lock.serverAddress",
                () -> "redis://localhost:" + REDIS.getFirstMappedPort());
    }

    @Autowired private MetadataService metadataService;
    @Autowired private ExecutionService executionService;
    @Autowired private DefaultEventProcessor eventProcessor;

    @Test
    void contextLoads() {
        assertNotNull(metadataService);
        assertNotNull(executionService);
        assertNotNull(eventProcessor);
    }
}
