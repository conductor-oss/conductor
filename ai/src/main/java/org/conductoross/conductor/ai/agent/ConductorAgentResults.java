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
package org.conductoross.conductor.ai.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.a2a.A2AResults;
import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.Artifact;
import org.conductoross.conductor.ai.a2a.model.Part;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.conductoross.conductor.ai.a2a.model.TaskStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Output keys and result-shaping for the {@code AGENT} (conductor) task branch, so the task output
 * is consistent with the remote A2A branch regardless of whether it settled on the initial call or
 * after polling.
 */
public final class ConductorAgentResults {

    /** Child workflow execution id (used to poll/respond/cancel across retries). */
    public static final String KEY_EXECUTION_ID = "executionId";

    /** Name of the executed agent. */
    public static final String KEY_AGENT_NAME = "agentName";

    /** Normalized execution state. */
    public static final String KEY_STATE = "state";

    /** When the agent execution started (epoch millis); also anchors the absolute deadline. */
    public static final String KEY_START_TIME = "agentStartTime";

    /** When the agent execution reached a terminal state (epoch millis). */
    public static final String KEY_END_TIME = "agentEndTime";

    /** Bookkeeping: consecutive transient poll failures so far. */
    public static final String KEY_POLL_FAILURES = "agentPollFailures";

    /** True when the execution paused for external input (human answer / tool result). */
    public static final String KEY_WAITING = "waiting";

    /** The pending tool/human request surfaced while waiting. */
    public static final String KEY_PENDING_TOOL = "pendingTool";

    /** Every tool call the agent is blocked on, when it asked for more than one. */
    public static final String KEY_PENDING_TOOLS = "pendingTools";

    /**
     * Handle for a batch of tool calls being run on the agent's behalf. Present only while that
     * batch is in flight, and the reason the AGENT task can stay IN_PROGRESS across replicas.
     */
    public static final String KEY_TOOL_DISPATCH_ID = "toolDispatchId";

    /**
     * The workflow running this agent's tools. Named to match the SUB_WORKFLOW system task's own
     * output, which is what the execution view keys its drill-in link off.
     */
    public static final String KEY_SUB_WORKFLOW_ID = "subWorkflowId";

    /** Latest/final text emitted by the agent. */
    /** Tool calls the platform ran itself, with the input each was given. */
    public static final String KEY_EXECUTED_TOOLS = "executedTools";

    public static final String KEY_TEXT = "text";

    /** Structured output of a completed run. */
    public static final String KEY_OUTPUT = "output";

    private ConductorAgentResults() {}

    /**
     * Writes the output of a completed execution: the workflow's structured {@code output} map is
     * surfaced verbatim under {@link #KEY_OUTPUT} and the final text under {@link #KEY_TEXT}.
     */
    public static void writeCompleted(
            Map<String, Object> outputData, ConductorAgentExecution execution) {
        if (execution.getOutput() != null) {
            outputData.put(KEY_OUTPUT, execution.getOutput());
        }
        if (execution.getText() != null) {
            outputData.put(KEY_TEXT, execution.getText());
        }
        writeExecutedTools(outputData, execution);
    }

    /**
     * Records the tools the platform ran itself, keeping what earlier turns reported.
     *
     * <p>A run reports these per turn, and a turn that pauses for a function has usually run some
     * getting there. Overwriting rather than accumulating would leave only the last turn's, so the
     * work an agent did on the way to its answer would disappear as it went.
     */
    public static void writeExecutedTools(
            Map<String, Object> outputData, ConductorAgentExecution execution) {
        if (execution.getExecutedTools() == null || execution.getExecutedTools().isEmpty()) {
            return;
        }
        List<Object> all = new ArrayList<>();
        Object existing = outputData.get(KEY_EXECUTED_TOOLS);
        if (existing instanceof List<?> list) {
            all.addAll(list);
        }
        all.addAll(execution.getExecutedTools());
        outputData.put(KEY_EXECUTED_TOOLS, all);
    }

    /**
     * Adds the canonical A2A task representation and convenience fields used by the remote A2A
     * branch. Conductor-specific fields remain alongside it for backward compatibility.
     *
     * <p>The child execution id is the A2A task id. A caller-supplied agent session is the A2A
     * context id; otherwise the execution id supplies a stable opaque context. Successful output is
     * represented as both readable text (when available) and a data part containing the full
     * structured agent output.
     */
    public static void writeA2AOutput(
            Map<String, Object> outputData,
            ConductorAgentExecution execution,
            String sessionId,
            ObjectMapper objectMapper) {
        A2ATask task = new A2ATask();
        task.setKind("task");
        task.setId(execution.getExecutionId());
        task.setContextId(StringUtils.firstNonBlank(sessionId, execution.getExecutionId()));

        String protocolState = protocolState(execution.getState());
        A2AMessage statusMessage = statusMessage(execution, task.getContextId());
        TaskStatus status = new TaskStatus();
        status.setState(protocolState);
        status.setMessage(statusMessage);
        Long statusTime =
                execution.getState() != null && execution.getState().isTerminal()
                        ? execution.getEndTime()
                        : execution.getStartTime();
        if (statusTime != null && statusTime > 0) {
            status.setTimestamp(Instant.ofEpochMilli(statusTime).toString());
        }
        task.setStatus(status);

        if (execution.getState() == ConductorAgentState.COMPLETED) {
            List<Part> parts = resultParts(execution);
            if (!parts.isEmpty()) {
                Artifact artifact = new Artifact();
                artifact.setArtifactId(execution.getExecutionId() + "-result");
                artifact.setName("Conductor agent result");
                artifact.setParts(parts);
                task.setArtifacts(List.of(artifact));
            }
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("agentType", "conductor");
        metadata.put("executionId", execution.getExecutionId());
        if (StringUtils.isNotBlank(execution.getAgentName())) {
            metadata.put("agentName", execution.getAgentName());
        }
        task.setMetadata(metadata);

        outputData.putAll(A2AResults.taskOutput(task, objectMapper));
    }

    private static A2AMessage statusMessage(ConductorAgentExecution execution, String contextId) {
        List<Part> parts = new ArrayList<>();
        String text = resultText(execution);
        if (StringUtils.isNotBlank(text)) {
            Part textPart = new Part();
            textPart.setKind("text");
            textPart.setText(text);
            parts.add(textPart);
        } else if (execution.getState() == ConductorAgentState.FAILED
                || execution.getState() == ConductorAgentState.CANCELED) {
            String reason = execution.getReasonForIncompletion();
            if (StringUtils.isNotBlank(reason)) {
                Part textPart = new Part();
                textPart.setKind("text");
                textPart.setText(reason);
                parts.add(textPart);
            }
        }
        if (execution.getPendingTool() != null && !execution.getPendingTool().isEmpty()) {
            Part dataPart = new Part();
            dataPart.setKind("data");
            dataPart.setData(Map.of("pendingTool", execution.getPendingTool()));
            parts.add(dataPart);
        }
        if (parts.isEmpty()) {
            return null;
        }

        A2AMessage message = new A2AMessage();
        message.setKind("message");
        message.setRole("agent");
        message.setMessageId(execution.getExecutionId() + "-agent-message");
        message.setTaskId(execution.getExecutionId());
        message.setContextId(contextId);
        message.setParts(parts);
        return message;
    }

    private static List<Part> resultParts(ConductorAgentExecution execution) {
        List<Part> parts = new ArrayList<>();
        String text = resultText(execution);
        if (StringUtils.isNotBlank(text)) {
            Part textPart = new Part();
            textPart.setKind("text");
            textPart.setText(text);
            parts.add(textPart);
        }
        if (execution.getOutput() != null && !execution.getOutput().isEmpty()) {
            Part dataPart = new Part();
            dataPart.setKind("data");
            dataPart.setData(execution.getOutput());
            parts.add(dataPart);
        }
        return parts;
    }

    private static String resultText(ConductorAgentExecution execution) {
        if (StringUtils.isNotBlank(execution.getText())) {
            return execution.getText();
        }
        if (execution.getOutput() == null) {
            return null;
        }
        Object text = execution.getOutput().get("text");
        if (!(text instanceof CharSequence)) {
            text = execution.getOutput().get("result");
        }
        return text instanceof CharSequence ? text.toString() : null;
    }

    private static String protocolState(ConductorAgentState state) {
        if (state == null) {
            return TaskState.WORKING;
        }
        return switch (state) {
            case RUNNING -> TaskState.WORKING;
            case WAITING -> TaskState.INPUT_REQUIRED;
            case COMPLETED -> TaskState.COMPLETED;
            case FAILED -> TaskState.FAILED;
            case CANCELED -> TaskState.CANCELED;
        };
    }
}
