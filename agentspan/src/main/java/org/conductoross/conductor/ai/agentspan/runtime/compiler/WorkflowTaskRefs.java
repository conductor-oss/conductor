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
package org.conductoross.conductor.ai.agentspan.runtime.compiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

/** Renames duplicate {@code taskReferenceName}s in a compiled {@code WorkflowTask} tree. */
class WorkflowTaskRefs {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTaskRefs.class);

    private WorkflowTaskRefs() {}

    /**
     * Ensure every taskReferenceName in the workflow is unique.
     *
     * <p>Walks the entire task tree (including forks, loops, switch cases, and inline
     * sub-workflows). When a duplicate is found, it is renamed by appending {@code _2}, {@code _3},
     * etc. All {@code ${oldRef...}} expressions in the workflow's input/output parameters are
     * updated to use the new name.
     *
     * <p>This is a safety net that runs for ALL compilation paths. Individual compilers (e.g.
     * {@code compileGraphStructure}) may also use {@code allocRef} to prevent duplicates at
     * construction time.
     */
    static void ensureUniqueRefNames(List<WorkflowTask> tasks, WorkflowDef wf) {
        // Pass 1: collect all tasks and detect duplicates
        Set<String> seen = new LinkedHashSet<>();
        Map<String, String> renames = new LinkedHashMap<>(); // oldRef -> newRef
        deduplicateRefs(tasks, seen, renames);

        // Pass 2: if any renames occurred, update ${oldRef...} references everywhere
        if (!renames.isEmpty()) {
            log.info("Renaming {} duplicate taskReferenceName(s): {}", renames.size(), renames);
            updateRefExpressions(tasks, renames);
            // Also update workflow-level outputParameters
            if (wf.getOutputParameters() != null) {
                Map<String, Object> updated = replaceRefsInMap(wf.getOutputParameters(), renames);
                wf.setOutputParameters(updated);
            }
        }
    }

    /** Walk the task tree, renaming duplicate refs and recording the renames. */
    private static void deduplicateRefs(
            List<WorkflowTask> tasks, Set<String> seen, Map<String, String> renames) {
        if (tasks == null) return;
        for (WorkflowTask task : tasks) {
            if (task == null) continue;

            String ref = task.getTaskReferenceName();
            if (ref != null && !ref.isEmpty()) {
                if (!seen.add(ref)) {
                    // Duplicate — allocate a unique name
                    String newRef = ref;
                    int n = 2;
                    while (!seen.add(newRef + "_" + n)) n++;
                    newRef = ref + "_" + n;
                    log.warn("Duplicate taskReferenceName '{}' renamed to '{}'", ref, newRef);
                    task.setTaskReferenceName(newRef);
                    renames.put(ref, newRef);
                }
            }

            // Recurse into nested structures
            deduplicateRefs(task.getLoopOver(), seen, renames);
            if (task.getDecisionCases() != null) {
                for (List<WorkflowTask> branch : task.getDecisionCases().values()) {
                    deduplicateRefs(branch, seen, renames);
                }
            }
            deduplicateRefs(task.getDefaultCase(), seen, renames);
            if (task.getForkTasks() != null) {
                for (List<WorkflowTask> branch : task.getForkTasks()) {
                    deduplicateRefs(branch, seen, renames);
                }
            }
            // Skip sub-workflows whose workflowDefinition is a runtime expression String
            // (e.g. "${parse_wf.output.result}") used by plan-execute inline sub-workflows.
            if (task.getSubWorkflowParam() != null
                    && task.getSubWorkflowParam().getWorkflowDefinition()
                            instanceof WorkflowDef nestedWfDef
                    && nestedWfDef.getTasks() != null) {
                // Sub-workflows have their own ref namespace
                ensureUniqueRefNames(nestedWfDef.getTasks(), nestedWfDef);
            }
        }
    }

    /**
     * Walk the task tree and update all {@code ${oldRef...}} expressions in inputParameters to use
     * the new ref names.
     */
    private static void updateRefExpressions(
            List<WorkflowTask> tasks, Map<String, String> renames) {
        if (tasks == null) return;
        for (WorkflowTask task : tasks) {
            if (task == null) continue;
            if (task.getInputParameters() != null) {
                Map<String, Object> updated = replaceRefsInMap(task.getInputParameters(), renames);
                task.setInputParameters(updated);
            }
            // Also update expression field (used by SWITCH evaluators, INLINE expressions, etc.)
            if (task.getExpression() instanceof String expr) {
                String replaced = replaceRefsInString(expr, renames);
                if (!replaced.equals(expr)) {
                    task.setExpression(replaced);
                }
            }
            // Recurse
            updateRefExpressions(task.getLoopOver(), renames);
            if (task.getDecisionCases() != null) {
                for (List<WorkflowTask> branch : task.getDecisionCases().values()) {
                    updateRefExpressions(branch, renames);
                }
            }
            updateRefExpressions(task.getDefaultCase(), renames);
            if (task.getForkTasks() != null) {
                for (List<WorkflowTask> branch : task.getForkTasks()) {
                    updateRefExpressions(branch, renames);
                }
            }
        }
    }

    /**
     * Replace {@code ${oldRef.xxx}} → {@code ${newRef.xxx}} in all string values of a parameter
     * map. Recurses into nested maps and lists.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> replaceRefsInMap(
            Map<String, Object> params, Map<String, String> renames) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            result.put(entry.getKey(), replaceRefsInValue(entry.getValue(), renames));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object replaceRefsInValue(Object value, Map<String, String> renames) {
        if (value instanceof String s) {
            return replaceRefsInString(s, renames);
        } else if (value instanceof Map) {
            return replaceRefsInMap((Map<String, Object>) value, renames);
        } else if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(replaceRefsInValue(item, renames));
            }
            return result;
        }
        return value;
    }

    /**
     * In a string, replace all occurrences of {@code ${oldRef.} and {@code ${oldRef}}
     * with the new ref name.
     */
    private static String replaceRefsInString(String s, Map<String, String> renames) {
        if (s == null || !s.contains("${")) return s;
        String result = s;
        for (Map.Entry<String, String> entry : renames.entrySet()) {
            String oldRef = entry.getKey();
            String newRef = entry.getValue();
            // Replace ${oldRef.xxx} → ${newRef.xxx}  and  ${oldRef} → ${newRef}
            result = result.replace("${" + oldRef + ".", "${" + newRef + ".");
            result = result.replace("${" + oldRef + "}", "${" + newRef + "}");
        }
        return result;
    }
}
