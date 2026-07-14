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
package org.conductoross.conductor.ai.agentspan.runtime.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Event DTO for SSE streaming. Each event represents a semantic agent-level state change (thinking,
 * tool call, guardrail result, HITL pause, etc.).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentSSEEvent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private long id;
    private String type;
    private String executionId;
    private String content;
    private String toolName;
    private Object args;
    private Object result;
    private String target;
    private Object output;
    private String guardrailName;
    private Map<String, Object> pendingTool;
    private long timestamp;
    // context_condensed fields
    private Integer messagesBefore;
    private Integer messagesAfter;
    private Integer exchangesCondensed;

    public AgentSSEEvent() {}

    private AgentSSEEvent(String type, String executionId) {
        this.type = type;
        this.executionId = executionId;
        this.timestamp = System.currentTimeMillis();
    }

    // ── Factory methods ──────────────────────────────────────────────

    public static AgentSSEEvent thinking(String executionId, String taskRef) {
        AgentSSEEvent e = new AgentSSEEvent("thinking", executionId);
        e.content = taskRef;
        return e;
    }

    public static AgentSSEEvent toolCall(String executionId, String toolName, Object args) {
        AgentSSEEvent e = new AgentSSEEvent("tool_call", executionId);
        e.toolName = toolName;
        e.args = args;
        return e;
    }

    public static AgentSSEEvent toolResult(String executionId, String toolName, Object result) {
        AgentSSEEvent e = new AgentSSEEvent("tool_result", executionId);
        e.toolName = toolName;
        e.result = result;
        return e;
    }

    public static AgentSSEEvent handoff(String executionId, String target) {
        AgentSSEEvent e = new AgentSSEEvent("handoff", executionId);
        e.target = target;
        return e;
    }

    public static AgentSSEEvent waiting(String executionId, Map<String, Object> pendingTool) {
        AgentSSEEvent e = new AgentSSEEvent("waiting", executionId);
        e.pendingTool = pendingTool;
        return e;
    }

    public static AgentSSEEvent guardrailPass(String executionId, String name) {
        AgentSSEEvent e = new AgentSSEEvent("guardrail_pass", executionId);
        e.guardrailName = name;
        return e;
    }

    public static AgentSSEEvent guardrailFail(String executionId, String name, String message) {
        AgentSSEEvent e = new AgentSSEEvent("guardrail_fail", executionId);
        e.guardrailName = name;
        e.content = message;
        return e;
    }

    public static AgentSSEEvent error(String executionId, String taskRef, String message) {
        AgentSSEEvent e = new AgentSSEEvent("error", executionId);
        e.content = message;
        e.toolName = taskRef;
        return e;
    }

    public static AgentSSEEvent done(String executionId, Object output) {
        AgentSSEEvent e = new AgentSSEEvent("done", executionId);
        e.output = output;
        return e;
    }

    public static AgentSSEEvent contextCondensed(
            String executionId,
            String trigger,
            int messagesBefore,
            int messagesAfter,
            int exchangesCondensed) {
        AgentSSEEvent e = new AgentSSEEvent("context_condensed", executionId);
        e.content = trigger;
        e.messagesBefore = messagesBefore;
        e.messagesAfter = messagesAfter;
        e.exchangesCondensed = exchangesCondensed;
        return e;
    }

    public static AgentSSEEvent subagentStart(
            String executionId, String subagentIdentifier, String prompt) {
        AgentSSEEvent e = new AgentSSEEvent("subagent_start", executionId);
        e.target = subagentIdentifier;
        e.content = prompt != null ? prompt : "";
        return e;
    }

    public static AgentSSEEvent subagentStop(
            String executionId, String subagentIdentifier, String result) {
        AgentSSEEvent e = new AgentSSEEvent("subagent_stop", executionId);
        e.target = subagentIdentifier;
        e.result = result != null ? result : "";
        return e;
    }

    // ── Serialization ────────────────────────────────────────────────

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException ex) {
            return "{\"type\":\"error\",\"content\":\"Serialization failed\"}";
        }
    }

    // ── Getters / Setters ────────────────────────────────────────────

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Object getArgs() {
        return args;
    }

    public void setArgs(Object args) {
        this.args = args;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public Object getOutput() {
        return output;
    }

    public void setOutput(Object output) {
        this.output = output;
    }

    public String getGuardrailName() {
        return guardrailName;
    }

    public void setGuardrailName(String guardrailName) {
        this.guardrailName = guardrailName;
    }

    public Map<String, Object> getPendingTool() {
        return pendingTool;
    }

    public void setPendingTool(Map<String, Object> pendingTool) {
        this.pendingTool = pendingTool;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getMessagesBefore() {
        return messagesBefore;
    }

    public void setMessagesBefore(Integer messagesBefore) {
        this.messagesBefore = messagesBefore;
    }

    public Integer getMessagesAfter() {
        return messagesAfter;
    }

    public void setMessagesAfter(Integer messagesAfter) {
        this.messagesAfter = messagesAfter;
    }

    public Integer getExchangesCondensed() {
        return exchangesCondensed;
    }

    public void setExchangesCondensed(Integer exchangesCondensed) {
        this.exchangesCondensed = exchangesCondensed;
    }
}
