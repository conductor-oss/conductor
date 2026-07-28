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
package com.netflix.conductor.common.metadata.workflow;

import java.util.Map;

/**
 * Derives the <b>classifier</b> of a workflow definition from its {@code metadata} map.
 *
 * <p>The classifier is a tag used to distinguish kinds of definitions/executions: an untagged def
 * is a plain <b>workflow</b> (classifier {@link #WORKFLOW}); AgentSpan-compiled defs are tagged
 * {@link #AGENT}. The scheme is open-ended — a def may carry any explicit {@code
 * metadata.classifier} value, enabling future kinds without schema/code changes.
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
 * <p>Note: unlike the metadata map (where absence of a classifier simply means "plain workflow"),
 * the resolved value is never {@code null} — untagged defs resolve to the literal {@link #WORKFLOW}
 * token so that indexed executions can be filtered with plain equality across all index backends.
 */
public final class WorkflowClassifier {

    /** Classifier value for AgentSpan agent definitions/executions. */
    public static final String AGENT = "agent";

    /** Classifier value for plain (untagged) workflow definitions/executions. */
    public static final String WORKFLOW = "workflow";

    private static final String META_CLASSIFIER = "classifier";
    private static final String META_AGENT_SDK = "agent_sdk";
    private static final String META_AGENT_DEF = "agentDef";

    private WorkflowClassifier() {}

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
}
