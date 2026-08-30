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
package org.conductoross.conductor.ai.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.service.WorkflowService;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a batch of agent tool calls as a sub-workflow built around {@code FORK_JOIN_DYNAMIC}.
 *
 * <p>One task per tool call, scheduled in parallel, each named after the tool so an ordinary worker
 * picks it up. The definition is supplied inline on the start request, so nothing has to be
 * registered up front and the shape can follow whatever the model asked for on this turn.
 *
 * <p>The sub-workflow id is the dispatch handle. It is durable and Conductor already persists it on
 * the waiting task, so a later poll resolves the batch on any replica — this class keeps nothing.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "conductor.integrations.ai.agent.tool-execution",
        havingValue = "subworkflow")
@Conditional(AIIntegrationEnabledCondition.class)
public class SubWorkflowAgentToolDispatcher implements AgentToolDispatcher {

    /** Where each tool task's own reference name is recorded, so results can be matched back. */
    static final String TOOL_CALL_ID = AgentToolNaming.TOOL_CALL_ID;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkflowService workflowService;

    // Resolved on use rather than injected: A2AWorkers documents a real constructor cycle through
    // WorkflowServiceImpl -> WorkflowExecutorOps -> SystemTaskRegistry ->
    // WorkerTaskAnnotationScanner,
    // and taking WorkflowExecutor eagerly here risks re-creating it.
    private final ObjectProvider<WorkflowExecutor> workflowExecutor;

    public SubWorkflowAgentToolDispatcher(
            WorkflowService workflowService, ObjectProvider<WorkflowExecutor> workflowExecutor) {
        this.workflowService = workflowService;
        this.workflowExecutor = workflowExecutor;
    }

    @Override
    public AgentToolDispatch dispatch(Request request) {
        List<Map<String, Object>> dynamicTasks = new ArrayList<>();
        for (Map<String, Object> toolCall : request.toolCalls()) {
            dynamicTasks.add(toolTask(request, toolCall));
        }

        // StartWorkflowInput rather than StartWorkflowRequest: parent linkage lives only on the
        // former, and without it the tool run is an orphan that outlives a terminated parent.
        StartWorkflowInput start = new StartWorkflowInput();
        start.setName(workflowName(request));
        start.setWorkflowDefinition(toolTurnDefinition(request));
        start.setWorkflowInput(Map.of("dynamicTasks", dynamicTasks));
        start.setCorrelationId(request.executionId());
        start.setParentWorkflowId(request.parentWorkflowId());
        start.setParentWorkflowTaskId(request.parentTaskId());

        String dispatchId = workflowExecutor.getObject().startWorkflow(start);
        log.debug(
                "Dispatched {} agent tool call(s) for execution {} as workflow {}",
                dynamicTasks.size(),
                request.executionId(),
                dispatchId);
        return AgentToolDispatch.running(dispatchId);
    }

    @Override
    public AgentToolDispatch status(String dispatchId) {
        Workflow workflow = workflowService.getExecutionStatus(dispatchId, true);
        if (workflow == null) {
            return AgentToolDispatch.failed(
                    dispatchId, "Tool workflow " + dispatchId + " not found");
        }
        if (!workflow.getStatus().isTerminal()) {
            return AgentToolDispatch.running(dispatchId);
        }
        if (!workflow.getStatus().isSuccessful()) {
            return AgentToolDispatch.failed(
                    dispatchId,
                    "Tool execution "
                            + dispatchId
                            + " ended "
                            + workflow.getStatus()
                            + (workflow.getReasonForIncompletion() != null
                                    ? ": " + workflow.getReasonForIncompletion()
                                    : ""));
        }
        return AgentToolDispatch.completed(dispatchId, resultsOf(workflow));
    }

    @Override
    public void cancel(String dispatchId) {
        try {
            workflowExecutor
                    .getObject()
                    .terminateWorkflow(dispatchId, "Owning agent task was cancelled");
        } catch (Exception e) {
            log.warn("Failed to terminate agent tool workflow {}: {}", dispatchId, e.getMessage());
        }
    }

    /**
     * Results keyed by {@code tool_call_id}, read back off each scheduled task. The id travels in
     * the task's own input rather than being derived from its reference name, so a provider id that
     * is not a legal reference name still round-trips.
     */
    private static Map<String, Object> resultsOf(Workflow workflow) {
        Map<String, Object> results = new LinkedHashMap<>();
        for (Task task : workflow.getTasks()) {
            Object toolCallId =
                    task.getInputData() != null ? task.getInputData().get(TOOL_CALL_ID) : null;
            if (toolCallId == null) {
                continue; // the fork and join tasks themselves
            }
            results.put(
                    String.valueOf(toolCallId),
                    task.getOutputData() == null ? Map.of() : task.getOutputData());
        }
        return results;
    }

    /**
     * One dynamic task per tool call. The tool's own arguments become the task input, so a worker
     * sees them as ordinary parameters rather than having to parse a payload.
     */
    private static Map<String, Object> toolTask(Request request, Map<String, Object> toolCall) {
        String toolName = String.valueOf(toolCall.get("tool_name"));
        String toolCallId = String.valueOf(toolCall.get("tool_call_id"));

        Map<String, Object> input =
                AgentToolNaming.toolInput(toolCall, toolCallId, toolName, request.executionId());

        Map<String, Object> task = new LinkedHashMap<>();
        task.put("name", taskNameFor(request, toolName));
        task.put("taskReferenceName", referenceName(request, toolCallId));
        task.put("type", "SIMPLE");
        task.put("inputParameters", input);
        return task;
    }

    /** A tool runs as a task of its own name unless the caller mapped it to another. */
    private static String taskNameFor(Request request, String toolName) {
        Map<String, String> overrides = request.toolTaskNames();
        if (overrides == null) {
            return toolName;
        }
        return overrides.getOrDefault(toolName, toolName);
    }

    /**
     * Escapes Conductor expressions out of values a model produced.
     *
     * <p>Tool arguments become a task's input parameters, and the engine resolves {@code ${...}} in
     * those against the running workflow. The arguments are written by a model, from a prompt that
     * may itself carry text from anywhere - so left alone, {@code ${workflow.input.customer_ssn}}
     * in a tool argument is a request the engine happily fulfils, handing workflow data to the tool
     * as if the workflow author had asked for it.
     *
     * <p>Doubling the dollar is the engine's own escape: it resolves {@code $${} back to a literal
     * {@code ${}, so the tool receives the text the model actually wrote and nothing more.
     */
    @SuppressWarnings("unchecked")
    static Object asLiteralText(Object value) {
        if (value instanceof String text) {
            return text.contains("${") ? text.replace("${", "$${") : text;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> copy.put(String.valueOf(key), asLiteralText(entry)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(entry -> copy.add(asLiteralText(entry)));
            return copy;
        }
        return value;
    }

    private static Map<String, Object> asLiteralText(Map<String, Object> arguments) {
        return (Map<String, Object>) asLiteralText((Object) arguments);
    }

    private static Map<String, Object> parseArguments(Object arguments) {
        if (arguments == null) {
            return Map.of();
        }
        if (arguments instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(String.valueOf(key), value));
            return copy;
        }
        String raw = arguments.toString().trim();
        if (raw.isEmpty()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(raw, Map.class);
        } catch (Exception e) {
            // A tool whose arguments are not a JSON object still gets them, verbatim, under a
            // predictable key — better than failing the turn over a shape we did not expect.
            log.debug("Tool arguments were not a JSON object; passing through as 'arguments'");
            return Map.of("arguments", raw);
        }
    }

    private static WorkflowDef toolTurnDefinition(Request request) {
        WorkflowTask fork = new WorkflowTask();
        fork.setName("agent_tools_fork");
        fork.setTaskReferenceName("agent_tools_fork");
        fork.setType("FORK_JOIN_DYNAMIC");
        fork.setDynamicForkTasksParam("dynamicTasks");
        fork.setDynamicForkTasksInputParamName("dynamicTasksInputs");
        Map<String, Object> forkInput = new LinkedHashMap<>();
        forkInput.put("dynamicTasks", "${workflow.input.dynamicTasks}");
        forkInput.put("dynamicTasksInputs", Map.of());
        fork.setInputParameters(forkInput);

        WorkflowTask join = new WorkflowTask();
        join.setName("agent_tools_join");
        join.setTaskReferenceName("agent_tools_join");
        join.setType("JOIN");

        WorkflowDef def = new WorkflowDef();
        def.setName(workflowName(request));
        def.setVersion(1);
        def.setDescription("Tool calls requested by agent execution " + request.executionId());
        def.setSchemaVersion(2);
        def.setTasks(List.of(fork, join));
        def.setTimeoutSeconds(0);
        return def;
    }

    private static String workflowName(Request request) {
        return "agent_tools_" + sanitize(request.taskRefName());
    }

    private static String referenceName(Request request, String toolCallId) {
        return "tool_" + sanitize(toolCallId);
    }

    private static String sanitize(String value) {
        return value == null || value.isBlank()
                ? "unknown"
                : value.replaceAll("[^A-Za-z0-9_]", "_");
    }
}
