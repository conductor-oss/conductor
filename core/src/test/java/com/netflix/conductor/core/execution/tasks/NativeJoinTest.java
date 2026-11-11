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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskContinuation;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** JOIN with live targets (no joinOn) and the any-of strategy, in native workflows. */
public class NativeJoinTest {

    private final Join join = new Join(new ConductorProperties());

    private static WorkflowTask simple(String ref) {
        WorkflowTask t = new WorkflowTask();
        t.setName(ref);
        t.setTaskReferenceName(ref);
        t.setType(TaskType.TASK_TYPE_SIMPLE);
        t.getMetadata().put("lane", "fork_1." + ref.charAt(0));
        return t;
    }

    private static TaskModel task(String ref, TaskModel.Status status) {
        TaskModel t = new TaskModel();
        t.setReferenceTaskName(ref);
        t.setTaskId("id-" + ref);
        t.setStatus(status);
        t.setWorkflowTask(simple(ref));
        t.addOutput("from", ref);
        return t;
    }

    /** A task the code paused on, with its marker in the given state. */
    private static TaskModel paused(String ref, String markerState, boolean done) {
        TaskModel t = task(ref, TaskModel.Status.COMPLETED);
        TaskContinuation marker = new TaskContinuation("fork_1." + ref.charAt(0), 2);
        marker.setState(markerState);
        marker.setDone(done);
        t.setContinuation(marker);
        return t;
    }

    /** A FORK_JOIN with two branches followed by a JOIN with no targets. */
    private WorkflowModel workflow(
            List<WorkflowTask> b0, List<WorkflowTask> b1, WorkflowTask joinTask) {
        WorkflowTask fork = new WorkflowTask();
        fork.setName("fork");
        fork.setTaskReferenceName("fork");
        fork.setType(TaskType.TASK_TYPE_FORK_JOIN);
        fork.setForkTasks(List.of(new ArrayList<>(b0), new ArrayList<>(b1)));
        WorkflowDef def = new WorkflowDef();
        def.setName("native");
        def.getMetadata().put("native", java.util.Map.of("sdk", "test"));
        def.setTasks(new ArrayList<>(List.of(fork, joinTask)));
        WorkflowModel wf = new WorkflowModel();
        wf.setWorkflowDefinition(def);
        return wf;
    }

    private static WorkflowTask joinDef() {
        WorkflowTask j = new WorkflowTask();
        j.setName("join");
        j.setTaskReferenceName("join");
        j.setType(TaskType.TASK_TYPE_JOIN);
        return j;
    }

    private static TaskModel joinTask(WorkflowTask def, boolean any) {
        TaskModel t = new TaskModel();
        t.setReferenceTaskName("join");
        t.setTaskType(TaskType.TASK_TYPE_JOIN);
        t.setStatus(TaskModel.Status.IN_PROGRESS);
        t.setWorkflowTask(def);
        t.setInputData(new HashMap<>());
        t.getInputData().put("joinOn", List.of());
        if (any) {
            t.getInputData().put(Join.JOIN_STRATEGY, Join.JOIN_STRATEGY_ANY);
        }
        return t;
    }

    @Test
    public void joinsOnTheCurrentLastTaskOfEachBranch() {
        WorkflowTask jd = joinDef();
        WorkflowModel wf = workflow(List.of(simple("a1")), List.of(simple("b1")), jd);
        wf.getTasks().add(task("a1", TaskModel.Status.COMPLETED));
        wf.getTasks().add(task("b1", TaskModel.Status.COMPLETED));
        TaskModel j = joinTask(jd, false);

        // branch a grows after the join was scheduled: the join must wait for a2
        wf.getWorkflowDefinition().insertAfter("a1", List.of(simple("a2")));
        assertFalse(join.execute(wf, j, null));
        assertEquals(TaskModel.Status.IN_PROGRESS, j.getStatus());

        wf.getTasks().add(task("a2", TaskModel.Status.COMPLETED));
        assertTrue(join.execute(wf, j, null));
        assertEquals(TaskModel.Status.COMPLETED, j.getStatus());
        assertEquals("a2", ((java.util.Map<?, ?>) j.getOutputData().get("a2")).get("from"));
    }

    @Test
    public void waitsWhileABranchLaneStillHasAContinuationToRun() {
        // The branch's last task is finished, but the code paused on it and has not yet said
        // what comes next (or is being re-run after a timeout): the join waits.
        WorkflowTask jd = joinDef();
        WorkflowModel wf = workflow(List.of(simple("a1")), List.of(simple("b1")), jd);
        wf.getTasks().add(task("b1", TaskModel.Status.COMPLETED));
        TaskModel j = joinTask(jd, false);

        for (String state :
                List.of(
                        TaskContinuation.WAITING,
                        TaskContinuation.RUNNABLE,
                        TaskContinuation.LEASED)) {
            wf.getTasks().removeIf(t -> t.getReferenceTaskName().equals("a1"));
            wf.getTasks().add(paused("a1", state, false));
            assertFalse(state, join.execute(wf, j, null));
            assertEquals(TaskModel.Status.IN_PROGRESS, j.getStatus());
        }
        // applied but the lane went on: in a stale copy of the definition a1 still looks last
        wf.getTasks().removeIf(t -> t.getReferenceTaskName().equals("a1"));
        wf.getTasks().add(paused("a1", TaskContinuation.APPLIED, false));
        assertFalse(join.execute(wf, j, null));
        // applied and the lane returned: settled
        wf.getTasks().removeIf(t -> t.getReferenceTaskName().equals("a1"));
        wf.getTasks().add(paused("a1", TaskContinuation.APPLIED, true));
        assertTrue(join.execute(wf, j, null));
        assertEquals(TaskModel.Status.COMPLETED, j.getStatus());
    }

    @Test
    public void aBranchEndingInASwitchEndsWithTheLastTaskOfTheTakenCase() {
        WorkflowTask jd = joinDef();
        WorkflowTask sw = new WorkflowTask();
        sw.setName("sw");
        sw.setTaskReferenceName("sw");
        sw.setType(TaskType.TASK_TYPE_SWITCH);
        sw.getMetadata().put("lane", "fork_1.0");
        sw.setDecisionCases(
                java.util.Map.of("x", List.of(simple("a1")), "y", List.of(simple("a2"))));
        WorkflowModel wf = workflow(List.of(sw), List.of(simple("b1")), jd);
        TaskModel chosen = new TaskModel();
        chosen.setReferenceTaskName("sw");
        chosen.setStatus(TaskModel.Status.COMPLETED);
        chosen.setWorkflowTask(sw);
        chosen.addOutput("selectedCase", "x");
        wf.getTasks().add(chosen);
        wf.getTasks().add(task("b1", TaskModel.Status.COMPLETED));
        TaskModel j = joinTask(jd, false);

        // the switch finished at once, but the task in its case has not
        wf.getTasks().add(task("a1", TaskModel.Status.IN_PROGRESS));
        assertFalse(join.execute(wf, j, null));
        assertEquals(TaskModel.Status.IN_PROGRESS, j.getStatus());

        // finished, and the code it paused has not returned yet
        wf.getTasks().removeIf(t -> t.getReferenceTaskName().equals("a1"));
        wf.getTasks().add(paused("a1", TaskContinuation.RUNNABLE, false));
        assertFalse(join.execute(wf, j, null));

        wf.getTasks().removeIf(t -> t.getReferenceTaskName().equals("a1"));
        wf.getTasks().add(paused("a1", TaskContinuation.APPLIED, true));
        assertTrue(join.execute(wf, j, null));
        assertEquals(TaskModel.Status.COMPLETED, j.getStatus());
    }

    @Test
    public void aDetachedJoinCompletesAtOnceWhileItsLanesRun() {
        WorkflowTask jd = joinDef();
        jd.getMetadata().put(Join.DETACHED, true);
        WorkflowModel wf = workflow(List.of(simple("a1")), List.of(simple("b1")), jd);
        wf.getTasks().add(task("a1", TaskModel.Status.IN_PROGRESS));
        TaskModel j = joinTask(jd, false);

        assertTrue(join.execute(wf, j, null));
        assertEquals(TaskModel.Status.COMPLETED, j.getStatus());
    }

    @Test
    public void aWaitingJoinWaitsOnlyForTheLanesItNames() {
        WorkflowTask jd = joinDef();
        WorkflowModel wf = workflow(List.of(simple("a1")), List.of(simple("b1")), joinDef());
        wf.getWorkflowDefinition().getTasks().add(jd);
        jd.setTaskReferenceName("wait");
        jd.getMetadata().put(Join.WAIT_FOR, List.of("fork_1.a"));
        wf.getTasks().add(task("a1", TaskModel.Status.IN_PROGRESS));
        wf.getTasks().add(task("b1", TaskModel.Status.IN_PROGRESS));
        TaskModel j = joinTask(jd, false);

        assertFalse(join.execute(wf, j, null));
        wf.getTasks().removeIf(t -> t.getReferenceTaskName().equals("a1"));
        wf.getTasks().add(task("a1", TaskModel.Status.COMPLETED));
        // b1 is still running, and this join did not ask for it
        assertTrue(join.execute(wf, j, null));
        assertEquals(TaskModel.Status.COMPLETED, j.getStatus());
    }

    @Test
    public void aWaitingJoinWaitsForTheForksItsLaneStarted() {
        WorkflowTask jd = joinDef();
        WorkflowTask inner = new WorkflowTask();
        inner.setName("inner");
        inner.setTaskReferenceName("inner");
        inner.setType(TaskType.TASK_TYPE_FORK_JOIN);
        inner.getMetadata().put("lane", "fork_1.a");
        inner.setForkTasks(List.of(new ArrayList<>(List.of(simple("c1")))));
        WorkflowModel wf = workflow(List.of(inner, simple("a1")), List.of(simple("b1")), joinDef());
        wf.getWorkflowDefinition().getTasks().add(jd);
        jd.setTaskReferenceName("wait");
        jd.getMetadata().put(Join.WAIT_FOR, List.of("fork_1.a"));
        wf.getTasks().add(task("a1", TaskModel.Status.COMPLETED));
        wf.getTasks().add(task("c1", TaskModel.Status.IN_PROGRESS));
        TaskModel j = joinTask(jd, false);

        assertFalse(join.execute(wf, j, null));
        wf.getTasks().removeIf(t -> t.getReferenceTaskName().equals("c1"));
        wf.getTasks().add(task("c1", TaskModel.Status.COMPLETED));
        assertTrue(join.execute(wf, j, null));
    }

    @Test
    public void aJoinWaitsForAForkTheBranchStartedInsideASwitchCase() {
        WorkflowTask inner = new WorkflowTask();
        inner.setName("inner");
        inner.setTaskReferenceName("inner");
        inner.setType(TaskType.TASK_TYPE_FORK_JOIN);
        inner.setForkTasks(List.of(new ArrayList<>(List.of(simple("late")))));
        WorkflowTask sw = new WorkflowTask();
        sw.setName("sw");
        sw.setTaskReferenceName("sw");
        sw.setType(TaskType.TASK_TYPE_SWITCH);
        sw.setDecisionCases(Map.of("1", new ArrayList<>(List.of(inner))));
        WorkflowModel wf = workflow(List.of(sw, simple("a1")), List.of(simple("b1")), joinDef());
        WorkflowTask jd = wf.getWorkflowDefinition().getTasks().get(1);
        TaskModel taken = task("sw", TaskModel.Status.COMPLETED);
        taken.addOutput("selectedCase", "1");
        wf.getTasks().add(taken);
        wf.getTasks().add(task("a1", TaskModel.Status.COMPLETED));
        wf.getTasks().add(task("b1", TaskModel.Status.COMPLETED));
        wf.getTasks().add(task("late", TaskModel.Status.IN_PROGRESS));
        TaskModel j = joinTask(jd, false);

        assertFalse(join.execute(wf, j, null));
        wf.getTasks().removeIf(t -> t.getReferenceTaskName().equals("late"));
        wf.getTasks().add(task("late", TaskModel.Status.COMPLETED));
        assertTrue(join.execute(wf, j, null));
    }

    @Test
    public void aWaitingJoinWaitsWhenItsLaneIsNotInTheDefinitionYet() {
        WorkflowTask jd = joinDef();
        WorkflowModel wf = workflow(List.of(simple("a1")), List.of(simple("b1")), joinDef());
        wf.getWorkflowDefinition().getTasks().add(jd);
        jd.setTaskReferenceName("wait");
        jd.getMetadata().put(Join.WAIT_FOR, List.of("fork_9.0"));
        assertFalse(join.execute(wf, joinTask(jd, false), null));
    }

    @Test
    public void aNativeJoinKeepsPollingAtItsIntervalAfterTheFastStart() {
        TaskModel nativeJoin = joinTask(joinDef(), false);
        nativeJoin.getWorkflowTask().getMetadata().put("lane", "main");
        nativeJoin.setPollCount(1000);
        assertEquals(1L, (long) join.getEvaluationOffset(nativeJoin, 30).orElseThrow());

        TaskModel plainJoin = joinTask(joinDef(), false);
        plainJoin.setPollCount(1000);
        assertEquals(30L, (long) join.getEvaluationOffset(plainJoin, 30).orElseThrow());
    }

    @Test
    public void anyOfCompletesOnTheFirstSuccess() {
        WorkflowTask jd = joinDef();
        WorkflowModel wf = workflow(List.of(simple("a1")), List.of(simple("b1")), jd);
        wf.getTasks().add(task("a1", TaskModel.Status.IN_PROGRESS));
        wf.getTasks().add(task("b1", TaskModel.Status.COMPLETED));
        TaskModel j = joinTask(jd, true);

        assertTrue(join.execute(wf, j, null));
        assertEquals(TaskModel.Status.COMPLETED, j.getStatus());
        assertEquals("b1", j.getOutputData().get("winner"));
    }

    @Test
    public void anyOfFailsWhenEveryBranchFailed() {
        WorkflowTask jd = joinDef();
        WorkflowModel wf = workflow(List.of(simple("a1")), List.of(simple("b1")), jd);
        wf.getTasks().add(task("a1", TaskModel.Status.FAILED));
        TaskModel b1 = task("b1", TaskModel.Status.IN_PROGRESS);
        wf.getTasks().add(b1);
        TaskModel j = joinTask(jd, true);

        assertFalse(join.execute(wf, j, null));
        b1.setStatus(TaskModel.Status.FAILED);
        assertTrue(join.execute(wf, j, null));
        assertEquals(TaskModel.Status.FAILED, j.getStatus());
    }

    @Test
    public void waitsWhenItsForkIsNotInTheDefinitionYet() {
        // evaluated from a copy loaded before the decide that appended the fork was saved
        WorkflowTask jd = joinDef();
        WorkflowDef stale = new WorkflowDef();
        stale.setName("native");
        stale.getMetadata().put("native", java.util.Map.of("sdk", "test"));
        stale.setTasks(new ArrayList<>(List.of(simple("before"))));
        WorkflowModel wf = new WorkflowModel();
        wf.setWorkflowDefinition(stale);
        TaskModel j = joinTask(jd, false);
        assertFalse(join.execute(wf, j, null));
        assertEquals(TaskModel.Status.IN_PROGRESS, j.getStatus());
    }
}
