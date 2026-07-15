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
package org.conductoross.conductor.ai.agentspan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agent.ConductorAgentExecution;
import org.conductoross.conductor.ai.agent.ConductorAgentRuntime;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentSummary;
import org.conductoross.conductor.ai.agent.NonRetryableAgentException;
import org.conductoross.conductor.ai.agentspan.runtime.model.AgentConfig;
import org.conductoross.conductor.ai.agentspan.runtime.model.AgentSummary;
import org.conductoross.conductor.ai.agentspan.runtime.model.StartRequest;
import org.conductoross.conductor.ai.agentspan.runtime.model.StartResponse;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Bridges the {@code ai}-module {@link ConductorAgentRuntime} interface to AgentSpan's embedded
 * {@link AgentService}.
 *
 * <p>The {@code ai} module defines {@link ConductorAgentRuntime} as an interface-first abstraction
 * with no compile dependency on any runtime implementation. This adapter is the AgentSpan
 * implementation: it translates between the coarse {@code ai}-module types ({@link
 * ConductorAgentStartRequest}, {@link ConductorAgentExecution}, {@link ConductorAgentState}, {@link
 * ConductorAgentSummary}) and the richer AgentSpan runtime types ({@link StartRequest}, {@link
 * StartResponse}, {@link AgentSummary}), delegating the actual orchestration to {@link
 * AgentService}.
 *
 * <p>Registered as a Spring bean only when the embedded runtime is enabled ({@code
 * agentspan.embedded=true}); otherwise the {@code AGENT} (conductor) task receives an empty {@code
 * Optional<ConductorAgentRuntime>} and fails terminally.
 *
 * <p>Per the {@link ConductorAgentRuntime} retryability contract, this adapter translates {@code
 * AgentService}'s permanent-failure signals — {@link IllegalArgumentException} (e.g. unknown agent
 * or execution) and {@link IllegalStateException} (e.g. nothing pending to resume) — into {@link
 * NonRetryableAgentException} so the {@code AGENT} task fails terminally instead of retrying a
 * misconfiguration forever. Other exceptions propagate as-is and are treated as transient.
 */
public class ConductorAgentRuntimeAdapter implements ConductorAgentRuntime {

    private final AgentService agentService;
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    public ConductorAgentRuntimeAdapter(AgentService agentService) {
        this.agentService = agentService;
    }

    @Override
    public ConductorAgentExecution start(ConductorAgentStartRequest request) {
        try {
            // Resolve the registered agent definition (the AgentSpan config stamped into the
            // WorkflowDef metadata) and rehydrate it into an AgentConfig the runtime can recompile.
            Map<String, Object> agentDef =
                    agentService.getAgentDef(request.getAgentName(), request.getAgentVersion());
            AgentConfig agentConfig = objectMapper.convertValue(agentDef, AgentConfig.class);

            StartRequest startRequest =
                    StartRequest.builder()
                            .agentConfig(agentConfig)
                            .prompt(request.getPrompt())
                            .sessionId(request.getSessionId())
                            .context(request.getContext())
                            .runId(request.getRunId())
                            .idempotencyKey(request.getIdempotencyKey())
                            .build();

            StartResponse response = agentService.start(startRequest);

            return ConductorAgentExecution.builder()
                    .executionId(response.getExecutionId())
                    .agentName(response.getAgentName())
                    .sessionId(request.getSessionId())
                    .state(ConductorAgentState.RUNNING)
                    .build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw nonRetryable(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ConductorAgentExecution getStatus(String executionId) {
        Map<String, Object> status;
        try {
            status = agentService.getStatus(executionId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw nonRetryable(e);
        }

        String statusName = asString(status.get("status"));
        boolean isComplete = asBoolean(status.get("isComplete"));
        boolean isWaiting = asBoolean(status.get("isWaiting"));

        ConductorAgentState state = deriveState(statusName, isComplete, isWaiting);

        Object output = status.get("output");
        Object pendingTool = status.get("pendingTool");

        Map<String, Object> outputMap = output instanceof Map ? (Map<String, Object>) output : null;

        return ConductorAgentExecution.builder()
                .executionId(asString(status.get("executionId")))
                .agentName(asString(status.get("agentName")))
                .sessionId(asString(status.get("sessionId")))
                .state(state)
                .output(outputMap)
                .text(extractText(outputMap))
                .pendingTool(pendingTool instanceof Map ? (Map<String, Object>) pendingTool : null)
                .reasonForIncompletion(asString(status.get("reasonForIncompletion")))
                .build();
    }

    /**
     * Surface the agent's final text from a completed run's output map. The compiled agent
     * WorkflowDef (see {@code AgentCompiler}) emits a canonical {@code result} output parameter —
     * the LLM's {@code output.result} — as the run's final output. When that resolved to a plain
     * string it is the agent's final text; schema-typed (structured) results stay a {@code Map} and
     * are left in {@link ConductorAgentExecution#getOutput()} only. An explicit {@code text} key,
     * if a snapshot ever carries one, takes precedence.
     */
    private static String extractText(Map<String, Object> output) {
        if (output == null) {
            return null;
        }
        Object text = output.get("text");
        if (!(text instanceof String)) {
            text = output.get("result");
        }
        return text instanceof String ? (String) text : null;
    }

    /**
     * Map the AgentSpan status snapshot onto the coarse {@link ConductorAgentState} the {@code
     * AGENT} task routes on. A waiting execution takes precedence over terminal flags so a paused
     * run surfaces its pending request; otherwise the workflow status determines the terminal
     * outcome.
     */
    private ConductorAgentState deriveState(
            String statusName, boolean isComplete, boolean isWaiting) {
        if (isWaiting) {
            return ConductorAgentState.WAITING;
        }
        if (isComplete && "COMPLETED".equals(statusName)) {
            return ConductorAgentState.COMPLETED;
        }
        if ("FAILED".equals(statusName) || "TIMED_OUT".equals(statusName)) {
            return ConductorAgentState.FAILED;
        }
        if ("TERMINATED".equals(statusName) || "CANCELED".equals(statusName)) {
            return ConductorAgentState.CANCELED;
        }
        return ConductorAgentState.RUNNING;
    }

    @Override
    public void respond(String executionId, Map<String, Object> message) {
        try {
            agentService.respond(executionId, message);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw nonRetryable(e);
        }
    }

    @Override
    public void cancel(String executionId, String reason) {
        // Cancel is best-effort (the delegate swallows failures), but translate for contract
        // consistency so any caller that does inspect the failure sees the retryability signal.
        try {
            agentService.cancelAgent(executionId, reason);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw nonRetryable(e);
        }
    }

    @Override
    public List<ConductorAgentSummary> listAgents() {
        List<AgentSummary> summaries;
        try {
            summaries = agentService.listAgents();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw nonRetryable(e);
        }
        List<ConductorAgentSummary> result = new ArrayList<>(summaries.size());
        for (AgentSummary summary : summaries) {
            result.add(
                    ConductorAgentSummary.builder()
                            .name(summary.getName())
                            .version(summary.getVersion())
                            .type(summary.getType())
                            .tags(summary.getTags())
                            .createTime(summary.getCreateTime())
                            .updateTime(summary.getUpdateTime())
                            .description(summary.getDescription())
                            .checksum(summary.getChecksum())
                            .build());
        }
        return result;
    }

    /**
     * Translates a permanent {@code AgentService} failure into the {@link ConductorAgentRuntime}
     * non-retryable contract exception, preserving the original message and cause.
     */
    private static NonRetryableAgentException nonRetryable(RuntimeException e) {
        return new NonRetryableAgentException(e.getMessage(), e);
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }
}
