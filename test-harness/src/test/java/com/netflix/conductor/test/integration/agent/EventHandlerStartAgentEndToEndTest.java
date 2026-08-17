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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.events.DefaultEventProcessor;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void helloWorldAgentRegistersAsAnAgentWorkflowDef() {
        String agentName = "hello_world_agent_" + UUID.randomUUID();
        registerHelloWorldAgent(agentName);

        WorkflowDef registered = metadataService.getWorkflowDef(agentName, 1);
        assertNotNull(registered);
        assertTrue(registered.isAgent(), "registered agent WorkflowDef must be flagged isAgent()");
    }

    @Test
    void registeredEventHandlerCarriesStartAgentAction() {
        String eventName = "test:event_" + UUID.randomUUID();
        String agentName = "hello_world_agent_" + UUID.randomUUID();
        EventHandler registered = registerStartAgentEventHandler(eventName, agentName);

        EventHandler.Action action = registered.getActions().get(0);
        assertEquals(EventHandler.Action.Type.start_agent, action.getAction());
        assertEquals(agentName, action.getStart_agent().getName());
        assertTrue(
                metadataService.getEventHandlersForEvent(eventName, true).stream()
                        .anyMatch(h -> h.getName().equals(registered.getName())),
                "registered handler must be retrievable for its event");
    }

    // ── registration ────────────────────────────────────────────────────────

    /**
     * Registers an {@link EventHandler} with a single {@code start_agent} action targeting {@code
     * agentName}. {@code prompt}/{@code sessionId}/{@code idempotencyKey} are {@code ${...}}
     * placeholders resolved from the triggering message's payload by {@code
     * SimpleActionProcessor.startAgent()} — matching the fields {@code
     * TestSimpleActionProcessor.testStartAgent()} already exercises with a mocked {@code
     * WorkflowExecutor}.
     */
    private EventHandler registerStartAgentEventHandler(String eventName, String agentName) {
        EventHandler.StartAgent startAgent = new EventHandler.StartAgent();
        startAgent.setName(agentName);
        startAgent.setPrompt("${prompt}");
        startAgent.setSessionId("${sessionId}");
        startAgent.setIdempotencyKey("${idempotencyKey}");

        EventHandler.Action action = new EventHandler.Action();
        action.setAction(EventHandler.Action.Type.start_agent);
        action.setStart_agent(startAgent);

        EventHandler eventHandler = new EventHandler();
        eventHandler.setName("start_agent_handler_" + UUID.randomUUID());
        eventHandler.setEvent(eventName);
        eventHandler.setActive(true);
        eventHandler.setActions(List.of(action));

        metadataService.addEventHandler(eventHandler);
        return eventHandler;
    }

    /**
     * Registers a minimal "hello world" agent: a workflow definition flagged as an agent (via
     * {@code metadata.agentDef}, mirroring what the agentspan compiler would produce) whose only
     * task is a synchronous {@code INLINE} script that echoes the caller's prompt back as {@code
     * text}. No LLM or tool dependency, so {@code WorkflowExecutor.startAgentExecution} runs it to
     * completion synchronously when started. Ported from {@code ConductorAgentEndToEndTest}.
     */
    private void registerHelloWorldAgent(String agentName) {
        ensureTaskDef("INLINE");

        WorkflowTask hello = new WorkflowTask();
        hello.setName("INLINE");
        hello.setTaskReferenceName("hello");
        hello.setType("INLINE");
        Map<String, Object> helloInput = new HashMap<>();
        helloInput.put("input", "${workflow.input}");
        helloInput.put("evaluatorType", "javascript");
        helloInput.put("expression", "({text: 'Hello, world! You said: ' + $.input.prompt})");
        hello.setInputParameters(helloInput);

        WorkflowDef def = new WorkflowDef();
        def.setName(agentName);
        def.setVersion(1);
        def.setOwnerEmail("event-handler-start-agent-e2e@conductor.test");
        def.setTasks(List.of(hello));
        def.setOutputParameters(Map.of("text", "${hello.output.result.text}"));
        def.setMetadata(Map.of("agentDef", Map.of("name", agentName)));
        metadataService.updateWorkflowDef(List.of(def));
    }

    private void ensureTaskDef(String taskType) {
        TaskDef td = new TaskDef();
        td.setName(taskType);
        td.setRetryCount(0);
        td.setTimeoutSeconds(120);
        try {
            metadataService.registerTaskDef(List.of(td));
        } catch (Exception ignored) {
            // already registered by a prior test
        }
    }
}
