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
package com.netflix.conductor.core.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskContinuation;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.NativeState;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.execution.NativeWorkflows.LastMain;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.core.execution.NativeWorkflows.APPENDED_BY;
import static com.netflix.conductor.core.execution.NativeWorkflows.idOf;
import static com.netflix.conductor.core.execution.NativeWorkflows.lastMain;
import static com.netflix.conductor.core.execution.NativeWorkflows.latestAttempts;

/**
 * What a rerun, a retry or a restart of a native execution undoes, so that the code runs again from
 * the right place: the entries the code appended go from the execution's definition, and the
 * continuations that must run again wait again. In memory, under the workflow's lock; the caller
 * saves what changed.
 */
@Component
public class NativeRerunPlanner {

    /**
     * What a rerun from {@code rerunFrom} undoes, decided before anything is removed. The code
     * after the task may have seen its result, so every continuation applied once the task had
     * ended runs again: the entries those continuations appended go, with their tasks. The tasks
     * that follow the task in its own list go too and are scheduled again from their entries, as a
     * rerun of a standard workflow does. What another lane started before the task ended stays.
     *
     * @param dropTaskIds the tasks the rerun removes
     * @param dropEntries the definition entries the rerun removes, by reference name
     * @param resetMarkers the continuations that run again, as {@code lane#n}
     */
    public record RerunPlan(
            Set<String> dropTaskIds, Set<String> dropEntries, Set<String> resetMarkers) {}

    public RerunPlan planRerun(WorkflowModel workflow, TaskModel rerunFrom) {
        WorkflowDef def = workflow.getWorkflowDefinition();
        // what the code did after the task ended may depend on it; a task that never ended had
        // nothing come after it in its lane
        long cut = rerunFrom.getEndTime() > 0 ? rerunFrom.getEndTime() : Long.MAX_VALUE;
        Set<String> resetMarkers = new HashSet<>();
        for (TaskModel task : workflow.getTasks()) {
            TaskContinuation marker = task.getContinuation();
            if (marker != null && marker.isApplied() && marker.getEndTime() >= cut) {
                resetMarkers.add(idOf(task).key());
            }
        }
        Set<String> dropEntries = new HashSet<>();
        for (WorkflowTask entry : def.collectTasks()) {
            if (resetMarkers.contains(entry.getMetadata().get(APPENDED_BY))) {
                entry.collectTasks().forEach(t -> dropEntries.add(t.getTaskReferenceName()));
            }
        }
        String ref = TaskUtils.removeIterationFromTaskRefName(rerunFrom.getReferenceTaskName());
        Set<String> dropRefs = new HashSet<>(dropEntries);
        dropRefs.addAll(followingInItsList(def, ref));
        // in a loop holding the task: the rest of its iteration and the later iterations, which
        // the reopened loop schedules again
        for (WorkflowTask loop : def.collectTasks()) {
            if (TaskType.DO_WHILE.name().equals(loop.getType())
                    && !loop.getTaskReferenceName().equals(ref)
                    && loop.has(ref)) {
                for (WorkflowTask body : loop.getLoopOver()) {
                    body.collectTasks().forEach(t -> dropRefs.add(t.getTaskReferenceName()));
                }
            }
        }
        Set<String> dropTaskIds = new HashSet<>();
        for (TaskModel task : workflow.getTasks()) {
            if (task.getSeq() > rerunFrom.getSeq()
                    && dropRefs.contains(
                            TaskUtils.removeIterationFromTaskRefName(
                                    task.getReferenceTaskName()))) {
                dropTaskIds.add(task.getTaskId());
            }
        }
        return new RerunPlan(dropTaskIds, dropEntries, resetMarkers);
    }

    /**
     * After a rerun removed the tasks of its plan: the entries the plan undoes go, and the rerun
     * task's marker and the markers of the continuations that run again wait again, so each runs
     * once more when the decider evaluates its task. A JOIN waits again for a fork holding the
     * rerun task, and when the code after it runs again (the lanes it waited for may run again).
     * Returns those JOINs: the caller saves them and queues them only after the rerun is saved.
     */
    public List<TaskModel> adjustRerun(
            WorkflowModel workflow, TaskModel rerunFrom, RerunPlan plan, List<TaskModel> touched) {
        WorkflowDef def = workflow.getWorkflowDefinition();
        NativeContinuationService.unpin(
                workflow); // continuations run again: parked code is ahead of the execution
        NativeDefinitionEdits.removeAll(def, plan.dropEntries());
        if (rerunFrom.getContinuation() != null) {
            rerunFrom.setContinuation(rerunFrom.getContinuation().reset());
        }
        for (TaskModel task : latestAttempts(workflow)) {
            TaskContinuation marker = task.getContinuation();
            if (marker != null
                    && task != rerunFrom
                    && plan.resetMarkers().contains(idOf(task).key())) {
                task.setContinuation(marker.reset());
                touched.add(task);
            }
        }
        NativeState state = workflow.getNativeState();
        if (state != null && def.getTasks().isEmpty()) {
            // the same id, <workflowId>|main: the fresh marker counts the rerun as a retry
            state.setBootstrap(
                    state.getBootstrap() != null
                            ? state.getBootstrap().reset()
                            : new TaskContinuation());
        }
        List<TaskModel> runAgain = new ArrayList<>(touched);
        List<TaskModel> joins = new ArrayList<>();
        for (TaskModel task : workflow.getTasks()) {
            if (TaskType.TASK_TYPE_JOIN.equals(task.getTaskType())
                    && task.getStatus() == TaskModel.Status.COMPLETED
                    && task.getWorkflowTask() != null) {
                WorkflowTask fork =
                        NativeDefinitionEdits.previousTask(def, task.getReferenceTaskName());
                if (runAgain.contains(task)
                        || (fork != null
                                && TaskType.FORK_JOIN.name().equals(fork.getType())
                                && fork.has(rerunFrom.getReferenceTaskName()))) {
                    task.setStatus(TaskModel.Status.IN_PROGRESS);
                    task.setExecuted(false);
                    task.setEndTime(0);
                    task.clearOutput();
                    touched.add(task);
                    joins.add(task);
                }
            }
        }
        workflow.setOutput(new HashMap<>());
        return joins;
    }

    /**
     * Before a retry runs the code again, removes from the definition the TERMINATE entry that
     * marks where the code threw, and everything else the continuation that threw drew: the
     * previews of what it would have called next, and an if it went past or a lane it started. The
     * workflow failed in that same completion, so none of it has a task, and the code draws it
     * again. Should one have a task, only the mark and its previews go.
     */
    public void undoThrow(WorkflowModel workflow, TaskModel thrown) {
        // the continuation that threw is the one that drew the mark
        WorkflowTask mark =
                workflow.getWorkflowDefinition().getTaskByRefName(thrown.getReferenceTaskName());
        Object key = mark == null ? null : mark.getMetadata().get(APPENDED_BY);
        Set<String> refs = new HashSet<>();
        for (WorkflowTask entry : workflow.getWorkflowDefinition().collectTasks()) {
            if (key != null && key.equals(entry.getMetadata().get(APPENDED_BY))) {
                entry.collectTasks().forEach(t -> refs.add(t.getTaskReferenceName()));
            }
        }
        if (workflow.getTasks().stream().anyMatch(t -> refs.contains(t.getReferenceTaskName()))) {
            refs.clear();
        }
        for (WorkflowTask entry : workflow.getWorkflowDefinition().collectTasks()) {
            if (thrown.getReferenceTaskName().equals(entry.getMetadata().get("previewAfter"))) {
                refs.add(entry.getTaskReferenceName());
            }
        }
        refs.add(thrown.getReferenceTaskName());
        NativeDefinitionEdits.removeAll(workflow.getWorkflowDefinition(), refs);
    }

    /**
     * Retry when the code itself failed (it threw, or diverged from its recording): the main lane's
     * last continuation runs again, and the slot is free for it. Returns it, or null when there is
     * none; the caller saves the workflow, then its holder, then pushes the decider.
     */
    public LastMain rearmLastMain(WorkflowModel workflow) {
        LastMain last = lastMain(workflow);
        if (last == null) {
            return null;
        }
        NativeContinuationService.unpin(
                workflow); // the continuation runs again: parked code has already run it
        TaskContinuation marker = last.marker();
        marker.setEndTime(0);
        marker.setLeased(false);
        marker.setRetryCount(marker.getRetryCount() + 1);
        marker.setDone(false);
        return last;
    }

    /**
     * The entries after {@code ref} in the flow of its own list, at any depth, up to the JOIN of
     * the fork or the loop that holds it: what the decider schedules again after the task.
     */
    private static Set<String> followingInItsList(WorkflowDef def, String ref) {
        Set<String> refs = new HashSet<>();
        WorkflowTask next = def.getNextTask(ref);
        while (next != null && !refs.contains(next.getTaskReferenceName())) {
            if (TaskType.DO_WHILE.name().equals(next.getType()) && next.has(ref)) {
                break;
            }
            if (TaskType.TASK_TYPE_JOIN.equals(next.getType())) {
                WorkflowTask fork =
                        NativeDefinitionEdits.previousTask(def, next.getTaskReferenceName());
                if (fork != null && fork.has(ref)) {
                    break;
                }
            }
            next.collectTasks().forEach(t -> refs.add(t.getTaskReferenceName()));
            next = def.getNextTask(next.getTaskReferenceName());
        }
        return refs;
    }
}
