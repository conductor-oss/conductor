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

import java.util.Map;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

/**
 * The text an agent shows the callers that list it: its MCP tool description and its A2A Agent Card
 * description. Both servers use this, so they always describe an agent the same way.
 */
public final class AgentIntroduction {

    private AgentIntroduction() {}

    /**
     * The agent's own {@code introduction}, as its author set it, from the agent definition stored
     * with the compiled workflow ({@code metadata.agentDef}); null when there is none.
     */
    public static String of(WorkflowDef def) {
        if (def == null
                || def.getMetadata() == null
                || !(def.getMetadata().get("agentDef") instanceof Map<?, ?> agentDef)) {
            return null;
        }
        return agentDef.get("introduction") instanceof String text && !text.isBlank() ? text : null;
    }
}
