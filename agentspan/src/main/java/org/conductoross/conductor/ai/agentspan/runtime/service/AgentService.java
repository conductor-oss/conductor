/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.conductoross.conductor.ai.agentspan.runtime.compiler.AgentCompiler;
import org.conductoross.conductor.ai.agentspan.runtime.compiler.MultiAgentCompiler;
import org.conductoross.conductor.ai.agentspan.runtime.normalizer.NormalizerRegistry;
import org.conductoross.conductor.ai.agentspan.runtime.util.WorkflowClassifiers;
import org.conductoross.conductor.common.metadata.agent.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.service.TaskService;
import com.netflix.conductor.service.WorkflowService;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agentspan.embedded", havingValue = "true")
@Slf4j
public class AgentService {

    private static final ObjectMapper MAPPER = new ObjectMapperProvider().getObjectMapper();
    private static final String AGENT_CLASSIFIER_BACKFILL_VERSION =
            "agent_classifier_backfill_version";
    // Version 2 additionally reindexes generated router sub-workflows, which older compiler
    // output persisted as ordinary workflow executions.
    private static final int AGENT_CLASSIFIER_BACKFILL_VERSION_VALUE = 2;

    private final AgentCompiler agentCompiler;
    private final NormalizerRegistry normalizerRegistry;
    private final ExecutionDAO executionDAO;
    private final MetadataDAO metadataDAO;

    private final IndexDAO indexDAO;

    private final WorkflowService workflowService;
    private final TaskService taskService;
    private final WorkflowExecutor workflowExecutor;

    private final AgentStreamRegistry streamRegistry;
    private final SkillRegistryService skillRegistryService;
    private final MetadataService metadataService;

    /**
     * Compile an agent config into a WorkflowDef and return it. Supports both native AgentConfig
     * and framework-specific raw configs.
     */
    @SuppressWarnings("unchecked")
    public CompileResponse compile(AgentStartRequest request) {
        AgentConfig config = resolveConfig(request);
        // Assign a default name for plan/compile if not provided
        if (config.getName() == null || config.getName().isEmpty()) {
            config.setName("agent_plan");
        }
        log.info("Compiling agent: {}", config.getName());
        WorkflowDef def = agentCompiler.compile(config);

        // Stamp agentDef into the compiled WorkflowDef so it is persisted when
        // the SDK passes the def inline to Conductor's start_workflow.
        String sdk = request.getFramework() != null ? request.getFramework() : "conductor";
        Map<String, Object> metadata =
                def.getMetadata() != null
                        ? new LinkedHashMap<>(def.getMetadata())
                        : new LinkedHashMap<>();
        metadata.put("agent_sdk", sdk);
        stampAgentDef(metadata, request, config);
        def.setMetadata(metadata);

        Set<String> workerNames = new LinkedHashSet<>(def.collectSimpleTaskNames());
        collectDeclaredWorkerNames(config, workerNames);
        List<String> requiredWorkers = new ArrayList<>(workerNames);
        Map<String, Object> defMap = MAPPER.convertValue(def, Map.class);
        return CompileResponse.builder()
                .workflowDef(defMap)
                .requiredWorkers(requiredWorkers)
                .build();
    }

    /**
     * /dg #6: compile a plan against a PLAN_EXECUTE harness config and return the resulting
     * Conductor WorkflowDef — without dispatching it. Lets callers inspect what PAC would produce
     * before running.
     *
     * <p>Uses the same {@link PlanAndCompileTask#inspectPlan(Map, String, String, int, Set, Map)}
     * path the runtime SUB_WORKFLOW dispatch uses, so there's exactly one compiler — no
     * inspect-only divergence.
     *
     * <p>Caller must supply both the agent config (so the compile knows about the tool list, model,
     * harness timeout) and the plan (typically what the planner LLM emitted, but can be a
     * hand-rolled static plan for offline validation).
     */
    public PlanAndCompileTask.InspectResult inspectPlan(InspectPlanRequest request) {
        if (request == null || request.getAgentConfig() == null) {
            throw new IllegalArgumentException("inspectPlan: agentConfig is required");
        }
        if (request.getPlan() == null) {
            throw new IllegalArgumentException("inspectPlan: plan is required");
        }
        AgentConfig config = request.getAgentConfig();
        if (config.getName() == null || config.getName().isEmpty()) {
            config.setName("agent_inspect");
        }
        if (config.getStrategy() != AgentConfig.Strategy.PLAN_EXECUTE) {
            throw new IllegalArgumentException(
                    "inspectPlan: agentConfig.strategy must be 'plan_execute', got '"
                            + (config.getStrategy() == null
                                    ? "null"
                                    : config.getStrategy().toValue())
                            + "'");
        }

        // Replicate what MultiAgentCompiler.compilePlanExecute computes
        // before calling PAC at runtime — so the inspect compile sees the
        // same inputs the real one would.
        String workflowName = MultiAgentCompiler.planWorkflowName(config.getName());
        String model = config.getModel() != null ? config.getModel() : "";
        int harnessTimeout = config.getTimeoutSeconds();
        List<ToolConfig> parentTools = config.getTools() != null ? config.getTools() : List.of();
        Set<String> knownToolNames = new HashSet<>();
        for (ToolConfig t : parentTools) {
            if (t.getName() != null && !t.getName().isEmpty()) {
                knownToolNames.add(t.getName());
            }
        }
        Map<String, ToolConfig> parentToolsByName = new LinkedHashMap<>();
        for (ToolConfig t : parentTools) {
            if (t.getName() != null && !t.getName().isEmpty()) {
                parentToolsByName.put(t.getName(), t);
            }
        }

        return new PlanAndCompileTask()
                .inspectPlan(
                        request.getPlan(),
                        workflowName,
                        model,
                        harnessTimeout,
                        knownToolNames,
                        parentToolsByName);
    }

    /**
     * Compile and register workflow + task definitions without starting execution. This is a CI/CD
     * operation — pushes the workflow to the server for later execution.
     */
    public AgentStartResponse deploy(AgentStartRequest request) {
        AgentConfig config = resolveConfig(request);
        log.info("Deploying agent: {}", config.getName());

        // 0. Pre-register child workflows for agent_tool types
        registerAgentToolWorkflows(config);

        // 1. Compile
        WorkflowDef def = agentCompiler.compile(config);

        // 1b. Stamp SDK metadata on the workflow definition
        String sdk = request.getFramework() != null ? request.getFramework() : "conductor";
        Map<String, Object> metadata =
                def.getMetadata() != null
                        ? new LinkedHashMap<>(def.getMetadata())
                        : new LinkedHashMap<>();
        metadata.put("agent_sdk", sdk);
        stampAgentDef(metadata, request, config);
        def.setMetadata(metadata);

        // 2. Register workflow definition (upsert)
        metadataDAO.updateWorkflowDef(def);

        // 3. Register task definitions for worker tools
        registerTaskDefinitions(config);

        Set<String> deployWorkerNames = new LinkedHashSet<>(def.collectSimpleTaskNames());
        collectDeclaredWorkerNames(config, deployWorkerNames);
        return AgentStartResponse.builder()
                .agentName(def.getName())
                .requiredWorkers(new ArrayList<>(deployWorkerNames))
                .build();
    }

    /** Start a deployed agent by name/version, or compile and start an inline agent definition. */
    @SuppressWarnings("unchecked")
    public AgentStartResponse start(AgentStartRequest request) {
        validateStartInput(request);
        validateStartSource(request);

        if (Strings.isNotEmpty(request.getName())) {
            return startRegistered(request);
        }

        return startInline(request);
    }

    private AgentStartResponse startRegistered(AgentStartRequest request) {
        WorkflowDef registeredDef = getRegisteredAgent(request.getName(), request.getVersion());
        AgentConfig config = resolveStoredConfig(registeredDef);
        Map<String, Object> storedAgentDef = storedAgentDef(registeredDef);

        WorkflowDef executionDef;
        if (Strings.isNotEmpty(request.getModel())) {
            // Per-call model override: recompile with the override applied, but never persist —
            // the stored agent definition (and its registered version) is left untouched.
            config.setModel(request.getModel());
            registerAgentToolWorkflows(config);
            executionDef = agentCompiler.compile(config);
            executionDef.setName(registeredDef.getName());
            executionDef.setVersion(registeredDef.getVersion());
            registerTaskDefinitions(config);
        } else {
            // MetadataDAO implementations may return cached definition instances. Work on a copy
            // so a per-run timeout override cannot mutate the deployed definition in memory.
            executionDef = MAPPER.convertValue(registeredDef, WorkflowDef.class);
        }
        if (request.getTimeoutSeconds() != null && request.getTimeoutSeconds() > 0) {
            executionDef.setTimeoutSeconds(request.getTimeoutSeconds());
        }

        log.debug(
                "Starting deployed agent: {} v{}",
                executionDef.getName(),
                executionDef.getVersion());
        return workflowExecutor.startAgentExecution(request, config, executionDef, storedAgentDef);
    }

    private AgentStartResponse startInline(AgentStartRequest request) {
        AgentConfig config = resolveConfig(request);

        // Apply per-call timeout override from AgentStartRequest
        if (request.getTimeoutSeconds() != null && request.getTimeoutSeconds() > 0) {
            config.setTimeoutSeconds(request.getTimeoutSeconds());
        }

        log.info("Starting agent: {}", config.getName());

        // 0. Pre-register child workflows for agent_tool types
        registerAgentToolWorkflows(config);

        // 1. Compile
        WorkflowDef def = agentCompiler.compile(config);

        // 1b. Stamp SDK metadata on the workflow definition
        String sdk = request.getFramework() != null ? request.getFramework() : "conductor";
        Map<String, Object> metadata =
                def.getMetadata() != null
                        ? new LinkedHashMap<>(def.getMetadata())
                        : new LinkedHashMap<>();
        metadata.put("agent_sdk", sdk);
        stampAgentDef(metadata, request, config);
        def.setMetadata(metadata);

        // 2. Register workflow definition (upsert)
        metadataDAO.updateWorkflowDef(def);

        // 3. Register task definitions for worker tools
        registerTaskDefinitions(config);

        return workflowExecutor.startAgentExecution(request, config, def, request.getRawConfig());
    }

    private WorkflowDef getRegisteredAgent(String name, Integer version) {
        WorkflowDef def =
                version != null
                        ? metadataDAO
                                .getWorkflowDef(name, version)
                                .orElseThrow(
                                        () ->
                                                new NotFoundException(
                                                        "Agent not found: "
                                                                + name
                                                                + " v"
                                                                + version))
                        : metadataDAO
                                .getLatestWorkflowDef(name)
                                .orElseThrow(
                                        () -> new NotFoundException("Agent not found: " + name));
        if (!WorkflowClassifiers.isAgent(def.getMetadata())) {
            throw new NotFoundException("Agent not found: " + name);
        }
        return def;
    }

    private AgentConfig resolveStoredConfig(WorkflowDef def) {
        Map<String, Object> agentDef = storedAgentDef(def);
        Object sdkValue = def.getMetadata().get("agent_sdk");
        String sdk = sdkValue instanceof String ? (String) sdkValue : "conductor";
        if (sdk.isBlank() || "conductor".equals(sdk)) {
            return MAPPER.convertValue(agentDef, AgentConfig.class);
        }
        return normalizerRegistry.normalize(sdk, agentDef);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> storedAgentDef(WorkflowDef def) {
        Map<String, Object> metadata = def.getMetadata();
        if (metadata != null && metadata.get("agentDef") instanceof Map<?, ?>) {
            return (Map<String, Object>) metadata.get("agentDef");
        }
        throw new IllegalStateException(
                "Registered agent definition is missing metadata.agentDef: "
                        + def.getName()
                        + " v"
                        + def.getVersion());
    }

    // ── Agent discovery ─────────────────────────────────────────────

    /** List all registered agents (workflow defs with agent_sdk metadata). */
    @SuppressWarnings("unchecked")
    public List<AgentSummary> listAgents() {
        // Use the portable getAllWorkflowDefs() (present across Conductor cores, incl. orkes'
        // vendored oss-core which lacks getAllWorkflowDefsLatestVersions()) and reduce to the
        // latest version per name ourselves.
        Map<String, WorkflowDef> latestByName = new HashMap<>();
        for (WorkflowDef d : metadataDAO.getAllWorkflowDefs()) {
            latestByName.merge(d.getName(), d, (a, b) -> a.getVersion() >= b.getVersion() ? a : b);
        }
        List<WorkflowDef> allDefs = new ArrayList<>(latestByName.values());
        List<AgentSummary> agents = new ArrayList<>();

        for (WorkflowDef def : allDefs) {
            Map<String, Object> metadata = def.getMetadata();
            // A def is an agent when its derived classifier resolves to "agent": either the
            // AgentSpan stamp (agent_sdk/agentDef) is present, or the def carries an explicit
            // metadata.classifier=agent tag. An explicit non-agent classifier excludes a def
            // even if it still carries a stamp.
            if (!WorkflowClassifiers.isAgent(metadata)) {
                continue;
            }

            String checksum;
            try {
                String json = MAPPER.writeValueAsString(def);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) {
                    hex.append(String.format("%02x", b));
                }
                checksum = hex.toString();
            } catch (Exception e) {
                log.warn("Failed to compute checksum for workflow {}", def.getName(), e);
                checksum = null;
            }

            List<String> tags = null;
            Object caps = metadata.get("agent_capabilities");
            if (caps instanceof List) {
                tags = (List<String>) caps;
            }

            agents.add(
                    AgentSummary.builder()
                            .name(def.getName())
                            .version(def.getVersion())
                            .type((String) metadata.get("agent_sdk"))
                            .tags(tags)
                            .createTime(def.getCreateTime())
                            .updateTime(def.getUpdateTime())
                            .description(def.getDescription())
                            .checksum(checksum)
                            .build());
        }

        return agents;
    }

    /** Search agent executions with optional filters. */
    public Map<String, Object> searchAgentExecutions(
            int start,
            int size,
            String sort,
            String freeText,
            String status,
            String agentName,
            String sessionId) {
        return searchAgentExecutions(
                start, size, sort, freeText, status, agentName, sessionId, null);
    }

    /**
     * Search agent executions with optional filters.
     *
     * <p>When {@code classifier} is provided (e.g. {@code agent}), the search filters on the
     * indexed execution classifier ({@code classifier IN (...)}) instead of enumerating every agent
     * workflow name into the query. This scales past name-list limits but requires a Conductor core
     * whose index backend supports the {@code classifier} search field; older cores silently drop
     * the condition, so classifier filtering stays opt-in.
     */
    public Map<String, Object> searchAgentExecutions(
            int start,
            int size,
            String sort,
            String freeText,
            String status,
            String agentName,
            String sessionId,
            String classifier) {
        String classifierList =
                classifier == null
                        ? ""
                        : Arrays.stream(classifier.split(","))
                                .map(String::trim)
                                .filter(c -> !c.isEmpty())
                                .collect(Collectors.joining(","));
        StringBuilder query;
        if (!classifierList.isEmpty()) {
            query = new StringBuilder("classifier IN (").append(classifierList).append(")");
            if (agentName != null && !agentName.isEmpty()) {
                query.append(" AND workflowType = '").append(agentName).append("'");
            }
        } else {
            // Legacy path: enumerate agent workflow names into the query.
            List<String> workflowNames;
            if (agentName != null && !agentName.isEmpty()) {
                workflowNames = List.of(agentName);
            } else {
                workflowNames =
                        listAgents().stream()
                                .map(AgentSummary::getName)
                                .collect(Collectors.toList());
            }

            if (workflowNames.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("totalHits", 0L);
                empty.put("results", List.of());
                return empty;
            }

            String nameList =
                    workflowNames.stream().map(n -> "'" + n + "'").collect(Collectors.joining(","));
            query = new StringBuilder("workflowType IN (").append(nameList).append(")");
        }
        if (status != null && !status.isEmpty()) {
            query.append(" AND status = '").append(status).append("'");
        }

        // Use sessionId as freeText search if provided
        String searchText = freeText != null ? freeText : "*";
        if (sessionId != null && !sessionId.isEmpty()) {
            searchText = sessionId;
        }

        SearchResult<WorkflowSummary> searchResult =
                workflowService.searchWorkflows(start, size, sort, searchText, query.toString());

        List<AgentExecutionSummary> results =
                searchResult.getResults().stream()
                        .map(
                                ws ->
                                        AgentExecutionSummary.builder()
                                                .executionId(ws.getWorkflowId())
                                                .agentName(ws.getWorkflowType())
                                                .version(ws.getVersion())
                                                .status(
                                                        ws.getStatus() != null
                                                                ? ws.getStatus().name()
                                                                : null)
                                                .startTime(ws.getStartTime())
                                                .endTime(ws.getEndTime())
                                                .updateTime(ws.getUpdateTime())
                                                .executionTime(ws.getExecutionTime())
                                                .input(ws.getInput())
                                                .output(ws.getOutput())
                                                .createdBy(ws.getCreatedBy())
                                                .build())
                        .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalHits", searchResult.getTotalHits());
        response.put("results", results);
        return response;
    }

    /** Get detailed execution status for a single agent execution. */
    public AgentExecutionDetail getExecutionDetail(String executionId) {
        Workflow workflow = workflowService.getExecutionStatus(executionId, true);

        // Find the last non-terminal task as the "current" task
        AgentExecutionDetail.CurrentTask currentTask = null;
        List<Task> tasks = workflow.getTasks();
        for (int i = tasks.size() - 1; i >= 0; i--) {
            Task task = tasks.get(i);
            if (!task.getStatus().isTerminal()) {
                currentTask =
                        AgentExecutionDetail.CurrentTask.builder()
                                .taskRefName(task.getReferenceTaskName())
                                .taskType(task.getTaskType())
                                .status(task.getStatus().name())
                                .inputData(task.getInputData())
                                .outputData(task.getOutputData())
                                .build();
                break;
            }
        }

        return AgentExecutionDetail.builder()
                .executionId(executionId)
                .agentName(workflow.getWorkflowName())
                .version(workflow.getWorkflowVersion())
                .status(workflow.getStatus().name())
                .input(workflow.getInput())
                .output(workflow.getOutput())
                .currentTask(currentTask)
                .build();
    }

    /** Pause a running agent execution. */
    public void pauseAgent(String executionId) {
        workflowService.pauseWorkflow(executionId);
    }

    /** Resume a paused agent execution. */
    public void resumeAgent(String executionId) {
        workflowService.resumeWorkflow(executionId);
    }

    /** Cancel a running agent execution. */
    public void cancelAgent(String executionId, String reason) {
        workflowService.terminateWorkflow(
                executionId, reason != null ? reason : "Cancelled by user");
    }

    /**
     * Permanently delete an execution record from the database.
     *
     * <p>Wraps Conductor's {@code WorkflowService.deleteWorkflow} to hard-delete completed
     * execution records. Running executions should be terminated first.
     *
     * @param executionId the execution to remove
     * @param archiveTasks if true, archive task records instead of deleting them
     */
    public void deleteExecutionRecord(String executionId, boolean archiveTasks) {
        workflowService.deleteWorkflow(executionId, archiveTasks);
    }

    /**
     * Bulk-delete completed execution records older than {@code olderThanDays} days.
     *
     * <p>Searches for COMPLETED, FAILED, TERMINATED, and TIMED_OUT executions whose end time is
     * before the cutoff, then removes them from the DB in batches.
     *
     * @param olderThanDays minimum age in days for executions to be pruned
     * @param archiveTasks if true, archive task records instead of deleting
     * @return number of executions deleted
     */
    public int pruneExecutions(int olderThanDays, boolean archiveTasks) {
        long cutoffEpochMs = Instant.now().minus(olderThanDays, ChronoUnit.DAYS).toEpochMilli();
        String[] terminalStatuses = {"COMPLETED", "FAILED", "TERMINATED", "TIMED_OUT"};

        List<String> workflowNames =
                listAgents().stream().map(AgentSummary::getName).collect(Collectors.toList());
        if (workflowNames.isEmpty()) {
            return 0;
        }

        String nameList =
                workflowNames.stream().map(n -> "'" + n + "'").collect(Collectors.joining(","));
        int deleted = 0;
        int batchSize = 100;

        for (String status : terminalStatuses) {
            String query =
                    "workflowType IN ("
                            + nameList
                            + ") AND status = '"
                            + status
                            + "' AND endTime < "
                            + cutoffEpochMs;
            int start = 0;
            while (true) {
                SearchResult<WorkflowSummary> page =
                        workflowService.searchWorkflows(
                                start, batchSize, "endTime:ASC", "*", query);
                List<WorkflowSummary> results = page.getResults();
                if (results == null || results.isEmpty()) {
                    break;
                }
                for (WorkflowSummary ws : results) {
                    try {
                        workflowService.deleteWorkflow(ws.getWorkflowId(), archiveTasks);
                        deleted++;
                    } catch (Exception e) {
                        log.warn(
                                "Could not delete execution {}: {}",
                                ws.getWorkflowId(),
                                e.getMessage());
                    }
                }
                if (results.size() < batchSize) {
                    break;
                }
                // After deletion, restart from 0 since the result set shifts
            }
        }
        return deleted;
    }

    /**
     * Gracefully stop an agent execution by setting the _stop_requested flag.
     *
     * <p>The loop exits after the current iteration completes and the workflow reaches COMPLETED
     * status with the last LLM output as the result. Also sends a WMQ message to unblock agents
     * waiting on PULL_WORKFLOW_MESSAGES.
     */
    public void stopAgent(String executionId) {
        // Set the stop flag — the DoWhile loop condition checks this variable.
        // Get the workflow model, update its variables map, and persist.
        WorkflowModel workflow = executionDAO.getWorkflow(executionId, false);
        workflow.getVariables().put("_stop_requested", true);
        executionDAO.updateWorkflow(workflow);
        // Note: the SDK also sends a WMQ unblock message via the Conductor client
        // to wake agents blocked on PULL_WORKFLOW_MESSAGES.
    }

    /**
     * Inject a persistent signal into a running agent's context.
     *
     * <p>Sets the {@code _signal_injection} workflow variable. The context injection script reads
     * this on each iteration and prepends it to the LLM's user message as {@code
     * [SIGNALS]...[/SIGNALS]}.
     */
    public void signalAgent(String executionId, String message) {
        WorkflowModel workflow = executionDAO.getWorkflow(executionId, false);
        workflow.getVariables().put("_signal_injection", message != null ? message : "");
        executionDAO.updateWorkflow(workflow);
    }

    /**
     * Get an agent execution with its full task list and token usage.
     *
     * <p>Exposed via {@code GET /api/agent/{id}}. Returns execution metadata, all tasks (for SDK
     * recursive token collection via {@code subWorkflowId}), and pre-computed token usage for LLM
     * tasks in this execution only.
     */
    public AgentRun getExecution(String executionId) {
        Workflow workflow = workflowService.getExecutionStatus(executionId, true);

        int promptTokens = 0, completionTokens = 0, totalTokens = 0;
        boolean hasTokens = false;

        List<AgentRun.TaskDetail> tasks = new ArrayList<>();
        for (Task task : workflow.getTasks()) {
            AgentRun.TaskDetail.TaskDetailBuilder tb =
                    AgentRun.TaskDetail.builder()
                            .taskType(task.getTaskType())
                            .referenceTaskName(task.getReferenceTaskName())
                            .status(task.getStatus().name())
                            .subWorkflowId(task.getSubWorkflowId())
                            .outputData(task.getOutputData());
            tasks.add(tb.build());

            if ("LLM_CHAT_COMPLETE".equalsIgnoreCase(task.getTaskType())) {
                Map<String, Object> out = task.getOutputData();
                if (out != null) {
                    promptTokens += toInt(out.get("promptTokens"));
                    completionTokens += toInt(out.get("completionTokens"));
                    totalTokens += toInt(out.get("tokenUsed"));
                    hasTokens = true;
                }
            }
        }

        AgentRun.TokenUsage tokenUsage =
                hasTokens
                        ? AgentRun.TokenUsage.builder()
                                .promptTokens(promptTokens)
                                .completionTokens(completionTokens)
                                .totalTokens(
                                        totalTokens == 0
                                                ? promptTokens + completionTokens
                                                : totalTokens)
                                .build()
                        : null;

        return AgentRun.builder()
                .executionId(executionId)
                .agentName(workflow.getWorkflowName())
                .version(workflow.getWorkflowVersion())
                .status(workflow.getStatus().name())
                .startTime(workflow.getStartTime())
                .endTime(workflow.getEndTime())
                .input(workflow.getInput())
                .output(workflow.getOutput())
                .tokenUsage(tokenUsage)
                .tasks(tasks)
                .build();
    }

    /**
     * Write the agent definition into workflow metadata so it can be inspected later without
     * re-running the agent. Stores the raw serialized config sent by the SDK — tools and guardrails
     * are already reduced to name references by the SDK serializer, so no function objects are
     * present.
     */
    private void stampAgentDef(
            Map<String, Object> metadata, AgentStartRequest request, AgentConfig normalizedConfig) {
        Map<String, Object> agentDef =
                request.getRawConfig() != null
                        ? request.getRawConfig()
                        : (request.getAgentConfig() != null
                                ? MAPPER.convertValue(request.getAgentConfig(), Map.class)
                                : null);
        if (agentDef != null) {
            if (request.getSkillRef() != null) {
                agentDef = new LinkedHashMap<>(agentDef);
                agentDef.put("skillRef", request.getSkillRef());
            }
            metadata.put("agentDef", agentDef);
        }
        if (normalizedConfig != null) {
            metadata.put("normalizedAgentDef", MAPPER.convertValue(normalizedConfig, Map.class));
        }
    }

    private static int toInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private void validateStartInput(AgentStartRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Start request is required");
        }
        if (StringUtils.isNotBlank(request.getPrompt())
                || hasMedia(request.getMedia())
                || isNotEmpty(request.getContext())) {
            return;
        }
        throw new IllegalArgumentException(
                "Agent execution requires a non-empty prompt, at least one media item, or non-empty context.");
    }

    private void validateStartSource(AgentStartRequest request) {
        boolean hasName = StringUtils.isNotEmpty(request.getName());
        boolean hasInlineConfig =
                request.getAgentConfig() != null
                        || StringUtils.isNotEmpty(request.getFramework())
                        || request.getRawConfig() != null
                        || request.getSkillRef() != null;

        if (request.getVersion() != null && !hasName) {
            throw new IllegalArgumentException("agentVersion requires agentName");
        }
        if (hasName && hasInlineConfig) {
            throw new IllegalArgumentException(
                    "Specify either agentName/agentVersion or inline agent construction details, not both");
        }
        if (!hasName && !hasInlineConfig) {
            throw new IllegalArgumentException(
                    "Agent start requires agentName or inline agent construction details");
        }
    }

    private boolean hasMedia(List<String> media) {
        if (media == null || media.isEmpty()) {
            return false;
        }
        return media.stream().anyMatch(item -> item != null && !item.isBlank());
    }

    private boolean isNotEmpty(Map<String, Object> map) {
        return map != null && !map.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getAgentDef(String name, Integer version) {
        WorkflowDef def;
        if (version != null) {
            def =
                    metadataDAO
                            .getWorkflowDef(name, version)
                            .orElseThrow(
                                    () ->
                                            new NotFoundException(
                                                    "Agent not found: " + name + " v" + version));
        } else {
            def =
                    metadataDAO
                            .getLatestWorkflowDef(name)
                            .orElseThrow(() -> new NotFoundException("Agent not found: " + name));
        }
        Map<String, Object> metadata = def.getMetadata();
        if (metadata != null && metadata.get("agentDef") instanceof Map) {
            return (Map<String, Object>) metadata.get("agentDef");
        }
        throw new NotFoundException("No agent definition found for: " + name);
    }

    public void deleteAgent(String name, Integer version) {
        if (version != null) {
            metadataDAO.removeWorkflowDef(name, version);
        } else {
            // Remove latest version
            WorkflowDef def =
                    metadataDAO
                            .getLatestWorkflowDef(name)
                            .orElseThrow(() -> new NotFoundException("Agent not found: " + name));
            metadataDAO.removeWorkflowDef(name, def.getVersion());
        }
    }

    /**
     * Search for an existing workflow with the given correlationId (idempotency key). Returns the
     * execution ID if a RUNNING or COMPLETED execution exists, null otherwise.
     */
    private String findExistingExecution(String workflowName, String idempotencyKey) {
        try {
            String query =
                    "workflowType = '" + workflowName + "' AND status IN ('RUNNING', 'COMPLETED')";
            SearchResult<WorkflowSummary> results =
                    workflowService.searchWorkflows(0, 1, "startTime:DESC", idempotencyKey, query);
            if (results.getTotalHits() > 0) {
                WorkflowSummary match = results.getResults().get(0);
                if (idempotencyKey.equals(match.getCorrelationId())) {
                    return match.getWorkflowId();
                }
            }
        } catch (Exception e) {
            log.debug("Idempotency check failed for key '{}': {}", idempotencyKey, e.getMessage());
        }
        return null;
    }

    /** Walk the agent tree and register task definitions for all worker tools. */
    private void registerTaskDefinitions(AgentConfig config) {
        Set<String> registered = new HashSet<>();
        collectAndRegisterTasks(config, registered);
    }

    /**
     * Dynamic worker-tool dispatch is emitted by a runtime fork and is therefore absent from {@link
     * WorkflowDef#collectSimpleTaskNames()}. Keep the compile/deploy contract truthful by reporting
     * those user-owned workers explicitly. Compiler-owned SWARM transfer controls are deliberately
     * excluded; only declared worker tools and declared condition workers need a poller.
     */
    private static void collectDeclaredWorkerNames(AgentConfig config, Set<String> names) {
        if (config.getTools() != null) {
            for (ToolConfig tool : config.getTools()) {
                if ("worker".equals(tool.getToolType())
                        && tool.getName() != null
                        && !tool.getName().isBlank()) {
                    names.add(tool.getName());
                }
            }
        }
        if (config.getStrategy() == AgentConfig.Strategy.SWARM && config.getHandoffs() != null) {
            for (HandoffConfig handoff : config.getHandoffs()) {
                if ("on_condition".equals(handoff.getType())
                        && handoff.getTaskName() != null
                        && !handoff.getTaskName().isBlank()) {
                    names.add(handoff.getTaskName());
                }
            }
        }
        if (config.getCallbacks() != null) {
            for (CallbackConfig callback : config.getCallbacks()) {
                if (callback.getTaskName() != null && !callback.getTaskName().isBlank()) {
                    names.add(callback.getTaskName());
                }
            }
        }
        if (config.getAgents() != null) {
            for (AgentConfig agent : config.getAgents()) {
                collectDeclaredWorkerNames(agent, names);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectAndRegisterTasks(AgentConfig config, Set<String> registered) {
        // Credential names declared on each SIMPLE task's TaskDef.runtimeMetadata (embedded only,
        // gated
        // inside registerTaskDef). Worker tools use their per-tool creds (with agent-level
        // fallback);
        // the other user-code task kinds
        // (guardrail/callback/stop_when/gate/instructions/router/graph)
        // have no per-item credential list, so they use the agent-level names. Hoisted once per
        // config.
        Map<String, List<String>> toolCreds = AgentCompiler.collectToolCredentials(config);
        List<String> agentCreds = AgentCompiler.collectAgentCredentials(config);

        // Register dispatch task for this agent's tools
        if (config.getTools() != null) {
            for (ToolConfig tool : config.getTools()) {
                String tt = tool.getToolType();
                if ("worker".equals(tt) && !registered.contains(tool.getName())) {
                    registerTaskDef(tool.getName(), toolCreds.get(tool.getName()));
                    registered.add(tool.getName());
                }
            }
        }

        // Register stop_when worker (user-authored predicate → agent-level creds)
        if (config.getStopWhen() != null && config.getStopWhen().getTaskName() != null) {
            String taskName = config.getStopWhen().getTaskName();
            if (!registered.contains(taskName)) {
                registerTaskDef(taskName, agentCreds);
                registered.add(taskName);
            }
        }

        // Register termination worker (compiled as SIMPLE task by TerminationCompiler)
        if (config.getTermination() != null) {
            String taskName = config.getName() + "_termination";
            if (!registered.contains(taskName)) {
                registerTaskDef(taskName);
                registered.add(taskName);
            }
        }

        // Register custom guardrail workers
        if (config.getGuardrails() != null) {
            for (GuardrailConfig g : config.getGuardrails()) {
                if ("custom".equals(g.getGuardrailType()) && g.getTaskName() != null) {
                    if (!registered.contains(g.getTaskName())) {
                        registerTaskDef(g.getTaskName(), agentCreds);
                        registered.add(g.getTaskName());
                    }
                }
            }
        }

        // Register callback workers
        if (config.getCallbacks() != null) {
            for (CallbackConfig cb : config.getCallbacks()) {
                if (cb.getTaskName() != null && !registered.contains(cb.getTaskName())) {
                    registerTaskDef(cb.getTaskName(), agentCreds);
                    registered.add(cb.getTaskName());
                }
            }
        }

        // Register callable gate workers (text_contains gates are INLINE, no registration needed)
        if (config.getGate() != null
                && config.getGate().get("taskName") instanceof String gateTaskName) {
            if (!registered.contains(gateTaskName)) {
                registerTaskDef(gateTaskName, agentCreds);
                registered.add(gateTaskName);
            }
        }

        // Register callable instructions worker (_worker_ref in instructions map)
        if (config.getInstructions() instanceof Map<?, ?> instrMap
                && instrMap.get("_worker_ref") instanceof String instrTaskName
                && !instrTaskName.isBlank()) {
            if (!registered.contains(instrTaskName)) {
                registerTaskDef(instrTaskName, agentCreds);
                registered.add(instrTaskName);
            }
        }

        // Register worker-based router (WorkerRef with taskName)
        if (config.getRouter() instanceof Map<?, ?> routerMap
                && routerMap.get("taskName") instanceof String routerTaskName) {
            if (!registered.contains(routerTaskName)) {
                registerTaskDef(routerTaskName, agentCreds);
                registered.add(routerTaskName);
            }
        } else if (config.getRouter() instanceof WorkerRef workerRef
                && workerRef.getTaskName() != null) {
            if (!registered.contains(workerRef.getTaskName())) {
                registerTaskDef(workerRef.getTaskName(), agentCreds);
                registered.add(workerRef.getTaskName());
            }
        }

        // Declarative SWARM conditions are the only handoff workers. Generated transfer and
        // handoff-check names are compiler-owned INLINE logic and must never be registered.
        if (config.getStrategy() == AgentConfig.Strategy.SWARM && config.getHandoffs() != null) {
            for (HandoffConfig handoff : config.getHandoffs()) {
                if ("on_condition".equals(handoff.getType())
                        && handoff.getTaskName() != null
                        && !handoff.getTaskName().isBlank()
                        && !registered.contains(handoff.getTaskName())) {
                    registerTaskDef(handoff.getTaskName(), agentCreds);
                    registered.add(handoff.getTaskName());
                }
            }
        }

        // Register process_selection worker for manual
        if (config.getStrategy() == AgentConfig.Strategy.MANUAL) {
            String taskName = config.getName() + "_process_selection";
            if (!registered.contains(taskName)) {
                registerTaskDef(taskName);
                registered.add(taskName);
            }
        }

        // Register graph-structure node workers and router workers
        if (config.getMetadata() != null
                && config.getMetadata().get("_graph_structure") instanceof Map<?, ?> graph) {
            // Node workers
            if (graph.get("nodes") instanceof List<?> nodes) {
                for (Object nodeObj : nodes) {
                    if (nodeObj instanceof Map<?, ?> node
                            && node.get("_worker_ref") instanceof String workerRef) {
                        if (!registered.contains(workerRef)) {
                            registerTaskDef(workerRef, agentCreds);
                            registered.add(workerRef);
                        }
                    }
                }
            }
            // Conditional edge router workers
            if (graph.get("conditional_edges") instanceof List<?> condEdges) {
                for (Object ceObj : condEdges) {
                    if (ceObj instanceof Map<?, ?> ce
                            && ce.get("_router_ref") instanceof String routerRef) {
                        if (!registered.contains(routerRef)) {
                            registerTaskDef(routerRef, agentCreds);
                            registered.add(routerRef);
                        }
                    }
                }
            }
        }

        // Recurse into sub-agents
        if (config.getAgents() != null) {
            for (AgentConfig sub : config.getAgents()) {
                if (!sub.isExternal()) {
                    collectAndRegisterTasks(sub, registered);
                }
            }
        }
    }

    // ── Agent-as-tool workflow registration ──────────────────────

    /**
     * Pre-register child agent workflows for any agent_tool type tools. Called before compilation
     * so the enrichment script can reference the child workflow by name.
     */
    @SuppressWarnings("unchecked")
    private void registerAgentToolWorkflows(AgentConfig config) {
        if (config.getTools() != null) {
            for (ToolConfig tool : config.getTools()) {
                if (!"agent_tool".equals(tool.getToolType()) || tool.getConfig() == null) {
                    continue;
                }

                Object agentConfigObj = tool.getConfig().get("agentConfig");
                if (agentConfigObj == null) continue;

                // Convert the AgentConfig (or LinkedHashMap from Jackson) to AgentConfig
                AgentConfig childConfig;
                if (agentConfigObj instanceof AgentConfig) {
                    childConfig = (AgentConfig) agentConfigObj;
                } else if (agentConfigObj instanceof Map) {
                    Map<String, Object> childMap = (Map<String, Object>) agentConfigObj;
                    Object framework = childMap.get("_framework");
                    if (!(framework instanceof String) || ((String) framework).isEmpty()) {
                        framework = childMap.get("framework");
                    }
                    if (framework instanceof String frameworkId && !frameworkId.isEmpty()) {
                        childConfig = normalizerRegistry.normalize(frameworkId, childMap);
                    } else {
                        childConfig = MAPPER.convertValue(childMap, AgentConfig.class);
                    }
                } else {
                    log.warn(
                            "Unexpected agentConfig type for tool '{}': {}",
                            tool.getName(),
                            agentConfigObj.getClass());
                    continue;
                }

                // Recursively register any nested agent_tool workflows
                registerAgentToolWorkflows(childConfig);

                // Compile and register the child agent workflow
                WorkflowDef childDef = agentCompiler.compile(childConfig);
                metadataDAO.updateWorkflowDef(childDef);
                log.info(
                        "Registered agent_tool child workflow: {} for tool '{}'",
                        childDef.getName(),
                        tool.getName());

                // Register task definitions for the child's worker tools
                registerTaskDefinitions(childConfig);

                // Store the workflow name back so the enrichment script can reference it
                tool.getConfig().put("workflowName", childDef.getName());
            }
        }

        // Also recurse into sub-agents (they might have agent_tool tools too)
        if (config.getAgents() != null) {
            for (AgentConfig sub : config.getAgents()) {
                if (!sub.isExternal()) {
                    registerAgentToolWorkflows(sub);
                }
            }
        }
    }

    // ── Config resolution ─────────────────────────────────────────

    /**
     * Resolve the AgentConfig from a AgentStartRequest. If {@code framework} is set, normalize the
     * raw config via the appropriate normalizer. Otherwise, use the native {@code agentConfig}
     * field directly.
     */
    private AgentConfig resolveConfig(AgentStartRequest request) {
        if (request.getFramework() != null && !request.getFramework().isEmpty()) {
            log.debug("Normalizing framework '{}' agent config", request.getFramework());
            if ("skill".equals(request.getFramework())
                    && request.getRawConfig() == null
                    && request.getSkillRef() != null) {
                if (skillRegistryService == null) {
                    throw new IllegalStateException("Skill registry is not available");
                }
                request.setRawConfig(skillRegistryService.resolveRawConfig(request.getSkillRef()));
            }
            return normalizerRegistry.normalize(request.getFramework(), request.getRawConfig());
        }
        return request.getAgentConfig();
    }

    // ── SSE Streaming ──────────────────────────────────────────────

    /** Open an SSE stream for an agent execution. Replays missed events on reconnect. */
    public SseEmitter openStream(String executionId, Long lastEventId) {
        log.info("Opening SSE stream for execution {} (lastEventId={})", executionId, lastEventId);
        return streamRegistry.register(executionId, lastEventId);
    }

    /** Respond to a pending HITL task in an agent execution. */
    public void respond(String executionId, Map<String, Object> output) {
        log.info("Responding to execution {}: {}", executionId, output);

        // Find the pending task (HUMAN type, IN_PROGRESS status)
        Workflow workflow = workflowService.getExecutionStatus(executionId, true);
        Task pendingTask = null;
        for (Task task : workflow.getTasks()) {
            if ("HUMAN".equals(task.getTaskType()) && task.getStatus() == Task.Status.IN_PROGRESS) {
                pendingTask = task;
                break;
            }
        }

        if (pendingTask == null) {
            throw new IllegalStateException(
                    "No pending HUMAN task found in execution " + executionId);
        }

        // Update the task with the human's response
        TaskResult taskResult = new TaskResult();
        taskResult.setTaskId(pendingTask.getTaskId());
        taskResult.setWorkflowInstanceId(executionId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        Map<String, Object> outputData =
                new LinkedHashMap<>(
                        pendingTask.getOutputData() != null
                                ? pendingTask.getOutputData()
                                : Map.of());
        outputData.putAll(output);
        taskResult.setOutputData(outputData);
        taskService.updateTask(taskResult);
        log.info(
                "Completed HUMAN task {} in execution {}",
                pendingTask.getReferenceTaskName(),
                executionId);
    }

    /** Get the current status of an agent execution. */
    public AgentStatusResponse getStatus(String executionId) {
        Workflow workflow = workflowService.getExecutionStatus(executionId, true);
        boolean isComplete = workflow.getStatus().isTerminal();
        Map<String, Object> pendingTool = null;
        boolean waiting = false;

        // Find pending HUMAN or PULL_WORKFLOW_MESSAGES task
        for (Task task : workflow.getTasks()) {
            if (("HUMAN".equals(task.getTaskType())
                            || "PULL_WORKFLOW_MESSAGES".equals(task.getTaskType()))
                    && task.getStatus() == Task.Status.IN_PROGRESS) {
                pendingTool = new LinkedHashMap<>();
                pendingTool.put("taskRefName", task.getReferenceTaskName());
                if (task.getInputData() != null) {
                    pendingTool.put("tool_name", task.getInputData().get("tool_name"));
                    pendingTool.put("parameters", task.getInputData().get("parameters"));
                    List<Map<String, Object>> toolCalls =
                            AgentHumanTask.extractToolCalls(task.getInputData().get("tool_calls"));
                    if (toolCalls != null) {
                        pendingTool.put("toolCalls", toolCalls);
                    }
                    if (task.getInputData().get("response_schema") != null) {
                        pendingTool.put(
                                "response_schema", task.getInputData().get("response_schema"));
                    }
                    if (task.getInputData().get("response_ui_schema") != null) {
                        pendingTool.put(
                                "response_ui_schema",
                                task.getInputData().get("response_ui_schema"));
                    }
                }
                waiting = true;
                break;
            }
        }

        return AgentStatusResponse.builder()
                .executionId(executionId)
                .status(workflow.getStatus().name())
                .complete(isComplete)
                .running(workflow.getStatus() == Workflow.WorkflowStatus.RUNNING)
                .waiting(waiting)
                .output(isComplete ? workflow.getOutput() : null)
                .reasonForIncompletion(workflow.getReasonForIncompletion())
                .pendingTool(pendingTool)
                .startTime(workflow.getStartTime())
                .endTime(workflow.getEndTime() > 0 ? workflow.getEndTime() : null)
                .build();
    }

    // ── Framework event push ─────────────────────────────────────────

    /**
     * Translate a framework event map (from Python worker HTTP push) to an AgentSSEEvent and fan it
     * out to all registered SSE emitters. Silently ignored if no clients are connected.
     */
    public void pushFrameworkEvent(String executionId, Map<String, Object> event) {
        String type = event.getOrDefault("type", "").toString();
        AgentSSEEvent sseEvent =
                switch (type) {
                    case "thinking" ->
                            AgentSSEEvent.thinking(
                                    executionId, event.getOrDefault("content", "").toString());
                    case "tool_call" ->
                            AgentSSEEvent.toolCall(
                                    executionId,
                                    event.getOrDefault("toolName", "").toString(),
                                    event.get("args"));
                    case "tool_result" ->
                            AgentSSEEvent.toolResult(
                                    executionId,
                                    event.getOrDefault("toolName", "").toString(),
                                    event.getOrDefault("result", ""));
                    case "context_condensed" ->
                            AgentSSEEvent.contextCondensed(
                                    executionId,
                                    event.getOrDefault("trigger", "").toString(),
                                    event.get("messagesBefore") instanceof Number n
                                            ? n.intValue()
                                            : 0,
                                    event.get("messagesAfter") instanceof Number n
                                            ? n.intValue()
                                            : 0,
                                    event.get("exchangesCondensed") instanceof Number n
                                            ? n.intValue()
                                            : 0);
                    case "subagent_start" ->
                            AgentSSEEvent.subagentStart(
                                    executionId,
                                    extractSubagentIdentifier(event),
                                    event.getOrDefault("prompt", "").toString());
                    case "subagent_stop" ->
                            AgentSSEEvent.subagentStop(
                                    executionId,
                                    extractSubagentIdentifier(event),
                                    event.getOrDefault("result", "").toString());
                    default -> {
                        log.debug(
                                "Unknown framework event type '{}' for execution {}",
                                type,
                                executionId);
                        yield null;
                    }
                };
        if (sseEvent != null) {
            streamRegistry.send(executionId, sseEvent);
        }
    }

    private String extractSubagentIdentifier(Map<String, Object> event) {
        // Tier 2/3: subWorkflowId is set; Tier 1 native subagents: agentId is set
        Object subWorkflowId = event.get("subWorkflowId");
        if (subWorkflowId != null && !subWorkflowId.toString().isBlank()) {
            return subWorkflowId.toString();
        }
        Object agentId = event.get("agentId");
        return agentId != null ? agentId.toString() : "unknown";
    }

    // ── Task registration ────────────────────────────────────────────

    private void registerTaskDef(String taskName) {
        registerTaskDef(taskName, null);
    }

    /**
     * Register a worker TaskDef. {@code runtimeMetadata} declares the secret names the conductor
     * core must resolve at the SIMPLE task's poll and inject onto the wire-only {@code
     * Task.runtimeMetadata} (conductor-oss PR #1255). This is the only credential-delivery path for
     * workers, in BOTH modes: embedded, the host's SecretsDAO resolves the names; standalone, the
     * built-in conductor core resolves them via AgentspanSecretsDAO (the encrypted credential
     * store).
     */
    private void registerTaskDef(String taskName, List<String> runtimeMetadata) {
        TaskDef taskDef = new TaskDef();
        taskDef.setName(taskName);
        taskDef.setRetryCount(2);
        taskDef.setRetryDelaySeconds(2);
        taskDef.setRetryLogic(TaskDef.RetryLogic.LINEAR_BACKOFF);
        taskDef.setTimeoutSeconds(0);
        taskDef.setResponseTimeoutSeconds(3600);
        taskDef.setTimeoutPolicy(TaskDef.TimeoutPolicy.RETRY);
        if (runtimeMetadata != null && !runtimeMetadata.isEmpty()) {
            taskDef.setRuntimeMetadata(new ArrayList<>(runtimeMetadata));
        }

        try {
            TaskDef existing = metadataDAO.getTaskDef(taskName);
            if (existing != null) {
                if (metadataService != null) {
                    metadataService.updateTaskDef(taskDef);
                } else {
                    metadataDAO.updateTaskDef(taskDef);
                }
                log.debug("Updated task definition: {}", taskName);
                return;
            }
        } catch (Exception e) {
            // Task doesn't exist, create it
        }

        // Prefer the stable MetadataService (registerTaskDef upserts, returns void in all cores);
        // fall back to the DAO only outside Spring (tests).
        if (metadataService != null) {
            metadataService.registerTaskDef(List.of(taskDef));
        } else {
            metadataDAO.createTaskDef(taskDef);
        }
        log.info("Registered task definition: {}", taskName);
    }

    // ── Execution lifecycle (UI delegation) ─────────────────────────

    public Workflow getFullExecution(String executionId) {
        return workflowService.getExecutionStatus(executionId, true);
    }

    public void restartExecution(String executionId, boolean useLatestDefinitions) {
        workflowService.restartWorkflow(executionId, useLatestDefinitions);
    }

    public void retryExecution(String executionId, boolean resumeSubworkflowTasks) {
        workflowService.retryWorkflow(executionId, resumeSubworkflowTasks);
    }

    public String rerunExecution(String executionId, RerunWorkflowRequest request) {
        return workflowService.rerunWorkflow(executionId, request);
    }

    public TaskListResponse getExecutionTasks(
            String executionId, String status, int count, int start) {
        Workflow wf = workflowService.getExecutionStatus(executionId, true);
        List<Task> allTasks = wf.getTasks();

        // Build per-status summary from all tasks (before filtering)
        Map<String, Long> summary =
                allTasks.stream()
                        .collect(
                                Collectors.groupingBy(
                                        t -> t.getStatus().name(), Collectors.counting()));

        // Apply status filter
        List<Task> filtered = allTasks;
        if (status != null && !status.isEmpty()) {
            filtered =
                    allTasks.stream()
                            .filter(t -> status.equals(t.getStatus().name()))
                            .collect(Collectors.toList());
        }

        int totalHits = filtered.size();
        int end = Math.min(start + count, filtered.size());
        List<Task> page = start >= filtered.size() ? List.of() : filtered.subList(start, end);

        return new TaskListResponse(page, totalHits, summary);
    }

    public SearchResult<WorkflowSummary> searchExecutionsRaw(
            int start, int size, String sort, String freeText, String query) {
        return searchExecutionsRaw(start, size, sort, freeText, query, null);
    }

    public SearchResult<WorkflowSummary> searchExecutionsRaw(
            int start, int size, String sort, String freeText, String query, String classifier) {
        return workflowService.searchWorkflows(
                start, size, sort, freeText, withClassifierFilter(query, classifier));
    }

    /**
     * Folds an optional classifier filter (comma-separated values, e.g. {@code agent} or {@code
     * agent,workflow}) into the structured search query as a {@code classifier IN (...)} clause,
     * mirroring Conductor's own search endpoints.
     */
    static String withClassifierFilter(String query, String classifier) {
        if (classifier == null || classifier.isBlank()) {
            return query;
        }
        String values =
                Arrays.stream(classifier.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.joining(","));
        if (values.isEmpty()) {
            return query;
        }
        String clause = "classifier IN (" + values + ")";
        return (query == null || query.isBlank()) ? clause : query + " AND " + clause;
    }

    public WorkflowDef getAgentDefinition(String name, Integer version) {
        if (version != null) {
            return metadataDAO
                    .getWorkflowDef(name, version)
                    .orElseThrow(() -> new NotFoundException("Definition not found: " + name));
        }
        return metadataDAO
                .getLatestWorkflowDef(name)
                .orElseThrow(() -> new NotFoundException("Definition not found: " + name));
    }

    public void updateTaskResult(TaskResult taskResult) {
        taskService.updateTask(taskResult);
    }

    public List<TaskExecLog> getTaskLogs(String taskId) {
        return taskService.getTaskLogs(taskId);
    }
}
