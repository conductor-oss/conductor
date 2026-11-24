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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.netflix.conductor.common.metadata.tasks.TaskContinuation;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.NativeState;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * What the engine reads from a native (code-defined) workflow without the service that drives it:
 * whether a workflow is native, which continuation an entry or a task names, and where the lanes of
 * the code are. All of it is read from the execution; nothing here writes or keeps state.
 */
public final class NativeWorkflows {

    private NativeWorkflows() {}

    static final String MAIN_LANE = "main";

    /**
     * Task output key holding how a task failed (FAILED, TIMED_OUT, ...) once its status has become
     * COMPLETED_WITH_ERRORS, so that the code can still be told.
     */
    public static final String OUTPUT_FAILED_STATUS = "conductor.failedStatus";

    static final String HANDLER_LANE = "handlers";

    /** Entry metadata naming the continuation that appended the entry, as {@code lane#n}. */
    public static final String APPENDED_BY = "appendedBy";

    static final String METADATA_LANE = "lane";
    static final String METADATA_CONTINUATION = "continuation";
    static final String SYNTHETIC_SEPARATOR = "|";

    public static boolean isNative(WorkflowDef def) {
        return def != null && def.getMetadata() != null && def.getMetadata().containsKey("native");
    }

    public static boolean isNative(WorkflowModel workflow) {
        return isNative(workflow.getWorkflowDefinition());
    }

    /**
     * The marker of an entry the code paused on ({@code metadata.continuation}: its number in the
     * entry's lane), new and not yet run; null when it names none or a malformed one.
     */
    public static TaskContinuation markerOf(WorkflowTask entry) {
        return idOf(entry) != null ? new TaskContinuation() : null;
    }

    /** Which continuation a marker is: the lane of the code, and its number in the lane. */
    record Id(String lane, int n) {
        boolean isMain() {
            return MAIN_LANE.equals(lane);
        }

        boolean isHandlers() {
            return HANDLER_LANE.equals(lane);
        }

        /** As {@code lane#n}: how entries name the continuation that appended them. */
        String key() {
            return lane + "#" + n;
        }
    }

    /** The workflow's first continuation, which has no task. */
    static final Id FIRST = new Id(MAIN_LANE, 1);

    /** The continuation an entry the code paused on names, or null. */
    static Id idOf(WorkflowTask entry) {
        Object n =
                entry == null || entry.getMetadata() == null
                        ? null
                        : entry.getMetadata().get(METADATA_CONTINUATION);
        String lane = entry == null ? null : laneOf(entry);
        return n instanceof Number number && lane != null ? new Id(lane, number.intValue()) : null;
    }

    /** The continuation a task's marker is: the one its entry names. */
    static Id idOf(TaskModel task) {
        return idOf(task.getWorkflowTask());
    }

    /** The continuation of a lane without a task: the first one, or the current handler run. */
    static Id idOf(WorkflowModel workflow, String lane) {
        return MAIN_LANE.equals(lane)
                ? FIRST
                : new Id(HANDLER_LANE, workflow.getNativeState().getHandlers().getN());
    }

    static String laneOf(WorkflowTask entry) {
        Object lane = entry.getMetadata() == null ? null : entry.getMetadata().get(METADATA_LANE);
        return lane == null ? null : lane.toString();
    }

    /** The id of the continuation message of a lane without a task: the bootstrap or handlers. */
    static String syntheticId(String workflowId, String lane) {
        return workflowId + SYNTHETIC_SEPARATOR + lane;
    }

    public static boolean isSyntheticId(String taskId) {
        return taskId != null && taskId.contains(SYNTHETIC_SEPARATOR);
    }

    static String workflowIdOf(String syntheticId) {
        return syntheticId.substring(0, syntheticId.indexOf(SYNTHETIC_SEPARATOR));
    }

    static String laneOfSynthetic(String syntheticId) {
        return syntheticId.substring(syntheticId.indexOf(SYNTHETIC_SEPARATOR) + 1);
    }

    /** The marker of the bootstrap or handler lane a synthetic id names. */
    public static TaskContinuation syntheticMarkerOf(WorkflowModel workflow, String syntheticId) {
        return syntheticMarker(workflow, laneOfSynthetic(syntheticId));
    }

    static TaskContinuation syntheticMarker(WorkflowModel workflow, String lane) {
        NativeState state = workflow.getNativeState();
        if (state == null) {
            return null;
        }
        return MAIN_LANE.equals(lane) ? state.getBootstrap() : state.getHandlers();
    }

    /** The task type workers poll for the workflow's continuations: one per workflow version. */
    public static String codeType(WorkflowModel workflow) {
        WorkflowDef def = workflow.getWorkflowDefinition();
        return def.getName() + ".v" + def.getVersion() + ".code";
    }

    /**
     * Whether every branch of a live-target JOIN has finished saying what it has to say: its last
     * task is terminal and, if the code paused on it, that continuation was applied and ended the
     * lane. A marker still waiting, runnable or leased means the lane has more to append; an
     * applied marker that did not end the lane means the branch's real last entry is not this one
     * yet in the copy of the definition being read.
     */
    public static boolean lanesSettled(WorkflowModel workflow, List<String> tails) {
        for (String ref : tails) {
            TaskModel task = workflow.getTaskByRefName(ref);
            if (task == null || !task.getStatus().isTerminal()) {
                return false;
            }
            TaskContinuation marker = task.getContinuation();
            if (marker != null && !(marker.isApplied() && marker.isDone())) {
                return false;
            }
        }
        return true;
    }

    /** The main lane's last continuation, and the task it is on (null for the bootstrap). */
    public record LastMain(TaskContinuation marker, TaskModel holder) {}

    /** The marker whose continuation the main lane ran last: on a task, or the bootstrap. */
    public static LastMain lastMain(WorkflowModel workflow) {
        TaskModel best = null;
        for (TaskModel task : latestAttempts(workflow)) {
            TaskContinuation marker = task.getContinuation();
            Id id = marker == null ? null : idOf(task);
            if (id != null && id.isMain() && (best == null || id.n() > idOf(best).n())) {
                best = task;
            }
        }
        if (best != null) {
            return new LastMain(best.getContinuation(), best);
        }
        NativeState state = workflow.getNativeState();
        return state == null || state.getBootstrap() == null
                ? null
                : new LastMain(state.getBootstrap(), null);
    }

    /** Whether any task's lane has code still to run: a continuation not yet applied. */
    public static boolean hasUnappliedLane(WorkflowModel workflow) {
        for (TaskModel task : latestAttempts(workflow)) {
            TaskContinuation marker = task.getContinuation();
            if (marker != null && !marker.isApplied()) {
                return true;
            }
        }
        return false;
    }

    /** The latest attempt of every task reference. */
    static List<TaskModel> latestAttempts(WorkflowModel workflow) {
        Map<String, TaskModel> latest = new LinkedHashMap<>();
        for (TaskModel task : workflow.getTasks()) {
            TaskModel previous = latest.get(task.getReferenceTaskName());
            if (previous == null || task.getSeq() > previous.getSeq()) {
                latest.put(task.getReferenceTaskName(), task);
            }
        }
        return new ArrayList<>(latest.values());
    }

    /**
     * Whether the decider must revisit a native workflow soon: a continuation is queued or leased
     * and may need moving to a live worker's queue, or a task is scheduled and its queue message
     * may have been lost to a storage failure (only the sweeper repairs that).
     */
    public static boolean needsPromptRevisit(WorkflowModel workflow) {
        NativeState state = workflow.getNativeState();
        if (state == null) {
            return false;
        }
        if ((state.getBootstrap() != null && !state.getBootstrap().isApplied())
                || (state.getHandlers() != null && state.getHandlers().isLeased())) {
            return true;
        }
        for (TaskModel task : workflow.getTasks()) {
            TaskContinuation marker = task.getContinuation();
            if (task.getStatus() == TaskModel.Status.SCHEDULED
                    || (marker != null
                            && !marker.isApplied()
                            && (marker.isLeased() || task.getStatus().isTerminal()))) {
                return true;
            }
        }
        return false;
    }
}
