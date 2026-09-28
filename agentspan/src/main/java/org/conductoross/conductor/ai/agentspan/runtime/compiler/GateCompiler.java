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
import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.util.JavaScriptBuilder;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

/**
 * Compiles gate conditions into Conductor workflow tasks.
 *
 * <p>A gate is a conditional check inserted between sequential pipeline stages. If the gate
 * evaluates to "stop", the pipeline ends early; otherwise it continues.
 *
 * <p>{@code TextGate} (type "text_contains") is compiled to an INLINE JavaScript task — no worker
 * round-trip needed. Callable gates are compiled to SIMPLE tasks that delegate to a Python SDK
 * worker.
 */
public class GateCompiler {

    /**
     * Compile a gate configuration into a workflow task. Returns a task whose output is {@code
     * {decision: "continue" | "stop"}}.
     *
     * @param gateConfig the gate configuration map from AgentConfig
     * @param refName task reference name for the gate task
     * @param prevOutputRef Conductor expression referencing the previous stage's output
     * @return a fully configured WorkflowTask
     */
    @SuppressWarnings("unchecked")
    public static WorkflowTask compileGate(
            Map<String, Object> gateConfig, String refName, String prevOutputRef) {

        String type = (String) gateConfig.get("type");

        if ("text_contains".equals(type)) {
            return compileTextContainsGate(gateConfig, refName, prevOutputRef);
        }

        // Callable gate: SIMPLE task for SDK worker
        if (gateConfig.containsKey("taskName")) {
            return compileWorkerGate(gateConfig, refName, prevOutputRef);
        }

        throw new IllegalArgumentException("Unknown gate type: " + type);
    }

    private static WorkflowTask compileTextContainsGate(
            Map<String, Object> config, String refName, String prevOutputRef) {

        String text = (String) config.get("text");
        Object caseSensitiveObj = config.getOrDefault("caseSensitive", true);
        boolean caseSensitive =
                caseSensitiveObj instanceof Boolean ? (Boolean) caseSensitiveObj : true;

        String script =
                JavaScriptBuilder.iife(
                        "var content = String($.result || '');"
                                + (caseSensitive ? "" : "content = content.toLowerCase();")
                                + "var sentinel = "
                                + JavaScriptBuilder.toJson(
                                        caseSensitive ? text : text.toLowerCase())
                                + ";"
                                + "var found = content.indexOf(sentinel) >= 0;"
                                + "return {decision: found ? 'stop' : 'continue'};");

        WorkflowTask task = new WorkflowTask();
        task.setType("INLINE");
        task.setTaskReferenceName(refName);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("evaluatorType", "graaljs");
        inputs.put("expression", script);
        inputs.put("result", prevOutputRef);
        task.setInputParameters(inputs);

        return task;
    }

    private static WorkflowTask compileWorkerGate(
            Map<String, Object> config, String refName, String prevOutputRef) {

        WorkflowTask task = new WorkflowTask();
        task.setType("SIMPLE");
        task.setName((String) config.get("taskName"));
        task.setTaskReferenceName(refName);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("result", prevOutputRef);
        task.setInputParameters(inputs);

        return task;
    }
}
