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

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestComplexSwitchScenarios {

    private SystemTaskRegistry systemTaskRegistry;
    private Switch switchSystemTask;

    @Before
    public void setUp() {
        systemTaskRegistry = mock(SystemTaskRegistry.class);
        switchSystemTask = new Switch();

        // Register system tasks
        when(systemTaskRegistry.get(TaskType.TASK_TYPE_SWITCH)).thenReturn(switchSystemTask);
        when(systemTaskRegistry.isSystemTask(TaskType.TASK_TYPE_SWITCH)).thenReturn(true);
        when(systemTaskRegistry.get(TaskType.TASK_TYPE_DO_WHILE))
                .thenReturn(new DoWhile(null, null));
        when(systemTaskRegistry.isSystemTask(TaskType.TASK_TYPE_DO_WHILE)).thenReturn(true);
        when(systemTaskRegistry.get(TaskType.TASK_TYPE_SUB_WORKFLOW))
                .thenReturn(new SubWorkflow(null));
        when(systemTaskRegistry.isSystemTask(TaskType.TASK_TYPE_SUB_WORKFLOW)).thenReturn(true);
    }

    private TaskModel createSwitchTask(
            String refName,
            String status,
            String selectedCase,
            Map<String, List<WorkflowTask>> decisionCases) {
        TaskModel task = new TaskModel();
        task.setReferenceTaskName(refName);
        task.setTaskType(TaskType.TASK_TYPE_SWITCH);
        task.setStatus(TaskModel.Status.valueOf(status));
        task.addOutput("selectedCase", selectedCase);

        WorkflowTask wt = new WorkflowTask();
        wt.setType(TaskType.TASK_TYPE_SWITCH);
        wt.setTaskReferenceName(refName);
        wt.setDecisionCases(decisionCases);
        task.setWorkflowTask(wt);

        return task;
    }

    private WorkflowTask createWorkflowTask(String refName, String type) {
        WorkflowTask wt = new WorkflowTask();
        wt.setTaskReferenceName(refName);
        wt.setType(type);
        return wt;
    }

    private TaskModel createTask(String refName, String type, String status) {
        TaskModel task = new TaskModel();
        task.setReferenceTaskName(refName);
        task.setTaskType(type);
        task.setStatus(TaskModel.Status.valueOf(status));
        return task;
    }

    @Test
    public void testSwitchEndingWithDoWhile_InProgress() {
        // Scenario: Switch -> Case A -> DoWhile (IN_PROGRESS)
        // Join should receive "DoWhileRef" and wait.

        WorkflowTask doWhileDef = createWorkflowTask("doWhileRef", TaskType.TASK_TYPE_DO_WHILE);
        Map<String, List<WorkflowTask>> cases = new HashMap<>();
        cases.put("CaseA", List.of(doWhileDef));

        TaskModel switchTask = createSwitchTask("switchRef", "COMPLETED", "CaseA", cases);
        TaskModel doWhileTask =
                createTask("doWhileRef", TaskType.TASK_TYPE_DO_WHILE, "IN_PROGRESS");

        WorkflowModel workflow = new WorkflowModel();
        workflow.getTasks().add(switchTask);
        workflow.getTasks().add(doWhileTask);

        String terminalRef =
                switchSystemTask.getTerminalTaskRef(workflow, switchTask, systemTaskRegistry);

        // Should resolve to the DoWhile task
        assertEquals("doWhileRef", terminalRef);
    }

    @Test
    public void testSwitchEndingWithDoWhile_Completed() {
        // Scenario: Switch -> Case A -> DoWhile (COMPLETED)

        WorkflowTask doWhileDef = createWorkflowTask("doWhileRef", TaskType.TASK_TYPE_DO_WHILE);
        Map<String, List<WorkflowTask>> cases = new HashMap<>();
        cases.put("CaseA", List.of(doWhileDef));

        TaskModel switchTask = createSwitchTask("switchRef", "COMPLETED", "CaseA", cases);
        TaskModel doWhileTask = createTask("doWhileRef", TaskType.TASK_TYPE_DO_WHILE, "COMPLETED");

        WorkflowModel workflow = new WorkflowModel();
        workflow.getTasks().add(switchTask);
        workflow.getTasks().add(doWhileTask);

        String terminalRef =
                switchSystemTask.getTerminalTaskRef(workflow, switchTask, systemTaskRegistry);

        // Should resolve to the DoWhile task (which is terminal)
        assertEquals("doWhileRef", terminalRef);
    }

    @Test
    public void testSwitchEndingWithSubWorkflow() {
        // Scenario: Switch -> Case A -> SubWorkflow (IN_PROGRESS)

        WorkflowTask subWfDef = createWorkflowTask("subWfRef", TaskType.TASK_TYPE_SUB_WORKFLOW);
        Map<String, List<WorkflowTask>> cases = new HashMap<>();
        cases.put("CaseA", List.of(subWfDef));

        TaskModel switchTask = createSwitchTask("switchRef", "COMPLETED", "CaseA", cases);
        TaskModel subWfTask =
                createTask("subWfRef", TaskType.TASK_TYPE_SUB_WORKFLOW, "IN_PROGRESS");

        WorkflowModel workflow = new WorkflowModel();
        workflow.getTasks().add(switchTask);
        workflow.getTasks().add(subWfTask);

        String terminalRef =
                switchSystemTask.getTerminalTaskRef(workflow, switchTask, systemTaskRegistry);

        assertEquals("subWfRef", terminalRef);
    }

    @Test
    public void testNestedSwitch_Switch_DoWhile() {
        // Scenario: Switch 1 -> Case A -> Switch 2 -> Case B -> DoWhile

        // Inner Switch content
        WorkflowTask doWhileDef = createWorkflowTask("doWhileRef", TaskType.TASK_TYPE_DO_WHILE);
        Map<String, List<WorkflowTask>> innerCases = new HashMap<>();
        innerCases.put("CaseB", List.of(doWhileDef));
        WorkflowTask innerSwitchDef =
                createWorkflowTask("innerSwitchRef", TaskType.TASK_TYPE_SWITCH);
        innerSwitchDef.setDecisionCases(innerCases);

        // Outer Switch content
        Map<String, List<WorkflowTask>> outerCases = new HashMap<>();
        outerCases.put("CaseA", List.of(innerSwitchDef));

        // Runtime tasks
        TaskModel outerSwitch =
                createSwitchTask("outerSwitchRef", "COMPLETED", "CaseA", outerCases);
        TaskModel innerSwitch =
                createSwitchTask("innerSwitchRef", "COMPLETED", "CaseB", innerCases);
        TaskModel doWhileTask =
                createTask("doWhileRef", TaskType.TASK_TYPE_DO_WHILE, "IN_PROGRESS");

        WorkflowModel workflow = new WorkflowModel();
        workflow.getTasks().add(outerSwitch);
        workflow.getTasks().add(innerSwitch);
        workflow.getTasks().add(doWhileTask);

        // Resolve Outer Switch
        String terminalRef =
                switchSystemTask.getTerminalTaskRef(workflow, outerSwitch, systemTaskRegistry);

        // Should recurse: Outer -> Inner -> DoWhile
        assertEquals("doWhileRef", terminalRef);
    }

    @Test
    public void testNestedSwitch_Switch_Switch_Simple() {
        // Deep nesting: Switch 1 -> Switch 2 -> Switch 3 -> Simple Task

        WorkflowTask simpleDef = createWorkflowTask("simpleRef", "SIMPLE");

        Map<String, List<WorkflowTask>> level3Cases = new HashMap<>();
        level3Cases.put("C", List.of(simpleDef));
        WorkflowTask switch3Def = createWorkflowTask("switch3Ref", TaskType.TASK_TYPE_SWITCH);
        switch3Def.setDecisionCases(level3Cases);

        Map<String, List<WorkflowTask>> level2Cases = new HashMap<>();
        level2Cases.put("B", List.of(switch3Def));
        WorkflowTask switch2Def = createWorkflowTask("switch2Ref", TaskType.TASK_TYPE_SWITCH);
        switch2Def.setDecisionCases(level2Cases);

        Map<String, List<WorkflowTask>> level1Cases = new HashMap<>();
        level1Cases.put("A", List.of(switch2Def));

        TaskModel switch1 = createSwitchTask("switch1Ref", "COMPLETED", "A", level1Cases);
        TaskModel switch2 = createSwitchTask("switch2Ref", "COMPLETED", "B", level2Cases);
        TaskModel switch3 = createSwitchTask("switch3Ref", "COMPLETED", "C", level3Cases);
        TaskModel simpleTask = createTask("simpleRef", "SIMPLE", "COMPLETED");

        WorkflowModel workflow = new WorkflowModel();
        workflow.getTasks().add(switch1);
        workflow.getTasks().add(switch2);
        workflow.getTasks().add(switch3);
        workflow.getTasks().add(simpleTask);

        String terminalRef =
                switchSystemTask.getTerminalTaskRef(workflow, switch1, systemTaskRegistry);

        assertEquals("simpleRef", terminalRef);
    }
}
