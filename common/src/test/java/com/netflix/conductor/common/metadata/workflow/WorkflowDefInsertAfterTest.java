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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorkflowDefInsertAfterTest {

    @Test
    public void insertsAfterATopLevelTask() {
        WorkflowDef def = def(task("a"), task("b"));
        assertTrue(def.insertAfter("a", List.of(task("x"), task("y"))));
        assertEquals(List.of("a", "x", "y", "b"), refs(def.getTasks()));
        assertTrue(def.containsTaskRef("y"));
    }

    @Test
    public void insertsInsideAForkBranch() {
        WorkflowTask fork = fork("f", List.of(task("b0")), List.of(task("b1")));
        WorkflowDef def = def(fork, task("after"));
        assertTrue(def.insertAfter("b1", List.of(task("b1x"))));
        assertEquals(List.of("b0"), refs(fork.getForkTasks().get(0)));
        assertEquals(List.of("b1", "b1x"), refs(fork.getForkTasks().get(1)));
        assertEquals(List.of("f", "after"), refs(def.getTasks()));
        assertEquals(5, def.collectTasks().size());
    }

    @Test
    public void insertsInsideADecisionCaseAndTheDefaultCase() {
        WorkflowTask sw = new WorkflowTask();
        sw.setName("sw");
        sw.setTaskReferenceName("sw");
        sw.setType(TaskType.TASK_TYPE_SWITCH);
        Map<String, List<WorkflowTask>> cases = new LinkedHashMap<>();
        cases.put("0", new ArrayList<>(List.of(task("c0"))));
        sw.setDecisionCases(cases);
        sw.setDefaultCase(new ArrayList<>(List.of(task("d0"))));
        WorkflowDef def = def(sw);

        assertTrue(def.insertAfter("c0", List.of(task("c1"))));
        assertTrue(def.insertAfter("d0", List.of(task("d1"))));
        assertEquals(List.of("c0", "c1"), refs(sw.getDecisionCases().get("0")));
        assertEquals(List.of("d0", "d1"), refs(sw.getDefaultCase()));
    }

    @Test
    public void insertsInsideALoopBody() {
        WorkflowTask loop = new WorkflowTask();
        loop.setName("loop");
        loop.setTaskReferenceName("loop");
        loop.setType(TaskType.TASK_TYPE_DO_WHILE);
        loop.setLoopOver(new ArrayList<>(List.of(task("l0"))));
        WorkflowDef def = def(loop);
        assertTrue(def.insertAfter("l0", List.of(task("l1"))));
        assertEquals(List.of("l0", "l1"), refs(loop.getLoopOver()));
    }

    @Test
    public void returnsFalseAndChangesNothingWhenTheReferenceIsUnknown() {
        WorkflowDef def = def(task("a"));
        assertFalse(def.insertAfter("nope", List.of(task("x"))));
        assertEquals(List.of("a"), refs(def.getTasks()));
        assertFalse(def.containsTaskRef("x"));
    }

    @Test
    public void removesTasksAtAnyDepth() {
        WorkflowTask fork = fork("f", List.of(task("b0"), task("b0x")), List.of(task("b1")));
        WorkflowDef def = def(task("a"), fork, task("z"));
        assertEquals(2, def.removeAll(Set.of("b0x", "z")));
        assertEquals(List.of("a", "f"), refs(def.getTasks()));
        assertEquals(List.of("b0"), refs(fork.getForkTasks().get(0)));
        assertEquals(1, def.removeAll(Set.of("f")));
        assertEquals(List.of("a"), refs(def.getTasks()));
    }

    private static WorkflowDef def(WorkflowTask... tasks) {
        WorkflowDef def = new WorkflowDef();
        def.setName("test");
        def.setTasks(new ArrayList<>(List.of(tasks)));
        return def;
    }

    private static WorkflowTask task(String ref) {
        WorkflowTask task = new WorkflowTask();
        task.setName(ref);
        task.setTaskReferenceName(ref);
        task.setType(TaskType.TASK_TYPE_SIMPLE);
        return task;
    }

    @SafeVarargs
    private static WorkflowTask fork(String ref, List<WorkflowTask>... branches) {
        WorkflowTask fork = new WorkflowTask();
        fork.setName(ref);
        fork.setTaskReferenceName(ref);
        fork.setType(TaskType.TASK_TYPE_FORK_JOIN);
        List<List<WorkflowTask>> forkTasks = new ArrayList<>();
        for (List<WorkflowTask> branch : branches) {
            forkTasks.add(new ArrayList<>(branch));
        }
        fork.setForkTasks(forkTasks);
        return fork;
    }

    private static List<String> refs(List<WorkflowTask> tasks) {
        return tasks.stream().map(WorkflowTask::getTaskReferenceName).toList();
    }
}
