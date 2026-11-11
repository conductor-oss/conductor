/*
 * Copyright 2022 Conductor Authors.
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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.netflix.conductor.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.NativeContinuationService;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_JOIN;

@Component(TASK_TYPE_JOIN)
public class Join extends WorkflowSystemTask {

    @VisibleForTesting static final double EVALUATION_OFFSET_BASE = 1.2;

    /**
     * Keys propagated from fork-branch outputs into the JOIN output for Conductor-Agents agent
     * executions. Only these are copied so the JOIN payload stays small for multi-agent merges —
     * full fork outputs are read directly from the individual tool tasks by the agent message
     * builder, so duplicating them in JOIN is unnecessary. This mirrors the Conductor-Agents JOIN
     * task; for non-agent workflows the full fork output is copied as before.
     */
    private static final Set<String> AGENT_PROPAGATED_KEYS = Set.of("_state_updates", "state");

    private final ConductorProperties properties;

    /** JOIN input that selects the join strategy; {@code ANY} completes on the first success. */
    public static final String JOIN_STRATEGY = "joinStrategy";

    public static final String JOIN_STRATEGY_ANY = "ANY";

    /** Native JOIN metadata: completes at once, leaving its fork's lanes running on their own. */
    public static final String DETACHED = "detached";

    /** Native JOIN metadata: the ids of the lanes to wait for, instead of the fork before it. */
    public static final String WAIT_FOR = "waitFor";

    public Join(ConductorProperties properties) {
        super(TASK_TYPE_JOIN);
        this.properties = properties;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        StringBuilder failureReason = new StringBuilder();
        StringBuilder optionalTaskFailures = new StringBuilder();
        boolean agentExecution = isAgentExecution(workflow);
        if (isNativeWorkflow(workflow)
                && task.getWorkflowTask() != null
                && Boolean.TRUE.equals(task.getWorkflowTask().getMetadata().get(DETACHED))) {
            // lanes nothing waits for yet: the code goes on while they run
            task.setStatus(TaskModel.Status.COMPLETED);
            return true;
        }
        List<String> joinOn = (List<String>) task.getInputData().get("joinOn");
        if ((joinOn == null || joinOn.isEmpty())
                && !task.isLoopOverTask()
                && isNativeWorkflow(workflow)) {
            // Live targets: a JOIN after a FORK_JOIN that names no targets joins on whatever each
            // branch currently ends with. Native workflows grow fork branches at run time, so the
            // targets cannot be fixed when the JOIN is scheduled.
            joinOn = liveJoinTargets(workflow, task);
            if (joinOn == null) {
                // The JOIN can be evaluated from a copy of the workflow loaded before the decide
                // that appended its fork was saved; the fork is then not in the definition yet.
                // Wait for the next evaluation rather than completing with no targets.
                return false;
            }
            if (!JOIN_STRATEGY_ANY.equals(task.getInputData().get(JOIN_STRATEGY))
                    && !NativeContinuationService.lanesSettled(workflow, joinOn)) {
                // any-of checks each candidate on its own below: a lane that is still running
                // must not hold back a branch that already returned
                return false;
            }
        }
        if (joinOn == null) {
            joinOn = List.of();
        }
        if (JOIN_STRATEGY_ANY.equals(task.getInputData().get(JOIN_STRATEGY))) {
            return executeAnyOf(workflow, task, joinOn);
        }
        if (task.isLoopOverTask()) {
            // If join is part of loop over task, wait for specific iteration to get complete
            joinOn =
                    joinOn.stream()
                            .map(name -> TaskUtils.appendIteration(name, task.getIteration()))
                            .toList();
        }

        boolean allTasksTerminal =
                joinOn.stream().map(ref -> awaited(workflow, ref)).allMatch(Join::isSettled);

        for (String joinOnRef : joinOn) {
            TaskModel forkedTask = awaited(workflow, joinOnRef);
            if (!isSettled(forkedTask)) {
                // Not scheduled yet, still running, or failed and not yet decided on: wait
                continue;
            }

            TaskModel.Status taskStatus = forkedTask.getStatus();

            // Only add to task output if it's not empty. The output is the named task's own,
            // also when it is a switch the JOIN waited past.
            TaskModel namedTask = workflow.getTaskByRefName(joinOnRef);
            if (!namedTask.getOutputData().isEmpty()) {
                Map<String, Object> forkOutput = namedTask.getOutputData();
                if (agentExecution) {
                    forkOutput = prepareAgentOutput(namedTask);
                }
                task.addOutput(joinOnRef, forkOutput);
            }

            // Determine if the join task fails immediately due to a non-optional, non-permissive
            // task failure,
            // or waits for all tasks to be terminal if the failed task is permissive.
            var isJoinFailure =
                    !taskStatus.isSuccessful()
                            && !forkedTask.getWorkflowTask().isOptional()
                            && (!forkedTask.getWorkflowTask().isPermissive() || allTasksTerminal);
            if (isJoinFailure) {
                final String failureReasons =
                        joinOn.stream()
                                .map(ref -> awaited(workflow, ref))
                                .filter(Objects::nonNull)
                                .filter(t -> !t.getStatus().isSuccessful())
                                .map(TaskModel::getReasonForIncompletion)
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining(" "));
                failureReason.append(failureReasons);
                task.setReasonForIncompletion(failureReason.toString());
                // A canceled forked task (e.g. a manually terminated sub-workflow) is a
                // cancellation, not a failure: surface CANCELED so the decider maps the
                // workflow to TERMINATED. Any genuinely failed non-optional forked task
                // still wins and fails the join.
                boolean hasNonCanceledFailure =
                        joinOn.stream()
                                .map(ref -> awaited(workflow, ref))
                                .filter(Objects::nonNull)
                                .filter(t -> t.getStatus().isTerminal())
                                .filter(t -> !t.getStatus().isSuccessful())
                                .filter(t -> !t.getWorkflowTask().isOptional())
                                .anyMatch(t -> t.getStatus() != TaskModel.Status.CANCELED);
                task.setStatus(
                        hasNonCanceledFailure
                                ? TaskModel.Status.FAILED
                                : TaskModel.Status.CANCELED);
                return true;
            }

            // check for optional task failures
            if (forkedTask.getWorkflowTask().isOptional()
                    && taskStatus == TaskModel.Status.COMPLETED_WITH_ERRORS) {
                optionalTaskFailures
                        .append(
                                String.format(
                                        "%s/%s",
                                        forkedTask.getTaskDefName(), forkedTask.getTaskId()))
                        .append(" ");
            }
        }

        // Finalize the join task's status based on the outcomes of all referenced tasks.
        if (allTasksTerminal) {
            if (!optionalTaskFailures.isEmpty()) {
                task.setStatus(TaskModel.Status.COMPLETED_WITH_ERRORS);
                optionalTaskFailures.append("completed with errors");
                task.setReasonForIncompletion(optionalTaskFailures.toString());
            } else {
                task.setStatus(TaskModel.Status.COMPLETED);
            }
            return true;
        }

        // Task execution not complete, waiting on more tasks to reach terminal state.
        return false;
    }

    /**
     * The task the JOIN waits for when it names {@code ref}. A switch completes as soon as it picks
     * a case, so a JOIN naming one waits for the last task of the case it took, following a switch
     * that ends that case too. An empty case leaves the switch itself; a case task not scheduled
     * yet is {@code null}, which the JOIN waits for.
     */
    private static TaskModel awaited(WorkflowModel workflow, String ref) {
        TaskModel task = workflow.getTaskByRefName(ref);
        while (task != null
                && (TaskType.TASK_TYPE_SWITCH.equals(task.getTaskType())
                        || TaskType.TASK_TYPE_DECISION.equals(task.getTaskType()))
                && task.getStatus() == TaskModel.Status.COMPLETED
                && task.getWorkflowTask() != null
                && task.getInputData().get("case") != null) {
            WorkflowTask entry = task.getWorkflowTask();
            List<WorkflowTask> caseTasks =
                    entry.getDecisionCases().get(String.valueOf(task.getInputData().get("case")));
            if (caseTasks == null) {
                caseTasks = entry.getDefaultCase();
            }
            if (caseTasks == null || caseTasks.isEmpty()) {
                return task;
            }
            String last = caseTasks.get(caseTasks.size() - 1).getTaskReferenceName();
            task =
                    workflow.getTaskByRefName(
                            task.isLoopOverTask()
                                    ? TaskUtils.appendIteration(last, task.getIteration())
                                    : last);
        }
        return task;
    }

    /**
     * Whether a task the JOIN waits for is over. A failed attempt is over only once the decider has
     * looked at it: it may still be retried. The JOIN can run on a copy of the workflow loaded
     * after the attempt failed and before the decider scheduled its retry, and must not fail the
     * workflow on the attempt the retry replaces.
     */
    private static boolean isSettled(TaskModel task) {
        return task != null
                && task.getStatus().isTerminal()
                && (task.getStatus().isSuccessful() || task.isExecuted() || task.isRetried());
    }

    private static boolean isAgentExecution(WorkflowModel workflow) {
        WorkflowDef def = workflow.getWorkflowDefinition();
        return def != null && def.isAgent();
    }

    /** True when the fork branch carries state intended for a multi-agent state merge. */
    private static boolean carriesAgentState(Map<String, Object> output) {
        return output != null && AGENT_PROPAGATED_KEYS.stream().anyMatch(output::containsKey);
    }

    /**
     * Shapes a fork branch's output for a Conductor-Agents JOIN:
     *
     * <ul>
     *   <li>Branches marked with {@code _agent_tool_name} keep their tool identity: the full output
     *       is wrapped under {@code _agent_tool_output}. Identity takes precedence because a tool
     *       output may also contain state updates.
     *   <li>State-bearing branches (any {@link #AGENT_PROPAGATED_KEYS}) are compacted to just the
     *       merge keys to keep the JOIN payload small.
     *   <li>Unmarked outputs (e.g. MCP or HTTP tool results) pass through untouched for the ReAct
     *       state merge.
     * </ul>
     *
     * <p>Constructed maps are unmodifiable; the pass-through branch intentionally returns the
     * task's live output map to preserve default JOIN behavior.
     */
    private static Map<String, Object> prepareAgentOutput(TaskModel forkedTask) {
        Map<String, Object> output = forkedTask.getOutputData();

        Object agentToolName = forkedTask.getInputData().get("_agent_tool_name");
        if (agentToolName != null) {
            // LinkedHashMap (not Map.of): tool outputs may legitimately contain null values.
            Map<String, Object> toolOutput = new LinkedHashMap<>();
            toolOutput.put("_agent_tool_name", agentToolName);
            toolOutput.put("_agent_tool_output", output);
            return Collections.unmodifiableMap(toolOutput);
        }

        if (carriesAgentState(output)) {
            return compactAgentOutput(output);
        }
        return output;
    }

    private static Map<String, Object> compactAgentOutput(Map<String, Object> output) {
        Map<String, Object> compact = new LinkedHashMap<>();
        if (output != null) {
            for (String key : AGENT_PROPAGATED_KEYS) {
                if (output.containsKey(key)) {
                    compact.put(key, output.get(key));
                }
            }
        }
        return Collections.unmodifiableMap(compact);
    }

    /**
     * The reference name of the last task of each branch of the FORK_JOIN that precedes this JOIN
     * in the execution's definition, read at evaluation time; null when that fork is not in the
     * definition yet.
     */
    private List<String> liveJoinTargets(WorkflowModel workflow, TaskModel joinTask) {
        if (joinTask.getWorkflowTask() == null || workflow.getWorkflowDefinition() == null) {
            return List.of();
        }
        String joinRef = joinTask.getWorkflowTask().getTaskReferenceName();
        if (!workflow.getWorkflowDefinition().containsTaskRef(joinRef)) {
            // stale copy: the fragment that holds this JOIN is not in the definition yet
            return null;
        }
        if (joinTask.getWorkflowTask().getMetadata().get(WAIT_FOR) instanceof List<?> lanes) {
            return targetsOfLanes(workflow, lanes);
        }
        WorkflowTask fork =
                workflow.getWorkflowDefinition()
                        .getPreviousTask(joinTask.getWorkflowTask().getTaskReferenceName());
        if (fork == null || !TaskType.FORK_JOIN.name().equals(fork.getType())) {
            return List.of();
        }
        List<String> targets = new ArrayList<>();
        for (List<WorkflowTask> branch : fork.getForkTasks()) {
            addBranchTargets(workflow, branch, targets);
        }
        return targets;
    }

    /** What the named lanes end with; null when one is not in this copy of the definition yet. */
    private List<String> targetsOfLanes(WorkflowModel workflow, List<?> lanes) {
        List<String> targets = new ArrayList<>();
        Set<Object> found = new HashSet<>();
        for (WorkflowTask task : workflow.getWorkflowDefinition().collectTasks()) {
            if (TaskType.FORK_JOIN.name().equals(task.getType())) {
                for (List<WorkflowTask> branch : task.getForkTasks()) {
                    Object lane = branch.isEmpty() ? null : branch.get(0).getMetadata().get("lane");
                    if (lane != null && lanes.contains(lane)) {
                        found.add(lane);
                        addBranchTargets(workflow, branch, targets);
                    }
                }
            }
        }
        return found.size() == lanes.size() ? targets : null;
    }

    /** What a branch ends with, and what any fork it started ends with. */
    private void addBranchTargets(
            WorkflowModel workflow, List<WorkflowTask> branch, List<String> targets) {
        if (branch.isEmpty()) {
            return;
        }
        for (WorkflowTask task : branch) {
            addForksStartedBy(workflow, task, targets);
        }
        String tail = lastTaskRef(workflow, branch);
        if (!targets.contains(tail)) {
            targets.add(tail);
        }
    }

    /** What the forks an entry started end with: its own, and any in the switch case it took. */
    private void addForksStartedBy(
            WorkflowModel workflow, WorkflowTask task, List<String> targets) {
        for (List<WorkflowTask> inner : task.getForkTasks()) {
            addBranchTargets(workflow, inner, targets);
        }
        for (WorkflowTask inCase : takenCase(workflow, task)) {
            addForksStartedBy(workflow, inCase, targets);
        }
    }

    /** The tasks of the case a SWITCH took; empty until it has chosen, or for any other task. */
    private List<WorkflowTask> takenCase(WorkflowModel workflow, WorkflowTask task) {
        if (!TaskType.SWITCH.name().equals(task.getType())) {
            return List.of();
        }
        TaskModel taken = workflow.getTaskByRefName(task.getTaskReferenceName());
        if (taken == null || !taken.getStatus().isTerminal()) {
            return List.of();
        }
        Object selected = taken.getOutputData().get("selectedCase");
        List<WorkflowTask> caseTasks =
                task.getDecisionCases()
                        .getOrDefault(String.valueOf(selected), task.getDefaultCase());
        return caseTasks == null ? List.of() : caseTasks;
    }

    /**
     * The task a branch currently ends with. A SWITCH finishes as soon as it has chosen, so a list
     * that ends in one really ends with the last task of the case it took.
     */
    private String lastTaskRef(WorkflowModel workflow, List<WorkflowTask> tasks) {
        WorkflowTask last = tasks.get(tasks.size() - 1);
        List<WorkflowTask> caseTasks = takenCase(workflow, last);
        return caseTasks.isEmpty() ? last.getTaskReferenceName() : lastTaskRef(workflow, caseTasks);
    }

    /** Live targets apply to native (code-first) workflows; others keep the static joinOn. */
    private static boolean isNativeWorkflow(WorkflowModel workflow) {
        return workflow.getWorkflowDefinition() != null
                && workflow.getWorkflowDefinition().getMetadata() != null
                && workflow.getWorkflowDefinition().getMetadata().containsKey("native");
    }

    /**
     * Any-of join: completes with the output of the first target that finished successfully, and
     * fails when every target is terminal and none succeeded. The other branches are not cancelled;
     * they keep running and are cancelled with the rest of the workflow when it ends.
     */
    private boolean executeAnyOf(WorkflowModel workflow, TaskModel task, List<String> joinOn) {
        boolean allTerminal = true;
        StringBuilder reasons = new StringBuilder();
        for (String ref : joinOn) {
            TaskModel target = workflow.getTaskByRefName(ref);
            if (target == null || !target.getStatus().isTerminal()) {
                allTerminal = false;
                continue;
            }
            if (target.getStatus().isSuccessful()
                    && !target.getOutputData()
                            .containsKey(NativeContinuationService.OUTPUT_FAILED_STATUS)) {
                if (!NativeContinuationService.lanesSettled(workflow, List.of(ref))) {
                    allTerminal = false; // the branch's code has not returned yet
                    continue;
                }
                task.addOutput(ref, target.getOutputData());
                task.addOutput("winner", ref);
                task.setStatus(TaskModel.Status.COMPLETED);
                return true;
            }
            if (target.getReasonForIncompletion() != null) {
                reasons.append(target.getReasonForIncompletion()).append(' ');
            }
        }
        if (allTerminal && !joinOn.isEmpty()) {
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion(
                    "no branch of the any-of join succeeded: " + reasons.toString().trim());
            return true;
        }
        return false;
    }

    /**
     * Evaluating a JOIN only reads the state of its targets, so running it again is harmless and a
     * JOIN whose server died mid-evaluation is picked up again in about a minute.
     */
    @Override
    public boolean isExecuteIdempotent() {
        return true;
    }

    @Override
    public Optional<Long> getEvaluationOffset(TaskModel taskModel, long maxOffset) {
        // Check if joinMode is set to SYNC — read directly from the workflow task definition
        // rather than from input data so the value is never duplicated into the task's payload.
        WorkflowTask workflowTask = taskModel.getWorkflowTask();
        if (workflowTask != null && WorkflowTask.JoinMode.SYNC == workflowTask.getJoinMode()) {
            // Synchronous mode: evaluate immediately every time (no backoff)
            return Optional.of(0L);
        }

        // Asynchronous mode (default): use exponential backoff
        int pollCount = taskModel.getPollCount();
        // Assuming pollInterval = 50ms and evaluationOffsetThreshold = 200 this will cause
        // a JOIN task to be evaluated continuously during the first 10 seconds and the FORK/JOIN
        // will end with minimal delay.
        if (pollCount <= properties.getSystemTaskPostponeThreshold()) {
            return Optional.of(0L);
        }

        double exp = pollCount - properties.getSystemTaskPostponeThreshold();
        long offset = Math.min((long) Math.pow(EVALUATION_OFFSET_BASE, exp), maxOffset);
        if (workflowTask != null && workflowTask.getMetadata().containsKey("lane")) {
            long interval = properties.getNativeWorkflow().getJoinPollInterval().toSeconds();
            offset = Math.min(offset, Math.max(1, interval));
        }
        return Optional.of(offset);
    }

    public boolean isAsync() {
        return true;
    }
}
