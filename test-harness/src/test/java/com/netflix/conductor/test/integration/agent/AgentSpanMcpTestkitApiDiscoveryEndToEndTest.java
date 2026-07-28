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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;
import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;
import org.conductoross.conductor.common.metadata.agent.ToolConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.s3.storage.S3PayloadStorage;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.test.config.LocalStackS3Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises AgentSpan API discovery and MCP calls through the real Conductor engine and
 * mcp-testkit's HTTP transport. The server exposes a deterministic authenticated OpenAPI document
 * and MCP protocol surface, so this catches routing and durability regressions that a hand-built
 * JSON fixture cannot.
 */
@SpringBootTest(classes = ConductorTestApp.class)
@Import(LocalStackS3Configuration.class)
@Tag("agentspan-deterministic-e2e")
@TestPropertySource(
        locations = "classpath:application-integrationtest.properties",
        properties = {
            "conductor.db.type=redis_standalone",
            "conductor.queue.type=redis_standalone",
            "conductor.app.sweeperThreadCount=1",
            "conductor.app.sweeper.sweepBatchSize=1",
            "conductor.integrations.ai.enabled=true",
            "agentspan.embedded=true",
            "conductor.ai.outbound.allow-private-networks=true",
            "conductor.external-payload-storage.type=s3",
            "conductor.external-payload-storage.s3.bucketName="
                    + AgentSpanMcpTestkitApiDiscoveryEndToEndTest.PAYLOAD_BUCKET,
            "conductor.external-payload-storage.s3.region=us-east-1",
            "conductor.external-payload-storage.s3.use_default_client=false"
        })
class AgentSpanMcpTestkitApiDiscoveryEndToEndTest {

    private static final String TESTKIT_AUTH = "mcp-testkit-e2e-key";
    static final String PAYLOAD_BUCKET = "agentspan-mcp-e2e-payloads";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:6.2-alpine"))
                    .withExposedPorts(6379);

    @SuppressWarnings("resource")
    private static final GenericContainer<?> MCP_TESTKIT =
            new GenericContainer<>(DockerImageName.parse("python:3.12-slim"))
                    .withExposedPorts(3001)
                    .withCommand(
                            "sh",
                            "-c",
                            "pip install --no-cache-dir mcp-testkit==1.0.3"
                                    + " && mcp-testkit --transport http --host 0.0.0.0 --port 3001"
                                    + " --auth "
                                    + TESTKIT_AUTH);

    @SuppressWarnings("resource")
    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
                    .withServices(LocalStackContainer.Service.S3);

    static {
        REDIS.start();
        MCP_TESTKIT.start();
        LOCALSTACK.start();
        LocalStackS3Configuration.setLocalStackEndpoint(
                LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        createPayloadBucket();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("conductor.redis.availability-zone", () -> "us-east-1c");
        registry.add("conductor.redis.data-center-region", () -> "us-east-1");
        registry.add("conductor.redis.workflow-namespace-prefix", () -> "agentspan-mcp-e2e");
        registry.add("conductor.redis.queue-namespace-prefix", () -> "agentspan-mcp-e2e");
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
        registry.add(
                "conductor.ai.outbound.allowed-origins[0]",
                AgentSpanMcpTestkitApiDiscoveryEndToEndTest::origin);
    }

    @Autowired private MetadataService metadataService;
    @Autowired private WorkflowExecutor workflowExecutor;
    @Autowired private ExecutionService executionService;
    @Autowired private SystemTaskRegistry systemTaskRegistry;
    @Autowired private ExternalPayloadStorageUtils externalPayloadStorageUtils;
    @Autowired private ExternalPayloadStorage externalPayloadStorage;
    @Autowired private QueueDAO queueDAO;
    @Autowired private AsyncSystemTaskExecutor asyncSystemTaskExecutor;
    @Autowired private AgentService agentService;

    @Autowired
    @Qualifier(SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER)
    private Set<WorkflowSystemTask> asyncSystemTasks;

    @BeforeEach
    void usesRealS3PayloadStorage() {
        assertTrue(
                externalPayloadStorage instanceof S3PayloadStorage,
                () ->
                        "AgentSpan E2E must use LocalStack S3, not a test double: "
                                + externalPayloadStorage.getClass().getName());
    }

    @Test
    void discoversAuthenticatedOpenApiToolsThroughTheWorkflowEngine() {
        String workflowName = "mcp_testkit_discovery_" + UUID.randomUUID();
        registerDiscoveryWorkflow(workflowName, TESTKIT_AUTH);

        Workflow workflow = awaitTerminal(start(workflowName));

        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertTrue(
                systemTaskRegistry.isSystemTask("LIST_API_TOOLS"),
                "API discovery must execute as a compiler-owned system task, never as a worker");
        assertEquals(1, workflow.getTasks().size());
        var discoveryTask = workflow.getTasks().get(0);
        assertEquals("LIST_API_TOOLS", discoveryTask.getTaskType());
        assertTrue(
                discoveryTask.getExternalOutputPayloadStoragePath() != null,
                "the full mcp-testkit catalog must survive Conductor's real external payload path");
        Map<String, Object> output = outputOf(discoveryTask);
        assertEquals(
                "openapi3",
                output.get("format"),
                () ->
                        "unexpected API discovery task output="
                                + output
                                + ", task status="
                                + discoveryTask.getStatus()
                                + ", workflow output="
                                + workflow.getOutput());
        assertEquals(origin(), output.get("baseUrl"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) output.get("tools");
        assertTrue(
                tools.stream().anyMatch(tool -> "math_add".equals(tool.get("name"))),
                "mcp-testkit's OpenAPI surface must expose math_add as a discovered API tool");
    }

    @Test
    void rejectsMissingCredentialsWithoutPersistingDiscoveredTools() {
        String workflowName = "mcp_testkit_unauthorized_" + UUID.randomUUID();
        registerDiscoveryWorkflow(workflowName, "wrong-key");

        Workflow workflow = awaitTerminal(start(workflowName));

        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
        assertTrue(
                !outputOf(workflow.getTasks().get(0)).containsKey("tools"),
                "an unauthorized OpenAPI fetch must not leave partial discovered tool metadata");
    }

    @Test
    void makesTheExternalizedApiCatalogAvailableToTheNextCompilerTask() {
        String workflowName = "mcp_testkit_discovery_consumer_" + UUID.randomUUID();
        registerDiscoveryConsumerWorkflow(workflowName);

        Workflow workflow = awaitTerminal(start(workflowName));

        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        @SuppressWarnings("unchecked")
        Map<String, Object> summary =
                (Map<String, Object>)
                        outputOf(taskByReference(workflow, "summarize_catalog")).get("result");
        assertEquals("openapi3", summary.get("format"));
        assertTrue(
                ((Number) summary.get("toolCount")).intValue() > 60,
                "the compiler's next task must see the externalized full API catalog");
    }

    @Test
    void discoversAndInvokesAuthenticatedMcpToolsThroughTheWorkflowEngine() {
        String workflowName = "mcp_testkit_protocol_" + UUID.randomUUID();
        registerMcpWorkflow(workflowName, TESTKIT_AUTH, true);

        Workflow workflow = awaitTerminal(start(workflowName));

        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertTrue(systemTaskRegistry.isSystemTask("LIST_MCP_TOOLS"));
        assertTrue(systemTaskRegistry.isSystemTask("CALL_MCP_TOOL"));
        assertEquals(2, workflow.getTasks().size());

        Map<String, Object> listed = outputOf(taskByReference(workflow, "list_mcp"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) listed.get("tools");
        assertTrue(
                tools.stream().anyMatch(tool -> "math_add".equals(tool.get("name"))),
                "the real MCP catalog must expose math_add before AgentSpan can dispatch it");

        Map<String, Object> callOutput = outputOf(taskByReference(workflow, "call_math"));
        assertEquals(false, callOutput.get("isError"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) callOutput.get("content");
        assertEquals("text", content.get(0).get("type"));
        assertTrue(
                String.valueOf(content.get(0).get("text")).contains("5.0"),
                "math_add must preserve the deterministic MCP result");
    }

    @Test
    void rejectsUnauthenticatedMcpDiscoveryBeforeAnyCallCanBeScheduled() {
        String workflowName = "mcp_testkit_protocol_unauthorized_" + UUID.randomUUID();
        registerMcpWorkflow(workflowName, "wrong-key", false);

        Workflow workflow = awaitTerminal(start(workflowName));

        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        var listTask = taskByReference(workflow, "list_mcp");
        assertTrue(
                !outputOf(listTask).containsKey("tools"),
                "a failed MCP handshake must not leak a partial tool list into routing state");
        assertTrue(
                listTask.getReasonForIncompletion().contains("HTTP 401"),
                "the terminal task must retain the real authentication failure");
    }

    @Test
    void compiledAgentDispatchesMcpToolAndFeedsItsActualResultIntoTheNextModelTurn() {
        String agentName = "scripted_mcp_agent_" + UUID.randomUUID().toString().replace('-', '_');
        AgentConfig config =
                AgentConfig.builder()
                        .name(agentName)
                        .model("openai/gpt-4o-mini")
                        .instructions(
                                "Use the math_add tool when asked for a sum, then answer with its result.")
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name("math_add")
                                                .description("Adds two numbers")
                                                .toolType("mcp")
                                                .inputSchema(
                                                        Map.of(
                                                                "type",
                                                                "object",
                                                                "properties",
                                                                Map.of(
                                                                        "a",
                                                                        Map.of("type", "number"),
                                                                        "b",
                                                                        Map.of("type", "number")),
                                                                "required",
                                                                List.of("a", "b")))
                                                .config(
                                                        Map.of(
                                                                "server_url",
                                                                origin() + "/mcp",
                                                                "max_tools",
                                                                100,
                                                                "headers",
                                                                Map.of(
                                                                        "Authorization",
                                                                        "Bearer " + TESTKIT_AUTH)))
                                                .build()))
                        .maxTurns(3)
                        .build();
        agentService.deploy(AgentStartRequest.builder().agentConfig(config).build());

        AgentStartResponse started =
                agentService.start(
                        AgentStartRequest.builder()
                                .name(agentName)
                                .prompt("What is 2 + 3?")
                                .build());

        Task firstLlm = awaitScheduledLlm(started.getExecutionId());
        completeLlm(
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
                                        "math_call",
                                        "name",
                                        "math_add",
                                        "type",
                                        "CALL_MCP_TOOL",
                                        "inputParameters",
                                        Map.of("a", 2, "b", 3)))));

        Task finalLlm = awaitScheduledLlm(started.getExecutionId());
        Workflow betweenTurns = executionService.getExecutionStatus(started.getExecutionId(), true);
        Task mcp = taskByType(betweenTurns, "CALL_MCP_TOOL");
        assertEquals(false, outputOf(mcp).get("isError"));

        completeLlm(finalLlm, Map.of("finishReason", "STOP", "result", "The sum is 5."));
        Workflow completed = awaitTerminal(started.getExecutionId());

        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
        assertEquals("The sum is 5.", completed.getOutput().get("result"));
    }

    private void registerDiscoveryWorkflow(String name, String auth) {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("LIST_API_TOOLS");
        taskDef.setRetryCount(0);
        taskDef.setResponseTimeoutSeconds(20);
        taskDef.setTimeoutSeconds(30);
        metadataService.registerTaskDef(List.of(taskDef));

        WorkflowTask discovery = new WorkflowTask();
        discovery.setName("LIST_API_TOOLS");
        discovery.setTaskReferenceName("discover");
        discovery.setType("LIST_API_TOOLS");
        discovery.setInputParameters(
                Map.of(
                        "specUrl",
                        origin() + "/api-docs",
                        "headers",
                        Map.of("Authorization", "Bearer " + auth)));

        WorkflowDef workflow = new WorkflowDef();
        workflow.setName(name);
        workflow.setVersion(1);
        workflow.setOwnerEmail("agentspan-e2e@conductor.test");
        workflow.setTasks(List.of(discovery));
        metadataService.updateWorkflowDef(List.of(workflow));
    }

    private void registerMcpWorkflow(String name, String auth, boolean includeCall) {
        WorkflowTask list = new WorkflowTask();
        list.setName("LIST_MCP_TOOLS");
        list.setTaskReferenceName("list_mcp");
        list.setType("LIST_MCP_TOOLS");
        list.setRetryCount(0);
        Map<String, Object> listInput = new HashMap<>();
        listInput.put("mcpServer", origin() + "/mcp");
        listInput.put("headers", new HashMap<>(Map.of("Authorization", "Bearer " + auth)));
        list.setInputParameters(listInput);

        WorkflowDef workflow = new WorkflowDef();
        workflow.setName(name);
        workflow.setVersion(1);
        workflow.setOwnerEmail("agentspan-e2e@conductor.test");
        if (!includeCall) {
            workflow.setTasks(List.of(list));
            metadataService.updateWorkflowDef(List.of(workflow));
            return;
        }

        WorkflowTask call = new WorkflowTask();
        call.setName("CALL_MCP_TOOL");
        call.setTaskReferenceName("call_math");
        call.setType("CALL_MCP_TOOL");
        call.setRetryCount(0);
        Map<String, Object> callInput = new HashMap<>();
        callInput.put("mcpServer", origin() + "/mcp");
        callInput.put("method", "math_add");
        callInput.put("arguments", new HashMap<>(Map.of("a", 2, "b", 3)));
        callInput.put("headers", new HashMap<>(Map.of("Authorization", "Bearer " + auth)));
        call.setInputParameters(callInput);
        workflow.setTasks(List.of(list, call));
        metadataService.updateWorkflowDef(List.of(workflow));
    }

    private void registerDiscoveryConsumerWorkflow(String name) {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("LIST_API_TOOLS");
        taskDef.setRetryCount(0);
        taskDef.setResponseTimeoutSeconds(20);
        taskDef.setTimeoutSeconds(30);
        metadataService.registerTaskDef(List.of(taskDef));

        WorkflowTask discovery = new WorkflowTask();
        discovery.setName("LIST_API_TOOLS");
        discovery.setTaskReferenceName("discover");
        discovery.setType("LIST_API_TOOLS");
        discovery.setInputParameters(
                Map.of(
                        "specUrl",
                        origin() + "/api-docs",
                        "headers",
                        Map.of("Authorization", "Bearer " + TESTKIT_AUTH)));

        WorkflowTask summarize = new WorkflowTask();
        summarize.setName("INLINE");
        summarize.setTaskReferenceName("summarize_catalog");
        summarize.setType("INLINE");
        Map<String, Object> summarizeInput = new HashMap<>();
        summarizeInput.put("evaluatorType", "graaljs");
        summarizeInput.put(
                "expression",
                "(function(){ return {format: $.catalog.format, toolCount: $.catalog.tools.length}; })();");
        summarizeInput.put("catalog", "${discover.output}");
        summarize.setInputParameters(summarizeInput);

        WorkflowDef workflow = new WorkflowDef();
        workflow.setName(name);
        workflow.setVersion(1);
        workflow.setOwnerEmail("agentspan-e2e@conductor.test");
        workflow.setTasks(List.of(discovery, summarize));
        metadataService.updateWorkflowDef(List.of(workflow));
    }

    private String start(String workflowName) {
        StartWorkflowInput input = new StartWorkflowInput();
        input.setName(workflowName);
        input.setVersion(1);
        input.setWorkflowInput(Map.of());
        return workflowExecutor.startWorkflow(input);
    }

    private Workflow awaitTerminal(String workflowId) {
        AtomicReference<Workflow> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            drainAsyncSystemTaskQueues();
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

    private void completeLlm(Task llm, Map<String, Object> output) {
        TaskResult result = new TaskResult();
        result.setTaskId(llm.getTaskId());
        result.setWorkflowInstanceId(llm.getWorkflowInstanceId());
        result.setStatus(TaskResult.Status.COMPLETED);
        result.setOutputData(output);
        workflowExecutor.updateTask(result);
    }

    private void drainAsyncSystemTaskQueues() {
        for (WorkflowSystemTask task : asyncSystemTasks) {
            for (String taskId : queueDAO.pop(task.getTaskType(), 5, 100)) {
                asyncSystemTaskExecutor.execute(task, taskId);
            }
        }
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

    private Map<String, Object> outputOf(com.netflix.conductor.common.metadata.tasks.Task task) {
        String externalPath = task.getExternalOutputPayloadStoragePath();
        return externalPath == null
                ? task.getOutputData()
                : externalPayloadStorageUtils.downloadPayload(externalPath);
    }

    private com.netflix.conductor.common.metadata.tasks.Task taskByReference(
            Workflow workflow, String reference) {
        return workflow.getTasks().stream()
                .filter(task -> reference.equals(task.getReferenceTaskName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("task not found: " + reference));
    }

    private Task taskByType(Workflow workflow, String type) {
        return workflow.getTasks().stream()
                .filter(task -> type.equals(task.getTaskType()))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "task not found for type "
                                                + type
                                                + "; workflow tasks="
                                                + workflow.getTasks().stream()
                                                        .map(
                                                                task ->
                                                                        task.getTaskType()
                                                                                + "/"
                                                                                + task.getStatus()
                                                                                + " "
                                                                                + task
                                                                                        .getOutputData())
                                                        .toList()));
    }

    private static String origin() {
        return "http://localhost:" + MCP_TESTKIT.getFirstMappedPort();
    }

    /**
     * The API catalog intentionally exceeds the configured task-output threshold. Persisting it to
     * a real S3-compatible service proves that the compiler can consume an externalized catalog
     * without relying on the test harness' mock payload store.
     */
    private static void createPayloadBucket() {
        try (S3Client client =
                S3Client.builder()
                        .endpointOverride(
                                LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3))
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create(
                                                LOCALSTACK.getAccessKey(),
                                                LOCALSTACK.getSecretKey())))
                        .forcePathStyle(true)
                        .build()) {
            client.createBucket(CreateBucketRequest.builder().bucket(PAYLOAD_BUCKET).build());
        }
    }
}
