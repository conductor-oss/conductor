/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.util;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

/**
 * Static helpers operating on Conductor {@link WorkflowTask} trees that multiple compile sites need
 * without forming a circular dependency among themselves. Lives in {@code runtime.util} so both
 * {@code runtime.compiler} and {@code runtime.service} can call without pulling each other in.
 */
public final class WorkflowTaskUtils {

    private WorkflowTaskUtils() {}

    /**
     * Backfill {@code task.name} for every node in a task tree that has it unset. Conductor's
     * WorkflowSweeper trips on null task names with {@code NullPointerException: TaskDef name
     * cannot be null}; the outer compile-time pass in {@code AgentCompiler} runs this over the
     * parent workflow, but anywhere a sub-tree is built dynamically (e.g. {@code
     * PlanAndCompileTask}'s SUB_WORKFLOW or any path that embeds {@link
     * com.netflix.conductor.common.metadata.workflow.WorkflowDef}s not seen by the outer pass)
     * needs to call this on its outputs.
     *
     * <p>Conventions matching the existing compile sites:
     *
     * <ul>
     *   <li>{@code LLM_CHAT_COMPLETE} → {@code "llm_chat_complete"}.
     *   <li>{@code SIMPLE} with a non-empty name → preserved (workers poll on it).
     *   <li>Anything else with no name → falls back to the task's {@code taskReferenceName}.
     * </ul>
     *
     * <p>Recurses into {@link WorkflowTask#getDecisionCases()}, {@link
     * WorkflowTask#getDefaultCase()}, {@link WorkflowTask#getForkTasks()}, and {@link
     * WorkflowTask#getLoopOver()}. Does NOT recurse into {@code
     * SubWorkflowParam.workflowDefinition} — that's a separate pass owned by the embedding
     * compiler.
     */
    public static void ensureTaskName(WorkflowTask task) {
        if (task == null) return;
        if ("LLM_CHAT_COMPLETE".equals(task.getType())) {
            // Always normalize to the lowercase TaskDef name. Several compile
            // sites set name = "LLM_CHAT_COMPLETE" (matching the type) — that
            // makes Conductor look up a TaskDef of that uppercase name, miss,
            // and fall back to defaults that change LLM behavior (notably the
            // tool-routing defaults). The lowercase form is the only registered
            // TaskDef. This must run unconditionally, not just when name is
            // empty.
            task.setName("llm_chat_complete");
        } else if ("SIMPLE".equals(task.getType())
                && task.getName() != null
                && !task.getName().isEmpty()) {
            // SIMPLE tasks: preserve the task definition name.
        } else if (task.getName() == null || task.getName().isEmpty()) {
            task.setName(task.getTaskReferenceName());
        }
        if (task.getDecisionCases() != null) {
            task.getDecisionCases()
                    .values()
                    .forEach(branch -> branch.forEach(WorkflowTaskUtils::ensureTaskName));
        }
        if (task.getDefaultCase() != null) {
            task.getDefaultCase().forEach(WorkflowTaskUtils::ensureTaskName);
        }
        if (task.getForkTasks() != null) {
            task.getForkTasks()
                    .forEach(branch -> branch.forEach(WorkflowTaskUtils::ensureTaskName));
        }
        if (task.getLoopOver() != null) {
            task.getLoopOver().forEach(WorkflowTaskUtils::ensureTaskName);
        }
    }

    /** Convenience: backfill names across every task in a {@link WorkflowDef}. */
    public static void ensureAllTaskNames(WorkflowDef wf) {
        if (wf == null || wf.getTasks() == null) return;
        wf.getTasks().forEach(WorkflowTaskUtils::ensureTaskName);
    }
}
