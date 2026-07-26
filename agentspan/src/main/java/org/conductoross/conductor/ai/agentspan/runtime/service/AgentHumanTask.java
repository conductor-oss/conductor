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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.metadata.agent.AgentSSEEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_HUMAN;

/**
 * Custom HUMAN task that emits a WAITING SSE event immediately when started.
 *
 * <p>Conductor's default {@code Human} task sets status to IN_PROGRESS but does NOT call {@code
 * TaskStatusListener.onTaskScheduled/onTaskInProgress} for system tasks. This override hooks into
 * {@code start()} to emit the SSE event directly.
 *
 * <p>Registered as the {@code HUMAN} system task by {@link AgentHumanTaskConfig}, which is gated on
 * {@code agentspan.embedded=true}. It is intentionally not a {@code @Component}: two beans named
 * {@code HUMAN} (this one and Conductor's {@code Human}) would collide during component scanning,
 * and both would land in {@code SystemTaskRegistry}'s taskType→task map as duplicate keys. The
 * config's {@code @Bean("HUMAN")} instead <em>overrides</em> Conductor's default so exactly one
 * {@code HUMAN} bean exists.
 */
public class AgentHumanTask extends WorkflowSystemTask {

    private static final Logger logger = LoggerFactory.getLogger(AgentHumanTask.class);

    private final AgentStreamRegistry streamRegistry;

    public AgentHumanTask(AgentStreamRegistry streamRegistry) {
        super(TASK_TYPE_HUMAN);
        this.streamRegistry = streamRegistry;
        logger.debug("AgentHumanTask registered (overrides default HUMAN with SSE support)");
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        task.setStatus(TaskModel.Status.IN_PROGRESS);

        // Emit WAITING event immediately
        String wfId = workflow.getWorkflowId();
        String taskRef = task.getReferenceTaskName();
        Map<String, Object> pendingTool = new HashMap<>();
        Map<String, Object> input = task.getInputData();
        if (input != null) {
            pendingTool.put("tool_name", input.get("tool_name"));
            pendingTool.put("parameters", input.get("parameters"));
            List<Map<String, Object>> toolCalls = extractToolCalls(input.get("tool_calls"));
            if (toolCalls != null) {
                pendingTool.put("toolCalls", toolCalls);
            }
            if (input.get("response_schema") != null) {
                pendingTool.put("response_schema", input.get("response_schema"));
            }
            if (input.get("response_ui_schema") != null) {
                pendingTool.put("response_ui_schema", input.get("response_ui_schema"));
            }
        }
        pendingTool.put("taskRefName", taskRef);

        try {
            streamRegistry.send(wfId, AgentSSEEvent.waiting(wfId, pendingTool));
            logger.debug("Emitted WAITING event for HUMAN task {} in workflow {}", taskRef, wfId);
        } catch (Exception e) {
            logger.warn("Failed to emit WAITING event for workflow {}: {}", wfId, e.getMessage());
        }
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }

    /**
     * Normalise the compiler-emitted {@code tool_calls} array (originally sourced from {@code
     * ${llm}.output.toolCalls}) into the SSE-facing {@code [{name, args}, ...]} shape consumers
     * read.
     *
     * <p>One HUMAN task gates a whole batch of tool calls with a single {@code {approved, reason}}
     * verdict, which is why the singular {@code tool_name} / {@code parameters} keys remain {@code
     * null}: they cannot honestly represent N tools. Consumers should iterate the returned array to
     * see every tool awaiting approval.
     *
     * <p>Args precedence matches the rest of the runtime (see {@code JavaScriptBuilder}'s {@code
     * tc.inputParameters || tc.input}): {@code inputParameters} is the canonical key for an LLM
     * tool-call element, with {@code input} accepted as a fallback.
     */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> extractToolCalls(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> calls = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> typed = (Map<String, Object>) map;
            Object name = typed.get("name");
            // Conductor task inputs land under inputParameters; LLM-native
            // tool calls land under input. inputParameters is canonical.
            Object args =
                    typed.containsKey("inputParameters")
                            ? typed.get("inputParameters")
                            : typed.get("input");
            Map<String, Object> normalised = new HashMap<>();
            if (name != null) normalised.put("name", name);
            if (args != null) normalised.put("args", args);
            if (!normalised.isEmpty()) calls.add(normalised);
        }
        return calls.isEmpty() ? null : calls;
    }
}
