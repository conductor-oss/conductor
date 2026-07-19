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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.awaitility.Awaitility;
import org.conductoross.conductor.ai.agent.ConductorAgentClient;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartResponse;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentDagService;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;
import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;
import org.conductoross.conductor.common.metadata.agent.CallbackConfig;
import org.conductoross.conductor.common.metadata.agent.CompileResponse;
import org.conductoross.conductor.common.metadata.agent.CreateTrackingWorkflowRequest;
import org.conductoross.conductor.common.metadata.agent.GuardrailConfig;
import org.conductoross.conductor.common.metadata.agent.HandoffConfig;
import org.conductoross.conductor.common.metadata.agent.InjectTaskRequest;
import org.conductoross.conductor.common.metadata.agent.ToolConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.s3.storage.S3PayloadStorage;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.test.config.LocalStackS3Configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real persistence contract for AgentSpan deployment. These assertions intentionally use the same
 * compiler, metadata service, and Redis-backed task-definition registry as production rather than
 * inspecting calls made to mocked collaborators.
 */
@Tag("agentspan-deterministic-e2e")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = ConductorTestApp.class)
@Import(LocalStackS3Configuration.class)
@TestPropertySource(
        locations = "classpath:application-integrationtest.properties",
        properties = {
            "conductor.db.type=redis_standalone",
            "conductor.queue.type=redis_standalone",
            "conductor.app.sweeperThreadCount=1",
            "conductor.app.sweeper.sweepBatchSize=1",
            "conductor.integrations.ai.enabled=true",
            "agentspan.embedded=true",
            "agentspan.skills.enabled=true",
            "conductor.ai.outbound.allow-private-networks=true",
            "conductor.external-payload-storage.type=s3",
            "conductor.external-payload-storage.s3.bucketName="
                    + AgentSpanDeploymentContractEndToEndTest.PAYLOAD_BUCKET,
            "conductor.external-payload-storage.s3.region=us-east-1",
            "conductor.external-payload-storage.s3.use_default_client=false"
        })
class AgentSpanDeploymentContractEndToEndTest {

    static final String PAYLOAD_BUCKET = "agentspan-deployment-e2e-payloads";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:6.2-alpine"))
                    .withExposedPorts(6379);

    @SuppressWarnings("resource")
    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
                    .withServices(LocalStackContainer.Service.S3);

    static {
        REDIS.start();
        LOCALSTACK.start();
        LocalStackS3Configuration.setLocalStackEndpoint(
                LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        createPayloadBucket();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("conductor.redis.availability-zone", () -> "us-east-1c");
        registry.add("conductor.redis.data-center-region", () -> "us-east-1");
        registry.add("conductor.redis.workflow-namespace-prefix", () -> "agentspan-deploy-e2e");
        registry.add("conductor.redis.queue-namespace-prefix", () -> "agentspan-deploy-e2e");
        registry.add(
                "conductor.redis.hosts",
                () -> "localhost:" + REDIS.getFirstMappedPort() + ":us-east-1c");
        registry.add(
                "conductor.redis-lock.serverAddress",
                () -> "redis://localhost:" + REDIS.getFirstMappedPort());
        registry.add("conductor.external-payload-storage.type", () -> "s3");
        registry.add("conductor.external-payload-storage.s3.bucketName", () -> PAYLOAD_BUCKET);
        registry.add("conductor.external-payload-storage.s3.region", () -> "us-east-1");
        registry.add("conductor.external-payload-storage.s3.use_default_client", () -> "false");
    }

    @Autowired private AgentService agentService;
    @Autowired private AgentDagService agentDagService;
    @Autowired private ConductorAgentClient conductorAgentClient;
    @Autowired private ExecutionDAO executionDAO;
    @Autowired private MetadataService metadataService;
    @Autowired private ExternalPayloadStorage externalPayloadStorage;
    @Autowired private WorkflowExecutor workflowExecutor;
    @Autowired private ExecutionService executionService;
    @Autowired private QueueDAO queueDAO;
    @Autowired private AsyncSystemTaskExecutor asyncSystemTaskExecutor;

    @Autowired
    @Qualifier(SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER)
    private Set<WorkflowSystemTask> asyncSystemTasks;

    @LocalServerPort private int port;

    @org.junit.jupiter.api.BeforeEach
    void usesRealPayloadStorage() {
        assertTrue(
                externalPayloadStorage instanceof S3PayloadStorage,
                () ->
                        "AgentSpan deployment E2E must use LocalStack S3, not a test double: "
                                + externalPayloadStorage.getClass().getName());
    }

    @Test
    void swarmDeploymentRegistersOnlyDeclaredWorkersAndKeepsTransferControlsCompilerOwned() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String team = "team_" + suffix;
        String agentA = "agent_a_" + suffix;
        String agentB = "agent_b_" + suffix;
        String lookup = "lookup_customer_" + suffix;
        String similarName = "search_transfer_to_" + agentB + "_notes";
        String condition = "evaluate_handoff_" + suffix;
        String guardrail = "validate_output_" + suffix;

        AgentConfig config =
                AgentConfig.builder()
                        .name(team)
                        .model("openai/gpt-4o-mini")
                        .strategy(AgentConfig.Strategy.SWARM)
                        .credentials(List.of("AGENT_SECRET_" + suffix))
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name(lookup)
                                                .description("Look up a customer")
                                                .toolType("worker")
                                                .config(
                                                        Map.of(
                                                                "credentials",
                                                                List.of("TOOL_SECRET_" + suffix)))
                                                .build(),
                                        ToolConfig.builder()
                                                .name(similarName)
                                                .description("An ordinary user-owned worker")
                                                .toolType("worker")
                                                .build()))
                        .guardrails(
                                List.of(
                                        GuardrailConfig.builder()
                                                .name("output_contract")
                                                .guardrailType("custom")
                                                .taskName(guardrail)
                                                .build()))
                        .handoffs(
                                List.of(
                                        HandoffConfig.builder()
                                                .type("on_condition")
                                                .target(agentB)
                                                .taskName(condition)
                                                .build()))
                        .allowedTransitions(Map.of(team, List.of(agentB), agentA, List.of(agentB)))
                        .agents(
                                List.of(
                                        AgentConfig.builder()
                                                .name(agentA)
                                                .model("openai/gpt-4o-mini")
                                                .build(),
                                        AgentConfig.builder()
                                                .name(agentB)
                                                .model("openai/gpt-4o-mini")
                                                .build()))
                        .build();

        CompileResponse compiled =
                agentService.compile(AgentStartRequest.builder().agentConfig(config).build());
        assertTrue(
                compiled.getRequiredWorkers().containsAll(List.of(lookup, similarName, condition)));
        assertFalse(compiled.getRequiredWorkers().contains(team + "_handoff_check"));
        assertFalse(compiled.getRequiredWorkers().contains(agentA + "_check_transfer"));
        assertFalse(compiled.getRequiredWorkers().contains(team + "_transfer_to_" + agentB));

        AgentStartResponse deployed =
                agentService.deploy(AgentStartRequest.builder().agentConfig(config).build());
        assertEquals(team, deployed.getAgentName());
        assertTrue(
                deployed.getRequiredWorkers().containsAll(List.of(lookup, similarName, condition)));
        assertFalse(deployed.getRequiredWorkers().contains(team + "_handoff_check"));
        assertFalse(deployed.getRequiredWorkers().contains(agentA + "_check_transfer"));
        assertFalse(deployed.getRequiredWorkers().contains(team + "_transfer_to_" + agentB));

        assertEquals(
                List.of("TOOL_SECRET_" + suffix),
                metadataService.getTaskDef(lookup).getRuntimeMetadata());
        assertEquals(
                List.of("AGENT_SECRET_" + suffix),
                metadataService.getTaskDef(condition).getRuntimeMetadata());
        assertEquals(
                List.of("AGENT_SECRET_" + suffix),
                metadataService.getTaskDef(guardrail).getRuntimeMetadata());
        assertThrows(
                NotFoundException.class, () -> metadataService.getTaskDef(team + "_handoff_check"));
        assertThrows(
                NotFoundException.class,
                () -> metadataService.getTaskDef(agentA + "_check_transfer"));
        assertThrows(
                NotFoundException.class,
                () -> metadataService.getTaskDef(team + "_transfer_to_" + agentB));
    }

    @Test
    void legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds() throws Exception {
        String agent = "legacy_backfill_agent_" + UUID.randomUUID().toString().replace('-', '_');
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(agent)
                                        .model("openai/gpt-4o-mini")
                                        .maxTurns(1)
                                        .build())
                        .build());

        AgentStartResponse started =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(agent)
                                .prompt("Create a persisted execution for the legacy backfill")
                                .build());
        Task llm = awaitScheduledLlm(started.getExecutionId());
        completeScriptedLlm(llm, Map.of("finishReason", "STOP", "result", "Done."));
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                awaitAgentTerminal(started.getExecutionId()).getStatus());

        WorkflowDef legacyDefinition = metadataService.getWorkflowDef(agent, null);
        Map<String, Object> legacyMetadata =
                new java.util.LinkedHashMap<>(legacyDefinition.getMetadata());
        legacyMetadata.remove("agent_classifier_backfill_version");
        legacyDefinition.setMetadata(legacyMetadata);
        metadataService.updateWorkflowDef(List.of(legacyDefinition));

        Method backfill =
                AgentService.class.getDeclaredMethod("backfillLegacyAgentExecutionClassifiers");
        backfill.setAccessible(true);
        backfill.invoke(agentService);

        assertEquals(
                2,
                metadataService
                        .getWorkflowDef(agent, null)
                        .getMetadata()
                        .get("agent_classifier_backfill_version"));
    }

    @Test
    void deploymentPersistsSdkLifecycleCallbacksAtTheirConfiguredWorkflowBoundaries() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "callback_agent_" + suffix;
        String tool = "callback_tool_" + suffix;
        String beforeAgent = "before_agent_" + suffix;
        String beforeModel = "before_model_" + suffix;
        String afterModel = "after_model_" + suffix;
        String beforeTool = "before_tool_" + suffix;
        String afterTool = "after_tool_" + suffix;
        String afterAgent = "after_agent_" + suffix;

        AgentConfig config =
                AgentConfig.builder()
                        .name(agent)
                        .model("openai/gpt-4o-mini")
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name(tool)
                                                .description("A real declared worker tool")
                                                .toolType("worker")
                                                .build()))
                        .callbacks(
                                List.of(
                                        CallbackConfig.builder()
                                                .position("before_agent")
                                                .taskName(beforeAgent)
                                                .build(),
                                        CallbackConfig.builder()
                                                .position("before_model")
                                                .taskName(beforeModel)
                                                .build(),
                                        CallbackConfig.builder()
                                                .position("after_model")
                                                .taskName(afterModel)
                                                .build(),
                                        CallbackConfig.builder()
                                                .position("before_tool")
                                                .taskName(beforeTool)
                                                .build(),
                                        CallbackConfig.builder()
                                                .position("after_tool")
                                                .taskName(afterTool)
                                                .build(),
                                        CallbackConfig.builder()
                                                .position("after_agent")
                                                .taskName(afterAgent)
                                                .build()))
                        .build();

        AgentStartResponse deployed =
                agentService.deploy(AgentStartRequest.builder().agentConfig(config).build());
        assertTrue(
                deployed.getRequiredWorkers()
                        .containsAll(
                                List.of(
                                        beforeAgent,
                                        beforeModel,
                                        afterModel,
                                        beforeTool,
                                        afterTool,
                                        afterAgent,
                                        tool)));
        for (String callback :
                List.of(beforeAgent, beforeModel, afterModel, beforeTool, afterTool, afterAgent)) {
            assertEquals(List.of(), metadataService.getTaskDef(callback).getRuntimeMetadata());
        }

        WorkflowDef workflow = metadataService.getWorkflowDef(agent, null);
        assertEquals(beforeAgent, workflow.getTasks().get(0).getName());
        assertTrue(
                taskIndex(workflow, afterAgent) < taskIndex(workflow, agent + "_synth_output"),
                "after_agent runs after the agent loop and before compiler-owned output synthesis");
        WorkflowTask loop =
                workflow.getTasks().stream()
                        .filter(task -> "DO_WHILE".equals(task.getType()))
                        .findFirst()
                        .orElseThrow();
        List<WorkflowTask> loopTasks = loop.getLoopOver();
        assertTrue(loopTasks.stream().anyMatch(task -> beforeModel.equals(task.getName())));
        assertTrue(loopTasks.stream().anyMatch(task -> afterModel.equals(task.getName())));
        WorkflowTask toolRouter =
                loopTasks.stream()
                        .filter(task -> "SWITCH".equals(task.getType()))
                        .filter(task -> task.getTaskReferenceName().endsWith("_tool_router"))
                        .findFirst()
                        .orElseThrow();
        List<WorkflowTask> toolCallTasks = toolRouter.getDecisionCases().get("tool_call");
        assertEquals(beforeTool, toolCallTasks.get(0).getName());
        assertEquals(afterTool, toolCallTasks.get(toolCallTasks.size() - 1).getName());
    }

    @Test
    void hybridTransferControlsStayLlmVisibleButNeverBecomeWorkers() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "hybrid_agent_" + suffix;
        String child = "hybrid_child_" + suffix;
        String worker = "hybrid_lookup_" + suffix;
        String generatedTransfer = agent + "_transfer_to_" + child;
        String generatedCheck = agent + "_check_transfer";

        AgentConfig config =
                AgentConfig.builder()
                        .name(agent)
                        .model("openai/gpt-4o-mini")
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name(worker)
                                                .description("A user-owned tool")
                                                .toolType("worker")
                                                .build()))
                        .agents(
                                List.of(
                                        AgentConfig.builder()
                                                .name(child)
                                                .model("openai/gpt-4o-mini")
                                                .build()))
                        .build();

        CompileResponse compiled =
                agentService.compile(AgentStartRequest.builder().agentConfig(config).build());
        assertTrue(compiled.getRequiredWorkers().contains(worker));
        assertFalse(compiled.getRequiredWorkers().contains(generatedTransfer));
        assertFalse(compiled.getRequiredWorkers().contains(generatedCheck));

        AgentStartResponse deployed =
                agentService.deploy(AgentStartRequest.builder().agentConfig(config).build());
        assertTrue(deployed.getRequiredWorkers().contains(worker));
        assertFalse(deployed.getRequiredWorkers().contains(generatedTransfer));
        assertFalse(deployed.getRequiredWorkers().contains(generatedCheck));
        assertThrows(NotFoundException.class, () -> metadataService.getTaskDef(generatedTransfer));
        assertThrows(NotFoundException.class, () -> metadataService.getTaskDef(generatedCheck));

        WorkflowDef workflow = metadataService.getWorkflowDef(agent, null);
        WorkflowTask loop =
                workflow.getTasks().stream()
                        .filter(task -> "DO_WHILE".equals(task.getType()))
                        .findFirst()
                        .orElseThrow();
        WorkflowTask transferDetector =
                loop.getLoopOver().stream()
                        .filter(task -> generatedCheck.equals(task.getTaskReferenceName()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("INLINE", transferDetector.getType());
        WorkflowTask llm =
                loop.getLoopOver().stream()
                        .filter(task -> "LLM_CHAT_COMPLETE".equals(task.getType()))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> specs =
                (List<Map<String, Object>>) llm.getInputParameters().get("tools");
        assertTrue(specs.stream().anyMatch(spec -> generatedTransfer.equals(spec.get("name"))));
    }

    @Test
    void dagInjectionAndEmbeddedClientStatusUseTheRealRedisExecutionStore() {
        String workflowName = "tracking_agent_" + UUID.randomUUID().toString().replace('-', '_');
        CreateTrackingWorkflowRequest tracking = new CreateTrackingWorkflowRequest();
        tracking.setWorkflowName(workflowName);
        tracking.setInput(Map.of("prompt", "investigate"));
        String executionId = agentDagService.createTrackingWorkflow(tracking).getExecutionId();
        InjectTaskRequest injected = new InjectTaskRequest();
        injected.setTaskDefName("real_lookup");
        injected.setReferenceTaskName("real_lookup_ref");
        injected.setType("SIMPLE");
        injected.setInputData(Map.of("customer", "c-42"));

        String taskId = agentDagService.injectTask(executionId, injected).getTaskId();
        var persisted = executionDAO.getWorkflow(executionId, true);
        assertEquals(1, persisted.getTasks().size());
        assertEquals(taskId, persisted.getTasks().get(0).getTaskId());
        assertEquals("real_lookup", persisted.getTasks().get(0).getTaskDefName());
        assertEquals(
                "real_lookup_ref",
                persisted.getWorkflowDefinition().getTasks().get(0).getTaskReferenceName());

        agentDagService.completeTrackingWorkflow(executionId, Map.of("result", "durably complete"));
        ConductorAgentStatusResponse status = conductorAgentClient.getAgentStatus(executionId);
        assertEquals(ConductorAgentState.COMPLETED, status.getStatus());
        assertTrue(status.isComplete());
        assertEquals(Map.of("result", "durably complete"), status.getOutput());
    }

    @Test
    void embeddedSdkStartPersistsContextAndNeverLeaksCredentialNamesIntoWorkflowInput() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "client_agent_" + suffix;
        String statefulTool = "client_stateful_tool_" + suffix;
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(agent)
                                        .model("openai/gpt-4o-mini")
                                        .credentials(List.of("SHOULD_NOT_BE_WORKFLOW_INPUT"))
                                        .tools(
                                                List.of(
                                                        ToolConfig.builder()
                                                                .name(statefulTool)
                                                                .description("Stateful worker")
                                                                .toolType("worker")
                                                                .stateful(true)
                                                                .build()))
                                        .build())
                        .build());

        ConductorAgentStartRequest request =
                ConductorAgentStartRequest.builder()
                        .name(agent)
                        .prompt("hello")
                        .context(Map.of("tenant", "acme"))
                        .sessionId("session-42")
                        .runId("run-42")
                        .idempotencyKey("client-idempotency-" + suffix)
                        .build();
        ConductorAgentStartResponse first = conductorAgentClient.startAgent(request);
        ConductorAgentStartResponse replay = conductorAgentClient.startAgent(request);

        assertEquals(first.getExecutionId(), replay.getExecutionId());
        Workflow workflow = executionDAO.getWorkflow(first.getExecutionId(), true).toWorkflow();
        assertEquals(Map.of("tenant", "acme"), workflow.getInput().get("context"));
        assertEquals("session-42", workflow.getInput().get("session_id"));
        assertFalse(workflow.getInput().containsKey("credentials"));
        assertFalse(workflow.getInput().containsKey("SHOULD_NOT_BE_WORKFLOW_INPUT"));
        assertTrue(first.getRequiredWorkers().contains(statefulTool));
    }

    @Test
    void controlPlaneRestContractCompilesAndDeploysAnAgentOverTheRealServer() throws Exception {
        String agent = "http_agent_" + UUID.randomUUID().toString().replace('-', '_');
        Map<String, Object> agentConfig =
                Map.of("name", agent, "model", "openai/gpt-4o-mini", "maxTurns", 1);
        Map<String, Object> request = Map.of("agentConfig", agentConfig);

        HttpResponse<String> compile = jsonRequest("POST", "/api/agent/compile", request);
        assertEquals(200, compile.statusCode());
        JsonNode compiled = JSON.readTree(compile.body());
        assertEquals(agent, compiled.path("workflowDef").path("name").asText());
        assertTrue(compiled.path("requiredWorkers").isArray());

        HttpResponse<String> deploy = jsonRequest("POST", "/api/agent/deploy", request);
        assertEquals(200, deploy.statusCode());
        assertEquals(agent, JSON.readTree(deploy.body()).path("agentName").asText());

        HttpResponse<String> listed = plainRequest("GET", "/api/agent/list", null, null);
        assertEquals(200, listed.statusCode());
        assertTrue(
                JSON.readTree(listed.body()).findValuesAsText("name").contains(agent),
                () -> "deployed agent is missing from the actual REST listing: " + listed.body());

        HttpResponse<String> definition =
                plainRequest("GET", "/api/agent/definitions/" + agent, null, null);
        assertEquals(200, definition.statusCode());
        assertEquals(agent, JSON.readTree(definition.body()).path("name").asText());

        HttpResponse<String> details = plainRequest("GET", "/api/agent/" + agent, null, null);
        assertEquals(200, details.statusCode());
        assertEquals(agent, JSON.readTree(details.body()).path("name").asText());

        HttpResponse<String> delete = plainRequest("DELETE", "/api/agent/" + agent, null, null);
        assertEquals(200, delete.statusCode());
        HttpResponse<String> missing = plainRequest("GET", "/api/agent/" + agent, null, null);
        assertEquals(404, missing.statusCode());
    }

    @Test
    void controlPlaneStartsAnInlineAgentAndInspectsAPlanUsingTheProductionCompiler()
            throws Exception {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String inlineAgent = "inline_http_agent_" + suffix;
        Map<String, Object> inlineConfig =
                Map.of("name", inlineAgent, "model", "openai/gpt-4o-mini", "maxTurns", 1);

        HttpResponse<String> started =
                jsonRequest(
                        "POST",
                        "/api/agent/start",
                        Map.of(
                                "agentConfig",
                                inlineConfig,
                                "prompt",
                                "Answer through the real inline start path",
                                "context",
                                Map.of("tenant", "inline-acme"),
                                "timeoutSeconds",
                                45));
        assertEquals(200, started.statusCode());
        String executionId = JSON.readTree(started.body()).path("executionId").asText();
        assertFalse(executionId.isBlank());

        Task llm = awaitScheduledLlm(executionId);
        completeScriptedLlm(
                llm, Map.of("finishReason", "STOP", "result", "Inline start completed."));
        Workflow completed = awaitAgentTerminal(executionId);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
        assertEquals("Inline start completed.", completed.getOutput().get("result"));
        assertEquals(
                "inline-acme", ((Map<?, ?>) completed.getOutput().get("context")).get("tenant"));
        assertTrue(
                executionDAO
                                .getWorkflow(executionId, true)
                                .getWorkflowDefinition()
                                .getTimeoutSeconds()
                        > 0,
                "the inline REST start must produce an executable workflow definition");

        String planTool = "inspect_plan_tool_" + suffix;
        Map<String, Object> planConfig =
                Map.of(
                        "name",
                        "inspect_plan_" + suffix,
                        "model",
                        "openai/gpt-4o-mini",
                        "strategy",
                        "plan_execute",
                        "planner",
                        Map.of("name", "planner_" + suffix, "model", "openai/gpt-4o-mini"),
                        "tools",
                        List.of(
                                Map.of(
                                        "name",
                                        planTool,
                                        "description",
                                        "Real plan tool",
                                        "toolType",
                                        "worker")));
        Map<String, Object> plan =
                Map.of(
                        "steps",
                        List.of(
                                Map.of(
                                        "id",
                                        "lookup",
                                        "operations",
                                        List.of(
                                                Map.of(
                                                        "tool",
                                                        planTool,
                                                        "args",
                                                        Map.of("customerId", "c-42"))))));
        HttpResponse<String> inspected =
                jsonRequest(
                        "POST",
                        "/api/agent/inspect-plan",
                        Map.of("agentConfig", planConfig, "plan", plan));
        assertEquals(200, inspected.statusCode());
        JsonNode inspectedJson = JSON.readTree(inspected.body());
        assertTrue(
                inspectedJson.path("error").isMissingNode() || inspectedJson.path("error").isNull(),
                inspected.body());
        assertEquals(1, inspectedJson.path("stats").path("stepCount").asInt());
        assertTrue(
                inspectedJson
                        .path("workflowDef")
                        .path("tasks")
                        .get(0)
                        .path("name")
                        .asText()
                        .equals(planTool),
                inspected.body());

        HttpResponse<String> invalidInspection =
                jsonRequest(
                        "POST",
                        "/api/agent/inspect-plan",
                        Map.of("agentConfig", planConfig, "plan", Map.of("steps", List.of())));
        assertEquals(200, invalidInspection.statusCode());
        assertTrue(
                JSON.readTree(invalidInspection.body()).path("error").asText().contains("steps"),
                invalidInspection.body());
    }

    @Test
    void sequentialAndParallelStrategiesExecuteRealChildWorkflowsAndPreserveContext() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String sequential = "sequential_team_" + suffix;
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(sequential)
                                        .model("openai/gpt-4o-mini")
                                        .strategy(AgentConfig.Strategy.SEQUENTIAL)
                                        .synthesize(false)
                                        .agents(
                                                List.of(
                                                        AgentConfig.builder()
                                                                .name("researcher_" + suffix)
                                                                .model("openai/gpt-4o-mini")
                                                                .build(),
                                                        AgentConfig.builder()
                                                                .name("reviewer_" + suffix)
                                                                .model("openai/gpt-4o-mini")
                                                                .build()))
                                        .build())
                        .build());

        AgentStartResponse sequentialRun =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(sequential)
                                .prompt("Research then review")
                                .context(Map.of("tenant", "sequential-acme"))
                                .build());
        Task researcher = awaitScheduledLlmAcrossSubworkflows(sequentialRun.getExecutionId());
        assertTrue(researcher.getReferenceTaskName().contains("researcher_" + suffix));
        completeScriptedLlm(
                researcher, Map.of("finishReason", "STOP", "result", "Research findings."));
        Task reviewer = awaitScheduledLlmAcrossSubworkflows(sequentialRun.getExecutionId());
        assertTrue(reviewer.getReferenceTaskName().contains("reviewer_" + suffix));
        assertTrue(
                JSON.valueToTree(reviewer.getInputData()).toString().contains("Research findings."),
                "the later child must receive the prior child's durable output");
        completeScriptedLlm(
                reviewer, Map.of("finishReason", "STOP", "result", "Reviewed findings."));
        Workflow sequentialCompleted = awaitAgentTerminal(sequentialRun.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, sequentialCompleted.getStatus());
        assertEquals("Reviewed findings.", sequentialCompleted.getOutput().get("result"));
        assertEquals(
                "sequential-acme",
                ((Map<?, ?>) sequentialCompleted.getOutput().get("context")).get("tenant"));

        String parallel = "parallel_team_" + suffix;
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(parallel)
                                        .model("openai/gpt-4o-mini")
                                        .strategy(AgentConfig.Strategy.PARALLEL)
                                        .synthesize(false)
                                        .agents(
                                                List.of(
                                                        AgentConfig.builder()
                                                                .name("facts_" + suffix)
                                                                .model("openai/gpt-4o-mini")
                                                                .build(),
                                                        AgentConfig.builder()
                                                                .name("risks_" + suffix)
                                                                .model("openai/gpt-4o-mini")
                                                                .build()))
                                        .build())
                        .build());

        AgentStartResponse parallelRun =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(parallel)
                                .prompt("Analyze in parallel")
                                .context(Map.of("tenant", "parallel-acme"))
                                .build());
        Task firstParallel = awaitScheduledLlmAcrossSubworkflows(parallelRun.getExecutionId());
        completeScriptedLlm(
                firstParallel, Map.of("finishReason", "STOP", "result", "Facts result."));
        Task secondParallel = awaitScheduledLlmAcrossSubworkflows(parallelRun.getExecutionId());
        completeScriptedLlm(
                secondParallel, Map.of("finishReason", "STOP", "result", "Risks result."));
        Workflow parallelCompleted = awaitAgentTerminal(parallelRun.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, parallelCompleted.getStatus());
        String parallelResult = String.valueOf(parallelCompleted.getOutput().get("result"));
        assertTrue(parallelResult.contains("Facts result."));
        assertTrue(parallelResult.contains("Risks result."));
        assertEquals(
                "parallel-acme",
                ((Map<?, ?>) parallelCompleted.getOutput().get("context")).get("tenant"));
    }

    @Test
    void roundRobinAndRandomStrategiesSelectRealSubagentsWithinTheirTurnBudgets() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String first = "round_first_" + suffix;
        String second = "round_second_" + suffix;
        String roundRobin = "round_robin_" + suffix;
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(roundRobin)
                                        .model("openai/gpt-4o-mini")
                                        .strategy(AgentConfig.Strategy.ROUND_ROBIN)
                                        .maxTurns(2)
                                        .synthesize(false)
                                        .agents(
                                                List.of(
                                                        AgentConfig.builder()
                                                                .name(first)
                                                                .model("openai/gpt-4o-mini")
                                                                .build(),
                                                        AgentConfig.builder()
                                                                .name(second)
                                                                .model("openai/gpt-4o-mini")
                                                                .build()))
                                        .build())
                        .build());
        AgentStartResponse roundRun =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(roundRobin)
                                .prompt("Take turns exactly once")
                                .build());
        Task firstTurn = awaitScheduledLlmAcrossSubworkflows(roundRun.getExecutionId());
        completeScriptedLlm(firstTurn, Map.of("finishReason", "STOP", "result", "First turn."));
        Task secondTurn = awaitScheduledLlmAcrossSubworkflows(roundRun.getExecutionId());
        assertFalse(
                firstTurn.getWorkflowInstanceId().equals(secondTurn.getWorkflowInstanceId()),
                "round-robin must advance to a different child workflow on the second turn");
        completeScriptedLlm(secondTurn, Map.of("finishReason", "STOP", "result", "Second turn."));
        Workflow roundCompleted = awaitAgentTerminal(roundRun.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, roundCompleted.getStatus());
        assertTrue(
                String.valueOf(roundCompleted.getOutput().get("result")).contains("First turn."));
        assertTrue(
                String.valueOf(roundCompleted.getOutput().get("result")).contains("Second turn."));

        String random = "random_team_" + suffix;
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(random)
                                        .model("openai/gpt-4o-mini")
                                        .strategy(AgentConfig.Strategy.RANDOM)
                                        .maxTurns(1)
                                        .synthesize(false)
                                        .agents(
                                                List.of(
                                                        AgentConfig.builder()
                                                                .name("random_a_" + suffix)
                                                                .model("openai/gpt-4o-mini")
                                                                .build(),
                                                        AgentConfig.builder()
                                                                .name("random_b_" + suffix)
                                                                .model("openai/gpt-4o-mini")
                                                                .build()))
                                        .build())
                        .build());
        AgentStartResponse randomRun =
                agentService.start(
                        AgentStartRequest.builder().name(random).prompt("One random turn").build());
        Task randomTurn = awaitScheduledLlmAcrossSubworkflows(randomRun.getExecutionId());
        assertTrue(
                randomTurn.getReferenceTaskName().contains("random_a_" + suffix)
                        || randomTurn.getReferenceTaskName().contains("random_b_" + suffix));
        completeScriptedLlm(randomTurn, Map.of("finishReason", "STOP", "result", "Random turn."));
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                awaitAgentTerminal(randomRun.getExecutionId()).getStatus());
    }

    @Test
    void graphStructuredAgentFansOutMergesReducerStateAndRunsItsJoinWorker() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "graph_agent_" + suffix;
        String factsWorker = "graph_facts_" + suffix;
        String risksWorker = "graph_risks_" + suffix;
        String joinWorker = "graph_join_" + suffix;
        GraphStateWorker facts = new GraphStateWorker(factsWorker, "facts");
        GraphStateWorker risks = new GraphStateWorker(risksWorker, "risks");
        GraphStateWorker join = new GraphStateWorker(joinWorker, "joined");
        TaskRunnerConfigurer runner = startWorkers(facts, risks, join);
        try {
            Map<String, Object> graph =
                    Map.of(
                            "input_key",
                            "request",
                            "nodes",
                            List.of(
                                    Map.of("name", "facts", "_worker_ref", factsWorker),
                                    Map.of("name", "risks", "_worker_ref", risksWorker),
                                    Map.of("name", "join", "_worker_ref", joinWorker)),
                            "edges",
                            List.of(
                                    Map.of("source", "__start__", "target", "facts"),
                                    Map.of("source", "__start__", "target", "risks"),
                                    Map.of("source", "facts", "target", "join"),
                                    Map.of("source", "risks", "target", "join"),
                                    Map.of("source", "join", "target", "__end__")));
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(agent)
                                            .model("openai/gpt-4o-mini")
                                            .metadata(
                                                    Map.of(
                                                            "_graph_structure",
                                                            graph,
                                                            "_reducers",
                                                            Map.of("items", "add")))
                                            .build())
                            .build());

            AgentStartResponse execution =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Analyze this graph request")
                                    .build());
            Workflow completed = awaitAgentTerminal(execution.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
            assertEquals(1, facts.invocationCount.get());
            assertEquals(1, risks.invocationCount.get());
            assertEquals(1, join.invocationCount.get());
            @SuppressWarnings("unchecked")
            Map<String, Object> joinState = (Map<String, Object>) join.lastInput.get().get("state");
            assertEquals(List.of("facts", "risks"), joinState.get("items"));
            assertEquals("joined", completed.getOutput().get("result"));
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void graphStructuredAgentUsesItsRealConditionalRouterAndExecutesOnlyTheSelectedBranch() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "conditional_graph_" + suffix;
        String classifyWorker = "graph_classify_" + suffix;
        String routerWorker = "graph_route_" + suffix;
        String selectedWorker = "graph_selected_" + suffix;
        String skippedWorker = "graph_skipped_" + suffix;
        GraphStateWorker classify = new GraphStateWorker(classifyWorker, "classified");
        GraphDecisionWorker router = new GraphDecisionWorker(routerWorker, "selected");
        GraphStateWorker selected = new GraphStateWorker(selectedWorker, "selected result");
        GraphStateWorker skipped = new GraphStateWorker(skippedWorker, "skipped result");
        TaskRunnerConfigurer runner = startWorkers(classify, router, selected, skipped);
        try {
            Map<String, Object> graph =
                    Map.of(
                            "nodes",
                            List.of(
                                    Map.of("name", "classify", "_worker_ref", classifyWorker),
                                    Map.of("name", "selected", "_worker_ref", selectedWorker),
                                    Map.of("name", "skipped", "_worker_ref", skippedWorker)),
                            "edges",
                            List.of(Map.of("source", "__start__", "target", "classify")),
                            "conditional_edges",
                            List.of(
                                    Map.of(
                                            "source",
                                            "classify",
                                            "_router_ref",
                                            routerWorker,
                                            "targets",
                                            Map.of("selected", "selected", "skipped", "skipped"))));
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(agent)
                                            .model("openai/gpt-4o-mini")
                                            .metadata(Map.of("_graph_structure", graph))
                                            .build())
                            .build());
            AgentStartResponse execution =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Select the intended branch")
                                    .build());
            Workflow completed = awaitAgentTerminal(execution.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
            assertEquals(1, classify.invocationCount.get());
            assertEquals(1, router.invocationCount.get());
            assertEquals(1, selected.invocationCount.get());
            assertEquals(0, skipped.invocationCount.get());
            assertEquals("selected result", completed.getOutput().get("result"));
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void graphLlmNodeRunsItsPrepModelFinishPipelineAndHonorsTheSkipBranch() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "llm_graph_" + suffix;
        String prepWorker = "graph_llm_prep_" + suffix;
        String finishWorker = "graph_llm_finish_" + suffix;
        GraphLlmPrepWorker prep = new GraphLlmPrepWorker(prepWorker);
        GraphLlmFinishWorker finish = new GraphLlmFinishWorker(finishWorker);
        TaskRunnerConfigurer runner = startWorkers(prep, finish);
        try {
            Map<String, Object> graph =
                    Map.of(
                            "nodes",
                            List.of(
                                    Map.of(
                                            "name",
                                            "analyze",
                                            "_worker_ref",
                                            prepWorker,
                                            "_llm_node",
                                            true,
                                            "_llm_prep_ref",
                                            prepWorker,
                                            "_llm_finish_ref",
                                            finishWorker)),
                            "edges",
                            List.of(
                                    Map.of("source", "__start__", "target", "analyze"),
                                    Map.of("source", "analyze", "target", "__end__")));
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(agent)
                                            .model("openai/gpt-4o-mini")
                                            .metadata(Map.of("_graph_structure", graph))
                                            .tools(
                                                    List.of(
                                                            ToolConfig.builder()
                                                                    .name(prepWorker)
                                                                    .description(
                                                                            "Prepare graph LLM input")
                                                                    .toolType("worker")
                                                                    .build(),
                                                            ToolConfig.builder()
                                                                    .name(finishWorker)
                                                                    .description(
                                                                            "Apply graph LLM output")
                                                                    .toolType("worker")
                                                                    .build()))
                                            .build())
                            .build());

            AgentStartResponse modelRun =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Use the graph model path")
                                    .build());
            Task llm = awaitScheduledLlm(modelRun.getExecutionId());
            completeScriptedLlm(llm, Map.of("finishReason", "STOP", "result", "Model analysis."));
            Workflow modelCompleted = awaitAgentTerminal(modelRun.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, modelCompleted.getStatus());
            assertEquals("Model analysis.", modelCompleted.getOutput().get("result"));
            assertEquals(1, finish.invocationCount.get());

            AgentStartResponse skipRun =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("skip the graph model path")
                                    .build());
            Workflow skipped = awaitAgentTerminal(skipRun.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, skipped.getStatus());
            assertEquals("Precomputed graph result.", skipped.getOutput().get("result"));
            assertEquals(
                    1,
                    finish.invocationCount.get(),
                    "skip branch must not execute the finish worker or create an LLM task");
            assertEquals(2, prep.invocationCount.get());
            assertFalse(
                    executionService
                            .getExecutionStatus(skipRun.getExecutionId(), true)
                            .getTasks()
                            .stream()
                            .anyMatch(task -> "LLM_CHAT_COMPLETE".equals(task.getTaskType())));
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void graphDynamicFanOutCreatesAndJoinsRealRuntimeWorkerBranches() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "dynamic_graph_" + suffix;
        String dispatchWorker = "graph_dispatch_" + suffix;
        String routerWorker = "graph_dynamic_router_" + suffix;
        String firstWorker = "graph_dynamic_first_" + suffix;
        String secondWorker = "graph_dynamic_second_" + suffix;
        GraphStateWorker dispatch = new GraphStateWorker(dispatchWorker, "dispatch");
        GraphDynamicRouterWorker router = new GraphDynamicRouterWorker(routerWorker);
        GraphStateWorker first = new GraphStateWorker(firstWorker, "first");
        GraphStateWorker second = new GraphStateWorker(secondWorker, "second");
        TaskRunnerConfigurer runner = startWorkers(dispatch, router, first, second);
        try {
            Map<String, Object> graph =
                    Map.of(
                            "nodes",
                            List.of(
                                    Map.of("name", "dispatch", "_worker_ref", dispatchWorker),
                                    Map.of("name", "first", "_worker_ref", firstWorker),
                                    Map.of("name", "second", "_worker_ref", secondWorker)),
                            "edges",
                            List.of(Map.of("source", "__start__", "target", "dispatch")),
                            "conditional_edges",
                            List.of(
                                    Map.of(
                                            "source",
                                            "dispatch",
                                            "_router_ref",
                                            routerWorker,
                                            "_dynamic_fanout",
                                            true,
                                            "targets",
                                            Map.of("first", "first", "second", "second"))));
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(agent)
                                            .model("openai/gpt-4o-mini")
                                            .metadata(
                                                    Map.of(
                                                            "_graph_structure",
                                                            graph,
                                                            "_reducers",
                                                            Map.of("items", "add")))
                                            .build())
                            .build());
            AgentStartResponse execution =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Fan out to both graph workers")
                                    .build());
            Workflow completed = awaitAgentTerminal(execution.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
            assertEquals(1, dispatch.invocationCount.get());
            assertEquals(1, router.invocationCount.get());
            assertEquals(1, first.invocationCount.get());
            assertEquals(1, second.invocationCount.get());
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void graphCycleUsesTheBoundedDoWhileStateBridgeAndExitsOnTheRouterDecision() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "cycle_graph_" + suffix;
        String stepWorker = "cycle_step_" + suffix;
        String routerWorker = "cycle_router_" + suffix;
        GraphStateWorker step = new GraphStateWorker(stepWorker, "loop step");
        GraphLoopRouterWorker router = new GraphLoopRouterWorker(routerWorker);
        TaskRunnerConfigurer runner = startWorkers(step, router);
        try {
            Map<String, Object> graph =
                    Map.of(
                            "input_key",
                            "request",
                            "recursion_limit",
                            4,
                            "nodes",
                            List.of(Map.of("name", "step", "_worker_ref", stepWorker)),
                            "edges",
                            List.of(Map.of("source", "__start__", "target", "step")),
                            "conditional_edges",
                            List.of(
                                    Map.of(
                                            "source",
                                            "step",
                                            "_router_ref",
                                            routerWorker,
                                            "targets",
                                            Map.of("continue", "step", "exit", "__end__"))));
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(agent)
                                            .model("openai/gpt-4o-mini")
                                            .metadata(Map.of("_graph_structure", graph))
                                            .build())
                            .build());

            AgentStartResponse execution =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Run the bounded graph cycle")
                                    .build());
            Workflow completed = awaitAgentTerminal(execution.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
            assertEquals(2, step.invocationCount.get());
            assertEquals(2, router.invocationCount.get());
            assertEquals(
                    0,
                    queueDAO.getSize(stepWorker),
                    "the exit decision must leave no unpolled loop work behind");
            assertTrue(
                    completed.getTasks().stream()
                            .anyMatch(task -> "DO_WHILE".equals(task.getTaskType())));
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void graphMidStreamFanOutJoinsTheRealBranchesAndPassesReducedStateToItsSuccessor() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "mid_fanout_graph_" + suffix;
        String prepareWorker = "mid_fanout_prepare_" + suffix;
        String factsWorker = "mid_fanout_facts_" + suffix;
        String risksWorker = "mid_fanout_risks_" + suffix;
        String joinWorker = "mid_fanout_join_" + suffix;
        GraphStateWorker prepare = new GraphStateWorker(prepareWorker, "prepared");
        GraphStateWorker facts = new GraphStateWorker(factsWorker, "facts");
        GraphStateWorker risks = new GraphStateWorker(risksWorker, "risks");
        GraphStateWorker join = new GraphStateWorker(joinWorker, "joined");
        TaskRunnerConfigurer runner = startWorkers(prepare, facts, risks, join);
        try {
            Map<String, Object> graph =
                    Map.of(
                            "input_key",
                            "request",
                            "nodes",
                            List.of(
                                    Map.of("name", "prepare", "_worker_ref", prepareWorker),
                                    Map.of("name", "facts", "_worker_ref", factsWorker),
                                    Map.of("name", "risks", "_worker_ref", risksWorker),
                                    Map.of("name", "join", "_worker_ref", joinWorker)),
                            "edges",
                            List.of(
                                    Map.of("source", "__start__", "target", "prepare"),
                                    Map.of("source", "prepare", "target", "facts"),
                                    Map.of("source", "prepare", "target", "risks"),
                                    Map.of("source", "facts", "target", "join"),
                                    Map.of("source", "risks", "target", "join"),
                                    Map.of("source", "join", "target", "__end__")));
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(agent)
                                            .model("openai/gpt-4o-mini")
                                            .metadata(
                                                    Map.of(
                                                            "_graph_structure",
                                                            graph,
                                                            "_reducers",
                                                            Map.of("items", "add")))
                                            .build())
                            .build());

            AgentStartResponse execution =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Prepare then fan out")
                                    .build());
            Workflow completed = awaitAgentTerminal(execution.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
            assertEquals(1, prepare.invocationCount.get());
            assertEquals(1, facts.invocationCount.get());
            assertEquals(1, risks.invocationCount.get());
            assertEquals(1, join.invocationCount.get());
            @SuppressWarnings("unchecked")
            Map<String, Object> joinedState =
                    (Map<String, Object>) join.lastInput.get().get("state");
            assertEquals(List.of("facts", "risks"), joinedState.get("items"));
            assertEquals("joined", completed.getOutput().get("result"));
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void graphHumanNodeWaitsForStructuredHumanInputAndMergesItIntoStateWithoutAnLlm() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "human_graph_" + suffix;
        String unusedNodeWorker = "human_graph_node_" + suffix;
        Map<String, Object> graph =
                Map.of(
                        "input_key",
                        "request",
                        "nodes",
                        List.of(
                                Map.of(
                                        "name",
                                        "review",
                                        "_worker_ref",
                                        unusedNodeWorker,
                                        "_human_node",
                                        true,
                                        "_human_prompt",
                                        "Provide the approval and reviewer")),
                        "edges",
                        List.of(
                                Map.of("source", "__start__", "target", "review"),
                                Map.of("source", "review", "target", "__end__")));
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(agent)
                                        .model("openai/gpt-4o-mini")
                                        .metadata(Map.of("_graph_structure", graph))
                                        .build())
                        .build());

        AgentStartResponse execution =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(agent)
                                .prompt("Review the requested change")
                                .build());
        Task human =
                awaitTaskAcrossSubworkflows(
                        execution.getExecutionId(), "HUMAN", Task.Status.IN_PROGRESS);
        assertTrue(
                JSON.valueToTree(human.getInputData())
                        .path("response_schema")
                        .path("properties")
                        .has("response"));
        assertFalse(
                executionService
                        .getExecutionStatus(execution.getExecutionId(), true)
                        .getTasks()
                        .stream()
                        .anyMatch(task -> "LLM_CHAT_COMPLETE".equals(task.getTaskType())));

        TaskResult response = new TaskResult(human);
        response.setStatus(TaskResult.Status.COMPLETED);
        response.setOutputData(Map.of("approved", true, "reviewer", "sam"));
        workflowExecutor.updateTask(response);

        Workflow completed = awaitAgentTerminal(execution.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
        String result = String.valueOf(completed.getOutput().get("result"));
        assertTrue(result.contains("approved"));
        assertTrue(result.contains("reviewer"));
        assertFalse(
                completed.getTasks().stream()
                        .anyMatch(task -> "LLM_CHAT_COMPLETE".equals(task.getTaskType())),
                "structured human fields must skip graph-node LLM normalization");
        assertEquals(
                0,
                queueDAO.getSize(unusedNodeWorker),
                "a graph HUMAN node must not dispatch its declarative worker reference");
    }

    @Test
    void graphSubgraphNodeRunsTheInlineChildWorkflowAndReturnsItsStateToTheParent() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String parent = "parent_subgraph_" + suffix;
        String child = "child_subgraph_" + suffix;
        String prepWorker = "subgraph_prep_" + suffix;
        String childWorker = "subgraph_child_" + suffix;
        String finishWorker = "subgraph_finish_" + suffix;
        GraphSubgraphPrepWorker prep = new GraphSubgraphPrepWorker(prepWorker);
        GraphStateWorker childStep = new GraphStateWorker(childWorker, "child");
        GraphSubgraphFinishWorker finish = new GraphSubgraphFinishWorker(finishWorker);
        TaskRunnerConfigurer runner = startWorkers(prep, childStep, finish);
        try {
            Map<String, Object> childGraph =
                    Map.of(
                            "_is_subgraph",
                            true,
                            "nodes",
                            List.of(Map.of("name", "child_step", "_worker_ref", childWorker)),
                            "edges",
                            List.of(
                                    Map.of("source", "__start__", "target", "child_step"),
                                    Map.of("source", "child_step", "target", "__end__")));
            AgentConfig childConfig =
                    AgentConfig.builder()
                            .name(child)
                            .model("openai/gpt-4o-mini")
                            .metadata(Map.of("_graph_structure", childGraph))
                            .tools(
                                    List.of(
                                            ToolConfig.builder()
                                                    .name(childWorker)
                                                    .description("Run the child graph step")
                                                    .toolType("worker")
                                                    .build()))
                            .build();
            Map<String, Object> parentGraph =
                    Map.of(
                            "input_key",
                            "request",
                            "nodes",
                            List.of(
                                    Map.of(
                                            "name",
                                            "delegate",
                                            "_worker_ref",
                                            prepWorker,
                                            "_subgraph_node",
                                            true,
                                            "_subgraph_prep_ref",
                                            prepWorker,
                                            "_subgraph_finish_ref",
                                            finishWorker)),
                            "edges",
                            List.of(
                                    Map.of("source", "__start__", "target", "delegate"),
                                    Map.of("source", "delegate", "target", "__end__")));
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("_graph_structure", parentGraph);
            metadata.put("_subgraph_configs", Map.of("delegate", childConfig));
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(parent)
                                            .model("openai/gpt-4o-mini")
                                            .metadata(metadata)
                                            .tools(
                                                    List.of(
                                                            ToolConfig.builder()
                                                                    .name(prepWorker)
                                                                    .description(
                                                                            "Prepare child state")
                                                                    .toolType("worker")
                                                                    .build(),
                                                            ToolConfig.builder()
                                                                    .name(childWorker)
                                                                    .description("Run child state")
                                                                    .toolType("worker")
                                                                    .build(),
                                                            ToolConfig.builder()
                                                                    .name(finishWorker)
                                                                    .description(
                                                                            "Merge child state")
                                                                    .toolType("worker")
                                                                    .build()))
                                            .build())
                            .build());

            AgentStartResponse execution =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(parent)
                                    .prompt("Delegate this request to the child graph")
                                    .build());
            Workflow completed = awaitAgentTerminal(execution.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
            assertEquals(1, prep.invocationCount.get());
            assertEquals(1, childStep.invocationCount.get());
            assertEquals(1, finish.invocationCount.get());
            assertEquals("subgraph result", completed.getOutput().get("result"));
            @SuppressWarnings("unchecked")
            Map<String, Object> childState =
                    (Map<String, Object>) finish.lastInput.get().get("subgraph_result");
            assertEquals(List.of("child"), childState.get("items"));
            assertFalse(
                    completed.getTasks().stream()
                            .anyMatch(task -> "LLM_CHAT_COMPLETE".equals(task.getTaskType())));
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void frameworkRawConfigsPersistNormalizeAndRunThroughTheRegisteredAgentPath() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String openAiAgent = "openai_sdk_" + suffix;
        Map<String, Object> openAiRaw = new java.util.LinkedHashMap<>();
        openAiRaw.put("name", openAiAgent);
        openAiRaw.put("model", "gpt-4o-mini");
        openAiRaw.put("instructions", "Use provider-native web search before answering.");
        openAiRaw.put("modelSettings", Map.of("temperature", 0.25, "maxTokens", 96));
        openAiRaw.put("tools", List.of(Map.of("_type", "WebSearchTool")));
        AgentStartResponse openAiDeployment =
                agentService.deploy(
                        AgentStartRequest.builder()
                                .framework("openai")
                                .rawConfig(openAiRaw)
                                .build());
        assertTrue(openAiDeployment.getRequiredWorkers().isEmpty());
        WorkflowDef storedOpenAi = metadataService.getWorkflowDef(openAiAgent, null);
        assertEquals("openai", storedOpenAi.getMetadata().get("agent_sdk"));
        assertEquals(openAiRaw, storedOpenAi.getMetadata().get("agentDef"));
        @SuppressWarnings("unchecked")
        Map<String, Object> normalizedOpenAi =
                (Map<String, Object>) storedOpenAi.getMetadata().get("normalizedAgentDef");
        assertEquals("openai/gpt-4o-mini", normalizedOpenAi.get("model"));

        AgentStartResponse openAiExecution =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(openAiAgent)
                                .prompt("Find the current release notes")
                                .build());
        Task openAiLlm = awaitScheduledLlm(openAiExecution.getExecutionId());
        assertEquals(true, openAiLlm.getInputData().get("webSearch"));
        assertEquals(0.25, openAiLlm.getInputData().get("temperature"));
        assertEquals(96, openAiLlm.getInputData().get("maxTokens"));
        completeScriptedLlm(
                openAiLlm, Map.of("finishReason", "STOP", "result", "OpenAI framework result."));
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                awaitAgentTerminal(openAiExecution.getExecutionId()).getStatus());

        String googleAgent = "google_adk_sdk_" + suffix;
        Map<String, Object> googleRaw = new java.util.LinkedHashMap<>();
        googleRaw.put("name", googleAgent);
        googleRaw.put("model", "openai/gpt-4o-mini");
        googleRaw.put("global_instruction", "Follow the organization policy.");
        googleRaw.put("instruction", "Search before answering.");
        googleRaw.put("tools", List.of(Map.of("_type", "GoogleSearchTool")));
        AgentStartResponse googleDeployment =
                agentService.deploy(
                        AgentStartRequest.builder()
                                .framework("google_adk")
                                .rawConfig(googleRaw)
                                .build());
        assertTrue(googleDeployment.getRequiredWorkers().isEmpty());

        AgentStartResponse googleExecution =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(googleAgent)
                                .prompt("Research the service status")
                                .build());
        Task googleLlm = awaitScheduledLlm(googleExecution.getExecutionId());
        assertEquals(true, googleLlm.getInputData().get("webSearch"));
        assertTrue(
                String.valueOf(googleLlm.getInputData().get("messages"))
                        .contains("Follow the organization policy."));
        completeScriptedLlm(
                googleLlm,
                Map.of("finishReason", "STOP", "result", "Google ADK framework result."));
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                awaitAgentTerminal(googleExecution.getExecutionId()).getStatus());
        WorkflowDef storedGoogle = metadataService.getWorkflowDef(googleAgent, null);
        assertEquals("google_adk", storedGoogle.getMetadata().get("agent_sdk"));
        assertEquals(0, queueDAO.getSize("google_search"));
    }

    @Test
    void openAiSdkAgentToolNormalizesItsChildAndWaitsForTheRealChildWorkflow() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String parent = "openai_agent_tool_parent_" + suffix;
        String child = "openai_agent_tool_child_" + suffix;
        String tool = "delegate_sdk_research_" + suffix;
        Map<String, Object> childRaw =
                Map.of(
                        "name",
                        child,
                        "model",
                        "gpt-4o-mini",
                        "instructions",
                        "Return a concise research finding.",
                        "tools",
                        List.of());
        Map<String, Object> parentRaw =
                Map.of(
                        "name",
                        parent,
                        "model",
                        "gpt-4o-mini",
                        "instructions",
                        "Delegate research when necessary.",
                        "tools",
                        List.of(
                                Map.of(
                                        "_type",
                                        "AgentTool",
                                        "name",
                                        tool,
                                        "description",
                                        "Delegate to the SDK child agent.",
                                        "agent",
                                        childRaw)));
        agentService.deploy(
                AgentStartRequest.builder().framework("openai").rawConfig(parentRaw).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> persistedChildDefinition =
                (Map<String, Object>)
                        metadataService.getWorkflowDef(child, null).getMetadata().get("agentDef");
        assertEquals(
                "openai/gpt-4o-mini",
                persistedChildDefinition.get("model"),
                "deploy must normalize and compile the child before the parent can call it");

        AgentStartResponse execution =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(parent)
                                .prompt("Delegate this SDK-shaped request")
                                .build());
        Task parentLlm = awaitScheduledLlm(execution.getExecutionId());
        completeScriptedLlm(
                parentLlm,
                Map.of(
                        "finishReason",
                        "TOOL_CALLS",
                        "result",
                        "",
                        "toolCalls",
                        List.of(
                                Map.of(
                                        "taskReferenceName",
                                        "sdk_agent_tool_call",
                                        "name",
                                        tool,
                                        "type",
                                        "SUB_WORKFLOW",
                                        "inputParameters",
                                        Map.of("request", "Research the durable result")))));

        Task childLlm = awaitScheduledLlmAcrossSubworkflows(execution.getExecutionId());
        assertTrue(childLlm.getReferenceTaskName().contains(child));
        completeScriptedLlm(
                childLlm, Map.of("finishReason", "STOP", "result", "SDK child research complete."));
        Task parentFollowup = awaitScheduledLlm(execution.getExecutionId());
        completeScriptedLlm(
                parentFollowup,
                Map.of("finishReason", "STOP", "result", "Parent used the SDK child result."));

        Workflow completed = awaitAgentTerminal(execution.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
        Task childTask = taskByType(completed, "SUB_WORKFLOW");
        assertEquals(Task.Status.COMPLETED, childTask.getStatus());
        assertTrue(childTask.getSubWorkflowId() != null && !childTask.getSubWorkflowId().isBlank());
        assertEquals(
                "SDK child research complete.",
                executionService
                        .getExecutionStatus(childTask.getSubWorkflowId(), true)
                        .getOutput()
                        .get("result"));
        assertEquals("Parent used the SDK child result.", completed.getOutput().get("result"));
    }

    @Test
    void langGraphSdkShapeNormalizesAStateGraphAndExecutesItsRealNodeWorkers() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "langgraph_sdk_" + suffix;
        String prepareWorker = "langgraph_prepare_" + suffix;
        String finishWorker = "langgraph_finish_" + suffix;
        GraphStateWorker prepare = new GraphStateWorker(prepareWorker, "prepared");
        GraphStateWorker finish = new GraphStateWorker(finishWorker, "finished");
        TaskRunnerConfigurer runner = startWorkers(prepare, finish);
        try {
            Map<String, Object> graph =
                    Map.of(
                            "input_key",
                            "request",
                            "nodes",
                            List.of(
                                    Map.of("name", "prepare", "_worker_ref", prepareWorker),
                                    Map.of("name", "finish", "_worker_ref", finishWorker)),
                            "edges",
                            List.of(
                                    Map.of("source", "__start__", "target", "prepare"),
                                    Map.of("source", "prepare", "target", "finish"),
                                    Map.of("source", "finish", "target", "__end__")));
            Map<String, Object> raw =
                    Map.of("name", agent, "model", "openai/gpt-4o-mini", "_graph", graph);
            AgentStartResponse deployment =
                    agentService.deploy(
                            AgentStartRequest.builder()
                                    .framework("langgraph")
                                    .rawConfig(raw)
                                    .build());
            assertTrue(
                    deployment
                            .getRequiredWorkers()
                            .containsAll(List.of(prepareWorker, finishWorker)));
            WorkflowDef stored = metadataService.getWorkflowDef(agent, null);
            assertEquals("langgraph", stored.getMetadata().get("agent_sdk"));
            @SuppressWarnings("unchecked")
            Map<String, Object> normalized =
                    (Map<String, Object>) stored.getMetadata().get("normalizedAgentDef");
            assertTrue(normalized.containsKey("metadata"));

            AgentStartResponse execution =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Run the SDK graph")
                                    .build());
            Workflow completed = awaitAgentTerminal(execution.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
            assertEquals(1, prepare.invocationCount.get());
            assertEquals(1, finish.invocationCount.get());
            assertEquals("finished", completed.getOutput().get("result"));
            @SuppressWarnings("unchecked")
            Map<String, Object> finishState =
                    (Map<String, Object>) finish.lastInput.get().get("state");
            assertEquals(List.of("prepared"), finishState.get("items"));
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void hybridGeneratedTransferStartsTheChildWithoutQueuingAControlWorker() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "hybrid_runtime_" + suffix;
        String child = "hybrid_child_" + suffix;
        String ordinaryWorker = "hybrid_ordinary_" + suffix;
        String transfer = agent + "_transfer_to_" + child;
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(agent)
                                        .model("openai/gpt-4o-mini")
                                        .tools(
                                                List.of(
                                                        ToolConfig.builder()
                                                                .name(ordinaryWorker)
                                                                .description("An ordinary worker")
                                                                .toolType("worker")
                                                                .build()))
                                        .agents(
                                                List.of(
                                                        AgentConfig.builder()
                                                                .name(child)
                                                                .model("openai/gpt-4o-mini")
                                                                .build()))
                                        .build())
                        .build());
        AgentStartResponse execution =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(agent)
                                .prompt("Transfer to the specialist")
                                .build());
        Task source = awaitScheduledLlm(execution.getExecutionId());
        completeScriptedLlm(
                source,
                Map.of(
                        "finishReason",
                        "TOOL_CALLS",
                        "result",
                        "",
                        "toolCalls",
                        List.of(
                                Map.of(
                                        "taskReferenceName",
                                        "transfer_call",
                                        "name",
                                        transfer,
                                        "type",
                                        "SIMPLE",
                                        "inputParameters",
                                        Map.of()))));
        Task target = awaitScheduledLlmAcrossSubworkflows(execution.getExecutionId());
        assertTrue(target.getReferenceTaskName().contains(child));
        completeScriptedLlm(
                target, Map.of("finishReason", "STOP", "result", "Hybrid child response."));
        Workflow completed = awaitAgentTerminal(execution.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
        assertEquals(0, queueDAO.getSize(transfer));
        assertEquals(0, queueDAO.getSize(ordinaryWorker));
        assertTrue(
                String.valueOf(completed.getOutput().get("result"))
                        .contains("Hybrid child response."));
    }

    @Test
    void hybridOrdinaryWorkerCallRunsNormallyWithoutStartingAnUnselectedChild() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "hybrid_worker_runtime_" + suffix;
        String child = "hybrid_worker_child_" + suffix;
        String ordinaryWorker = "hybrid_worker_" + suffix;
        RecordingWorker worker = new RecordingWorker(ordinaryWorker);
        TaskRunnerConfigurer runner = startWorker(worker);
        try {
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(agent)
                                            .model("openai/gpt-4o-mini")
                                            .tools(
                                                    List.of(
                                                            ToolConfig.builder()
                                                                    .name(ordinaryWorker)
                                                                    .description(
                                                                            "Look up a customer")
                                                                    .toolType("worker")
                                                                    .build()))
                                            .agents(
                                                    List.of(
                                                            AgentConfig.builder()
                                                                    .name(child)
                                                                    .model("openai/gpt-4o-mini")
                                                                    .build()))
                                            .maxTurns(3)
                                            .build())
                            .build());
            AgentStartResponse execution =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Use the ordinary worker, not the specialist")
                                    .build());
            Task firstLlm = awaitScheduledLlm(execution.getExecutionId());
            completeScriptedLlm(
                    firstLlm,
                    Map.of(
                            "finishReason",
                            "TOOL_CALLS",
                            "result",
                            "",
                            "toolCalls",
                            List.of(
                                    Map.of(
                                            "taskReferenceName",
                                            "ordinary_call",
                                            "name",
                                            ordinaryWorker,
                                            "type",
                                            "SIMPLE",
                                            "inputParameters",
                                            Map.of("customerId", "hybrid-customer")))));
            Task secondLlm = awaitScheduledLlm(execution.getExecutionId());
            completeScriptedLlm(
                    secondLlm,
                    Map.of("finishReason", "STOP", "result", "Hybrid worker result used."));
            Workflow completed = awaitAgentTerminal(execution.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
            assertEquals(1, worker.invocationCount.get());
            assertEquals("hybrid-customer", worker.lastInput.get().get("customerId"));
            @SuppressWarnings("unchecked")
            Map<String, Object> hybridResult =
                    (Map<String, Object>) completed.getOutput().get("result");
            assertEquals("Hybrid worker result used.", hybridResult.get("direct"));
            assertEquals(null, hybridResult.get(child));
            assertFalse(
                    completed.getTasks().stream()
                            .anyMatch(task -> "SUB_WORKFLOW".equals(task.getTaskType())),
                    "without a generated transfer the hybrid child must not start");
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void workerRouterAndManualSelectionUseRealWorkersAndHumanResponses() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String routerAgent = "router_team_" + suffix;
        String routerTask = "choose_specialist_" + suffix;
        String specialist = "specialist_" + suffix;
        SequencedRouterWorker routerWorker = new SequencedRouterWorker(routerTask, specialist);
        TaskRunnerConfigurer routerRunner = startWorker(routerWorker);
        try {
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(routerAgent)
                                            .model("openai/gpt-4o-mini")
                                            .strategy(AgentConfig.Strategy.ROUTER)
                                            .router(
                                                    org.conductoross.conductor.common.metadata.agent
                                                            .WorkerRef.builder()
                                                            .taskName(routerTask)
                                                            .build())
                                            .maxTurns(3)
                                            .synthesize(false)
                                            .agents(
                                                    List.of(
                                                            AgentConfig.builder()
                                                                    .name(specialist)
                                                                    .model("openai/gpt-4o-mini")
                                                                    .build()))
                                            .build())
                            .build());
            AgentStartResponse routed =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(routerAgent)
                                    .prompt("Route this request")
                                    .build());
            Task routedLlm = awaitScheduledLlmAcrossSubworkflows(routed.getExecutionId());
            completeScriptedLlm(
                    routedLlm, Map.of("finishReason", "STOP", "result", "Specialist answer."));
            Workflow routedCompleted = awaitAgentTerminal(routed.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, routedCompleted.getStatus());
            assertEquals(2, routerWorker.invocationCount.get());
            assertTrue(
                    routerWorker
                            .inputs
                            .get(1)
                            .get("conversation")
                            .toString()
                            .contains("Specialist answer."));
            assertTrue(
                    String.valueOf(routedCompleted.getOutput().get("result"))
                            .contains("Specialist answer."));
        } finally {
            routerRunner.shutdown();
        }

        String manualAgent = "manual_team_" + suffix;
        String manualSpecialist = "manual_specialist_" + suffix;
        ManualSelectionWorker selectionWorker =
                new ManualSelectionWorker(manualAgent + "_process_selection");
        TaskRunnerConfigurer manualRunner = startWorker(selectionWorker);
        try {
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(manualAgent)
                                            .model("openai/gpt-4o-mini")
                                            .strategy(AgentConfig.Strategy.MANUAL)
                                            .maxTurns(1)
                                            .synthesize(false)
                                            .agents(
                                                    List.of(
                                                            AgentConfig.builder()
                                                                    .name(manualSpecialist)
                                                                    .model("openai/gpt-4o-mini")
                                                                    .build()))
                                            .build())
                            .build());
            AgentStartResponse manual =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(manualAgent)
                                    .prompt("Wait for a real operator choice")
                                    .build());
            Task human =
                    awaitTaskAcrossSubworkflows(
                            manual.getExecutionId(), "HUMAN", Task.Status.IN_PROGRESS);
            assertTrue(
                    JSON.valueToTree(human.getInputData())
                            .path("response_schema")
                            .path("properties")
                            .path("selected")
                            .path("enum")
                            .toString()
                            .contains(manualSpecialist));
            TaskResult humanResponse = new TaskResult(human);
            humanResponse.setStatus(TaskResult.Status.COMPLETED);
            humanResponse.setOutputData(Map.of("selected", manualSpecialist));
            workflowExecutor.updateTask(humanResponse);

            Task manualLlm = awaitScheduledLlmAcrossSubworkflows(manual.getExecutionId());
            completeScriptedLlm(
                    manualLlm, Map.of("finishReason", "STOP", "result", "Manual answer."));
            Workflow manualCompleted = awaitAgentTerminal(manual.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, manualCompleted.getStatus());
            assertEquals(1, selectionWorker.invocationCount.get());
            assertEquals(manualSpecialist, selectionWorker.selected.get());
            assertTrue(
                    String.valueOf(manualCompleted.getOutput().get("result"))
                            .contains("Manual answer."));
        } finally {
            manualRunner.shutdown();
        }
    }

    @Test
    void staticPlanExecutesThroughPlanAndCompileAndARealDynamicWorkerWithoutCallingAnLlm() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String harness = "plan_execute_" + suffix;
        String tool = "planned_lookup_" + suffix;
        RecordingWorker worker = new RecordingWorker(tool);
        TaskRunnerConfigurer runner = startWorker(worker);
        try {
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(harness)
                                            .model("openai/gpt-4o-mini")
                                            .strategy(AgentConfig.Strategy.PLAN_EXECUTE)
                                            .planner(
                                                    AgentConfig.builder()
                                                            .name("planner_" + suffix)
                                                            .model("openai/gpt-4o-mini")
                                                            .build())
                                            .tools(
                                                    List.of(
                                                            ToolConfig.builder()
                                                                    .name(tool)
                                                                    .description(
                                                                            "Look up a planned customer")
                                                                    .toolType("worker")
                                                                    .build()))
                                            .build())
                            .build());
            Map<String, Object> plan =
                    Map.of(
                            "steps",
                            List.of(
                                    Map.of(
                                            "id",
                                            "lookup",
                                            "operations",
                                            List.of(
                                                    Map.of(
                                                            "tool",
                                                            tool,
                                                            "args",
                                                            Map.of("customerId", "plan-c-42"))))));
            AgentStartResponse execution =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(harness)
                                    .prompt("Execute the supplied plan")
                                    .context(Map.of("tenant", "plan-acme"))
                                    .staticPlan(plan)
                                    .build());
            Workflow completed = awaitAgentTerminal(execution.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
            assertEquals(1, worker.invocationCount.get());
            assertEquals("plan-c-42", worker.lastInput.get().get("customerId"));
            assertFalse(
                    executionService
                            .getExecutionStatus(execution.getExecutionId(), true)
                            .getTasks()
                            .stream()
                            .anyMatch(task -> "LLM_CHAT_COMPLETE".equals(task.getTaskType())),
                    "a usable static plan must bypass the planner LLM entirely");
            assertTrue(
                    String.valueOf(completed.getOutput().get("result")).contains("Alice"),
                    () ->
                            "the dynamic plan must surface its normalized worker output: "
                                    + completed.getOutput());
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void invalidStaticPlanFailsClosedToItsFallbackWithoutCreatingAnUnknownWorkerTask() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String harness = "plan_fallback_" + suffix;
        String declaredTool = "declared_plan_tool_" + suffix;
        String unknownTool = "hallucinated_plan_tool_" + suffix;
        String fallback = "plan_fallback_agent_" + suffix;
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(harness)
                                        .model("openai/gpt-4o-mini")
                                        .strategy(AgentConfig.Strategy.PLAN_EXECUTE)
                                        .planner(
                                                AgentConfig.builder()
                                                        .name("planner_" + suffix)
                                                        .model("openai/gpt-4o-mini")
                                                        .build())
                                        .fallback(
                                                AgentConfig.builder()
                                                        .name(fallback)
                                                        .model("openai/gpt-4o-mini")
                                                        .maxTurns(1)
                                                        .build())
                                        .tools(
                                                List.of(
                                                        ToolConfig.builder()
                                                                .name(declaredTool)
                                                                .description(
                                                                        "The only permitted plan tool")
                                                                .toolType("worker")
                                                                .build()))
                                        .build())
                        .build());

        Map<String, Object> invalidPlan =
                Map.of(
                        "steps",
                        List.of(
                                Map.of(
                                        "id",
                                        "invalid",
                                        "operations",
                                        List.of(
                                                Map.of(
                                                        "tool",
                                                        unknownTool,
                                                        "args",
                                                        Map.of("input", "must not dispatch"))))));
        AgentStartResponse started =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(harness)
                                .prompt("Execute the invalid plan safely")
                                .staticPlan(invalidPlan)
                                .build());

        Task fallbackLlm = awaitScheduledLlmAcrossSubworkflows(started.getExecutionId());
        assertTrue(
                fallbackLlm.getReferenceTaskName().contains(fallback),
                "the configured fallback—not the skipped planner—must recover the invalid plan");
        completeScriptedLlm(
                fallbackLlm,
                Map.of("finishReason", "STOP", "result", "Fallback repaired the plan."));
        Workflow completed = awaitAgentTerminal(started.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
        assertTrue(
                String.valueOf(completed.getOutput().get("result")).contains("Fallback repaired"));
        assertEquals(
                0,
                queueDAO.getSize(unknownTool),
                "an unknown plan tool must never become a SIMPLE task that no worker polls");
    }

    @Test
    void registeredAgentModelOverrideAppliesOnlyToTheRunningExecution() {
        String agent = "model_override_" + UUID.randomUUID().toString().replace('-', '_');
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(agent)
                                        .model("openai/gpt-4o-mini")
                                        .maxTurns(1)
                                        .build())
                        .build());

        AgentStartResponse started =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(agent)
                                .model("anthropic/claude-sonnet-4-20250514")
                                .prompt("Use the per-execution model only")
                                .build());
        Task overriddenLlm = awaitScheduledLlm(started.getExecutionId());
        assertEquals("anthropic", overriddenLlm.getInputData().get("llmProvider"));
        assertEquals("claude-sonnet-4-20250514", overriddenLlm.getInputData().get("model"));
        completeScriptedLlm(
                overriddenLlm,
                Map.of("finishReason", "STOP", "result", "Override execution complete."));
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                awaitAgentTerminal(started.getExecutionId()).getStatus());

        WorkflowDef deployed = metadataService.getWorkflowDef(agent, null);
        WorkflowTask storedLlm =
                deployed.getTasks().stream()
                        .filter(task -> "LLM_CHAT_COMPLETE".equals(task.getType()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("openai", storedLlm.getInputParameters().get("llmProvider"));
        assertEquals("gpt-4o-mini", storedLlm.getInputParameters().get("model"));
    }

    @Test
    void completedAgentExecutionRestartsThroughTheControlPlaneAndRunsANewTurn() throws Exception {
        String agent = "restartable_agent_" + UUID.randomUUID().toString().replace('-', '_');
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(agent)
                                        .model("openai/gpt-4o-mini")
                                        .maxTurns(1)
                                        .build())
                        .build());
        AgentStartResponse started =
                agentService.start(
                        AgentStartRequest.builder().name(agent).prompt("Run once").build());
        Task originalTurn = awaitScheduledLlm(started.getExecutionId());
        completeScriptedLlm(
                originalTurn, Map.of("finishReason", "STOP", "result", "Original completion."));
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                awaitAgentTerminal(started.getExecutionId()).getStatus());

        HttpResponse<String> restart =
                plainRequest(
                        "POST",
                        "/api/agent/executions/"
                                + started.getExecutionId()
                                + "/restart?useLatestDefinitions=false",
                        null,
                        null);
        assertEquals(200, restart.statusCode());
        Task restartedTurn = awaitScheduledLlm(started.getExecutionId());
        assertFalse(originalTurn.getTaskId().equals(restartedTurn.getTaskId()));
        completeScriptedLlm(
                restartedTurn, Map.of("finishReason", "STOP", "result", "Restart completion."));
        Workflow restarted = awaitAgentTerminal(started.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, restarted.getStatus());
        assertEquals("Restart completion.", restarted.getOutput().get("result"));
    }

    @Test
    void generatePdfToolRunsAsARealSystemTaskAndNeverRequiresAWorker() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "pdf_agent_" + suffix;
        String pdfTool = "generate_pdf_" + suffix;
        AgentStartResponse deployed =
                agentService.deploy(
                        AgentStartRequest.builder()
                                .agentConfig(
                                        AgentConfig.builder()
                                                .name(agent)
                                                .model("openai/gpt-4o-mini")
                                                .maxTurns(2)
                                                .tools(
                                                        List.of(
                                                                ToolConfig.builder()
                                                                        .name(pdfTool)
                                                                        .description(
                                                                                "Render markdown to a PDF")
                                                                        .toolType("generate_pdf")
                                                                        .inputSchema(
                                                                                Map.of(
                                                                                        "type",
                                                                                        "object",
                                                                                        "properties",
                                                                                        Map.of(
                                                                                                "markdown",
                                                                                                Map
                                                                                                        .of(
                                                                                                                "type",
                                                                                                                "string")),
                                                                                        "required",
                                                                                        List.of(
                                                                                                "markdown")))
                                                                        .config(
                                                                                Map.of(
                                                                                        "taskType",
                                                                                        "GENERATE_PDF",
                                                                                        "pageSize",
                                                                                        "A4"))
                                                                        .build()))
                                                .build())
                                .build());
        assertFalse(deployed.getRequiredWorkers().contains(pdfTool));
        assertThrows(NotFoundException.class, () -> metadataService.getTaskDef(pdfTool));

        AgentStartResponse execution =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(agent)
                                .prompt("Render a concise report")
                                .build());
        Task firstLlm = awaitScheduledLlm(execution.getExecutionId());
        completeScriptedLlm(
                firstLlm,
                Map.of(
                        "finishReason",
                        "TOOL_CALLS",
                        "result",
                        "",
                        "toolCalls",
                        List.of(
                                Map.of(
                                        "taskReferenceName",
                                        "pdf_call",
                                        "name",
                                        pdfTool,
                                        "type",
                                        "SIMPLE",
                                        "inputParameters",
                                        Map.of(
                                                "markdown",
                                                "# Real system-task report\n\nGenerated in E2E.")))));
        Task pdf =
                awaitTaskAcrossSubworkflows(
                        execution.getExecutionId(), "GENERATE_PDF", Task.Status.COMPLETED);
        assertTrue(
                String.valueOf(pdf.getOutputData()).contains("application/pdf"),
                () ->
                        "real PDF system-task output is missing its media contract: "
                                + pdf.getOutputData());
        Task finalLlm = awaitScheduledLlm(execution.getExecutionId());
        completeScriptedLlm(
                finalLlm, Map.of("finishReason", "STOP", "result", "PDF report is ready."));
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                awaitAgentTerminal(execution.getExecutionId()).getStatus());
        assertEquals(0, queueDAO.getSize(pdfTool));
    }

    @Test
    void sdkServerSideToolFamiliesRemainLlmVisibleWithoutCreatingWorkerDefinitions() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "server_side_tools_" + suffix;
        List<ToolConfig> tools =
                List.of(
                        ToolConfig.builder()
                                .name("http_" + suffix)
                                .description("HTTP tool")
                                .toolType("http")
                                .config(Map.of("url", "http://127.0.0.1/unused", "method", "GET"))
                                .build(),
                        ToolConfig.builder()
                                .name("pdf_" + suffix)
                                .description("PDF tool")
                                .toolType("generate_pdf")
                                .config(Map.of("taskType", "GENERATE_PDF", "pageSize", "A4"))
                                .build(),
                        ToolConfig.builder()
                                .name("rag_" + suffix)
                                .description("RAG search tool")
                                .toolType("rag_search")
                                .config(
                                        Map.of(
                                                "vectorDB",
                                                "test-vector-db",
                                                "index",
                                                "test-index",
                                                "namespace",
                                                "test"))
                                .build(),
                        ToolConfig.builder()
                                .name("human_" + suffix)
                                .description("Human response tool")
                                .toolType("human")
                                .build(),
                        ToolConfig.builder()
                                .name("messages_" + suffix)
                                .description("Workflow messages tool")
                                .toolType("pull_workflow_messages")
                                .config(Map.of("batchSize", 2))
                                .build(),
                        ToolConfig.builder()
                                .name("cli_" + suffix)
                                .description("CLI tool")
                                .toolType("cli")
                                .config(Map.of("allowedCommands", List.of("echo")))
                                .build());
        AgentStartResponse deployed =
                agentService.deploy(
                        AgentStartRequest.builder()
                                .agentConfig(
                                        AgentConfig.builder()
                                                .name(agent)
                                                .model("openai/gpt-4o-mini")
                                                .tools(tools)
                                                .maxTurns(1)
                                                .build())
                                .build());
        assertTrue(deployed.getRequiredWorkers().isEmpty(), deployed::toString);
        for (ToolConfig tool : tools) {
            assertThrows(NotFoundException.class, () -> metadataService.getTaskDef(tool.getName()));
        }

        WorkflowDef workflow = metadataService.getWorkflowDef(agent, null);
        WorkflowTask loop =
                workflow.getTasks().stream()
                        .filter(task -> "DO_WHILE".equals(task.getType()))
                        .findFirst()
                        .orElseThrow();
        WorkflowTask llm =
                loop.getLoopOver().stream()
                        .filter(
                                task ->
                                        "LLM_CHAT_COMPLETE".equals(task.getType())
                                                && task.getInputParameters().get("tools")
                                                        instanceof List<?>)
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> specifications =
                (List<Map<String, Object>>) llm.getInputParameters().get("tools");
        for (ToolConfig tool : tools) {
            assertTrue(
                    specifications.stream()
                            .anyMatch(spec -> tool.getName().equals(spec.get("name"))),
                    "the model must see the SDK server-side tool " + tool.getName());
        }

        AgentStartResponse execution =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(agent)
                                .prompt("Do not call a tool; just prove the specs are usable")
                                .build());
        Task llmTurn = awaitScheduledLlm(execution.getExecutionId());
        completeScriptedLlm(llmTurn, Map.of("finishReason", "STOP", "result", "No tool needed."));
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                awaitAgentTerminal(execution.getExecutionId()).getStatus());
    }

    @Test
    void secretAndProviderRestContractsExposeReadOnlyBackendsWithoutLeakingServerErrors()
            throws Exception {
        String secretName = "controller_secret_" + UUID.randomUUID().toString().replace('-', '_');
        String secretValue = "real-storage-value-" + UUID.randomUUID();

        HttpResponse<String> put =
                plainRequest("PUT", "/api/secrets/" + secretName, secretValue, "text/plain");
        assertEquals(501, put.statusCode());
        assertTrue(put.body().contains("read-only"));
        HttpResponse<String> exists =
                plainRequest("GET", "/api/secrets/" + secretName + "/exists", null, null);
        assertEquals(200, exists.statusCode());
        assertFalse(JSON.readTree(exists.body()).asBoolean());
        HttpResponse<String> get = plainRequest("GET", "/api/secrets/" + secretName, null, null);
        assertEquals(404, get.statusCode());

        HttpResponse<String> list = plainRequest("POST", "/api/secrets", "", "text/plain");
        assertEquals(200, list.statusCode());
        assertFalse(jsonArrayContains(JSON.readTree(list.body()), secretName));
        HttpResponse<String> grantable = plainRequest("GET", "/api/secrets", null, null);
        assertEquals(200, grantable.statusCode());
        assertFalse(jsonArrayContains(JSON.readTree(grantable.body()), secretName));
        HttpResponse<String> metadata = plainRequest("GET", "/api/secrets/v2", null, null);
        assertEquals(200, metadata.statusCode());
        assertFalse(JSON.readTree(metadata.body()).findValuesAsText("name").contains(secretName));

        HttpResponse<String> invalid =
                plainRequest("PUT", "/api/secrets/invalid%20secret", "x", "text/plain");
        assertEquals(400, invalid.statusCode());
        HttpResponse<String> empty =
                plainRequest("PUT", "/api/secrets/" + secretName, "", "text/plain");
        assertEquals(400, empty.statusCode());
        HttpResponse<String> absent =
                plainRequest("GET", "/api/secrets/not_present_" + secretName, null, null);
        assertEquals(404, absent.statusCode());

        HttpResponse<String> providers = plainRequest("GET", "/api/providers/status", null, null);
        assertEquals(200, providers.statusCode());
        JsonNode providerStatus = JSON.readTree(providers.body());
        assertFalse(providerStatus.path("managedByHost").asBoolean());
        assertTrue(
                providerStatus.path("providers").isArray()
                        && providerStatus.path("providers").size() >= 10);
        assertTrue(providerStatus.path("providers").findValuesAsText("name").contains("ollama"));

        HttpResponse<String> delete =
                plainRequest("DELETE", "/api/secrets/" + secretName, null, null);
        assertEquals(501, delete.statusCode());
        HttpResponse<String> gone =
                plainRequest("GET", "/api/secrets/" + secretName + "/exists", null, null);
        assertEquals(200, gone.statusCode());
        assertFalse(JSON.readTree(gone.body()).asBoolean());
    }

    @Test
    void controlPlaneTrackingLifecycleRoundTripsThroughTheSdkFacingHttpEndpoints()
            throws Exception {
        String workflowName = "http_tracking_" + UUID.randomUUID().toString().replace('-', '_');
        HttpResponse<String> created =
                jsonRequest(
                        "POST",
                        "/api/agent/execution",
                        Map.of(
                                "workflowName",
                                workflowName,
                                "input",
                                Map.of("prompt", "trace me")));
        assertEquals(200, created.statusCode());
        String executionId = JSON.readTree(created.body()).path("executionId").asText();
        assertFalse(executionId.isBlank());

        HttpResponse<String> injected =
                jsonRequest(
                        "POST",
                        "/api/agent/" + executionId + "/tasks",
                        Map.of(
                                "taskDefName",
                                "sdk_visible_task",
                                "referenceTaskName",
                                "sdk_visible_ref",
                                "type",
                                "SIMPLE",
                                "inputData",
                                Map.of("request", "show this in the DAG")));
        assertEquals(200, injected.statusCode());
        String taskId = JSON.readTree(injected.body()).path("taskId").asText();
        assertFalse(taskId.isBlank());

        HttpResponse<String> runningStatus =
                plainRequest("GET", "/api/agent/" + executionId + "/status", null, null);
        assertEquals(200, runningStatus.statusCode());
        assertEquals("RUNNING", JSON.readTree(runningStatus.body()).path("status").asText());
        HttpResponse<String> agentRun =
                plainRequest("GET", "/api/agent/execution/" + executionId, null, null);
        assertEquals(200, agentRun.statusCode());
        assertTrue(
                JSON.readTree(agentRun.body())
                        .findValuesAsText("referenceTaskName")
                        .contains("sdk_visible_ref"));
        HttpResponse<String> taskPage =
                plainRequest(
                        "GET",
                        "/api/agent/executions/"
                                + executionId
                                + "/tasks?status=IN_PROGRESS&count=10&start=0",
                        null,
                        null);
        assertEquals(200, taskPage.statusCode());
        assertEquals(1, JSON.readTree(taskPage.body()).path("totalHits").asInt());

        HttpResponse<String> signal =
                jsonRequest(
                        "POST",
                        "/api/agent/" + executionId + "/signal",
                        Map.of("message", "persist this signal"));
        assertEquals(200, signal.statusCode());
        HttpResponse<String> frameworkEvent =
                jsonRequest(
                        "POST",
                        "/api/agent/events/" + executionId,
                        Map.of(
                                "type",
                                "tool_result",
                                "toolName",
                                "sdk_visible_task",
                                "result",
                                "ok"));
        assertEquals(200, frameworkEvent.statusCode());

        HttpResponse<String> updateTask =
                jsonRequest(
                        "POST",
                        "/api/agent/tasks/"
                                + executionId
                                + "/sdk_visible_ref/COMPLETED?workerid=real-sdk-worker",
                        Map.of("result", "task updated over HTTP"));
        assertEquals(200, updateTask.statusCode());
        HttpResponse<String> completed =
                jsonRequest(
                        "POST",
                        "/api/agent/execution/" + executionId + "/complete",
                        Map.of("result", "tracking complete"));
        assertEquals(200, completed.statusCode());

        HttpResponse<String> finalStatus =
                plainRequest("GET", "/api/agent/" + executionId + "/status", null, null);
        assertEquals(200, finalStatus.statusCode());
        assertEquals("COMPLETED", JSON.readTree(finalStatus.body()).path("status").asText());
        assertEquals(
                "tracking complete",
                JSON.readTree(finalStatus.body()).path("output").path("result").asText());
        HttpResponse<String> full =
                plainRequest("GET", "/api/agent/executions/" + executionId + "/full", null, null);
        assertEquals(200, full.statusCode());
        assertEquals("COMPLETED", JSON.readTree(full.body()).path("status").asText());
        HttpResponse<String> logs =
                plainRequest("GET", "/api/agent/tasks/" + taskId + "/log", null, null);
        assertEquals(200, logs.statusCode());
        assertTrue(JSON.readTree(logs.body()).isArray());

        HttpResponse<String> deleted =
                plainRequest(
                        "DELETE",
                        "/api/agent/executions/" + executionId + "/record?archiveTasks=false",
                        null,
                        null);
        assertEquals(200, deleted.statusCode());
    }

    @Test
    void agentLifecycleEndpointsOperateOnPersistedRunningExecutions() throws Exception {
        String agent = "lifecycle_agent_" + UUID.randomUUID().toString().replace('-', '_');
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(agent)
                                        .model("openai/gpt-4o-mini")
                                        .maxTurns(3)
                                        .build())
                        .build());

        AgentStartResponse running =
                agentService.start(
                        AgentStartRequest.builder().name(agent).prompt("Keep running").build());
        Task runningLlm = awaitScheduledLlm(running.getExecutionId());
        HttpResponse<String> detail =
                plainRequest(
                        "GET", "/api/agent/executions/" + running.getExecutionId(), null, null);
        assertEquals(200, detail.statusCode());
        assertEquals("RUNNING", JSON.readTree(detail.body()).path("status").asText());

        HttpResponse<String> paused =
                plainRequest(
                        "PUT", "/api/agent/" + running.getExecutionId() + "/pause", null, null);
        assertEquals(200, paused.statusCode());
        HttpResponse<String> pausedStatus =
                plainRequest(
                        "GET", "/api/agent/" + running.getExecutionId() + "/status", null, null);
        assertEquals(200, pausedStatus.statusCode());
        assertEquals("PAUSED", JSON.readTree(pausedStatus.body()).path("status").asText());

        HttpResponse<String> resumed =
                plainRequest(
                        "PUT", "/api/agent/" + running.getExecutionId() + "/resume", null, null);
        assertEquals(200, resumed.statusCode());
        HttpResponse<String> signal =
                jsonRequest(
                        "POST",
                        "/api/agent/" + running.getExecutionId() + "/signal",
                        Map.of("message", "finish the current turn"));
        assertEquals(200, signal.statusCode());
        HttpResponse<String> stop =
                plainRequest(
                        "POST", "/api/agent/" + running.getExecutionId() + "/stop", null, null);
        assertEquals(200, stop.statusCode());
        completeScriptedLlm(
                runningLlm, Map.of("finishReason", "STOP", "result", "Stopped after this turn."));
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                awaitAgentTerminal(running.getExecutionId()).getStatus());

        HttpResponse<String> search =
                plainRequest(
                        "GET",
                        "/api/agent/executions?agentName=" + agent + "&status=COMPLETED",
                        null,
                        null);
        assertEquals(200, search.statusCode());
        assertTrue(JSON.readTree(search.body()).has("results"));

        AgentStartResponse cancel =
                agentService.start(
                        AgentStartRequest.builder().name(agent).prompt("Cancel me").build());
        awaitScheduledLlm(cancel.getExecutionId());
        HttpResponse<String> cancelled =
                plainRequest(
                        "DELETE",
                        "/api/agent/"
                                + cancel.getExecutionId()
                                + "/cancel?reason=operator%20cancelled",
                        null,
                        null);
        assertEquals(200, cancelled.statusCode());
        assertEquals(
                Workflow.WorkflowStatus.TERMINATED,
                awaitAgentTerminal(cancel.getExecutionId()).getStatus());
    }

    @Test
    void scriptedLlmJudgeAcceptsRejectsAndFailsClosedWithoutUsingAMock() {
        AgentStartResponse accepted = startLlmGuardrailedAgent("judge_accept", "raise");
        Task acceptedAnswer = awaitScheduledLlm(accepted.getExecutionId());
        completeScriptedLlm(
                acceptedAnswer, Map.of("finishReason", "STOP", "result", "The allowed answer."));
        Task acceptingJudge = awaitScheduledLlm(accepted.getExecutionId());
        assertTrue(
                acceptingJudge.getReferenceTaskName().contains("_llm_guardrail_"),
                "the next system LLM task must be the compiled judge, not another agent turn");
        completeScriptedLlm(
                acceptingJudge,
                Map.of("finishReason", "STOP", "result", Map.of("passed", true, "reason", "ok")));
        Workflow acceptedWorkflow = awaitAgentTerminal(accepted.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, acceptedWorkflow.getStatus());
        assertEquals("The allowed answer.", acceptedWorkflow.getOutput().get("result"));

        AgentStartResponse rejected = startLlmGuardrailedAgent("judge_reject", "raise");
        Task rejectedAnswer = awaitScheduledLlm(rejected.getExecutionId());
        completeScriptedLlm(
                rejectedAnswer, Map.of("finishReason", "STOP", "result", "The blocked answer."));
        Task rejectingJudge = awaitScheduledLlm(rejected.getExecutionId());
        completeScriptedLlm(
                rejectingJudge,
                Map.of(
                        "finishReason",
                        "STOP",
                        "result",
                        Map.of("passed", false, "reason", "policy rejected this answer")));
        Workflow rejectedWorkflow = awaitAgentTerminal(rejected.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.FAILED, rejectedWorkflow.getStatus());
        assertTrue(rejectedWorkflow.getReasonForIncompletion().contains("policy rejected"));

        AgentStartResponse judgeError = startLlmGuardrailedAgent("judge_error", "raise");
        Task errorAnswer = awaitScheduledLlm(judgeError.getExecutionId());
        completeScriptedLlm(
                errorAnswer, Map.of("finishReason", "STOP", "result", "The unanswered answer."));
        Task failingJudge = awaitScheduledLlm(judgeError.getExecutionId());
        // This is the real failure surface the compiled parser owns: an LLM response that cannot
        // be parsed as a guardrail decision. The parser must fail closed rather than let the
        // original agent output route to a handoff or a next turn.
        completeScriptedLlm(
                failingJudge, Map.of("finishReason", "STOP", "result", "not a judge decision"));
        Workflow errorWorkflow = awaitAgentTerminal(judgeError.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.FAILED, errorWorkflow.getStatus());
        assertTrue(errorWorkflow.getReasonForIncompletion().contains("Unparseable LLM response"));
    }

    @Test
    void skillRestContractStoresReadsDownloadsAndDeletesARealZipPackage() throws Exception {
        String skill = "http-skill-" + UUID.randomUUID().toString().replace('-', '_');
        String version = "v1";
        byte[] packageBytes = skillPackage(skill);
        String manifest =
                JSON.writeValueAsString(
                        Map.of("name", skill, "version", version, "model", "openai/gpt-4o-mini"));

        HttpResponse<String> register = registerSkill(manifest, packageBytes);
        assertEquals(200, register.statusCode());
        JsonNode detail = JSON.readTree(register.body());
        assertEquals(skill, detail.path("name").asText());
        assertEquals(version, detail.path("version").asText());
        assertEquals("READY", detail.path("status").asText());

        HttpResponse<String> listed =
                plainRequest("GET", "/api/skills?allVersions=true", null, null);
        assertEquals(200, listed.statusCode());
        assertTrue(JSON.readTree(listed.body()).findValuesAsText("name").contains(skill));
        HttpResponse<String> latest = plainRequest("GET", "/api/skills/" + skill, null, null);
        assertEquals(200, latest.statusCode());
        assertEquals(version, JSON.readTree(latest.body()).path("version").asText());
        HttpResponse<String> file =
                plainRequest(
                        "GET",
                        "/api/skills/"
                                + skill
                                + "/versions/"
                                + version
                                + "/files?path=references%2Fguide.md",
                        null,
                        null);
        assertEquals(200, file.statusCode());
        assertEquals("real package content", JSON.readTree(file.body()).path("content").asText());
        HttpResponse<String> download =
                plainRequest(
                        "GET",
                        "/api/skills/" + skill + "/versions/" + version + "/package",
                        null,
                        null);
        assertEquals(200, download.statusCode());
        assertEquals(new String(packageBytes, StandardCharsets.ISO_8859_1), download.body());

        HttpResponse<String> delete =
                plainRequest("DELETE", "/api/skills/" + skill + "/versions/" + version, null, null);
        assertEquals(204, delete.statusCode());
        HttpResponse<String> missing = plainRequest("GET", "/api/skills/" + skill, null, null);
        assertEquals(400, missing.statusCode());
    }

    @Test
    void skillRegistryRejectsMalformedAndUnsafePackagesWithoutPersistingThem() throws Exception {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String skill = "unsafe-skill-" + suffix;
        String manifest = JSON.writeValueAsString(Map.of("name", skill, "version", "v1"));

        HttpResponse<String> nonZip =
                registerSkill(manifest, "this is not a zip".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, nonZip.statusCode());
        assertTrue(nonZip.body().contains("SKILL.md") || nonZip.body().contains("zip"));

        HttpResponse<String> traversal = registerSkill(manifest, unsafeSkillPackage(skill));
        assertEquals(400, traversal.statusCode());
        assertTrue(traversal.body().contains("Invalid skill package path"));

        HttpResponse<String> mismatch =
                registerSkill(
                        JSON.writeValueAsString(Map.of("name", "other-" + skill, "version", "v1")),
                        skillPackage(skill));
        assertEquals(400, mismatch.statusCode());
        assertTrue(mismatch.body().contains("does not match"));

        HttpResponse<String> invalidVersion =
                registerSkill(
                        JSON.writeValueAsString(Map.of("name", skill, "version", "v/1")),
                        skillPackage(skill));
        assertEquals(400, invalidVersion.statusCode());
        assertTrue(invalidVersion.body().contains("Invalid skill version"));

        HttpResponse<String> absent =
                plainRequest(
                        "GET",
                        "/api/skills/" + skill + "/versions/v1/files?path=SKILL.md",
                        null,
                        null);
        assertEquals(400, absent.statusCode());
    }

    @Test
    void registeredSkillResolvesThroughTheSkillFrameworkAndRunsAsAnAgent() throws Exception {
        String skill = "runtime-skill-" + UUID.randomUUID().toString().replace('-', '_');
        String version = "v1";
        HttpResponse<String> registration =
                registerSkill(
                        JSON.writeValueAsString(
                                Map.of(
                                        "name",
                                        skill,
                                        "version",
                                        version,
                                        "model",
                                        "openai/gpt-4o-mini")),
                        skillPackage(skill, "Runtime skill reference material."));
        assertEquals(200, registration.statusCode());

        AgentStartResponse execution =
                agentService.start(
                        AgentStartRequest.builder()
                                .framework("skill")
                                .skillRef(
                                        Map.of(
                                                "name", skill, "version", version, "params",
                                                Map.of()))
                                .prompt("Use the registered skill")
                                .build());
        Task llm = awaitScheduledLlm(execution.getExecutionId());
        assertTrue(
                String.valueOf(llm.getInputData().get("messages"))
                        .contains("Use the reference guide."));
        completeScriptedLlm(
                llm, Map.of("finishReason", "STOP", "result", "Skill execution completed."));
        Workflow completed = awaitAgentTerminal(execution.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
        assertEquals("Skill execution completed.", completed.getOutput().get("result"));
        WorkflowDef stored = metadataService.getWorkflowDef(skill, null);
        assertEquals("skill", stored.getMetadata().get("agent_sdk"));
    }

    @Test
    void dynamicWorkerToolReceivesMergedStateThroughASdkWorkerAndReturnsItToTheAgent()
            throws Exception {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "worker_dispatch_agent_" + suffix;
        String workerName = "record_customer_" + suffix;
        RecordingWorker worker = new RecordingWorker(workerName);
        TaskRunnerConfigurer runner = startWorker(worker);
        try {
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(agent)
                                            .model("openai/gpt-4o-mini")
                                            .tools(
                                                    List.of(
                                                            ToolConfig.builder()
                                                                    .name(workerName)
                                                                    .description(
                                                                            "Records a customer")
                                                                    .toolType("worker")
                                                                    .build()))
                                            .maxTurns(3)
                                            .build())
                            .build());
            AgentStartResponse started =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Look up customer c-42")
                                    .context(Map.of("tenant", "acme"))
                                    .build());

            Task firstLlm = awaitScheduledLlm(started.getExecutionId());
            completeScriptedLlm(
                    firstLlm,
                    Map.of(
                            "finishReason",
                            "TOOL_CALLS",
                            "result",
                            "",
                            "toolCalls",
                            List.of(
                                    Map.of(
                                            "taskReferenceName",
                                            "customer_lookup",
                                            "name",
                                            workerName,
                                            "type",
                                            "SIMPLE",
                                            "inputParameters",
                                            Map.of("customerId", "c-42")))));

            Task secondLlm = awaitScheduledLlm(started.getExecutionId());
            Awaitility.await()
                    .atMost(20, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(1, worker.invocationCount.get()));
            assertEquals("c-42", worker.lastInput.get().get("customerId"));
            assertEquals(Map.of("tenant", "acme"), worker.lastInput.get().get("_agent_state"));

            Workflow betweenTurns =
                    executionService.getExecutionStatus(started.getExecutionId(), true);
            Task workerTask = taskByType(betweenTurns, workerName);
            assertEquals(Task.Status.COMPLETED, workerTask.getStatus());
            assertEquals("Alice", workerTask.getOutputData().get("customerName"));
            @SuppressWarnings("unchecked")
            Map<String, Object> stateUpdates =
                    (Map<String, Object>) workerTask.getOutputData().get("_state_updates");
            assertEquals("Alice", stateUpdates.get("customerName"));
            assertEquals(
                    "LLM_CHAT_COMPLETE",
                    secondLlm.getTaskType(),
                    "the next model turn must wait for the real worker result");

            completeScriptedLlm(
                    secondLlm, Map.of("finishReason", "STOP", "result", "Customer c-42 is Alice."));
            Workflow completed = awaitAgentTerminal(started.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
            assertEquals("Customer c-42 is Alice.", completed.getOutput().get("result"));
            @SuppressWarnings("unchecked")
            Map<String, Object> context =
                    (Map<String, Object>) completed.getOutput().get("context");
            assertEquals("acme", context.get("tenant"));
            assertEquals("Alice", context.get("customerName"));
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void matchingSwarmToolResultRoutesImmediatelyAfterTheRealJoin() throws Exception {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String team = "tool_result_team_" + suffix;
        String analyst = "tool_result_analyst_" + suffix;
        String workerName = "lookup_for_handoff_" + suffix;
        RecordingWorker worker = new RecordingWorker(workerName);
        TaskRunnerConfigurer runner = startWorker(worker);
        try {
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(team)
                                            .model("openai/gpt-4o-mini")
                                            .strategy(AgentConfig.Strategy.SWARM)
                                            .tools(
                                                    List.of(
                                                            ToolConfig.builder()
                                                                    .name(workerName)
                                                                    .description(
                                                                            "Looks up a customer for handoff")
                                                                    .toolType("worker")
                                                                    .build()))
                                            .agents(
                                                    List.of(
                                                            AgentConfig.builder()
                                                                    .name(analyst)
                                                                    .model("openai/gpt-4o-mini")
                                                                    .maxTurns(1)
                                                                    .build()))
                                            .handoffs(
                                                    List.of(
                                                            HandoffConfig.builder()
                                                                    .type("on_tool_result")
                                                                    .target(analyst)
                                                                    .toolName(workerName)
                                                                    .resultContains("Alice")
                                                                    .build()))
                                            .maxTurns(2)
                                            .synthesize(false)
                                            .build())
                            .build());
            AgentStartResponse started =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(team)
                                    .prompt("Look up the customer and route the result")
                                    .context(Map.of("tenant", "acme"))
                                    .build());

            Task sourceLlm = awaitScheduledLlmAcrossSubworkflows(started.getExecutionId());
            assertTrue(sourceLlm.getReferenceTaskName().contains(team + "_llm"));
            completeScriptedLlm(
                    sourceLlm,
                    Map.of(
                            "finishReason",
                            "TOOL_CALLS",
                            "result",
                            "",
                            "toolCalls",
                            List.of(
                                    Map.of(
                                            "taskReferenceName",
                                            "lookup_customer_one",
                                            "name",
                                            workerName,
                                            "type",
                                            "SIMPLE",
                                            "inputParameters",
                                            Map.of("customerId", "c-42")),
                                    Map.of(
                                            "taskReferenceName",
                                            "lookup_customer_two",
                                            "name",
                                            workerName,
                                            "type",
                                            "SIMPLE",
                                            "inputParameters",
                                            Map.of("customerId", "c-43")))));

            Task targetLlm = awaitScheduledLlmAcrossSubworkflows(started.getExecutionId());
            Awaitility.await()
                    .atMost(20, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(2, worker.invocationCount.get()));
            assertTrue(
                    targetLlm.getReferenceTaskName().contains(analyst + "_llm"),
                    "a matching JOIN result must select the target before another source LLM turn");
            assertEquals(
                    Set.of("c-42", "c-43"),
                    worker.inputs.stream()
                            .map(input -> String.valueOf(input.get("customerId")))
                            .collect(java.util.stream.Collectors.toSet()));
            assertTrue(
                    worker.inputs.stream()
                            .allMatch(
                                    input ->
                                            Map.of("tenant", "acme")
                                                    .equals(input.get("_agent_state"))));

            Workflow routingState =
                    executionService.getExecutionStatus(started.getExecutionId(), true);
            Task completedSourceChild =
                    routingState.getTasks().stream()
                            .filter(task -> "SUB_WORKFLOW".equals(task.getTaskType()))
                            .filter(task -> task.getStatus() == Task.Status.COMPLETED)
                            .findFirst()
                            .orElseThrow();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolResults =
                    (List<Map<String, Object>>)
                            completedSourceChild.getOutputData().get("tool_results");
            assertEquals(2, toolResults.size());
            assertEquals(
                    List.of(workerName, workerName),
                    toolResults.stream().map(result -> result.get("name")).toList(),
                    "JOIN results must retain duplicate calls in their deterministic output order");
            Workflow sourceChild =
                    executionService.getExecutionStatus(
                            completedSourceChild.getSubWorkflowId(), true);
            List<String> dynamicReferences =
                    sourceChild.getTasks().stream()
                            .filter(task -> workerName.equals(task.getTaskType()))
                            .map(Task::getReferenceTaskName)
                            .toList();
            assertEquals(2, dynamicReferences.size());
            assertEquals(2, new java.util.HashSet<>(dynamicReferences).size());

            completeScriptedLlm(
                    targetLlm,
                    Map.of("finishReason", "STOP", "result", "Analyst received Alice's record."));
            Workflow completed = awaitAgentTerminal(started.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
            @SuppressWarnings("unchecked")
            Map<String, Object> context =
                    (Map<String, Object>) completed.getOutput().get("context");
            assertEquals("Alice", context.get("customerName"));
            assertEquals("acme", context.get("tenant"));
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void concurrentDynamicToolExecutionsKeepTheirStateAndResultsIsolated() throws Exception {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "concurrent_state_agent_" + suffix;
        String workerName = "concurrent_state_worker_" + suffix;
        ConcurrentStateWorker worker = new ConcurrentStateWorker(workerName);
        TaskRunnerConfigurer runner = startWorker(worker);
        try {
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(agent)
                                            .model("openai/gpt-4o-mini")
                                            .tools(
                                                    List.of(
                                                            ToolConfig.builder()
                                                                    .name(workerName)
                                                                    .description(
                                                                            "Records state for this execution")
                                                                    .toolType("worker")
                                                                    .build()))
                                            .maxTurns(3)
                                            .build())
                            .build());
            AgentStartResponse alpha =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Process alpha")
                                    .context(Map.of("tenant", "alpha"))
                                    .build());
            AgentStartResponse beta =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Process beta")
                                    .context(Map.of("tenant", "beta"))
                                    .build());

            Task alphaFirst = awaitScheduledLlm(alpha.getExecutionId());
            Task betaFirst = awaitScheduledLlm(beta.getExecutionId());
            completeScriptedLlm(alphaFirst, scriptedSimpleToolCall(workerName, "alpha_call"));
            completeScriptedLlm(betaFirst, scriptedSimpleToolCall(workerName, "beta_call"));

            Task alphaSecond = awaitScheduledLlm(alpha.getExecutionId());
            Task betaSecond = awaitScheduledLlm(beta.getExecutionId());
            Awaitility.await()
                    .atMost(20, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(2, worker.inputs.size()));
            assertEquals(
                    Set.of(Map.of("tenant", "alpha"), Map.of("tenant", "beta")),
                    worker.inputs.stream()
                            .map(input -> input.get("_agent_state"))
                            .collect(java.util.stream.Collectors.toSet()));

            completeScriptedLlm(
                    alphaSecond, Map.of("finishReason", "STOP", "result", "Alpha complete."));
            completeScriptedLlm(
                    betaSecond, Map.of("finishReason", "STOP", "result", "Beta complete."));
            Workflow alphaCompleted = awaitAgentTerminal(alpha.getExecutionId());
            Workflow betaCompleted = awaitAgentTerminal(beta.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, alphaCompleted.getStatus());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, betaCompleted.getStatus());
            @SuppressWarnings("unchecked")
            Map<String, Object> alphaContext =
                    (Map<String, Object>) alphaCompleted.getOutput().get("context");
            @SuppressWarnings("unchecked")
            Map<String, Object> betaContext =
                    (Map<String, Object>) betaCompleted.getOutput().get("context");
            assertEquals("alpha", alphaContext.get("tenant"));
            assertEquals("alpha", alphaContext.get("handledTenant"));
            assertEquals("beta", betaContext.get("tenant"));
            assertEquals("beta", betaContext.get("handledTenant"));
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void swarmHumanToolWaitsForARealResponseBeforeItsResultCanRoute() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String team = "human_handoff_team_" + suffix;
        String analyst = "human_handoff_analyst_" + suffix;
        String humanTool = "request_approval_" + suffix;
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(team)
                                        .model("openai/gpt-4o-mini")
                                        .strategy(AgentConfig.Strategy.SWARM)
                                        .tools(
                                                List.of(
                                                        ToolConfig.builder()
                                                                .name(humanTool)
                                                                .description(
                                                                        "Ask a person whether to approve")
                                                                .toolType("human")
                                                                .inputSchema(
                                                                        Map.of(
                                                                                "type",
                                                                                "object",
                                                                                "properties",
                                                                                Map.of(
                                                                                        "question",
                                                                                        Map.of(
                                                                                                "type",
                                                                                                "string")),
                                                                                "required",
                                                                                List.of(
                                                                                        "question")))
                                                                .build()))
                                        .agents(
                                                List.of(
                                                        AgentConfig.builder()
                                                                .name(analyst)
                                                                .model("openai/gpt-4o-mini")
                                                                .maxTurns(1)
                                                                .build()))
                                        .handoffs(
                                                List.of(
                                                        HandoffConfig.builder()
                                                                .type("on_tool_result")
                                                                .target(analyst)
                                                                .toolName(humanTool)
                                                                .resultContains("approved")
                                                                .build()))
                                        .maxTurns(2)
                                        .synthesize(false)
                                        .build())
                        .build());
        AgentStartResponse started =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(team)
                                .prompt("Ask for the required approval")
                                .build());

        Task sourceLlm = awaitScheduledLlmAcrossSubworkflows(started.getExecutionId());
        completeScriptedLlm(
                sourceLlm,
                Map.of(
                        "finishReason",
                        "TOOL_CALLS",
                        "result",
                        "",
                        "toolCalls",
                        List.of(
                                Map.of(
                                        "taskReferenceName",
                                        "approval_call",
                                        "name",
                                        humanTool,
                                        "type",
                                        "HUMAN",
                                        "inputParameters",
                                        Map.of("question", "Approve the handoff?")))));

        Task human =
                awaitTaskAcrossSubworkflows(
                        started.getExecutionId(), "HUMAN", Task.Status.IN_PROGRESS);
        assertEquals(
                Workflow.WorkflowStatus.RUNNING,
                executionService.getExecutionStatus(started.getExecutionId(), true).getStatus());
        assertTrue(
                findScheduledLlm(started.getExecutionId(), new java.util.HashSet<>()) == null,
                "an incomplete HUMAN task must prevent both a source follow-up and a handoff");

        TaskResult response = new TaskResult(human);
        response.setStatus(TaskResult.Status.COMPLETED);
        response.addOutputData("answer", "approved");
        workflowExecutor.updateTask(response);

        Task targetLlm = awaitScheduledLlmAcrossSubworkflows(started.getExecutionId());
        assertTrue(targetLlm.getReferenceTaskName().contains(analyst + "_llm"));
        completeScriptedLlm(
                targetLlm, Map.of("finishReason", "STOP", "result", "Approval was accepted."));
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                awaitAgentTerminal(started.getExecutionId()).getStatus());
    }

    @Test
    void approvalRequiredWorkerWaitsForHumanApprovalAndRejectsWithoutDispatchingTheWorker() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "approval_agent_" + suffix;
        String protectedWorker = "protected_worker_" + suffix;
        RecordingWorker worker = new RecordingWorker(protectedWorker);
        TaskRunnerConfigurer runner = startWorker(worker);
        try {
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(agent)
                                            .model("openai/gpt-4o-mini")
                                            .tools(
                                                    List.of(
                                                            ToolConfig.builder()
                                                                    .name(protectedWorker)
                                                                    .description(
                                                                            "Make a protected change")
                                                                    .toolType("worker")
                                                                    .approvalRequired(true)
                                                                    .build()))
                                            .maxTurns(3)
                                            .build())
                            .build());

            AgentStartResponse approvedRun =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Request the protected change")
                                    .build());
            Task approvedLlm = awaitScheduledLlm(approvedRun.getExecutionId());
            completeScriptedLlm(
                    approvedLlm,
                    Map.of(
                            "finishReason",
                            "TOOL_CALLS",
                            "result",
                            "",
                            "toolCalls",
                            List.of(
                                    Map.of(
                                            "taskReferenceName",
                                            "protected_call",
                                            "name",
                                            protectedWorker,
                                            "type",
                                            "SIMPLE",
                                            "inputParameters",
                                            Map.of("customerId", "customer-1")))));
            Task approval =
                    awaitTaskAcrossSubworkflows(
                            approvedRun.getExecutionId(), "HUMAN", Task.Status.IN_PROGRESS);
            assertEquals(0, worker.invocationCount.get());
            assertTrue(
                    JSON.valueToTree(approval.getInputData())
                            .path("response_schema")
                            .path("properties")
                            .has("approved"));

            TaskResult approvedResponse = new TaskResult(approval);
            approvedResponse.setStatus(TaskResult.Status.COMPLETED);
            approvedResponse.setOutputData(
                    Map.of("approved", true, "reason", "Verified", "ticket", "CHG-42"));
            workflowExecutor.updateTask(approvedResponse);
            Task approvedFollowup = awaitScheduledLlm(approvedRun.getExecutionId());
            completeScriptedLlm(
                    approvedFollowup,
                    Map.of("finishReason", "STOP", "result", "Protected change completed."));
            Workflow approved = awaitAgentTerminal(approvedRun.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, approved.getStatus());
            assertEquals(1, worker.invocationCount.get());
            assertEquals("customer-1", worker.lastInput.get().get("customerId"));
            assertEquals("Protected change completed.", approved.getOutput().get("result"));

            AgentStartResponse rejectedRun =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Request the protected change again")
                                    .build());
            Task rejectedLlm = awaitScheduledLlm(rejectedRun.getExecutionId());
            completeScriptedLlm(
                    rejectedLlm,
                    Map.of(
                            "finishReason",
                            "TOOL_CALLS",
                            "result",
                            "",
                            "toolCalls",
                            List.of(
                                    Map.of(
                                            "taskReferenceName",
                                            "rejected_protected_call",
                                            "name",
                                            protectedWorker,
                                            "type",
                                            "SIMPLE",
                                            "inputParameters",
                                            Map.of("customerId", "customer-2")))));
            Task rejection =
                    awaitTaskAcrossSubworkflows(
                            rejectedRun.getExecutionId(), "HUMAN", Task.Status.IN_PROGRESS);
            TaskResult rejectedResponse = new TaskResult(rejection);
            rejectedResponse.setStatus(TaskResult.Status.COMPLETED);
            rejectedResponse.setOutputData(Map.of("approved", false, "reason", "Not authorized"));
            workflowExecutor.updateTask(rejectedResponse);
            Workflow rejected = awaitAgentTerminal(rejectedRun.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, rejected.getStatus());
            assertEquals(1, worker.invocationCount.get());
            assertEquals(0, queueDAO.getSize(protectedWorker));
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void toolLevelLlmGuardrailGatesWorkerExecutionAndFailsClosedOnRejection() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "tool_guardrail_agent_" + suffix;
        String guardedWorker = "tool_guardrail_worker_" + suffix;
        RecordingWorker worker = new RecordingWorker(guardedWorker);
        TaskRunnerConfigurer runner = startWorker(worker);
        try {
            GuardrailConfig guardrail =
                    GuardrailConfig.builder()
                            .name("tool_policy")
                            .guardrailType("llm")
                            .model("openai/gpt-4o-mini")
                            .policy("Only allow an explicitly safe tool request")
                            .onFail("raise")
                            .maxRetries(0)
                            .build();
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(agent)
                                            .model("openai/gpt-4o-mini")
                                            .tools(
                                                    List.of(
                                                            ToolConfig.builder()
                                                                    .name(guardedWorker)
                                                                    .description(
                                                                            "Run a guarded worker")
                                                                    .toolType("worker")
                                                                    .guardrails(List.of(guardrail))
                                                                    .build()))
                                            .maxTurns(3)
                                            .build())
                            .build());

            AgentStartResponse acceptedRun =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Make a safe request")
                                    .build());
            Task acceptedLlm = awaitScheduledLlm(acceptedRun.getExecutionId());
            completeScriptedLlm(
                    acceptedLlm,
                    Map.of(
                            "finishReason",
                            "TOOL_CALLS",
                            "result",
                            "",
                            "toolCalls",
                            List.of(
                                    Map.of(
                                            "taskReferenceName",
                                            "safe_guarded_call",
                                            "name",
                                            guardedWorker,
                                            "type",
                                            "SIMPLE",
                                            "inputParameters",
                                            Map.of("customerId", "safe-customer")))));
            Task acceptingJudge = awaitScheduledLlm(acceptedRun.getExecutionId());
            assertTrue(acceptingJudge.getReferenceTaskName().contains("_tool_llm_guardrail_"));
            assertEquals(0, worker.invocationCount.get());
            completeScriptedLlm(
                    acceptingJudge,
                    Map.of(
                            "finishReason",
                            "STOP",
                            "result",
                            Map.of("passed", true, "reason", "safe")));
            Task acceptedFollowup = awaitScheduledLlm(acceptedRun.getExecutionId());
            completeScriptedLlm(
                    acceptedFollowup,
                    Map.of("finishReason", "STOP", "result", "Guarded worker completed."));
            assertEquals(
                    Workflow.WorkflowStatus.COMPLETED,
                    awaitAgentTerminal(acceptedRun.getExecutionId()).getStatus());
            assertEquals(1, worker.invocationCount.get());

            AgentStartResponse rejectedRun =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Make an unsafe request")
                                    .build());
            Task rejectedLlm = awaitScheduledLlm(rejectedRun.getExecutionId());
            completeScriptedLlm(
                    rejectedLlm,
                    Map.of(
                            "finishReason",
                            "TOOL_CALLS",
                            "result",
                            "",
                            "toolCalls",
                            List.of(
                                    Map.of(
                                            "taskReferenceName",
                                            "unsafe_guarded_call",
                                            "name",
                                            guardedWorker,
                                            "type",
                                            "SIMPLE",
                                            "inputParameters",
                                            Map.of("customerId", "unsafe-customer")))));
            Task rejectingJudge = awaitScheduledLlm(rejectedRun.getExecutionId());
            completeScriptedLlm(
                    rejectingJudge,
                    Map.of(
                            "finishReason",
                            "STOP",
                            "result",
                            Map.of("passed", false, "reason", "unsafe tool request")));
            Workflow rejected = awaitAgentTerminal(rejectedRun.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.FAILED, rejected.getStatus());
            assertTrue(rejected.getReasonForIncompletion().contains("unsafe tool request"));
            assertEquals(1, worker.invocationCount.get());
            assertEquals(0, queueDAO.getSize(guardedWorker));
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void hallucinatedToolCannotCreateAnUnpolledSimpleTaskOrBlockTheNextModelTurn() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String agent = "unknown_tool_agent_" + suffix;
        String allowedWorker = "allowed_worker_" + suffix;
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(agent)
                                        .model("openai/gpt-4o-mini")
                                        .tools(
                                                List.of(
                                                        ToolConfig.builder()
                                                                .name(allowedWorker)
                                                                .description(
                                                                        "The only allowed worker")
                                                                .toolType("worker")
                                                                .build()))
                                        .maxTurns(3)
                                        .build())
                        .build());
        AgentStartResponse started =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(agent)
                                .prompt("Use a tool only when it exists")
                                .build());

        Task firstLlm = awaitScheduledLlm(started.getExecutionId());
        completeScriptedLlm(
                firstLlm,
                Map.of(
                        "finishReason",
                        "TOOL_CALLS",
                        "result",
                        "",
                        "toolCalls",
                        List.of(
                                Map.of(
                                        "taskReferenceName",
                                        "hallucinated_call",
                                        "name",
                                        "does_not_exist_" + suffix,
                                        "type",
                                        "SIMPLE",
                                        "inputParameters",
                                        Map.of("input", "ignored")))));

        Task secondLlm = awaitScheduledLlm(started.getExecutionId());
        Workflow betweenTurns = executionService.getExecutionStatus(started.getExecutionId(), true);
        assertTrue(
                betweenTurns.getTasks().stream()
                        .noneMatch(task -> "does_not_exist_".equals(task.getTaskDefName())),
                "a hallucinated call must be rejected by compiler-owned routing before a SIMPLE"
                        + " task can be queued");
        assertTrue(
                betweenTurns.getTasks().stream()
                        .noneMatch(task -> task.getTaskType().startsWith("does_not_exist_")),
                "the unknown call must never become a dynamic task type");

        completeScriptedLlm(
                secondLlm, Map.of("finishReason", "STOP", "result", "No valid tool exists."));
        Workflow completed = awaitAgentTerminal(started.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
        assertEquals("No valid tool exists.", completed.getOutput().get("result"));
    }

    @Test
    void dynamicAgentToolWaitsForARealChildWorkflowBeforeTheParentSelectsItsNextTurn() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String parent = "agent_tool_parent_" + suffix;
        String child = "agent_tool_child_" + suffix;
        String tool = "delegate_research_" + suffix;
        registerDeterministicChildWorkflow(child);
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(parent)
                                        .model("openai/gpt-4o-mini")
                                        .tools(
                                                List.of(
                                                        ToolConfig.builder()
                                                                .name(tool)
                                                                .description(
                                                                        "Delegates research to a child")
                                                                .toolType("agent_tool")
                                                                .config(
                                                                        Map.of(
                                                                                "workflowName",
                                                                                child))
                                                                .build()))
                                        .maxTurns(3)
                                        .build())
                        .build());
        AgentStartResponse started =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(parent)
                                .prompt("Delegate this question")
                                .context(Map.of("requestId", "r-42"))
                                .build());

        Task firstLlm = awaitScheduledLlm(started.getExecutionId());
        completeScriptedLlm(
                firstLlm,
                Map.of(
                        "finishReason",
                        "TOOL_CALLS",
                        "result",
                        "",
                        "toolCalls",
                        List.of(
                                Map.of(
                                        "taskReferenceName",
                                        "delegate_call",
                                        "name",
                                        tool,
                                        "type",
                                        "SUB_WORKFLOW",
                                        "inputParameters",
                                        Map.of("request", "research durable execution")))));

        Task secondLlm = awaitScheduledLlm(started.getExecutionId());
        Workflow betweenTurns = executionService.getExecutionStatus(started.getExecutionId(), true);
        Task childTask = taskByType(betweenTurns, "SUB_WORKFLOW");
        assertEquals(Task.Status.COMPLETED, childTask.getStatus());
        assertTrue(
                childTask.getSubWorkflowId() != null && !childTask.getSubWorkflowId().isBlank(),
                "the dynamic agent tool must expose the real child execution id");
        Workflow childExecution =
                executionService.getExecutionStatus(childTask.getSubWorkflowId(), true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, childExecution.getStatus());
        assertEquals("child research complete", childExecution.getOutput().get("result"));
        assertEquals(
                "LLM_CHAT_COMPLETE",
                secondLlm.getTaskType(),
                "the parent cannot select its next model turn before the child completes");

        completeScriptedLlm(
                secondLlm,
                Map.of("finishReason", "STOP", "result", "The child completed the research."));
        Workflow completed = awaitAgentTerminal(started.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
        assertEquals("The child completed the research.", completed.getOutput().get("result"));
    }

    @Test
    void dynamicHttpToolsUseRealRequestsAndAllowAFailedOptionalBranchToReachTheNextTurn()
            throws Exception {
        AtomicInteger successCalls = new AtomicInteger();
        AtomicInteger failureCalls = new AtomicInteger();
        AtomicReference<String> successQuery = new AtomicReference<>();
        HttpServer api = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        api.createContext(
                "/success",
                exchange -> {
                    successCalls.incrementAndGet();
                    successQuery.set(exchange.getRequestURI().getQuery());
                    respond(exchange, 200, "{\"answer\":\"ok\"}");
                });
        api.createContext(
                "/failure",
                exchange -> {
                    failureCalls.incrementAndGet();
                    respond(exchange, 503, "temporarily unavailable");
                });
        api.start();
        try {
            String suffix = UUID.randomUUID().toString().replace('-', '_');
            String agent = "http_dispatch_agent_" + suffix;
            String successTool = "http_success_" + suffix;
            String failureTool = "http_failure_" + suffix;
            String baseUrl = "http://localhost:" + api.getAddress().getPort();
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(agent)
                                            .model("openai/gpt-4o-mini")
                                            .tools(
                                                    List.of(
                                                            ToolConfig.builder()
                                                                    .name(successTool)
                                                                    .description(
                                                                            "Calls the success API")
                                                                    .toolType("http")
                                                                    .config(
                                                                            Map.of(
                                                                                    "url",
                                                                                    baseUrl
                                                                                            + "/success",
                                                                                    "method",
                                                                                    "GET",
                                                                                    "queryParams",
                                                                                    List.of("id")))
                                                                    .build(),
                                                            ToolConfig.builder()
                                                                    .name(failureTool)
                                                                    .description(
                                                                            "Calls the failure API")
                                                                    .toolType("http")
                                                                    .config(
                                                                            Map.of(
                                                                                    "url",
                                                                                    baseUrl
                                                                                            + "/failure",
                                                                                    "method",
                                                                                    "GET"))
                                                                    .build()))
                                            .maxTurns(3)
                                            .build())
                            .build());
            AgentStartResponse started =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(agent)
                                    .prompt("Call both APIs")
                                    .build());

            Task firstLlm = awaitScheduledLlm(started.getExecutionId());
            completeScriptedLlm(
                    firstLlm,
                    Map.of(
                            "finishReason",
                            "TOOL_CALLS",
                            "result",
                            "",
                            "toolCalls",
                            List.of(
                                    Map.of(
                                            "taskReferenceName",
                                            "success_call",
                                            "name",
                                            successTool,
                                            "type",
                                            "HTTP",
                                            "inputParameters",
                                            Map.of("id", "42")),
                                    Map.of(
                                            "taskReferenceName",
                                            "failure_call",
                                            "name",
                                            failureTool,
                                            "type",
                                            "HTTP",
                                            "inputParameters",
                                            Map.of()))));

            Task secondLlm = awaitScheduledLlm(started.getExecutionId());
            Workflow betweenTurns =
                    executionService.getExecutionStatus(started.getExecutionId(), true);
            List<Task> httpTasks =
                    betweenTurns.getTasks().stream()
                            .filter(task -> "HTTP".equals(task.getTaskType()))
                            .toList();
            assertEquals(
                    2, httpTasks.size(), "each declared dynamic HTTP call must have a unique task");
            assertEquals(1, successCalls.get());
            assertEquals(
                    2,
                    failureCalls.get(),
                    "the real dynamic task retry must redeliver the failed HTTP call exactly once");
            assertEquals("id=42", successQuery.get());
            Task success =
                    httpTasks.stream()
                            .filter(task -> task.getInputData().toString().contains("/success"))
                            .findFirst()
                            .orElseThrow();
            Task failed =
                    httpTasks.stream()
                            .filter(task -> task.getInputData().toString().contains("/failure"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(Task.Status.COMPLETED, success.getStatus());
            assertEquals(
                    200,
                    ((Number)
                                    ((Map<?, ?>) success.getOutputData().get("response"))
                                            .get("statusCode"))
                            .intValue());
            assertEquals(
                    Task.Status.COMPLETED_WITH_ERRORS,
                    failed.getStatus(),
                    "the optional dynamic branch must record its failure without failing the agent");
            assertTrue(
                    failed.getReasonForIncompletion().contains("503"),
                    "the failed HTTP branch must preserve the actual non-2xx response");
            assertEquals(
                    "LLM_CHAT_COMPLETE",
                    secondLlm.getTaskType(),
                    "an optional HTTP failure must not strand the agent before its next turn");

            completeScriptedLlm(
                    secondLlm,
                    Map.of("finishReason", "STOP", "result", "One API succeeded and one failed."));
            Workflow completed = awaitAgentTerminal(started.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
            assertEquals("One API succeeded and one failed.", completed.getOutput().get("result"));
        } finally {
            api.stop(0);
        }
    }

    @Test
    void explicitSwarmTransferIsACompilerOwnedControlThatStartsTheTargetWithoutAWorker() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String team = "swarm_team_" + suffix;
        String analyst = "swarm_analyst_" + suffix;
        String reviewer = "swarm_reviewer_" + suffix;
        String transfer = team + "_transfer_to_" + analyst;
        String reviewerTransfer = team + "_transfer_to_" + reviewer;
        String ordinaryTool = "ordinary_tool_" + suffix;
        AgentConfig analystConfig =
                AgentConfig.builder()
                        .name(analyst)
                        .model("openai/gpt-4o-mini")
                        .instructions("Analyze the delegated request")
                        .maxTurns(1)
                        .build();
        AgentConfig reviewerConfig =
                AgentConfig.builder()
                        .name(reviewer)
                        .model("openai/gpt-4o-mini")
                        .instructions("Review the delegated request")
                        .maxTurns(1)
                        .build();
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(team)
                                        .model("openai/gpt-4o-mini")
                                        .strategy(AgentConfig.Strategy.SWARM)
                                        .tools(
                                                List.of(
                                                        ToolConfig.builder()
                                                                .name(ordinaryTool)
                                                                .description("An ordinary tool")
                                                                .toolType("worker")
                                                                .build()))
                                        .agents(List.of(analystConfig, reviewerConfig))
                                        .allowedTransitions(
                                                Map.of(team, List.of(analyst, reviewer)))
                                        .maxTurns(2)
                                        .synthesize(false)
                                        .build())
                        .build());
        AgentStartResponse started =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(team)
                                .prompt("Investigate the durable handoff")
                                .context(Map.of("tenant", "acme"))
                                .build());

        Task sourceLlm = awaitScheduledLlmAcrossSubworkflows(started.getExecutionId());
        completeScriptedLlm(
                sourceLlm,
                Map.of(
                        "finishReason",
                        "TOOL_CALLS",
                        "result",
                        "",
                        "toolCalls",
                        List.of(
                                Map.of(
                                        "taskReferenceName",
                                        "ordinary_call",
                                        "name",
                                        ordinaryTool,
                                        "type",
                                        "SIMPLE",
                                        "inputParameters",
                                        Map.of("request", "must not run")),
                                Map.of(
                                        "taskReferenceName",
                                        "transfer_once",
                                        "name",
                                        transfer,
                                        "type",
                                        "SIMPLE",
                                        "inputParameters",
                                        Map.of(
                                                "message",
                                                "Please analyze the handoff and keep the tenant context.")),
                                Map.of(
                                        "taskReferenceName",
                                        "transfer_twice",
                                        "name",
                                        reviewerTransfer,
                                        "type",
                                        "SIMPLE",
                                        "inputParameters",
                                        Map.of("message", "This later transfer must not win.")))));

        Task targetLlm = awaitScheduledLlmAcrossSubworkflows(started.getExecutionId());
        assertFalse(
                targetLlm.getWorkflowInstanceId().equals(sourceLlm.getWorkflowInstanceId()),
                "the selected target must start in its own nested agent execution");
        completeScriptedLlm(
                targetLlm,
                Map.of("finishReason", "STOP", "result", "The analyst received the handoff."));

        Workflow completed = awaitAgentTerminal(started.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
        assertTrue(
                String.valueOf(completed.getOutput().get("result"))
                        .contains("[" + team + " -> " + analyst + "]:"),
                "the transfer annotation must be durable in the team transcript");
        assertFalse(
                String.valueOf(completed.getOutput().get("result"))
                        .contains("[" + team + " -> " + reviewer + "]:"),
                "only the first valid transfer target may be selected");

        Workflow outer = executionService.getExecutionStatus(started.getExecutionId(), true);
        assertTrue(
                outer.getTasks().stream()
                        .noneMatch(
                                task ->
                                        transfer.equals(task.getTaskDefName())
                                                || transfer.equals(task.getTaskType())),
                "an explicit transfer is an LLM-visible control signal, never a queued SIMPLE task");
        assertFalse(
                outer.getTasks().stream().anyMatch(task -> "HUMAN".equals(task.getTaskType())),
                "a valid explicit transfer must not run unrelated human work");
        assertEquals(
                0,
                queueDAO.getSize(ordinaryTool),
                "the transfer detector must skip every ordinary tool in the same model response");
    }

    @Test
    void swarmConditionWorkerReceivesTheCompleteRoutingContextAndSelectsTheTarget() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String team = "condition_team_" + suffix;
        String analyst = "condition_analyst_" + suffix;
        String conditionName = "select_analyst_" + suffix;
        ConditionWorker conditionWorker = new ConditionWorker(conditionName);
        TaskRunnerConfigurer runner = startWorker(conditionWorker);
        try {
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(team)
                                            .model("openai/gpt-4o-mini")
                                            .strategy(AgentConfig.Strategy.SWARM)
                                            .agents(
                                                    List.of(
                                                            AgentConfig.builder()
                                                                    .name(analyst)
                                                                    .model("openai/gpt-4o-mini")
                                                                    .maxTurns(1)
                                                                    .build()))
                                            .handoffs(
                                                    List.of(
                                                            HandoffConfig.builder()
                                                                    .type("on_condition")
                                                                    .target(analyst)
                                                                    .taskName(conditionName)
                                                                    .build()))
                                            .maxTurns(2)
                                            .synthesize(false)
                                            .build())
                            .build());
            AgentStartResponse started =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(team)
                                    .prompt("Use the condition worker to route this")
                                    .context(Map.of("tenant", "acme"))
                                    .build());

            Task sourceLlm = awaitScheduledLlmAcrossSubworkflows(started.getExecutionId());
            completeScriptedLlm(
                    sourceLlm,
                    Map.of("finishReason", "STOP", "result", "The condition should route now."));

            Task targetLlm = awaitScheduledLlmAcrossSubworkflows(started.getExecutionId());
            Awaitility.await()
                    .atMost(20, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(1, conditionWorker.invocationCount.get()));
            Map<String, Object> conditionInput = conditionWorker.lastInput.get();
            assertEquals("The condition should route now.", conditionInput.get("result"));
            assertTrue(String.valueOf(conditionInput.get("conversation")).contains("route now"));
            assertEquals(Map.of("tenant", "acme"), conditionInput.get("context"));
            assertEquals("0", String.valueOf(conditionInput.get("active_agent")));
            assertTrue(conditionInput.containsKey("tool_results"));
            completeScriptedLlm(
                    targetLlm,
                    Map.of("finishReason", "STOP", "result", "The analyst accepted the route."));

            Workflow completed = awaitAgentTerminal(started.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
            assertTrue(
                    String.valueOf(completed.getOutput().get("result"))
                            .contains("The analyst accepted the route."));
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void swarmTextMentionRoutesCaseInsensitivelyAndTerminalNonmatchingTextEndsTheSwarm() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String team = "text_team_" + suffix;
        String analyst = "text_analyst_" + suffix;
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(team)
                                        .model("openai/gpt-4o-mini")
                                        .strategy(AgentConfig.Strategy.SWARM)
                                        .agents(
                                                List.of(
                                                        AgentConfig.builder()
                                                                .name(analyst)
                                                                .model("openai/gpt-4o-mini")
                                                                .maxTurns(1)
                                                                .build()))
                                        .handoffs(
                                                List.of(
                                                        HandoffConfig.builder()
                                                                .type("on_text_mention")
                                                                .target(analyst)
                                                                .text("escalate")
                                                                .build()))
                                        .maxTurns(2)
                                        .synthesize(false)
                                        .build())
                        .build());

        AgentStartResponse matching =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(team)
                                .prompt("Decide whether escalation is needed")
                                .build());
        Task matchingSource = awaitScheduledLlmAcrossSubworkflows(matching.getExecutionId());
        completeScriptedLlm(
                matchingSource,
                Map.of("finishReason", "STOP", "result", "Please EsCaLaTe this case now."));
        Task matchedTarget = awaitScheduledLlmAcrossSubworkflows(matching.getExecutionId());
        assertTrue(
                matchedTarget.getReferenceTaskName().contains(analyst + "_llm"),
                "a case-variant text match must dispatch the configured target");
        completeScriptedLlm(
                matchedTarget,
                Map.of("finishReason", "STOP", "result", "Analyst accepted escalation."));
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                awaitAgentTerminal(matching.getExecutionId()).getStatus());

        AgentStartResponse nonmatching =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(team)
                                .prompt("Keep routine work with the source")
                                .build());
        Task firstSource = awaitScheduledLlmAcrossSubworkflows(nonmatching.getExecutionId());
        completeScriptedLlm(
                firstSource, Map.of("finishReason", "STOP", "result", "This is routine work."));
        assertTrue(firstSource.getReferenceTaskName().contains(team + "_llm"));
        Workflow completed = awaitAgentTerminal(nonmatching.getExecutionId());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
        assertTrue(
                String.valueOf(completed.getOutput().get("result"))
                        .contains("This is routine work."));
    }

    @Test
    void swarmConditionWorkerFalseMissingAndNonBooleanResultsNeverSelectATarget() {
        String suffix = UUID.randomUUID().toString().replace('-', '_');
        String team = "negative_condition_team_" + suffix;
        String analyst = "negative_condition_analyst_" + suffix;
        String conditionName = "negative_condition_" + suffix;
        OutcomeConditionWorker conditionWorker = new OutcomeConditionWorker(conditionName);
        TaskRunnerConfigurer runner = startWorker(conditionWorker);
        try {
            agentService.deploy(
                    AgentStartRequest.builder()
                            .agentConfig(
                                    AgentConfig.builder()
                                            .name(team)
                                            .model("openai/gpt-4o-mini")
                                            .strategy(AgentConfig.Strategy.SWARM)
                                            .agents(
                                                    List.of(
                                                            AgentConfig.builder()
                                                                    .name(analyst)
                                                                    .model("openai/gpt-4o-mini")
                                                                    .maxTurns(1)
                                                                    .build()))
                                            .handoffs(
                                                    List.of(
                                                            HandoffConfig.builder()
                                                                    .type("on_condition")
                                                                    .target(analyst)
                                                                    .taskName(conditionName)
                                                                    .build()))
                                            .maxTurns(2)
                                            .synthesize(false)
                                            .build())
                            .build());

            AgentStartResponse falseResult =
                    agentService.start(
                            AgentStartRequest.builder()
                                    .name(team)
                                    .prompt("The condition should decline")
                                    .context(Map.of("outcome", "false"))
                                    .build());
            Task firstSource = awaitScheduledLlmAcrossSubworkflows(falseResult.getExecutionId());
            completeScriptedLlm(
                    firstSource, Map.of("finishReason", "STOP", "result", "No handoff yet."));
            assertTrue(firstSource.getReferenceTaskName().contains(team + "_llm"));
            Workflow falseCompleted = awaitAgentTerminal(falseResult.getExecutionId());
            assertEquals(Workflow.WorkflowStatus.COMPLETED, falseCompleted.getStatus());
            assertTrue(
                    String.valueOf(falseCompleted.getOutput().get("result"))
                            .contains("No handoff yet."),
                    "handoff=false must end the swarm without invoking either source or target again");

            for (String outcome : List.of("missing", "non_boolean")) {
                AgentStartResponse invalidResult =
                        agentService.start(
                                AgentStartRequest.builder()
                                        .name(team)
                                        .prompt("The condition output is invalid")
                                        .context(Map.of("outcome", outcome))
                                        .build());
                Task source = awaitScheduledLlmAcrossSubworkflows(invalidResult.getExecutionId());
                completeScriptedLlm(
                        source,
                        Map.of("finishReason", "STOP", "result", "Do not route this output."));
                Workflow failed = awaitAgentTerminal(invalidResult.getExecutionId());
                assertEquals(
                        Workflow.WorkflowStatus.FAILED,
                        failed.getStatus(),
                        "on_condition must fail closed when handoff is " + outcome);
            }
            assertEquals(3, conditionWorker.invocationCount.get());
        } finally {
            runner.shutdown();
        }
    }

    private HttpResponse<String> jsonRequest(String method, String path, Object body)
            throws IOException, InterruptedException {
        return plainRequest(method, path, JSON.writeValueAsString(body), "application/json");
    }

    private HttpResponse<String> plainRequest(
            String method, String path, String body, String contentType)
            throws IOException, InterruptedException {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", contentType)
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return HTTP.send(
                request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.ISO_8859_1));
    }

    private HttpResponse<String> registerSkill(String manifest, byte[] packageBytes)
            throws IOException, InterruptedException {
        String boundary = "agent-span-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeMultipartPart(
                body,
                boundary,
                "manifest",
                null,
                "application/json",
                manifest.getBytes(StandardCharsets.UTF_8));
        writeMultipartPart(body, boundary, "package", "skill.zip", "application/zip", packageBytes);
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/skills/register"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                        .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.ISO_8859_1));
    }

    private static void writeMultipartPart(
            ByteArrayOutputStream body,
            String boundary,
            String name,
            String filename,
            String contentType,
            byte[] content)
            throws IOException {
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        String disposition = "Content-Disposition: form-data; name=\"" + name + "\"";
        if (filename != null) {
            disposition += "; filename=\"" + filename + "\"";
        }
        body.write((disposition + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static byte[] skillPackage(String name) throws IOException {
        return skillPackage(name, "real package content");
    }

    private static byte[] skillPackage(String name, String guideContent) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("SKILL.md"));
            zip.write(
                    ("---\nname: "
                                    + name
                                    + "\ndescription: Real HTTP skill package\n---\nUse the reference guide.\n")
                            .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("references/guide.md"));
            zip.write(guideContent.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static byte[] unsafeSkillPackage(String name) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("SKILL.md"));
            zip.write(
                    ("---\nname: " + name + "\ndescription: Unsafe package\n---\n")
                            .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("../outside.md"));
            zip.write("must never be accepted".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static boolean jsonArrayContains(JsonNode array, String expected) {
        for (JsonNode value : array) {
            if (expected.equals(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private AgentStartResponse startLlmGuardrailedAgent(String prefix, String onFail) {
        String agent = prefix + "_" + UUID.randomUUID().toString().replace('-', '_');
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(agent)
                                        .model("openai/gpt-4o-mini")
                                        .guardrails(
                                                List.of(
                                                        GuardrailConfig.builder()
                                                                .name("judge")
                                                                .guardrailType("llm")
                                                                .model("openai/gpt-4o-mini")
                                                                .policy("Only allow safe answers")
                                                                .onFail(onFail)
                                                                .maxRetries(0)
                                                                .build()))
                                        .maxTurns(2)
                                        .build())
                        .build());
        return agentService.start(
                AgentStartRequest.builder().name(agent).prompt("Produce one answer").build());
    }

    private void registerDeterministicChildWorkflow(String name) {
        WorkflowTask childTask = new WorkflowTask();
        childTask.setName("INLINE");
        childTask.setTaskReferenceName("child_result");
        childTask.setType("INLINE");
        childTask.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "expression",
                        "(function(){ return {result: 'child research complete'}; })();"));
        WorkflowDef child = new WorkflowDef();
        child.setName(name);
        child.setVersion(1);
        child.setOwnerEmail("agentspan-e2e@conductor.test");
        child.setTasks(List.of(childTask));
        child.setOutputParameters(Map.of("result", "${child_result.output.result.result}"));
        metadataService.updateWorkflowDef(List.of(child));
    }

    private TaskRunnerConfigurer startWorker(Worker worker) {
        return startWorkers(worker);
    }

    private TaskRunnerConfigurer startWorkers(Worker... workers) {
        TaskClient taskClient = new TaskClient();
        taskClient.setRootURI("http://localhost:" + port + "/api/");
        TaskRunnerConfigurer runner =
                new TaskRunnerConfigurer.Builder(taskClient, List.of(workers))
                        .withThreadCount(1)
                        .withTaskPollTimeout(1)
                        .build();
        runner.init();
        return runner;
    }

    private Task awaitScheduledLlm(String workflowId) {
        AtomicReference<Task> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            drainAsyncSystemTaskQueuesExceptLlm();
                            workflowExecutor.decide(workflowId);
                            Workflow workflow =
                                    executionService.getExecutionStatus(workflowId, true);
                            if (workflow == null || workflow.getTasks() == null) {
                                return false;
                            }
                            return workflow.getTasks().stream()
                                    .filter(task -> "LLM_CHAT_COMPLETE".equals(task.getTaskType()))
                                    .filter(task -> task.getStatus() == Task.Status.SCHEDULED)
                                    .reduce((first, second) -> second)
                                    .map(
                                            task -> {
                                                latest.set(task);
                                                return true;
                                            })
                                    .orElse(false);
                        });
        return latest.get();
    }

    private Task awaitScheduledLlmAcrossSubworkflows(String workflowId) {
        AtomicReference<Task> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            drainAsyncSystemTaskQueuesExceptLlm();
                            workflowExecutor.decide(workflowId);
                            Task scheduled =
                                    findScheduledLlm(workflowId, new java.util.HashSet<>());
                            if (scheduled == null) {
                                return false;
                            }
                            latest.set(scheduled);
                            return true;
                        });
        return latest.get();
    }

    private Task findScheduledLlm(String workflowId, Set<String> visited) {
        if (!visited.add(workflowId)) {
            return null;
        }
        Workflow workflow = executionService.getExecutionStatus(workflowId, true);
        if (workflow == null || workflow.getTasks() == null) {
            return null;
        }
        for (Task task : workflow.getTasks()) {
            if ("LLM_CHAT_COMPLETE".equals(task.getTaskType())
                    && task.getStatus() == Task.Status.SCHEDULED) {
                return task;
            }
        }
        for (Task task : workflow.getTasks()) {
            if (task.getSubWorkflowId() == null || task.getSubWorkflowId().isBlank()) {
                continue;
            }
            Task scheduled = findScheduledLlm(task.getSubWorkflowId(), visited);
            if (scheduled != null) {
                return scheduled;
            }
        }
        return null;
    }

    private Task awaitTaskAcrossSubworkflows(
            String workflowId, String taskType, Task.Status status) {
        AtomicReference<Task> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            drainAsyncSystemTaskQueuesExceptLlm();
                            workflowExecutor.decide(workflowId);
                            Task task =
                                    findTaskAcrossSubworkflows(
                                            workflowId,
                                            taskType,
                                            status,
                                            new java.util.HashSet<>());
                            if (task == null) {
                                return false;
                            }
                            latest.set(task);
                            return true;
                        });
        return latest.get();
    }

    private Task findTaskAcrossSubworkflows(
            String workflowId, String taskType, Task.Status status, Set<String> visited) {
        if (!visited.add(workflowId)) {
            return null;
        }
        Workflow workflow = executionService.getExecutionStatus(workflowId, true);
        if (workflow == null || workflow.getTasks() == null) {
            return null;
        }
        for (Task task : workflow.getTasks()) {
            if (taskType.equals(task.getTaskType()) && status == task.getStatus()) {
                return task;
            }
        }
        for (Task task : workflow.getTasks()) {
            if (task.getSubWorkflowId() == null || task.getSubWorkflowId().isBlank()) {
                continue;
            }
            Task nested =
                    findTaskAcrossSubworkflows(task.getSubWorkflowId(), taskType, status, visited);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private void completeScriptedLlm(Task llm, Map<String, Object> output) {
        TaskResult result = new TaskResult();
        result.setTaskId(llm.getTaskId());
        result.setWorkflowInstanceId(llm.getWorkflowInstanceId());
        result.setStatus(TaskResult.Status.COMPLETED);
        result.setOutputData(output);
        workflowExecutor.updateTask(result);
    }

    private static Map<String, Object> scriptedSimpleToolCall(String toolName, String reference) {
        return Map.of(
                "finishReason",
                "TOOL_CALLS",
                "result",
                "",
                "toolCalls",
                List.of(
                        Map.of(
                                "taskReferenceName",
                                reference,
                                "name",
                                toolName,
                                "type",
                                "SIMPLE",
                                "inputParameters",
                                Map.of())));
    }

    private Workflow awaitAgentTerminal(String workflowId) {
        AtomicReference<Workflow> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            drainAsyncSystemTaskQueuesExceptLlm();
                            workflowExecutor.decide(workflowId);
                            Workflow workflow =
                                    executionService.getExecutionStatus(workflowId, true);
                            latest.set(workflow);
                            return workflow != null
                                    && workflow.getStatus() != null
                                    && workflow.getStatus().isTerminal();
                        });
        return latest.get();
    }

    private void drainAsyncSystemTaskQueuesExceptLlm() {
        for (WorkflowSystemTask task : asyncSystemTasks) {
            if ("LLM_CHAT_COMPLETE".equals(task.getTaskType())) {
                continue;
            }
            for (String taskId : queueDAO.pop(task.getTaskType(), 5, 100)) {
                asyncSystemTaskExecutor.execute(task, taskId);
            }
        }
    }

    private static Task taskByType(Workflow workflow, String taskType) {
        return workflow.getTasks().stream()
                .filter(task -> taskType.equals(task.getTaskType()))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "task not found for type "
                                                + taskType
                                                + "; tasks="
                                                + workflow.getTasks()));
    }

    private static final class RecordingWorker implements Worker {

        private final String taskDefName;
        private final AtomicInteger invocationCount = new AtomicInteger();
        private final AtomicReference<Map<String, Object>> lastInput = new AtomicReference<>();
        private final List<Map<String, Object>> inputs =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        private RecordingWorker(String taskDefName) {
            this.taskDefName = taskDefName;
        }

        @Override
        public String getTaskDefName() {
            return taskDefName;
        }

        @Override
        public TaskResult execute(Task task) {
            invocationCount.incrementAndGet();
            Map<String, Object> input = new java.util.LinkedHashMap<>(task.getInputData());
            lastInput.set(input);
            inputs.add(input);
            TaskResult result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            result.addOutputData("customerName", "Alice");
            result.addOutputData("customerId", input.get("customerId"));
            result.addOutputData("_state_updates", Map.of("customerName", "Alice"));
            return result;
        }
    }

    private static final class ConditionWorker implements Worker {

        private final String taskDefName;
        private final AtomicInteger invocationCount = new AtomicInteger();
        private final AtomicReference<Map<String, Object>> lastInput = new AtomicReference<>();

        private ConditionWorker(String taskDefName) {
            this.taskDefName = taskDefName;
        }

        @Override
        public String getTaskDefName() {
            return taskDefName;
        }

        @Override
        public TaskResult execute(Task task) {
            invocationCount.incrementAndGet();
            lastInput.set(new java.util.LinkedHashMap<>(task.getInputData()));
            TaskResult result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            result.addOutputData("handoff", true);
            return result;
        }
    }

    private static final class ConcurrentStateWorker implements Worker {

        private final String taskDefName;
        private final List<Map<String, Object>> inputs =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        private ConcurrentStateWorker(String taskDefName) {
            this.taskDefName = taskDefName;
        }

        @Override
        public String getTaskDefName() {
            return taskDefName;
        }

        @Override
        @SuppressWarnings("unchecked")
        public TaskResult execute(Task task) {
            Map<String, Object> input = new java.util.LinkedHashMap<>(task.getInputData());
            inputs.add(input);
            Map<String, Object> state =
                    input.get("_agent_state") instanceof Map<?, ?> map
                            ? (Map<String, Object>) map
                            : Map.of();
            String tenant = String.valueOf(state.get("tenant"));
            TaskResult result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            result.addOutputData("handledTenant", tenant);
            result.addOutputData("_state_updates", Map.of("handledTenant", tenant));
            return result;
        }
    }

    private static final class GraphStateWorker implements Worker {

        private final String taskDefName;
        private final String contribution;
        private final AtomicInteger invocationCount = new AtomicInteger();
        private final AtomicReference<Map<String, Object>> lastInput = new AtomicReference<>();

        private GraphStateWorker(String taskDefName, String contribution) {
            this.taskDefName = taskDefName;
            this.contribution = contribution;
        }

        @Override
        public String getTaskDefName() {
            return taskDefName;
        }

        @Override
        @SuppressWarnings("unchecked")
        public TaskResult execute(Task task) {
            invocationCount.incrementAndGet();
            Map<String, Object> input = new java.util.LinkedHashMap<>(task.getInputData());
            lastInput.set(input);
            Map<String, Object> state =
                    input.get("state") instanceof Map<?, ?> raw
                            ? new java.util.LinkedHashMap<>((Map<String, Object>) raw)
                            : new java.util.LinkedHashMap<>();
            if ("joined".equals(contribution)) {
                state.put("result", contribution);
            } else {
                state.put("items", List.of(contribution));
            }
            TaskResult result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            result.addOutputData("state", state);
            result.addOutputData("result", contribution);
            return result;
        }
    }

    private static final class GraphDecisionWorker implements Worker {

        private final String taskDefName;
        private final String decision;
        private final AtomicInteger invocationCount = new AtomicInteger();

        private GraphDecisionWorker(String taskDefName, String decision) {
            this.taskDefName = taskDefName;
            this.decision = decision;
        }

        @Override
        public String getTaskDefName() {
            return taskDefName;
        }

        @Override
        @SuppressWarnings("unchecked")
        public TaskResult execute(Task task) {
            invocationCount.incrementAndGet();
            Map<String, Object> state =
                    task.getInputData().get("state") instanceof Map<?, ?> raw
                            ? new java.util.LinkedHashMap<>((Map<String, Object>) raw)
                            : Map.of();
            TaskResult result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            result.addOutputData("decision", decision);
            result.addOutputData("state", state);
            return result;
        }
    }

    private static final class GraphLlmPrepWorker implements Worker {

        private final String taskDefName;
        private final AtomicInteger invocationCount = new AtomicInteger();

        private GraphLlmPrepWorker(String taskDefName) {
            this.taskDefName = taskDefName;
        }

        @Override
        public String getTaskDefName() {
            return taskDefName;
        }

        @Override
        @SuppressWarnings("unchecked")
        public TaskResult execute(Task task) {
            invocationCount.incrementAndGet();
            Map<String, Object> state =
                    task.getInputData().get("state") instanceof Map<?, ?> raw
                            ? new java.util.LinkedHashMap<>((Map<String, Object>) raw)
                            : new java.util.LinkedHashMap<>();
            boolean skip = String.valueOf(state.get("request")).contains("skip");
            TaskResult result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            result.addOutputData("_skip_llm", skip);
            result.addOutputData("state", state);
            if (skip) {
                result.addOutputData("result", "Precomputed graph result.");
            } else {
                result.addOutputData(
                        "messages",
                        List.of(
                                Map.of(
                                        "role",
                                        "user",
                                        "message",
                                        "Analyze this graph state: " + state)));
            }
            return result;
        }
    }

    private static final class GraphLlmFinishWorker implements Worker {

        private final String taskDefName;
        private final AtomicInteger invocationCount = new AtomicInteger();

        private GraphLlmFinishWorker(String taskDefName) {
            this.taskDefName = taskDefName;
        }

        @Override
        public String getTaskDefName() {
            return taskDefName;
        }

        @Override
        @SuppressWarnings("unchecked")
        public TaskResult execute(Task task) {
            invocationCount.incrementAndGet();
            Map<String, Object> state =
                    task.getInputData().get("state") instanceof Map<?, ?> raw
                            ? new java.util.LinkedHashMap<>((Map<String, Object>) raw)
                            : new java.util.LinkedHashMap<>();
            String answer = String.valueOf(task.getInputData().get("llm_result"));
            state.put("result", answer);
            TaskResult result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            result.addOutputData("state", state);
            result.addOutputData("result", answer);
            return result;
        }
    }

    private static final class GraphDynamicRouterWorker implements Worker {

        private final String taskDefName;
        private final AtomicInteger invocationCount = new AtomicInteger();

        private GraphDynamicRouterWorker(String taskDefName) {
            this.taskDefName = taskDefName;
        }

        @Override
        public String getTaskDefName() {
            return taskDefName;
        }

        @Override
        @SuppressWarnings("unchecked")
        public TaskResult execute(Task task) {
            invocationCount.incrementAndGet();
            Map<String, Object> state =
                    task.getInputData().get("state") instanceof Map<?, ?> raw
                            ? new java.util.LinkedHashMap<>((Map<String, Object>) raw)
                            : new java.util.LinkedHashMap<>();
            TaskResult result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            result.addOutputData("state", state);
            result.addOutputData(
                    "dynamic_tasks",
                    List.of(
                            Map.of("node", "first", "input", state),
                            Map.of("node", "second", "input", state)));
            return result;
        }
    }

    private static final class GraphLoopRouterWorker implements Worker {

        private final String taskDefName;
        private final AtomicInteger invocationCount = new AtomicInteger();

        private GraphLoopRouterWorker(String taskDefName) {
            this.taskDefName = taskDefName;
        }

        @Override
        public String getTaskDefName() {
            return taskDefName;
        }

        @Override
        @SuppressWarnings("unchecked")
        public TaskResult execute(Task task) {
            int invocation = invocationCount.incrementAndGet();
            Map<String, Object> state =
                    task.getInputData().get("state") instanceof Map<?, ?> raw
                            ? new java.util.LinkedHashMap<>((Map<String, Object>) raw)
                            : new java.util.LinkedHashMap<>();
            state.put("loop_count", invocation);
            TaskResult result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            result.addOutputData("decision", invocation == 1 ? "continue" : "exit");
            result.addOutputData("state", state);
            return result;
        }
    }

    private static final class GraphSubgraphPrepWorker implements Worker {

        private final String taskDefName;
        private final AtomicInteger invocationCount = new AtomicInteger();

        private GraphSubgraphPrepWorker(String taskDefName) {
            this.taskDefName = taskDefName;
        }

        @Override
        public String getTaskDefName() {
            return taskDefName;
        }

        @Override
        @SuppressWarnings("unchecked")
        public TaskResult execute(Task task) {
            invocationCount.incrementAndGet();
            Map<String, Object> state =
                    task.getInputData().get("state") instanceof Map<?, ?> raw
                            ? new java.util.LinkedHashMap<>((Map<String, Object>) raw)
                            : new java.util.LinkedHashMap<>();
            state.put("prepared_by_parent", true);
            TaskResult result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            result.addOutputData("state", state);
            result.addOutputData("subgraph_input", state);
            return result;
        }
    }

    private static final class GraphSubgraphFinishWorker implements Worker {

        private final String taskDefName;
        private final AtomicInteger invocationCount = new AtomicInteger();
        private final AtomicReference<Map<String, Object>> lastInput = new AtomicReference<>();

        private GraphSubgraphFinishWorker(String taskDefName) {
            this.taskDefName = taskDefName;
        }

        @Override
        public String getTaskDefName() {
            return taskDefName;
        }

        @Override
        @SuppressWarnings("unchecked")
        public TaskResult execute(Task task) {
            invocationCount.incrementAndGet();
            Map<String, Object> input = new java.util.LinkedHashMap<>(task.getInputData());
            lastInput.set(input);
            Map<String, Object> childState =
                    input.get("subgraph_result") instanceof Map<?, ?> raw
                            ? new java.util.LinkedHashMap<>((Map<String, Object>) raw)
                            : new java.util.LinkedHashMap<>();
            childState.put("finished_by_parent", true);
            TaskResult result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            result.addOutputData("state", childState);
            result.addOutputData("result", "subgraph result");
            return result;
        }
    }

    private static final class SequencedRouterWorker implements Worker {

        private final String taskDefName;
        private final String firstDecision;
        private final AtomicInteger invocationCount = new AtomicInteger();
        private final List<Map<String, Object>> inputs =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        private SequencedRouterWorker(String taskDefName, String firstDecision) {
            this.taskDefName = taskDefName;
            this.firstDecision = firstDecision;
        }

        @Override
        public String getTaskDefName() {
            return taskDefName;
        }

        @Override
        public TaskResult execute(Task task) {
            inputs.add(new java.util.LinkedHashMap<>(task.getInputData()));
            TaskResult result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            result.addOutputData(
                    "result", invocationCount.getAndIncrement() == 0 ? firstDecision : "DONE");
            return result;
        }
    }

    private static final class ManualSelectionWorker implements Worker {

        private final String taskDefName;
        private final AtomicInteger invocationCount = new AtomicInteger();
        private final AtomicReference<String> selected = new AtomicReference<>();

        private ManualSelectionWorker(String taskDefName) {
            this.taskDefName = taskDefName;
        }

        @Override
        public String getTaskDefName() {
            return taskDefName;
        }

        @Override
        public TaskResult execute(Task task) {
            invocationCount.incrementAndGet();
            Object humanOutput = task.getInputData().get("human_output");
            String choice =
                    humanOutput instanceof Map<?, ?> output
                            ? String.valueOf(output.get("selected"))
                            : "";
            selected.set(choice);
            TaskResult result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            // The compiler's MANUAL switch is indexed by child order, while the HUMAN form emits
            // the human-readable child name. A real selection worker performs that translation.
            result.addOutputData("selected", "0");
            return result;
        }
    }

    private static final class OutcomeConditionWorker implements Worker {

        private final String taskDefName;
        private final AtomicInteger invocationCount = new AtomicInteger();

        private OutcomeConditionWorker(String taskDefName) {
            this.taskDefName = taskDefName;
        }

        @Override
        public String getTaskDefName() {
            return taskDefName;
        }

        @Override
        @SuppressWarnings("unchecked")
        public TaskResult execute(Task task) {
            invocationCount.incrementAndGet();
            Object context = task.getInputData().get("context");
            String outcome =
                    context instanceof Map<?, ?> map
                            ? String.valueOf(((Map<String, Object>) map).get("outcome"))
                            : "missing";
            TaskResult result = new TaskResult(task);
            result.setStatus(TaskResult.Status.COMPLETED);
            if ("false".equals(outcome)) {
                result.addOutputData("handoff", false);
            } else if ("non_boolean".equals(outcome)) {
                result.addOutputData("handoff", "true");
            }
            return result;
        }
    }

    private static int taskIndex(WorkflowDef workflow, String taskName) {
        for (int index = 0; index < workflow.getTasks().size(); index++) {
            if (taskName.equals(workflow.getTasks().get(index).getName())) {
                return index;
            }
        }
        throw new AssertionError("Missing task " + taskName);
    }

    private static void createPayloadBucket() {
        try (S3Client s3 =
                S3Client.builder()
                        .endpointOverride(
                                LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3))
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("test", "test")))
                        .build()) {
            s3.createBucket(CreateBucketRequest.builder().bucket(PAYLOAD_BUCKET).build());
        }
    }
}
