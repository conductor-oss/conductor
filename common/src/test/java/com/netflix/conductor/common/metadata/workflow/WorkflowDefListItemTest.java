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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WorkflowDefListItemTest {

    @Test
    public void fromWorkflowDefCopiesListFieldsAndDerivesTaskTypesAndCount() {
        WorkflowTask t1 = new WorkflowTask();
        t1.setType("SIMPLE");
        WorkflowTask t2 = new WorkflowTask();
        t2.setType("HTTP");
        WorkflowTask t3 = new WorkflowTask();
        t3.setType("SIMPLE"); // duplicate type must dedupe

        WorkflowDef def = new WorkflowDef();
        def.setName("payment_flow");
        def.setDescription("desc");
        def.setVersion(4);
        def.setCreateTime(123L);
        def.setSchemaVersion(2);
        def.setRestartable(true);
        def.setWorkflowStatusListenerEnabled(true);
        def.setOwnerEmail("owner@example.com");
        def.setInputParameters(List.of("in1"));
        def.setOutputParameters(Map.of("out1", "v"));
        def.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        def.setTimeoutSeconds(60L);
        def.setFailureWorkflow("failFlow");
        def.setTasks(List.of(t1, t2, t3));

        WorkflowDefListItem item = WorkflowDefListItem.fromWorkflowDef(def);

        assertEquals("payment_flow", item.getName());
        assertEquals("desc", item.getDescription());
        assertEquals(4, item.getVersion());
        assertEquals(Long.valueOf(123L), item.getCreateTime());
        assertEquals(2, item.getSchemaVersion());
        assertTrue(item.isRestartable());
        assertTrue(item.isWorkflowStatusListenerEnabled());
        assertEquals("owner@example.com", item.getOwnerEmail());
        assertEquals(List.of("in1"), item.getInputParameters());
        assertEquals(Map.of("out1", "v"), item.getOutputParameters());
        assertEquals(WorkflowDef.TimeoutPolicy.TIME_OUT_WF, item.getTimeoutPolicy());
        assertEquals(60L, item.getTimeoutSeconds());
        assertEquals(2, item.getTaskTypes().size());
        assertTrue(item.getTaskTypes().contains("SIMPLE"));
        assertTrue(item.getTaskTypes().contains("HTTP"));
        // tasks were declared out of alphabetical order (SIMPLE, HTTP, SIMPLE); the
        // taskTypes set must iterate alphabetically to match the Postgres path.
        assertEquals(List.of("HTTP", "SIMPLE"), new ArrayList<>(item.getTaskTypes()));
        assertEquals(3, item.getTaskCount());
        assertEquals("failFlow", item.getFailureWorkflow());
        // no metadata on this def -> plain workflow classifier
        assertEquals("workflow", item.getClassifier());
    }

    @Test
    public void fromWorkflowDefDerivesAgentClassifierFromMetadata() {
        WorkflowDef def = new WorkflowDef();
        def.setName("agent_flow");
        def.setVersion(1);
        def.setMetadata(Map.of("agent_sdk", "x"));

        WorkflowDefListItem item = WorkflowDefListItem.fromWorkflowDef(def);

        assertEquals("agent", item.getClassifier());
    }

    @Test
    public void fromWorkflowDefHandlesNullTasks() {
        WorkflowDef def = new WorkflowDef();
        def.setName("empty");
        def.setVersion(1);
        def.setTasks(null);

        WorkflowDefListItem item = WorkflowDefListItem.fromWorkflowDef(def);

        assertEquals(0, item.getTaskCount());
        assertEquals(0, item.getTaskTypes().size());
    }
}
