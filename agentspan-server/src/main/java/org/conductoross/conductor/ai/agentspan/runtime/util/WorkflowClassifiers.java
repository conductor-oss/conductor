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
package org.conductoross.conductor.ai.agentspan.runtime.util;

import java.util.Map;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

/**
 * Derives the <b>classifier</b> of a workflow definition from its {@code metadata} map.
 *
 * <p>The classifier distinguishes kinds of definitions/executions: an untagged def is a plain
 * <b>workflow</b>; AgentSpan-compiled defs are tagged <b>agent</b>. A def may also carry any
 * explicit {@code metadata.classifier} value, enabling future kinds without code changes.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>explicit {@code metadata.classifier} (non-blank) — wins;
 *   <li>otherwise {@code "agent"} when the AgentSpan stamp is present ({@code metadata.agent_sdk}
 *       or {@code metadata.agentDef});
 *   <li>otherwise {@code "workflow"} (a normal, untagged workflow).
 * </ol>
 *
 * <p>This is a local mirror of Conductor's {@code
 * com.netflix.conductor.common.metadata.workflow.WorkflowClassifier} so the library keeps working
 * against Conductor cores that predate that class. Once the minimum supported {@code
 * conductorVersion} ships it, this class can delegate to (or be replaced by) the Conductor one. The
 * derivation logic must stay in sync.
 */
public final class WorkflowClassifiers {

    /** Classifier value for AgentSpan agent definitions/executions. */
    public static final String AGENT = "agent";

    /** Classifier value for plain (untagged) workflow definitions/executions. */
    public static final String WORKFLOW = "workflow";

    private static final String META_CLASSIFIER = "classifier";
    private static final String META_AGENT_SDK = "agent_sdk";
    private static final String META_AGENT_DEF = "agentDef";

    private WorkflowClassifiers() {}

    /** Returns the classifier for {@code def}, never {@code null}. */
    public static String classifierOf(WorkflowDef def) {
        return def == null ? WORKFLOW : classifierOf(def.getMetadata());
    }

    /** Returns the classifier derived from a workflow def {@code metadata} map. */
    public static String classifierOf(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return WORKFLOW;
        }
        Object explicit = metadata.get(META_CLASSIFIER);
        if (explicit instanceof String s && !s.isBlank()) {
            return s;
        }
        if (metadata.get(META_AGENT_SDK) != null || metadata.get(META_AGENT_DEF) != null) {
            return AGENT;
        }
        return WORKFLOW;
    }

    /** Returns {@code true} when the def resolves to the {@link #AGENT} classifier. */
    public static boolean isAgent(Map<String, Object> metadata) {
        if (metadata == null) {
            return false;
        }
        return AGENT.equalsIgnoreCase(classifierOf(metadata));
    }
}
