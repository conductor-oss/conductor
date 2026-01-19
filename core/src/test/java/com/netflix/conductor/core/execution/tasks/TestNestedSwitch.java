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
package com.netflix.conductor.core.execution.tasks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SWITCH;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestNestedSwitch {

    private SystemTaskRegistry systemTaskRegistry;
    private Switch switchSystemTask;

    @Before
    public void setup() {
        systemTaskRegistry = mock(SystemTaskRegistry.class);
        switchSystemTask = new Switch();

        when(systemTaskRegistry.isSystemTask(TASK_TYPE_SWITCH)).thenReturn(true);
        when(systemTaskRegistry.get(TASK_TYPE_SWITCH)).thenReturn(switchSystemTask);
    }

    @Test
    public void testNestedSwitchResolution() {
        // Construct the workflow definition structure
        // D1 -> case "A" -> D2
        // D2 -> case "B" -> T1

        WorkflowTask t1Def = new WorkflowTask();
        t1Def.setTaskReferenceName("t1");
        t1Def.setType("SIMPLE");

        WorkflowTask d2Def = new WorkflowTask();
        d2Def.setTaskReferenceName("d2");
        d2Def.setType(TASK_TYPE_SWITCH);
        Map<String, List<WorkflowTask>> d2Cases = new HashMap<>();
        d2Cases.put("B", List.of(t1Def));
        d2Def.setDecisionCases(d2Cases);

        WorkflowTask d1Def = new WorkflowTask();
        d1Def.setTaskReferenceName("d1");
        d1Def.setType(TASK_TYPE_SWITCH);
        Map<String, List<WorkflowTask>> d1Cases = new HashMap<>();
        d1Cases.put("A", List.of(d2Def));
        d1Def.setDecisionCases(d1Cases);

        // Construct the execution state (WorkflowModel)
        WorkflowModel workflow = new WorkflowModel();

        // T1 is completed
        TaskModel t1 = new TaskModel();
        t1.setReferenceTaskName("t1");
        t1.setStatus(TaskModel.Status.COMPLETED);
        t1.setTaskType("SIMPLE");
        workflow.getTasks().add(t1);

        // D2 is completed, output "B"
        TaskModel d2 = new TaskModel();
        d2.setReferenceTaskName("d2");
        d2.setStatus(TaskModel.Status.COMPLETED);
        d2.setTaskType(TASK_TYPE_SWITCH);
        // Switch uses "selectedCase" logic inside execution (usually), but
        // getTerminalTaskRef reads it from output
        // Wait, Switch.getTerminalTaskRef reads "selectedCase" from
        // task.getOutputData().
        // So we must simulate that.
        d2.addOutput("selectedCase", "B");
        d2.setWorkflowTask(d2Def);
        workflow.getTasks().add(d2);

        // D1 is completed, output "A"
        TaskModel d1 = new TaskModel();
        d1.setReferenceTaskName("d1");
        d1.setStatus(TaskModel.Status.COMPLETED);
        d1.setTaskType(TASK_TYPE_SWITCH);
        d1.addOutput("selectedCase", "A");
        d1.setWorkflowTask(d1Def);
        workflow.getTasks().add(d1);

        // Resolve terminal task for D1
        // Should resolve to T1
        String terminalRef = switchSystemTask.getTerminalTaskRef(workflow, d1, systemTaskRegistry);

        assertEquals("t1", terminalRef);
    }
}
