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

import java.util.Map;

import org.conductoross.conductor.common.metadata.agent.LongTermMemoryConfig;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.model.WorkflowModel;

/** Trusted OCG identity for one completed root agent execution. */
public record OcgExecutionIdentity(
        String agent, String user, String sessionId, String executionId) {

    static OcgExecutionIdentity from(WorkflowModel workflow, LongTermMemoryConfig config) {
        Map<String, Object> input = workflow.getInput() == null ? Map.of() : workflow.getInput();
        String executionId = workflow.getWorkflowId();
        String agent = stringValue(config.getAgent(), "agentspan");
        String sessionId =
                masked(workflow, "session_id")
                        ? "[REDACTED]"
                        : stringValue(input.get("session_id"), executionId);
        String runtimeUser =
                masked(workflow, "user") ? "[REDACTED]" : stringValue(input.get("user"), null);
        String user = stringValue(config.getUser(), runtimeUser);
        if ("[REDACTED]".equals(user) || isBlank(user)) user = "agent:" + agent;
        if (!isBlank(user) && !user.startsWith("user:") && !user.startsWith("agent:")) {
            user = "user:" + user;
        }
        return new OcgExecutionIdentity(agent, user, sessionId, executionId);
    }

    private static boolean masked(WorkflowModel workflow, String field) {
        WorkflowDef definition = workflow.getWorkflowDefinition();
        return definition != null
                && definition.getMaskedFields() != null
                && definition.getMaskedFields().contains(field);
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        return String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
