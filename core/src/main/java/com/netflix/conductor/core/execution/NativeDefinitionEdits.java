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
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

/**
 * Edits of the definition a native execution owns: its entries are inserted, looked up and removed
 * in whichever list holds them (the top level, a fork branch, a switch case or a loop body).
 */
public final class NativeDefinitionEdits {

    private NativeDefinitionEdits() {}

    /**
     * Inserts entries right after the entry {@code ref}, in whichever list holds it: the top level,
     * a fork branch, a switch case or a loop body. False, changing nothing, when there is no such
     * entry.
     */
    static boolean insertAfter(WorkflowDef def, String ref, List<WorkflowTask> entries) {
        return insertAfter(def.getTasks(), ref, entries);
    }

    private static boolean insertAfter(
            List<WorkflowTask> list, String ref, List<WorkflowTask> entries) {
        for (int i = 0; i < list.size(); i++) {
            if (ref.equals(list.get(i).getTaskReferenceName())) {
                list.addAll(i + 1, entries);
                return true;
            }
            for (List<WorkflowTask> child : children(list.get(i))) {
                if (insertAfter(child, ref, entries)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The entry right before {@code ref} in the list that holds it; null when it is first. */
    public static WorkflowTask previousTask(WorkflowDef def, String ref) {
        return previous(def.getTasks(), ref);
    }

    private static WorkflowTask previous(List<WorkflowTask> list, String ref) {
        for (int i = 0; i < list.size(); i++) {
            if (ref.equals(list.get(i).getTaskReferenceName())) {
                return i == 0 ? null : list.get(i - 1);
            }
            for (List<WorkflowTask> child : children(list.get(i))) {
                WorkflowTask found = previous(child, ref);
                if (found != null) {
                    return found;
                }
                if (child.stream().anyMatch(t -> ref.equals(t.getTaskReferenceName()))) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Removes the entries named in {@code refs}, at any depth, with what they hold. */
    static int removeAll(WorkflowDef def, Set<String> refs) {
        return removeAll(def.getTasks(), refs);
    }

    private static int removeAll(List<WorkflowTask> list, Set<String> refs) {
        int removed = 0;
        for (Iterator<WorkflowTask> it = list.iterator(); it.hasNext(); ) {
            WorkflowTask task = it.next();
            if (refs.contains(task.getTaskReferenceName())) {
                it.remove();
                removed++;
            } else {
                for (List<WorkflowTask> child : children(task)) {
                    removed += removeAll(child, refs);
                }
            }
        }
        return removed;
    }

    /** The lists an entry holds, live: switch cases and the default, fork branches, loop body. */
    private static List<List<WorkflowTask>> children(WorkflowTask task) {
        List<List<WorkflowTask>> lists = new ArrayList<>();
        switch (TaskType.of(task.getType())) {
            case DECISION, SWITCH -> {
                lists.addAll(task.getDecisionCases().values());
                lists.add(task.getDefaultCase());
            }
            case FORK_JOIN -> lists.addAll(task.getForkTasks());
            case DO_WHILE -> lists.add(task.getLoopOver());
            default -> {}
        }
        return lists;
    }
}
