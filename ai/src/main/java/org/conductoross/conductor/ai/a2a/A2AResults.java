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
package org.conductoross.conductor.ai.a2a;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.Artifact;
import org.conductoross.conductor.ai.a2a.model.Part;
import org.conductoross.conductor.ai.a2a.model.TaskState;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds the task output that {@code AGENT} (polling/streaming) and the push-notification callback
 * both surface, so the shape is identical regardless of how the result arrived.
 */
public final class A2AResults {

    public static final String KEY_STATE = "state";
    public static final String KEY_TASK_ID = "taskId";
    public static final String KEY_CONTEXT_ID = "contextId";
    public static final String KEY_ARTIFACTS = "artifacts";
    public static final String KEY_TEXT = "text";
    public static final String KEY_AGENT_MESSAGE = "agentMessage";
    public static final String KEY_TASK = "task";
    public static final String KEY_PUSH_TOKEN = "pushToken";

    /** Bookkeeping (also surfaced for operator visibility): when the A2A call started. */
    public static final String KEY_STARTED_AT = "a2aStartedAt";

    /** Bookkeeping: consecutive transient poll failures so far. */
    public static final String KEY_POLL_FAILURES = "a2aPollFailures";

    private A2AResults() {}

    /** Output for a remote {@link A2ATask}: normalized state, ids, artifacts, text, full task. */
    public static Map<String, Object> taskOutput(A2ATask task, ObjectMapper objectMapper) {
        Map<String, Object> output = new HashMap<>();
        output.put(KEY_STATE, TaskState.normalize(stateOf(task)));
        output.put(KEY_TASK_ID, task.getId());
        output.put(KEY_CONTEXT_ID, task.getContextId());
        if (task.getArtifacts() != null) {
            output.put(KEY_ARTIFACTS, objectMapper.convertValue(task.getArtifacts(), List.class));
        }
        output.put(KEY_TASK, objectMapper.convertValue(task, Map.class));
        String text = extractText(task);
        if (text != null) {
            output.put(KEY_TEXT, text);
        }
        A2AMessage statusMessage = task.getStatus() != null ? task.getStatus().getMessage() : null;
        if (statusMessage != null) {
            output.put(KEY_AGENT_MESSAGE, objectMapper.convertValue(statusMessage, Map.class));
        }
        return output;
    }

    /** Output for a direct {@link A2AMessage} reply (no task was created). */
    public static Map<String, Object> messageOutput(A2AMessage message, ObjectMapper objectMapper) {
        Map<String, Object> output = new HashMap<>();
        output.put(KEY_STATE, "message");
        if (message != null) {
            output.put(KEY_CONTEXT_ID, message.getContextId());
            output.put(KEY_TASK_ID, message.getTaskId());
            output.put(KEY_AGENT_MESSAGE, objectMapper.convertValue(message, Map.class));
            String text = partsText(message.getParts());
            if (text != null) {
                output.put(KEY_TEXT, text);
            }
        }
        return output;
    }

    /** Concatenates the text of a task's artifact parts, falling back to its status message. */
    public static String extractText(A2ATask task) {
        StringBuilder sb = new StringBuilder();
        if (task.getArtifacts() != null) {
            for (Artifact artifact : task.getArtifacts()) {
                appendParts(sb, artifact.getParts());
            }
        }
        if (sb.length() == 0 && task.getStatus() != null && task.getStatus().getMessage() != null) {
            appendParts(sb, task.getStatus().getMessage().getParts());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    static String partsText(List<Part> parts) {
        StringBuilder sb = new StringBuilder();
        appendParts(sb, parts);
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void appendParts(StringBuilder sb, List<Part> parts) {
        if (parts == null) {
            return;
        }
        for (Part part : parts) {
            if (part != null && part.getText() != null) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(part.getText());
            }
        }
    }

    static String stateOf(A2ATask task) {
        return task.getStatus() != null ? task.getStatus().getState() : null;
    }
}
