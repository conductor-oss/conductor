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
package org.conductoross.conductor.ai.agentspan.runtime.compiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

/**
 * Binds an agent's server guardrails to the LLM tasks of its compiled workflow.
 *
 * <p>Every {@code LLM_CHAT_COMPLETE} task gets the bindings in its {@code guardrails} input,
 * including tasks in inline sub-workflow definitions, which is how sub-agents compiled with the
 * agent inherit them. Each of these tasks sends the conversation to a model, so each is guarded.
 *
 * <ul>
 *   <li>A task whose {@code guardrails} is an empty list has opted out and is left alone: a
 *       guardrail judge is a guardrail itself.
 *   <li>A task that already has bindings, from a sub-agent's own list, keeps them; entries naming a
 *       guardrail it does not bind yet are appended.
 * </ul>
 *
 * <p>Workflows compiled while the agent runs, and agents registered separately as tools, are not
 * reached from here.
 */
final class TaskGuardrails {

    static final String INPUT = "guardrails";

    private TaskGuardrails() {}

    static void bind(WorkflowDef workflow, List<Object> entries) {
        if (workflow == null || workflow.getTasks() == null || entries.isEmpty()) {
            return;
        }
        for (WorkflowTask top : workflow.getTasks()) {
            // collectTasks() covers forks, switches and loops, but stops at a sub-workflow.
            for (WorkflowTask task : top.collectTasks()) {
                if (task.getSubWorkflowParam() != null
                        && task.getSubWorkflowParam().getWorkflowDefinition()
                                instanceof WorkflowDef inline) {
                    bind(inline, entries);
                }
                if ("LLM_CHAT_COMPLETE".equals(task.getType())) {
                    bindTask(task, entries);
                }
            }
        }
    }

    private static void bindTask(WorkflowTask task, List<Object> entries) {
        Map<String, Object> inputs =
                task.getInputParameters() != null
                        ? new LinkedHashMap<>(task.getInputParameters())
                        : new LinkedHashMap<>();
        List<Object> bound = new ArrayList<>();
        if (inputs.get(INPUT) instanceof List<?> existing) {
            if (existing.isEmpty()) {
                return;
            }
            bound.addAll(existing);
        }
        Set<String> names = bound.stream().map(TaskGuardrails::name).collect(Collectors.toSet());
        for (Object entry : entries) {
            if (names.add(name(entry))) {
                bound.add(entry);
            }
        }
        inputs.put(INPUT, bound);
        task.setInputParameters(inputs);
    }

    /**
     * The guardrail an entry names: the entry itself, or its {@code guardrail} (or {@code name}).
     */
    static String name(Object entry) {
        if (entry instanceof Map<?, ?> map) {
            return Objects.toString(
                    map.get("guardrail") != null ? map.get("guardrail") : map.get("name"));
        }
        return Objects.toString(entry);
    }
}
