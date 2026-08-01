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
import java.util.concurrent.CompletionStage;

import org.conductoross.conductor.common.metadata.agent.LongTermMemoryConfig;

/** Server-side boundary for OCG lifecycle operations. */
public interface OcgClient {

    /** Queue a raw terminal agent run. Implementations must contain all transport failures. */
    CompletionStage<Void> exportAgentRun(LongTermMemoryConfig config, Map<String, Object> payload);

    /** Read the OCG memory summary for a trusted completed root execution. */
    default OcgExecutionMemory getExecutionMemory(
            LongTermMemoryConfig config, OcgExecutionIdentity identity) {
        throw new UnsupportedOperationException("Execution memory reads are not supported");
    }

    /** Read canonical human feedback for a trusted completed root execution. */
    OcgFeedback getFeedback(LongTermMemoryConfig config, OcgExecutionIdentity identity);

    /** Upsert canonical human feedback for a trusted completed root execution. */
    OcgFeedback setFeedback(
            LongTermMemoryConfig config,
            OcgExecutionIdentity identity,
            OcgFeedbackRating rating,
            String reason);
}
