/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.core.execution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestWorkflowDef {

    @Test
    public void testContainsType() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test_workflow");
        def.setVersion(1);
        def.setSchemaVersion(2);
        def.getTasks().add(createWorkflowTask("simple_task_1"));
        def.getTasks().add(createWorkflowTask("simple_task_2"));

        WorkflowTask task3 = createWorkflowTask("decision_task_1");
        def.getTasks().add(task3);
        task3.setType(TaskType.DECISION.name());
        task3.getDecisionCases()
                .put(
                        "Case1",
                        Arrays.asList(
                                createWorkflowTask("case_1_task_1"),
                                createWorkflowTask("case_1_task_2")));
        task3.getDecisionCases()
                .put(
                        "Case2",
                        Arrays.asList(
                                createWorkflowTask("case_2_task_1"),
                                createWorkflowTask("case_2_task_2")));
        task3.getDecisionCases()
                .put(
                        "Case3",
                        Collections.singletonList(
                                deciderTask(
                                        "decision_task_2",
                                        toMap("Case31", "case31_task_1", "case_31_task_2"),
                                        Collections.singletonList("case3_def_task"))));
        def.getTasks().add(createWorkflowTask("simple_task_3"));

        assertTrue(def.containsType(TaskType.SIMPLE.name()));
        assertTrue(def.containsType(TaskType.DECISION.name()));
        assertFalse(def.containsType(TaskType.DO_WHILE.name()));
    }

    @Test
    public void testGetNextTask_Decision() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test_workflow");
        def.setVersion(1);
        def.setSchemaVersion(2);
        def.getTasks().add(createWorkflowTask("simple_task_1"));
        def.getTasks().add(createWorkflowTask("simple_task_2"));

        WorkflowTask task3 = createWorkflowTask("decision_task_1");
        def.getTasks().add(task3);
        task3.setType(TaskType.DECISION.name());
        task3.getDecisionCases()
                .put(
                        "Case1",
                        Arrays.asList(
                                createWorkflowTask("case_1_task_1"),
                                createWorkflowTask("case_1_task_2")));
        task3.getDecisionCases()
                .put(
                        "Case2",
                        Arrays.asList(
                                createWorkflowTask("case_2_task_1"),
                                createWorkflowTask("case_2_task_2")));
        task3.getDecisionCases()
                .put(
                        "Case3",
                        Collections.singletonList(
                                deciderTask(
                                        "decision_task_2",
                                        toMap("Case31", "case31_task_1", "case_31_task_2"),
                                        Collections.singletonList("case3_def_task"))));
        def.getTasks().add(createWorkflowTask("simple_task_3"));

        // Assertions
        WorkflowTask next = def.getNextTask("simple_task_1");
        assertNotNull(next);
        assertEquals("simple_task_2", next.getTaskReferenceName());

        next = def.getNextTask("simple_task_2");
        assertNotNull(next);
        assertEquals(task3.getTaskReferenceName(), next.getTaskReferenceName());

        next = def.getNextTask("decision_task_1");
        assertNotNull(next);
        assertEquals("simple_task_3", next.getTaskReferenceName());

        next = def.getNextTask("case_1_task_1");
        assertNotNull(next);
        assertEquals("case_1_task_2", next.getTaskReferenceName());

        next = def.getNextTask("case_1_task_2");
        assertNotNull(next);
        assertEquals("simple_task_3", next.getTaskReferenceName());

        next = def.getNextTask("case3_def_task");
        assertNotNull(next);
        assertEquals("simple_task_3", next.getTaskReferenceName());

        next = def.getNextTask("case31_task_1");
        assertNotNull(next);
        assertEquals("case_31_task_2", next.getTaskReferenceName());
    }

    @Test
    public void testGetNextTask_Conditional() {
        String COND_TASK_WF = "COND_TASK_WF";
        List<WorkflowTask> workflowTasks = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            workflowTasks.add(createWorkflowTask("junit_task_" + i));
        }

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(COND_TASK_WF);
        workflowDef.setDescription(COND_TASK_WF);

        WorkflowTask subCaseTask = new WorkflowTask();
        subCaseTask.setType(TaskType.DECISION.name());
        subCaseTask.setCaseValueParam("case2");
        subCaseTask.setName("case2");
        subCaseTask.setTaskReferenceName("case2");
        Map<String, List<WorkflowTask>> dcx = new HashMap<>();
        dcx.put("sc1", workflowTasks.subList(4, 5));
        dcx.put("sc2", workflowTasks.subList(5, 7));
        subCaseTask.setDecisionCases(dcx);

        WorkflowTask caseTask = new WorkflowTask();
        caseTask.setType(TaskType.DECISION.name());
        caseTask.setCaseValueParam("case");
        caseTask.setName("case");
        caseTask.setTaskReferenceName("case");
        Map<String, List<WorkflowTask>> dc = new HashMap<>();
        dc.put("c1", Arrays.asList(workflowTasks.get(0), subCaseTask, workflowTasks.get(1)));
        dc.put("c2", Collections.singletonList(workflowTasks.get(3)));
        caseTask.setDecisionCases(dc);

        workflowDef.getTasks().add(caseTask);
        workflowDef.getTasks().addAll(workflowTasks.subList(8, 9));

        WorkflowTask nextTask = workflowDef.getNextTask("case");
        assertEquals("junit_task_8", nextTask.getTaskReferenceName());

        nextTask = workflowDef.getNextTask("junit_task_8");
        assertNull(nextTask);

        nextTask = workflowDef.getNextTask("junit_task_0");
        assertNotNull(nextTask);
        assertEquals("case2", nextTask.getTaskReferenceName());

        nextTask = workflowDef.getNextTask("case2");
        assertNotNull(nextTask);
        assertEquals("junit_task_1", nextTask.getTaskReferenceName());
    }

    private WorkflowTask createWorkflowTask(String name) {
        WorkflowTask task = new WorkflowTask();
        task.setName(name);
        task.setTaskReferenceName(name);
        return task;
    }

    private WorkflowTask deciderTask(
            String name, Map<String, List<String>> decisions, List<String> defaultTasks) {
        WorkflowTask task = createWorkflowTask(name);
        task.setType(TaskType.DECISION.name());
        decisions.forEach(
                (key, value) -> {
                    List<WorkflowTask> tasks = new LinkedList<>();
                    value.forEach(taskName -> tasks.add(createWorkflowTask(taskName)));
                    task.getDecisionCases().put(key, tasks);
                });
        List<WorkflowTask> tasks = new LinkedList<>();
        defaultTasks.forEach(defaultTask -> tasks.add(createWorkflowTask(defaultTask)));
        task.setDefaultCase(tasks);
        return task;
    }

    private Map<String, List<String>> toMap(String key, String... values) {
        Map<String, List<String>> map = new HashMap<>();
        List<String> vals = Arrays.asList(values);
        map.put(key, vals);
        return map;
    }
}
