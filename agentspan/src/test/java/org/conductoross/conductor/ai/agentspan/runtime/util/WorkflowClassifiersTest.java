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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WorkflowClassifiers}. Deterministic — pure derivation logic. Must stay in
 * sync with Conductor's {@code WorkflowClassifier}.
 */
class WorkflowClassifiersTest {

    @Test
    void nullDefResolvesToWorkflow() {
        assertThat(WorkflowClassifiers.classifierOf((WorkflowDef) null))
                .isEqualTo(WorkflowClassifiers.WORKFLOW);
    }

    @Test
    void nullOrEmptyMetadataResolvesToWorkflow() {
        assertThat(WorkflowClassifiers.classifierOf((Map<String, Object>) null))
                .isEqualTo(WorkflowClassifiers.WORKFLOW);
        assertThat(WorkflowClassifiers.classifierOf(new HashMap<>()))
                .isEqualTo(WorkflowClassifiers.WORKFLOW);
    }

    @Test
    void agentSdkStampResolvesToAgent() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent_sdk", "python");
        assertThat(WorkflowClassifiers.classifierOf(metadata)).isEqualTo(WorkflowClassifiers.AGENT);
        assertThat(WorkflowClassifiers.isAgent(metadata)).isTrue();
    }

    @Test
    void agentDefStampResolvesToAgent() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agentDef", Map.of("name", "my-agent"));
        assertThat(WorkflowClassifiers.classifierOf(metadata)).isEqualTo(WorkflowClassifiers.AGENT);
    }

    @Test
    void explicitClassifierWinsOverStamp() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent_sdk", "python");
        metadata.put("classifier", "pipeline");
        assertThat(WorkflowClassifiers.classifierOf(metadata)).isEqualTo("pipeline");
        assertThat(WorkflowClassifiers.isAgent(metadata)).isFalse();
    }

    @Test
    void blankExplicitClassifierIsIgnored() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("classifier", "  ");
        assertThat(WorkflowClassifiers.classifierOf(metadata))
                .isEqualTo(WorkflowClassifiers.WORKFLOW);
    }

    @Test
    void explicitAgentClassifierWithoutStampIsAgent() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("classifier", "agent");
        assertThat(WorkflowClassifiers.isAgent(metadata)).isTrue();
    }
}
