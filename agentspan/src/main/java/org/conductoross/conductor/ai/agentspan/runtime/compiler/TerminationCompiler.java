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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.util.JavaScriptBuilder;
import org.conductoross.conductor.common.metadata.agent.TerminationConfig;
import org.conductoross.conductor.common.metadata.agent.WorkerRef;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

/**
 * Compiles {@link TerminationConfig} into server-side InlineTask JavaScript. No Python worker is
 * needed -- the termination condition runs entirely within the Conductor engine via GraalJS.
 */
@Component
public class TerminationCompiler {

    /**
     * Compile a {@link TerminationConfig} into a Conductor {@link WorkflowTask} of type INLINE_TASK
     * that evaluates the termination condition server-side.
     *
     * @param config the termination configuration
     * @param agentName the agent name (used for task reference naming)
     * @param llmRef the task reference name of the LLM task whose output is evaluated
     * @return a fully configured WorkflowTask
     */
    public static WorkflowTask compileTermination(
            TerminationConfig config, String agentName, String llmRef) {
        agentName = AgentCompiler.toRef(agentName);
        String taskName = agentName + "_termination";
        String refName = agentName + "_termination";
        String resultRef = "${" + llmRef + ".output.result}";
        String iterationRef = "${" + agentName + "_loop.iteration}";

        // Match local compiler: emit a SIMPLE worker task.
        // The Python runtime registers a worker that evaluates the termination condition.
        WorkflowTask task = new WorkflowTask();
        task.setName(taskName);
        task.setTaskReferenceName(refName);
        task.setType("SIMPLE");

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("result", resultRef);
        inputs.put("iteration", iterationRef);
        inputs.put("messages", "${" + llmRef + ".input.messages}");
        task.setInputParameters(inputs);

        return task;
    }

    /**
     * Compile a stop_when {@link WorkerRef} into a SimpleTask that delegates termination evaluation
     * to a registered worker.
     *
     * @param taskName the worker task name
     * @param agentName the agent name (used for task reference naming)
     * @param llmRef the task reference name of the LLM task whose output is evaluated
     * @return a fully configured WorkflowTask of type SIMPLE
     */
    public static WorkflowTask compileStopWhen(String taskName, String agentName, String llmRef) {
        agentName = AgentCompiler.toRef(agentName);
        String refName = agentName + "_stop_when";
        String resultRef = "${" + llmRef + ".output.result}";
        String iterationRef = "${" + agentName + "_loop.iteration}";

        WorkflowTask task = new WorkflowTask();
        task.setName(taskName);
        task.setTaskReferenceName(refName);
        task.setType("SIMPLE");

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("result", resultRef);
        inputs.put("iteration", iterationRef);
        inputs.put("messages", "${" + llmRef + ".input.messages}");
        task.setInputParameters(inputs);

        return task;
    }

    /**
     * Compile a stop_when worker for multi-agent loops (swarm, rotation, manual). Uses {@code
     * workflow.variables.conversation} as the result input instead of a single LLM task output.
     *
     * @param taskName the worker task name
     * @param agentName the agent name (used for task reference naming)
     * @param loopRef the DO_WHILE loop task reference name
     * @return a fully configured WorkflowTask of type SIMPLE
     */
    public static WorkflowTask compileStopWhenForConversation(
            String taskName, String agentName, String loopRef) {
        agentName = AgentCompiler.toRef(agentName);
        String refName = agentName + "_stop_when";

        WorkflowTask task = new WorkflowTask();
        task.setName(taskName);
        task.setTaskReferenceName(refName);
        task.setType("SIMPLE");

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("result", "${workflow.variables.conversation}");
        inputs.put("iteration", "${" + loopRef + ".iteration}");
        task.setInputParameters(inputs);

        return task;
    }

    /**
     * Compile a termination condition for multi-agent loops (swarm, rotation, manual). Uses {@code
     * workflow.variables.conversation} as the result input.
     *
     * @param config the termination configuration
     * @param agentName the agent name (used for task reference naming)
     * @param loopRef the DO_WHILE loop task reference name
     * @return a fully configured WorkflowTask
     */
    public static WorkflowTask compileTerminationForConversation(
            TerminationConfig config, String agentName, String loopRef) {
        agentName = AgentCompiler.toRef(agentName);
        String taskName = agentName + "_termination";
        String refName = agentName + "_termination";

        WorkflowTask task = new WorkflowTask();
        task.setName(taskName);
        task.setTaskReferenceName(refName);
        task.setType("SIMPLE");

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("result", "${workflow.variables.conversation}");
        inputs.put("iteration", "${" + loopRef + ".iteration}");
        task.setInputParameters(inputs);

        return task;
    }

    /**
     * Recursively build the JavaScript expression string for a given {@link TerminationConfig}.
     * Composite types (and/or) inline their sub-conditions directly into the generated script.
     *
     * @param config the termination configuration
     * @return a JavaScript IIFE string
     */
    public static String buildTerminationScript(TerminationConfig config) {
        String type = config.getType();
        return switch (type) {
            case "text_mention" -> buildTextMentionScript(config);
            case "stop_message" -> buildStopMessageScript(config);
            case "max_message" -> buildMaxMessageScript(config);
            case "token_usage" -> buildTokenUsageScript(config);
            case "and" -> buildCompositeScript(config, true);
            case "or" -> buildCompositeScript(config, false);
            default -> throw new IllegalArgumentException("Unknown termination type: " + type);
        };
    }

    // ----------------------------------------------------------------
    // Private builders for each termination type
    // ----------------------------------------------------------------

    private static String buildTextMentionScript(TerminationConfig config) {
        String textJs = JavaScriptBuilder.toJson(config.getText());
        boolean caseSensitive =
                config.getCaseSensitive() != null ? config.getCaseSensitive() : true;

        return JavaScriptBuilder.iife(
                "  var content = String($.result || '');"
                        + "  var text = "
                        + textJs
                        + ";"
                        + "  var caseSensitive = "
                        + caseSensitive
                        + ";"
                        + "  var found = caseSensitive ? content.indexOf(text) >= 0 : content.toLowerCase().indexOf(text.toLowerCase()) >= 0;"
                        + "  return {should_continue: !found, reason: found ? 'Text mention detected: ' + text : ''};");
    }

    private static String buildStopMessageScript(TerminationConfig config) {
        String stopMessageJs = JavaScriptBuilder.toJson(config.getStopMessage());

        return JavaScriptBuilder.iife(
                "  var content = String($.result || '').trim();"
                        + "  var stopMessage = "
                        + stopMessageJs
                        + ";"
                        + "  var match = content === stopMessage;"
                        + "  return {should_continue: !match, reason: match ? 'Stop message received' : ''};");
    }

    private static String buildMaxMessageScript(TerminationConfig config) {
        int maxMessages = config.getMaxMessages() != null ? config.getMaxMessages() : 10;

        return JavaScriptBuilder.iife(
                "  var iteration = $.iteration || 0;"
                        + "  var maxMessages = "
                        + maxMessages
                        + ";"
                        + "  var exceeded = iteration >= maxMessages;"
                        + "  return {should_continue: !exceeded, reason: exceeded ? 'Max messages reached: ' + maxMessages : ''};");
    }

    private static String buildTokenUsageScript(TerminationConfig config) {
        int maxTotal = config.getMaxTotalTokens() != null ? config.getMaxTotalTokens() : 0;
        int maxPrompt = config.getMaxPromptTokens() != null ? config.getMaxPromptTokens() : 0;
        int maxCompletion =
                config.getMaxCompletionTokens() != null ? config.getMaxCompletionTokens() : 0;

        return JavaScriptBuilder.iife(
                "  var result = $.result || {};"
                        + "  var tokenUsed = result.tokenUsed || {};"
                        + "  var exceeded = false;"
                        + "  var reason = '';"
                        + "  if ("
                        + maxTotal
                        + " > 0 && (tokenUsed.total_tokens || 0) > "
                        + maxTotal
                        + ") { exceeded = true; reason = 'Total token limit exceeded'; }"
                        + "  if ("
                        + maxPrompt
                        + " > 0 && (tokenUsed.prompt_tokens || 0) > "
                        + maxPrompt
                        + ") { exceeded = true; reason = 'Prompt token limit exceeded'; }"
                        + "  if ("
                        + maxCompletion
                        + " > 0 && (tokenUsed.completion_tokens || 0) > "
                        + maxCompletion
                        + ") { exceeded = true; reason = 'Completion token limit exceeded'; }"
                        + "  return {should_continue: !exceeded, reason: reason};");
    }

    /**
     * Build a composite (AND/OR) script by inlining each sub-condition's check.
     *
     * <p>For AND: all sub-conditions must signal termination (should_continue == false) for the
     * composite to terminate.
     *
     * <p>For OR: any sub-condition signaling termination causes the composite to terminate.
     *
     * @param config the composite termination config
     * @param isAnd true for AND semantics, false for OR
     * @return a JavaScript IIFE string
     */
    private static String buildCompositeScript(TerminationConfig config, boolean isAnd) {
        List<TerminationConfig> conditions = config.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Composite termination ("
                            + (isAnd ? "and" : "or")
                            + ") must have at least one sub-condition");
        }

        StringBuilder body = new StringBuilder();
        body.append("  var results = [];");

        for (int i = 0; i < conditions.size(); i++) {
            TerminationConfig sub = conditions.get(i);
            String subBody = buildSubConditionBody(sub, i);
            body.append(subBody);
            body.append("  results.push(r").append(i).append(");");
        }

        if (isAnd) {
            // AND: all must signal termination (should_continue == false) for composite to
            // terminate
            body.append("  var allTerminate = true;");
            body.append("  var reasons = [];");
            body.append("  for (var i = 0; i < results.length; i++) {");
            body.append("    if (results[i].should_continue) { allTerminate = false; }");
            body.append("    if (results[i].reason) { reasons.push(results[i].reason); }");
            body.append("  }");
            body.append(
                    "  return {should_continue: !allTerminate, reason: allTerminate ? reasons.join('; ') : ''};");
        } else {
            // OR: any signaling termination causes composite to terminate
            body.append("  var anyTerminate = false;");
            body.append("  var reasons = [];");
            body.append("  for (var i = 0; i < results.length; i++) {");
            body.append("    if (!results[i].should_continue) { anyTerminate = true; }");
            body.append("    if (results[i].reason) { reasons.push(results[i].reason); }");
            body.append("  }");
            body.append(
                    "  return {should_continue: !anyTerminate, reason: anyTerminate ? reasons.join('; ') : ''};");
        }

        return JavaScriptBuilder.iife(body.toString());
    }

    /**
     * Build the inline JavaScript body for a single sub-condition within a composite. Each
     * sub-condition evaluates to a local variable {@code rN} containing {@code {should_continue,
     * reason}}.
     */
    private static String buildSubConditionBody(TerminationConfig sub, int index) {
        String varName = "r" + index;
        String type = sub.getType();

        return switch (type) {
            case "text_mention" -> buildInlineTextMention(sub, varName);
            case "stop_message" -> buildInlineStopMessage(sub, varName);
            case "max_message" -> buildInlineMaxMessage(sub, varName);
            case "token_usage" -> buildInlineTokenUsage(sub, varName);
            case "and" -> buildInlineComposite(sub, varName, true, index);
            case "or" -> buildInlineComposite(sub, varName, false, index);
            default ->
                    throw new IllegalArgumentException(
                            "Unknown termination type in composite: " + type);
        };
    }

    private static String buildInlineTextMention(TerminationConfig config, String varName) {
        String textJs = JavaScriptBuilder.toJson(config.getText());
        boolean caseSensitive =
                config.getCaseSensitive() != null ? config.getCaseSensitive() : true;

        return "  var "
                + varName
                + " = (function() {"
                + "    var content = String($.result || '');"
                + "    var text = "
                + textJs
                + ";"
                + "    var caseSensitive = "
                + caseSensitive
                + ";"
                + "    var found = caseSensitive ? content.indexOf(text) >= 0 : content.toLowerCase().indexOf(text.toLowerCase()) >= 0;"
                + "    return {should_continue: !found, reason: found ? 'Text mention detected: ' + text : ''};"
                + "  })();";
    }

    private static String buildInlineStopMessage(TerminationConfig config, String varName) {
        String stopMessageJs = JavaScriptBuilder.toJson(config.getStopMessage());

        return "  var "
                + varName
                + " = (function() {"
                + "    var content = String($.result || '').trim();"
                + "    var stopMessage = "
                + stopMessageJs
                + ";"
                + "    var match = content === stopMessage;"
                + "    return {should_continue: !match, reason: match ? 'Stop message received' : ''};"
                + "  })();";
    }

    private static String buildInlineMaxMessage(TerminationConfig config, String varName) {
        int maxMessages = config.getMaxMessages() != null ? config.getMaxMessages() : 10;

        return "  var "
                + varName
                + " = (function() {"
                + "    var iteration = $.iteration || 0;"
                + "    var maxMessages = "
                + maxMessages
                + ";"
                + "    var exceeded = iteration >= maxMessages;"
                + "    return {should_continue: !exceeded, reason: exceeded ? 'Max messages reached: ' + maxMessages : ''};"
                + "  })();";
    }

    private static String buildInlineTokenUsage(TerminationConfig config, String varName) {
        int maxTotal = config.getMaxTotalTokens() != null ? config.getMaxTotalTokens() : 0;
        int maxPrompt = config.getMaxPromptTokens() != null ? config.getMaxPromptTokens() : 0;
        int maxCompletion =
                config.getMaxCompletionTokens() != null ? config.getMaxCompletionTokens() : 0;

        return "  var "
                + varName
                + " = (function() {"
                + "    var result = $.result || {};"
                + "    var tokenUsed = result.tokenUsed || {};"
                + "    var exceeded = false;"
                + "    var reason = '';"
                + "    if ("
                + maxTotal
                + " > 0 && (tokenUsed.total_tokens || 0) > "
                + maxTotal
                + ") { exceeded = true; reason = 'Total token limit exceeded'; }"
                + "    if ("
                + maxPrompt
                + " > 0 && (tokenUsed.prompt_tokens || 0) > "
                + maxPrompt
                + ") { exceeded = true; reason = 'Prompt token limit exceeded'; }"
                + "    if ("
                + maxCompletion
                + " > 0 && (tokenUsed.completion_tokens || 0) > "
                + maxCompletion
                + ") { exceeded = true; reason = 'Completion token limit exceeded'; }"
                + "    return {should_continue: !exceeded, reason: reason};"
                + "  })();";
    }

    /**
     * Build an inline composite sub-condition. Recursively resolves nested composites. Uses a
     * unique prefix to avoid variable name collisions in deeply nested composites.
     */
    private static String buildInlineComposite(
            TerminationConfig config, String varName, boolean isAnd, int parentIndex) {
        List<TerminationConfig> conditions = config.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Composite termination must have at least one sub-condition");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("  var ").append(varName).append(" = (function() {");
        sb.append("    var results = [];");

        for (int i = 0; i < conditions.size(); i++) {
            String nestedVar = "s" + parentIndex + "_" + i;
            TerminationConfig sub = conditions.get(i);
            String nestedBody = buildNestedSubConditionBody(sub, nestedVar, parentIndex, i);
            sb.append(nestedBody);
            sb.append("    results.push(").append(nestedVar).append(");");
        }

        if (isAnd) {
            sb.append("    var allTerminate = true;");
            sb.append("    var reasons = [];");
            sb.append("    for (var i = 0; i < results.length; i++) {");
            sb.append("      if (results[i].should_continue) { allTerminate = false; }");
            sb.append("      if (results[i].reason) { reasons.push(results[i].reason); }");
            sb.append("    }");
            sb.append(
                    "    return {should_continue: !allTerminate, reason: allTerminate ? reasons.join('; ') : ''};");
        } else {
            sb.append("    var anyTerminate = false;");
            sb.append("    var reasons = [];");
            sb.append("    for (var i = 0; i < results.length; i++) {");
            sb.append("      if (!results[i].should_continue) { anyTerminate = true; }");
            sb.append("      if (results[i].reason) { reasons.push(results[i].reason); }");
            sb.append("    }");
            sb.append(
                    "    return {should_continue: !anyTerminate, reason: anyTerminate ? reasons.join('; ') : ''};");
        }

        sb.append("  })();");
        return sb.toString();
    }

    /**
     * Build an inline sub-condition body for use inside a nested composite. Variable names are
     * prefixed to avoid collisions.
     */
    private static String buildNestedSubConditionBody(
            TerminationConfig sub, String varName, int parentIndex, int childIndex) {
        String type = sub.getType();
        return switch (type) {
            case "text_mention" -> buildInlineTextMention(sub, varName);
            case "stop_message" -> buildInlineStopMessage(sub, varName);
            case "max_message" -> buildInlineMaxMessage(sub, varName);
            case "token_usage" -> buildInlineTokenUsage(sub, varName);
            case "and" -> buildInlineComposite(sub, varName, true, parentIndex * 10 + childIndex);
            case "or" -> buildInlineComposite(sub, varName, false, parentIndex * 10 + childIndex);
            default ->
                    throw new IllegalArgumentException(
                            "Unknown termination type in nested composite: " + type);
        };
    }
}
