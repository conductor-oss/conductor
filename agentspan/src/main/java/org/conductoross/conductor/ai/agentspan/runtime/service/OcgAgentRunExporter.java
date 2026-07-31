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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.conductoross.conductor.common.metadata.agent.LongTermMemoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Best-effort terminal-workflow exporter for OCG agent-run capture.
 *
 * <p>The exporter sends raw durable run data and never summarizes it. OCG owns folding,
 * summarization, fallback behavior, versioning, ranking, feedback, and retention.
 */
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class OcgAgentRunExporter implements WorkflowStatusListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(OcgAgentRunExporter.class);
    private static final Set<String> TOOL_TYPES = Set.of("SIMPLE", "HTTP", "CALL_MCP_TOOL");
    private static final Set<String> INTERNAL_TYPES =
            Set.of(
                    "LLM_CHAT_COMPLETE",
                    "LIST_MCP_TOOLS",
                    "LIST_API_TOOLS",
                    "SWITCH",
                    "DO_WHILE",
                    "INLINE",
                    "SET_VARIABLE",
                    "FORK_JOIN_DYNAMIC",
                    "JOIN",
                    "HUMAN",
                    "TERMINATE");

    private final ObjectMapper mapper;
    private final OcgClient ocgClient;

    public OcgAgentRunExporter(ObjectMapper mapper, OcgClient ocgClient) {
        this.mapper = mapper;
        this.ocgClient = ocgClient;
    }

    @Override
    public void onWorkflowCompleted(WorkflowModel workflow) {
        export(workflow);
    }

    @Override
    public void onWorkflowTerminated(WorkflowModel workflow) {
        export(workflow);
    }

    /** Starts capture without waiting for OCG; all failures are contained in the returned stage. */
    CompletionStage<Void> export(WorkflowModel workflow) {
        if (workflow == null || workflow.hasParent())
            return CompletableFuture.completedFuture(null);

        LongTermMemoryConfig config = memoryConfig(workflow);
        if (config == null || isBlank(config.getOcgUrl()) || isBlank(config.getCredential())) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            return ocgClient.exportAgentRun(config, buildPayload(workflow, config));
        } catch (Exception e) {
            LOGGER.warn(
                    "Unable to prepare OCG run capture for workflow {}: {}",
                    workflow.getWorkflowId(),
                    e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @SuppressWarnings("unchecked")
    private LongTermMemoryConfig memoryConfig(WorkflowModel workflow) {
        WorkflowDef definition = workflow.getWorkflowDefinition();
        if (definition == null || definition.getMetadata() == null) return null;
        Object agentDef = definition.getMetadata().get("agentDef");
        if (!(agentDef instanceof Map<?, ?> map)) return null;
        Object memory = map.get("longTermMemory");
        if (!(memory instanceof Map<?, ?>)) return null;
        return mapper.convertValue(memory, LongTermMemoryConfig.class);
    }

    Map<String, Object> buildPayload(WorkflowModel workflow, LongTermMemoryConfig config) {
        Map<String, Object> input = workflow.getInput() == null ? Map.of() : workflow.getInput();
        Map<String, Object> output = workflow.getOutput() == null ? Map.of() : workflow.getOutput();
        Set<String> maskedFields = maskedFields(workflow);
        Map<String, Object> safeInput = redactMap(input, maskedFields);
        Map<String, Object> safeOutput = redactMap(output, maskedFields);
        OcgExecutionIdentity identity = OcgExecutionIdentity.from(workflow, config);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent", identity.agent());
        if (!isBlank(identity.user())) payload.put("user", identity.user());
        payload.put("session_id", identity.sessionId());
        // Agent-run ingestion retains its existing turn_id field; it maps to the root execution.
        payload.put("turn_id", identity.executionId());
        copyString(safeInput, payload, "repo");
        copyString(safeInput, payload, "branch");
        copyString(safeInput, payload, "cwd");
        payload.put("input", stringValue(safeInput.get("prompt"), ""));
        payload.put("events", events(workflow, maskedFields));
        payload.put("result", jsonString(safeOutput.get("result")));
        payload.put("outcome", outcome(workflow));
        long startedAt = startTime(workflow);
        long endedAt = workflow.getEndTime() > 0 ? workflow.getEndTime() : startedAt;
        payload.put("started_at", Instant.ofEpochMilli(startedAt).toString());
        payload.put("ended_at", Instant.ofEpochMilli(endedAt).toString());
        return payload;
    }

    private List<Map<String, Object>> events(WorkflowModel workflow, Set<String> maskedFields) {
        if (workflow.getTasks() == null) return List.of();
        List<TaskModel> tasks = new ArrayList<>(workflow.getTasks());
        tasks.sort(Comparator.comparingInt(TaskModel::getSeq));
        List<Map<String, Object>> events = new ArrayList<>();
        for (TaskModel task : tasks) {
            String taskType = task.getTaskType();
            boolean subagent = "SUB_WORKFLOW".equals(taskType);
            if (!subagent && !isToolTask(task)) continue;

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", subagent ? "subagent" : "tool_call");
            event.put("name", eventName(task, subagent));
            event.put("detail", jsonString(redact(task.getInputData(), maskedFields, "")));
            boolean error = task.getStatus() != null && !task.getStatus().isSuccessful();
            Object eventOutput = task.getOutputData();
            if ((eventOutput == null || (eventOutput instanceof Map<?, ?> map && map.isEmpty()))
                    && error) {
                eventOutput = task.getReasonForIncompletion();
            }
            event.put("output", jsonString(redact(eventOutput, maskedFields, "")));
            event.put("is_error", error);
            events.add(event);
        }
        return events;
    }

    private boolean isToolTask(TaskModel task) {
        String type = task.getTaskType();
        if (type == null || INTERNAL_TYPES.contains(type)) return false;
        String ref = task.getReferenceTaskName();
        if (ref != null && ref.startsWith("_fw_")) return false;
        return TOOL_TYPES.contains(type) || task.getTaskDefinition().isPresent();
    }

    private String eventName(TaskModel task, boolean subagent) {
        if (subagent) {
            Object workflowName = task.getInputData().get("subWorkflowName");
            return stringValue(workflowName, task.getReferenceTaskName());
        }
        Object toolName = task.getInputData().get("toolName");
        if (toolName == null) toolName = task.getInputData().get("method");
        return stringValue(
                toolName, stringValue(task.getTaskDefName(), task.getReferenceTaskName()));
    }

    private Object redact(Object value, Set<String> maskedFields, String parentPath) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> clean = new LinkedHashMap<>();
            map.forEach(
                    (key, item) -> {
                        String name = String.valueOf(key);
                        String path = parentPath.isEmpty() ? name : parentPath + "." + name;
                        String lower = name.toLowerCase(Locale.ROOT);
                        if (maskedFields.contains(name)
                                || maskedFields.contains(path)
                                || lower.contains("secret")
                                || lower.contains("password")
                                || lower.contains("token")
                                || lower.contains("credential")
                                || lower.equals("authorization")
                                || lower.equals("x-api-key")
                                || lower.equals("apikey")
                                || lower.equals("api_key")) {
                            clean.put(name, "[REDACTED]");
                        } else {
                            clean.put(name, redact(item, maskedFields, path));
                        }
                    });
            return clean;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> redact(item, maskedFields, parentPath)).toList();
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> redactMap(Map<String, Object> value, Set<String> maskedFields) {
        return (Map<String, Object>) redact(value, maskedFields, "");
    }

    private static Set<String> maskedFields(WorkflowModel workflow) {
        WorkflowDef definition = workflow.getWorkflowDefinition();
        if (definition == null
                || definition.getMaskedFields() == null
                || definition.getMaskedFields().isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(definition.getMaskedFields());
    }

    private String jsonString(Object value) {
        if (value == null) return "";
        if (value instanceof String string) return string;
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static String outcome(WorkflowModel workflow) {
        if (workflow.getStatus() == WorkflowModel.Status.COMPLETED) return "success";
        if (workflow.getStatus() == WorkflowModel.Status.TERMINATED) return "interrupted";
        return "error";
    }

    private static long startTime(WorkflowModel workflow) {
        if (workflow.getCreateTime() != null && workflow.getCreateTime() > 0) {
            return workflow.getCreateTime();
        }
        return workflow.getTasks() == null
                ? 0
                : workflow.getTasks().stream()
                        .mapToLong(
                                task ->
                                        task.getStartTime() > 0
                                                ? task.getStartTime()
                                                : task.getScheduledTime())
                        .filter(value -> value > 0)
                        .min()
                        .orElse(0);
    }

    private static void copyString(
            Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null && !String.valueOf(value).isBlank())
            target.put(key, String.valueOf(value));
    }

    private static String stringValue(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
