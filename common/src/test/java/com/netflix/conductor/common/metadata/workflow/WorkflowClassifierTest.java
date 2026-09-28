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

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WorkflowClassifierTest {

    @Test
    public void nullDefIsWorkflow() {
        assertEquals(
                WorkflowClassifier.WORKFLOW, WorkflowClassifier.classifierOf((WorkflowDef) null));
    }

    @Test
    public void nullOrEmptyMetadataIsWorkflow() {
        assertEquals(
                WorkflowClassifier.WORKFLOW,
                WorkflowClassifier.classifierOf((Map<String, Object>) null));
        assertEquals(WorkflowClassifier.WORKFLOW, WorkflowClassifier.classifierOf(new HashMap<>()));
    }

    @Test
    public void untaggedDefIsWorkflow() {
        WorkflowDef def = new WorkflowDef();
        def.setName("plain");
        assertEquals(WorkflowClassifier.WORKFLOW, WorkflowClassifier.classifierOf(def));
    }

    @Test
    public void agentSdkStampMeansAgent() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent_sdk", "python");
        assertEquals(WorkflowClassifier.AGENT, WorkflowClassifier.classifierOf(metadata));
    }

    @Test
    public void agentDefStampMeansAgent() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agentDef", Map.of("name", "my-agent"));
        assertEquals(WorkflowClassifier.AGENT, WorkflowClassifier.classifierOf(metadata));
    }

    @Test
    public void explicitClassifierWins() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent_sdk", "python");
        metadata.put("classifier", "pipeline");
        assertEquals("pipeline", WorkflowClassifier.classifierOf(metadata));
    }

    @Test
    public void blankExplicitClassifierIsIgnored() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("classifier", "   ");
        assertEquals(WorkflowClassifier.WORKFLOW, WorkflowClassifier.classifierOf(metadata));

        metadata.put("agent_sdk", "python");
        assertEquals(WorkflowClassifier.AGENT, WorkflowClassifier.classifierOf(metadata));
    }

    @Test
    public void nonStringExplicitClassifierIsIgnored() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("classifier", 42);
        assertEquals(WorkflowClassifier.WORKFLOW, WorkflowClassifier.classifierOf(metadata));
    }

    @Test
    public void defWithMetadataResolvesThroughDefOverload() {
        WorkflowDef def = new WorkflowDef();
        def.setName("agentic");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent_sdk", "java");
        def.setMetadata(metadata);
        assertEquals(WorkflowClassifier.AGENT, WorkflowClassifier.classifierOf(def));
    }
}
