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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorkflowDefTest {

    @Test
    public void plainWorkflowIsNotAgent() {
        WorkflowDef def = new WorkflowDef();
        def.setName("plain");
        assertFalse(def.isAgent());
    }

    @Test
    public void nullMetadataIsNotAgent() {
        WorkflowDef def = new WorkflowDef();
        def.setMetadata(null);
        assertFalse(def.isAgent());
    }

    @Test
    public void emptyMetadataIsNotAgent() {
        WorkflowDef def = new WorkflowDef();
        def.setMetadata(new HashMap<>());
        assertFalse(def.isAgent());
    }

    @Test
    public void unrelatedMetadataIsNotAgent() {
        WorkflowDef def = new WorkflowDef();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("someOtherKey", "someValue");
        def.setMetadata(metadata);
        assertFalse(def.isAgent());
    }

    @Test
    public void agentSdkStampMeansAgent() {
        WorkflowDef def = new WorkflowDef();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent_sdk", "python");
        def.setMetadata(metadata);
        assertTrue(def.isAgent());
    }

    @Test
    public void agentDefStampMeansAgent() {
        WorkflowDef def = new WorkflowDef();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agentDef", Map.of("name", "my-agent"));
        def.setMetadata(metadata);
        assertTrue(def.isAgent());
    }

    @Test
    public void bothAgentStampsMeansAgent() {
        WorkflowDef def = new WorkflowDef();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent_sdk", "python");
        metadata.put("agentDef", Map.of("name", "my-agent"));
        def.setMetadata(metadata);
        assertTrue(def.isAgent());
    }
}
