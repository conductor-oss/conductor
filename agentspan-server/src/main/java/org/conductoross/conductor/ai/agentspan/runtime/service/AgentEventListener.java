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

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.conductoross.conductor.ai.agentspan.runtime.model.AgentSSEEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.listener.TaskStatusListener;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Listens to Conductor task/workflow state changes and translates them into {@link AgentSSEEvent}s
 * pushed to connected SSE clients via {@link AgentStreamRegistry}.
 *
 * <p>Overrides Conductor's default stub listeners via {@code @Primary}.
 */
@Component
@Primary
public class AgentEventListener implements TaskStatusListener, WorkflowStatusListener {

    private static final Logger logger = LoggerFactory.getLogger(AgentEventListener.class);

    /** Conductor AI task types that consume LLM/generation API calls. */
    private static final Set<String> AI_TASK_TYPES =
            Set.of("LLM_CHAT_COMPLETE", "GENERATE_IMAGE", "GENERATE_AUDIO", "GENERATE_VIDEO");

    private final AgentStreamRegistry streamRegistry;
    private final MeterRegistry meterRegistry;

    @Autowired
    public AgentEventListener(AgentStreamRegistry streamRegistry, MeterRegistry meterRegistry) {
        this.streamRegistry = streamRegistry;
        this.meterRegistry = meterRegistry;
        logger.info("AgentEventListener active (TaskStatusListener + WorkflowStatusListener)");
    }

    // ── TaskStatusListener ───────────────────────────────────────────

    @Override
    public void onTaskScheduled(TaskModel task) {
        String wfId = task.getWorkflowInstanceId();
        String taskType = task.getTaskType();
        String taskRef = task.getReferenceTaskName();
        logger.debug("onTaskScheduled: wfId={}, type={}, ref={}", wfId, taskType, taskRef);

        if ("LLM_CHAT_COMPLETE".equals(taskType)) {
            emit(wfId, AgentSSEEvent.thinking(wfId, taskRef));
        } else if ("PULL_WORKFLOW_MESSAGES".equals(taskType)) {
            emit(wfId, AgentSSEEvent.waiting(wfId, Map.of("taskRefName", taskRef)));
        } else if ("SUB_WORKFLOW".equals(taskType)) {
            // Register child workflow alias for event forwarding
            String childWfId = task.getSubWorkflowId();
            if (childWfId != null && !childWfId.isEmpty()) {
                streamRegistry.registerAlias(childWfId, wfId);
            }
            String target = extractHandoffTarget(taskRef);
            emit(wfId, AgentSSEEvent.handoff(wfId, target));
        }
    }

    @Override
    public void onTaskInProgress(TaskModel task) {
        // HUMAN tasks are handled by AgentHumanTask.start() directly.
        // This callback is not called for system tasks by Conductor.
        logger.debug(
                "onTaskInProgress: wfId={}, type={}, ref={}",
                task.getWorkflowInstanceId(),
                task.getTaskType(),
                task.getReferenceTaskName());
    }

    @Override
    public void onTaskCompleted(TaskModel task) {
        String wfId = task.getWorkflowInstanceId();
        String taskRef = task.getReferenceTaskName();
        logger.debug(
                "onTaskCompleted: wfId={}, type={}, ref={}", wfId, task.getTaskType(), taskRef);

        Map<String, Object> output = task.getOutputData();
        if (output == null) output = Map.of();

        // Tool dispatch — SIMPLE tasks that are tool invocations
        if (isToolTask(task)) {
            String toolName = resolveToolName(task);
            Object args = task.getInputData();
            Object result = output.get("result");
            if (result == null) result = output;
            emit(wfId, AgentSSEEvent.toolCall(wfId, toolName, args));
            emit(wfId, AgentSSEEvent.toolResult(wfId, toolName, result));
        }
        // Guardrail tasks — identified by "guardrail" in ref name
        else if (taskRef != null && taskRef.contains("guardrail") && output.containsKey("passed")) {
            Boolean passed = (Boolean) output.get("passed");
            String name = taskRef;
            if (Boolean.TRUE.equals(passed)) {
                emit(wfId, AgentSSEEvent.guardrailPass(wfId, name));
            } else {
                String message = (String) output.getOrDefault("message", "");
                emit(wfId, AgentSSEEvent.guardrailFail(wfId, name, message));
            }
        }
    }

    @Override
    public void onTaskFailed(TaskModel task) {
        String wfId = task.getWorkflowInstanceId();
        String reason = task.getReasonForIncompletion();
        logger.info(
                "onTaskFailed: wfId={}, ref={}, reason={}",
                wfId,
                task.getReferenceTaskName(),
                reason);
        emit(wfId, AgentSSEEvent.error(wfId, task.getReferenceTaskName(), reason));
    }

    @Override
    public void onTaskCanceled(TaskModel task) {
        // No SSE event needed
    }

    @Override
    public void onTaskFailedWithTerminalError(TaskModel task) {
        onTaskFailed(task);
    }

    @Override
    public void onTaskTimedOut(TaskModel task) {
        String wfId = task.getWorkflowInstanceId();
        emit(wfId, AgentSSEEvent.error(wfId, task.getReferenceTaskName(), "Task timed out"));
    }

    @Override
    public void onTaskCompletedWithErrors(TaskModel task) {
        // Treat as normal completion for SSE purposes
        onTaskCompleted(task);
    }

    @Override
    public void onTaskSkipped(TaskModel task) {
        // No SSE event needed
    }

    // ── WorkflowStatusListener ───────────────────────────────────────

    @Override
    public void onWorkflowCompleted(WorkflowModel workflow) {
        // Called by Conductor for non-IfEnabled path (rarely used)
        handleWorkflowCompleted(workflow);
    }

    @Override
    public void onWorkflowTerminated(WorkflowModel workflow) {
        // Called by Conductor for non-IfEnabled path (rarely used)
        handleWorkflowTerminated(workflow);
    }

    // The *IfEnabled variants are the PRIMARY callback path used by
    // WorkflowExecutorOps.notifyWorkflowStatusListener()

    @Override
    public void onWorkflowCompletedIfEnabled(WorkflowModel workflow) {
        handleWorkflowCompleted(workflow);
    }

    @Override
    public void onWorkflowTerminatedIfEnabled(WorkflowModel workflow) {
        handleWorkflowTerminated(workflow);
    }

    @Override
    public void onWorkflowFinalizedIfEnabled(WorkflowModel workflow) {
        // No SSE event needed
    }

    @Override
    public void onWorkflowStartedIfEnabled(WorkflowModel workflow) {
        // For sub-workflows: register alias so child events forward to parent,
        // and emit a HANDOFF event on the parent stream.
        // (onTaskScheduled is not called for system tasks like SUB_WORKFLOW,
        //  so we detect handoffs here instead.)
        String parentId = workflow.getParentWorkflowId();
        if (parentId == null || parentId.isEmpty()) return;

        String childId = workflow.getWorkflowId();
        String childName = workflow.getWorkflowName();

        // Register alias so future events from this child appear on the parent stream
        streamRegistry.registerAlias(childId, parentId);

        // Emit HANDOFF on the parent — extract agent name from sub-workflow name
        // Child workflow names follow patterns like: support_orchestrator_router_wf,
        // tech_support_wf, etc.
        String target = extractHandoffTarget(childName.replaceAll("_wf$", ""));
        logger.debug(
                "Sub-workflow started: child={}, parent={}, target={}", childId, parentId, target);
        emit(parentId, AgentSSEEvent.handoff(parentId, target));
    }

    @Override
    public void onWorkflowPausedIfEnabled(WorkflowModel workflow) {
        String wfId = workflow.getWorkflowId();
        logger.debug("onWorkflowPaused: wfId={}", wfId);
        emit(wfId, AgentSSEEvent.waiting(wfId, Map.of()));
    }

    @Override
    public void onWorkflowResumedIfEnabled(WorkflowModel workflow) {
        // No SSE event needed
    }

    private void handleWorkflowCompleted(WorkflowModel workflow) {
        String wfId = workflow.getWorkflowId();
        logger.info("onWorkflowCompleted: wfId={}", wfId);
        recordWorkflowAIMetrics(workflow);

        // For sub-workflows (children), do NOT emit DONE on the parent stream.
        // DONE is only emitted for the root (parent) workflow so the SSE client doesn't
        // close the stream prematurely when an intermediate sub-agent finishes.
        String parentId = workflow.getParentWorkflowId();
        if (parentId != null && !parentId.isEmpty()) {
            streamRegistry.complete(wfId);
            return;
        }

        Map<String, Object> output = workflow.getOutput();
        emit(wfId, AgentSSEEvent.done(wfId, output));
        streamRegistry.complete(wfId);
    }

    private void handleWorkflowTerminated(WorkflowModel workflow) {
        String wfId = workflow.getWorkflowId();
        logger.info(
                "onWorkflowTerminated: wfId={}, reason={}",
                wfId,
                workflow.getReasonForIncompletion());
        recordWorkflowAIMetrics(workflow);
        String reason = workflow.getReasonForIncompletion();
        emit(
                wfId,
                AgentSSEEvent.error(
                        wfId, "workflow", reason != null ? reason : "Workflow terminated"));
        streamRegistry.complete(wfId);
    }

    // ── Metrics ──────────────────────────────────────────────────────

    /**
     * Record Prometheus metrics for all AI tasks in a completed/terminated workflow.
     *
     * <p>System tasks (LLM_CHAT_COMPLETE, GENERATE_IMAGE, etc.) don't trigger {@code
     * TaskStatusListener.onTaskCompleted}, so we iterate the full task list when the workflow
     * finishes.
     *
     * <p>Publishes two metric families to {@code /actuator/prometheus}:
     *
     * <ul>
     *   <li>{@code agentspan_ai_requests_total} — counter per AI API call
     *   <li>{@code agentspan_ai_tokens_total} — counter per token type (prompt/completion)
     * </ul>
     *
     * <p>Tags: {@code agent}, {@code model}, {@code provider}, {@code task_type}.
     */
    private void recordWorkflowAIMetrics(WorkflowModel workflow) {
        if (meterRegistry == null || workflow.getTasks() == null || workflow.getTasks().isEmpty())
            return;

        String agent;
        try {
            agent = workflow.getWorkflowName();
        } catch (Exception e) {
            agent = "unknown";
        }

        for (TaskModel task : workflow.getTasks()) {
            String taskType = task.getTaskType();
            if (taskType == null || !AI_TASK_TYPES.contains(taskType)) continue;

            Map<String, Object> input = task.getInputData();
            Map<String, Object> output = task.getOutputData();
            if (input == null) input = Map.of();
            if (output == null) output = Map.of();

            String model =
                    input.get("model") != null ? String.valueOf(input.get("model")) : "unknown";
            String provider =
                    input.get("llmProvider") != null
                            ? String.valueOf(input.get("llmProvider"))
                            : "unknown";

            String taskLabel =
                    switch (taskType) {
                        case "LLM_CHAT_COMPLETE" -> "chat";
                        case "GENERATE_IMAGE" -> "image";
                        case "GENERATE_AUDIO" -> "audio";
                        case "GENERATE_VIDEO" -> "video";
                        default -> taskType.toLowerCase();
                    };

            // Request counter
            Counter.builder("agentspan.ai.requests")
                    .description("Total AI API requests")
                    .tag("agent", agent)
                    .tag("model", model)
                    .tag("provider", provider)
                    .tag("task_type", taskLabel)
                    .register(meterRegistry)
                    .increment();

            // Token counters
            int promptTokens = toInt(output.get("promptTokens"));
            int completionTokens = toInt(output.get("completionTokens"));
            int totalTokens = toInt(output.get("tokenUsed"));

            if (promptTokens > 0) {
                Counter.builder("agentspan.ai.tokens")
                        .description("Total AI tokens consumed")
                        .tag("agent", agent)
                        .tag("model", model)
                        .tag("provider", provider)
                        .tag("task_type", taskLabel)
                        .tag("token_type", "prompt")
                        .register(meterRegistry)
                        .increment(promptTokens);
            }
            if (completionTokens > 0) {
                Counter.builder("agentspan.ai.tokens")
                        .description("Total AI tokens consumed")
                        .tag("agent", agent)
                        .tag("model", model)
                        .tag("provider", provider)
                        .tag("task_type", taskLabel)
                        .tag("token_type", "completion")
                        .register(meterRegistry)
                        .increment(completionTokens);
            }
            if (totalTokens > 0) {
                Counter.builder("agentspan.ai.tokens")
                        .description("Total AI tokens consumed")
                        .tag("agent", agent)
                        .tag("model", model)
                        .tag("provider", provider)
                        .tag("task_type", taskLabel)
                        .tag("token_type", "total")
                        .register(meterRegistry)
                        .increment(totalTokens);
            }
        }
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    // ── Internal ─────────────────────────────────────────────────────

    private void emit(String executionId, AgentSSEEvent event) {
        try {
            streamRegistry.send(executionId, event);
        } catch (Exception e) {
            logger.warn(
                    "Failed to emit SSE event for execution {}: {}", executionId, e.getMessage());
        }
    }

    /**
     * Determine if a completed task is a tool invocation (not an internal system task like SWITCH,
     * DO_WHILE, INLINE, etc.).
     */
    private boolean isToolTask(TaskModel task) {
        String taskType = task.getTaskType();
        if (taskType == null) return false;
        // Skip framework passthrough wrapper tasks — they emit their own fine-grained events
        if (task.getReferenceTaskName() != null && task.getReferenceTaskName().startsWith("_fw_")) {
            return false;
        }
        // System task types that are NOT tool invocations
        switch (taskType) {
            case "LLM_CHAT_COMPLETE":
            case "SWITCH":
            case "DO_WHILE":
            case "INLINE":
            case "SET_VARIABLE":
            case "FORK_JOIN_DYNAMIC":
            case "JOIN":
            case "SUB_WORKFLOW":
            case "HUMAN":
            case "TERMINATE":
            case "HTTP":
            case "CALL_MCP_TOOL":
                return false;
            default:
                // SIMPLE or other user-defined task types = tool invocation
                return "SIMPLE".equals(taskType) || task.getTaskDefinition().isPresent();
        }
    }

    /**
     * Resolve the actual tool/function name from a task.
     *
     * <p>Server-compiled workflows use SIMPLE tasks where the actual tool name is stored in {@code
     * inputData.method} (set by the enrichment script). Locally-compiled workflows use SIMPLE tasks
     * with a dispatch pattern where the function name is stored in the output data. SDK-compiled
     * worker tasks use a custom task type matching the function name.
     */
    private String resolveToolName(TaskModel task) {
        String taskType = task.getTaskType();

        // Server-compiled SIMPLE tasks: tool name is in inputData.method
        Map<String, Object> input = task.getInputData();
        if (input != null && input.containsKey("method")) {
            return String.valueOf(input.get("method"));
        }

        // Locally-compiled (dispatch): function name in output data
        Map<String, Object> output = task.getOutputData();
        if (output != null && output.containsKey("function")) {
            return String.valueOf(output.get("function"));
        }

        // SDK-compiled workers: taskType is the function name (e.g. "get_weather")
        if (!"SIMPLE".equals(taskType) && taskType != null) {
            return taskType.toLowerCase();
        }

        // Fallback to task reference name
        return task.getReferenceTaskName();
    }

    /**
     * Extract the target agent name from a sub-workflow task reference.
     *
     * <p>Conductor generates indexed references for sub-workflows in multi-agent strategies.
     * Examples:
     *
     * <ul>
     *   <li>{@code 0_billing__1} → {@code billing}
     *   <li>{@code analysis_parallel_0_pros_analyst} → {@code pros_analyst}
     *   <li>{@code debate_round_robin_1_optimist__1} → {@code optimist}
     *   <li>{@code researcher_writer_step_0_researcher} → {@code researcher}
     *   <li>{@code support_handoff_billing} → {@code billing}
     * </ul>
     *
     * <p>Strategy:
     *
     * <ol>
     *   <li>Strip trailing {@code __N} turn counter
     *   <li>If it contains {@code _handoff_}, take everything after
     *   <li>Strip strategy-indexed prefixes ({@code _sequential_N_}, etc.)
     *   <li>Strip {@code _step_N_} sequential pipeline prefixes
     *   <li>Strip leading {@code N_} index prefix
     * </ol>
     */
    private String extractHandoffTarget(String taskRef) {
        if (taskRef == null) return "unknown";

        // Step 1: strip trailing __N (turn counter)
        String name = taskRef.replaceAll("__\\d+$", "");

        // Step 2: strip strategy-indexed prefixes
        // Matches: <parent>_<strategy>_<idx>_<agent_name>
        // Strategies: handoff (handoff+router), agent (round_robin+swarm),
        //   step (sequential), parallel, and others
        Matcher strategyMatcher =
                Pattern.compile(
                                "^.+?_(?:handoff|agent|step|sequential|parallel|round_robin|router|swarm|random|manual)_(\\d+)_(.*)")
                        .matcher(name);
        if (strategyMatcher.matches()) {
            return strategyMatcher.group(2);
        }

        // Step 3: strip leading N_ index prefix (e.g. "0_billing")
        Matcher idxMatcher = Pattern.compile("^\\d+_(.*)").matcher(name);
        if (idxMatcher.matches()) {
            return idxMatcher.group(1);
        }

        return name;
    }
}
