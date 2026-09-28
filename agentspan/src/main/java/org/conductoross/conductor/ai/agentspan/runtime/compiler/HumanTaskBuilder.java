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

import java.util.*;

import org.conductoross.conductor.ai.agentspan.runtime.util.JavaScriptBuilder;
import org.conductoross.conductor.common.metadata.agent.ModelParser;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

/**
 * Builds a consistent HUMAN task pipeline for Conductor workflows.
 *
 * <p>Every human-in-the-loop interaction in the system follows the same pattern:
 *
 * <ol>
 *   <li><b>HUMAN task</b> — pauses workflow for human input (always)
 *   <li><b>INLINE validate</b> — parses/coerces human response (optional)
 *   <li><b>SWITCH → LLM_CHAT_COMPLETE</b> — normalizes free-form text to structured JSON (optional)
 *   <li><b>INLINE process</b> — merges validated/normalized output into final form (optional)
 * </ol>
 *
 * <p>Action routing (approve/reject/edit SWITCH) is NOT part of this builder — callers add their
 * own routing using {@link Pipeline#getOutputRef()}.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * var pipeline = HumanTaskBuilder.create("my_human", "My Agent — Review")
 *     .contextInput("tool_calls", "${llm.output.toolCalls}")
 *     .responseSchema(HumanTaskBuilder.approvalResponseSchema())
 *     .responseUiSchema(HumanTaskBuilder.approvalResponseUiSchema())
 *     .approvalValidation(model)
 *     .build();
 *
 * tasks.addAll(pipeline.getTasks());
 * // pipeline.getOutputRef() → "my_human_process.output.result"
 * // Now add your own SWITCH routing on top.
 * }</pre>
 */
public class HumanTaskBuilder {

    /** Result of building a human task pipeline. */
    public static class Pipeline {
        private final List<WorkflowTask> tasks;
        private final String humanRef;
        private final String outputRef;

        Pipeline(List<WorkflowTask> tasks, String humanRef, String outputRef) {
            this.tasks = tasks;
            this.humanRef = humanRef;
            this.outputRef = outputRef;
        }

        /** All workflow tasks in the pipeline. */
        public List<WorkflowTask> getTasks() {
            return tasks;
        }

        /** Task reference name of the HUMAN task itself. */
        public String getHumanRef() {
            return humanRef;
        }

        /**
         * Conductor expression to reference the final processed output.
         *
         * <p>Without validation: {@code "humanRef.output"}
         *
         * <p>With validation: {@code "processRef.output.result"}
         */
        public String getOutputRef() {
            return outputRef;
        }
    }

    // ── Normalizer prompt constants ─────────────────────────────────────

    static final String APPROVAL_NORMALIZER_PROMPT =
            "Convert the human's response into a JSON object:\n"
                    + "{\"approved\": <boolean>, \"reason\": <string or null>}\n\n"
                    + "Rules:\n"
                    + "- approved=true for: approve, yes, ok, LGTM, looks good, go ahead, etc.\n"
                    + "- approved=false for: reject, no, deny, not approved, etc.\n"
                    + "- If they give a reason, put it in reason.\n"
                    + "- If input is already valid JSON with these fields, return as-is.\n"
                    + "Output ONLY the JSON object.";

    static final String GUARDRAIL_NORMALIZER_PROMPT =
            "Convert the human's response into a JSON object:\n"
                    + "{\"approved\": <boolean>, \"edited_output\": <string or null>, \"reason\": <string or null>}\n\n"
                    + "Rules:\n"
                    + "- approved=true for: approve, yes, ok, LGTM, looks good, go ahead, etc.\n"
                    + "- approved=false for: reject, no, deny, not approved, etc.\n"
                    + "- If they provide corrected content, put it in edited_output.\n"
                    + "- If they give a reason for rejection, put it in reason.\n"
                    + "- If input is already valid JSON with these fields, return as-is.\n"
                    + "Output ONLY the JSON object.";

    /**
     * Build the normalizer prompt for graph-node HUMAN tasks. Includes the human prompt as context
     * so the LLM knows what fields to extract.
     */
    static String graphNodeNormalizerPrompt(String humanPrompt) {
        return "Convert the human's free-form response into a JSON object with the appropriate fields.\n\n"
                + (humanPrompt != null && !humanPrompt.isEmpty()
                        ? "Context — the human was asked: \"" + humanPrompt + "\"\n\n"
                        : "")
                + "Rules:\n"
                + "- Extract all meaningful key-value pairs from the response.\n"
                + "- Use snake_case field names that match the context.\n"
                + "- Preserve the original values (strings, booleans, numbers) as-is.\n"
                + "- If input is already valid JSON, return as-is.\n"
                + "Output ONLY the JSON object.";
    }

    // ── Response schema constants ───────────────────────────────────────

    /** Standard approval schema: approved (boolean) + reason (string). */
    public static Map<String, Object> approvalResponseSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("approved"),
                "properties",
                        Map.of(
                                "approved",
                                        Map.of(
                                                "type", "boolean",
                                                "title", "Approved",
                                                "description",
                                                        "Approve or reject the tool execution"),
                                "reason",
                                        Map.of(
                                                "type", "string",
                                                "title", "Reason",
                                                "description", "Reason for rejection")));
    }

    /** Standard approval UI schema. */
    public static Map<String, Object> approvalResponseUiSchema() {
        return Map.of(
                "ui:order", List.of("approved", "reason"),
                "approved", Map.of("ui:widget", "radio"),
                "reason", Map.of("ui:widget", "textarea"));
    }

    /** Guardrail review schema: approved + edited_output + reason. */
    public static Map<String, Object> guardrailResponseSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("approved"),
                "properties",
                        Map.of(
                                "approved",
                                        Map.of(
                                                "type", "boolean",
                                                "title", "Approved",
                                                "description", "Approve or reject the LLM output"),
                                "edited_output",
                                        Map.of(
                                                "type", "string",
                                                "title", "Edited Output",
                                                "description",
                                                        "Provide corrected output if editing"),
                                "reason",
                                        Map.of(
                                                "type", "string",
                                                "title", "Reason",
                                                "description", "Reason for rejection")));
    }

    /** Guardrail review UI schema. */
    public static Map<String, Object> guardrailResponseUiSchema() {
        return Map.of(
                "ui:order", List.of("approved", "edited_output", "reason"),
                "approved", Map.of("ui:widget", "radio"),
                "edited_output", Map.of("ui:widget", "textarea"),
                "reason", Map.of("ui:widget", "textarea"));
    }

    /** Graph-node HUMAN schema: a free-form response textarea. */
    public static Map<String, Object> graphNodeResponseSchema() {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "response",
                        Map.of(
                                "type", "string",
                                "title", "Response",
                                "description", "Provide your response as JSON or plain text")));
    }

    /** Graph-node HUMAN UI schema. */
    public static Map<String, Object> graphNodeResponseUiSchema() {
        return Map.of(
                "ui:order", List.of("response"),
                "response", Map.of("ui:widget", "textarea"));
    }

    // ── Builder state ───────────────────────────────────────────────────

    private final String refPrefix;
    private final String displayName;
    private final Map<String, Object> contextInputs = new LinkedHashMap<>();
    private Map<String, Object> responseSchema;
    private Map<String, Object> responseUiSchema;
    private String validateScript;
    private String processScript;
    private String normalizerPrompt;
    private String model;
    private String processContentRef;
    private Object processStateRef;

    private HumanTaskBuilder(String refPrefix, String displayName) {
        this.refPrefix = refPrefix;
        this.displayName = displayName;
    }

    /** Create a new builder. */
    public static HumanTaskBuilder create(String refPrefix, String displayName) {
        return new HumanTaskBuilder(refPrefix, displayName);
    }

    /** Add a context input shown to the human. */
    public HumanTaskBuilder contextInput(String key, Object value) {
        if (value != null) contextInputs.put(key, value);
        return this;
    }

    /** Set JSON Schema for expected human response. */
    public HumanTaskBuilder responseSchema(Map<String, Object> schema) {
        this.responseSchema = schema;
        return this;
    }

    /** Set UI rendering hints for the response form. */
    public HumanTaskBuilder responseUiSchema(Map<String, Object> uiSchema) {
        this.responseUiSchema = uiSchema;
        return this;
    }

    /**
     * Configure the approval validation pipeline (tool approval).
     *
     * <p>Uses {@link JavaScriptBuilder#approvalValidateScript()} to parse the response and {@link
     * JavaScriptBuilder#approvalCheckScript()} to merge validated/normalized output. Output shape:
     * {@code {approved: boolean, reason: string, extra: object}}
     */
    public HumanTaskBuilder approvalValidation(String model) {
        this.validateScript = JavaScriptBuilder.approvalValidateScript();
        this.processScript = JavaScriptBuilder.approvalCheckScript();
        this.normalizerPrompt = APPROVAL_NORMALIZER_PROMPT;
        this.model = model;
        return this;
    }

    /**
     * Configure the guardrail validation pipeline.
     *
     * <p>Uses {@link JavaScriptBuilder#humanValidateScript()} to parse the response and {@link
     * JavaScriptBuilder#humanProcessScript()} to merge validated/normalized output. Output shape:
     * {@code {action: "approve"|"edit"|"reject", result: string, reason: string}}
     *
     * @param model LLM model for normalization
     * @param contentRef Conductor expression for the original LLM output being reviewed
     */
    public HumanTaskBuilder guardrailValidation(String model, String contentRef) {
        this.validateScript = JavaScriptBuilder.humanValidateScript();
        this.processScript = JavaScriptBuilder.humanProcessScript();
        this.normalizerPrompt = GUARDRAIL_NORMALIZER_PROMPT;
        this.model = model;
        this.processContentRef = contentRef;
        return this;
    }

    /**
     * Configure the graph-node validation pipeline.
     *
     * <p>Used for HUMAN tasks inside graph workflows. Validates the human response, normalizes
     * free-text to JSON via LLM if needed, and merges the result into the graph state. Also sets
     * default response schema and UI schema.
     *
     * <p>Output shape: {@code {state: object, result: string}}
     *
     * @param model LLM model for normalization
     * @param humanPrompt the prompt shown to the human (used as LLM normalizer context)
     * @param stateRef Conductor expression (String) or literal map for the previous graph state
     */
    public HumanTaskBuilder graphNodeValidation(String model, String humanPrompt, Object stateRef) {
        this.validateScript = JavaScriptBuilder.graphNodeValidateScript();
        this.processScript = JavaScriptBuilder.graphNodeProcessScript();
        this.normalizerPrompt = graphNodeNormalizerPrompt(humanPrompt);
        this.model = model;
        this.processStateRef = stateRef;
        this.responseSchema = graphNodeResponseSchema();
        this.responseUiSchema = graphNodeResponseUiSchema();
        return this;
    }

    /**
     * Configure a custom validation pipeline.
     *
     * @param validateScript GraalJS expression for the validate INLINE task
     * @param processScript GraalJS expression for the process INLINE task (null to skip)
     * @param normalizerPrompt system prompt for the LLM normalizer
     * @param model LLM model for normalization
     */
    public HumanTaskBuilder customValidation(
            String validateScript, String processScript, String normalizerPrompt, String model) {
        this.validateScript = validateScript;
        this.processScript = processScript;
        this.normalizerPrompt = normalizerPrompt;
        this.model = model;
        return this;
    }

    // ── Build ───────────────────────────────────────────────────────────

    /** Build the pipeline, returning all tasks and the output reference. */
    public Pipeline build() {
        List<WorkflowTask> tasks = new ArrayList<>();

        // ── 1. HUMAN task ───────────────────────────────────────────────
        WorkflowTask humanTask = new WorkflowTask();
        humanTask.setType("HUMAN");
        humanTask.setName(refPrefix);
        humanTask.setTaskReferenceName(refPrefix);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put(
                "__humanTaskDefinition",
                Map.of(
                        "assignmentCompletionStrategy",
                        "LEAVE_OPEN",
                        "displayName",
                        displayName,
                        "userFormTemplate",
                        Map.of("version", 0)));
        if (responseSchema != null) inputs.put("response_schema", responseSchema);
        if (responseUiSchema != null) inputs.put("response_ui_schema", responseUiSchema);
        inputs.putAll(contextInputs);
        humanTask.setInputParameters(inputs);
        tasks.add(humanTask);

        String outputRef = refPrefix + ".output";

        // ── 2. Validate + Normalize + Process (optional) ────────────────
        if (validateScript != null) {
            String validateRef = refPrefix + "_validate";
            String normalizerRef = refPrefix + "_normalizer";

            // Validate INLINE
            WorkflowTask validateTask = new WorkflowTask();
            validateTask.setType("INLINE");
            validateTask.setName(validateRef);
            validateTask.setTaskReferenceName(validateRef);
            Map<String, Object> valInputs = new LinkedHashMap<>();
            valInputs.put("evaluatorType", "graaljs");
            valInputs.put("expression", validateScript);
            valInputs.put("human_output", "${" + refPrefix + ".output}");
            validateTask.setInputParameters(valInputs);
            tasks.add(validateTask);

            // Normalize SWITCH + LLM (if model provided)
            if (model != null && normalizerPrompt != null) {
                String normSwitchRef = refPrefix + "_normalize_switch";

                WorkflowTask normSwitch = new WorkflowTask();
                normSwitch.setType("SWITCH");
                normSwitch.setName(normSwitchRef);
                normSwitch.setTaskReferenceName(normSwitchRef);
                normSwitch.setEvaluatorType("graaljs");
                normSwitch.setExpression("$.needs_normalize == true ? 'needs_normalize' : 'skip'");
                normSwitch.setInputParameters(
                        Map.of(
                                "needs_normalize",
                                "${" + validateRef + ".output.result.needs_normalize}"));

                // needs_normalize → LLM_CHAT_COMPLETE
                WorkflowTask normTask = new WorkflowTask();
                normTask.setType("LLM_CHAT_COMPLETE");
                normTask.setName("LLM_CHAT_COMPLETE");
                normTask.setTaskReferenceName(normalizerRef);
                Map<String, Object> normInputs = new LinkedHashMap<>();
                ModelParser.ParsedModel parsed = ModelParser.parse(model);
                normInputs.put("llmProvider", parsed.getProvider());
                normInputs.put("model", parsed.getModel());
                normInputs.put(
                        "messages",
                        List.of(
                                Map.of("role", "system", "message", normalizerPrompt),
                                Map.of(
                                        "role",
                                        "user",
                                        "message",
                                        "${" + validateRef + ".output.result.raw_text}")));
                normInputs.put("temperature", 0);
                normInputs.put("jsonOutput", true);
                normTask.setInputParameters(normInputs);

                normSwitch.setDecisionCases(Map.of("needs_normalize", List.of(normTask)));

                // default (skip) → SET_VARIABLE noop
                WorkflowTask noop = new WorkflowTask();
                noop.setType("SET_VARIABLE");
                noop.setName(refPrefix + "_normalize_noop");
                noop.setTaskReferenceName(refPrefix + "_normalize_noop");
                noop.setInputParameters(Map.of("_normalize_skipped", true));
                normSwitch.setDefaultCase(List.of(noop));

                tasks.add(normSwitch);
            }

            // Process/Check INLINE
            if (processScript != null) {
                String processRef = refPrefix + "_process";

                WorkflowTask processTask = new WorkflowTask();
                processTask.setType("INLINE");
                processTask.setName(processRef);
                processTask.setTaskReferenceName(processRef);
                Map<String, Object> procInputs = new LinkedHashMap<>();
                procInputs.put("evaluatorType", "graaljs");
                procInputs.put("expression", processScript);
                procInputs.put("validated", "${" + validateRef + ".output.result}");
                procInputs.put("normalized", "${" + normalizerRef + ".output.result}");
                if (processContentRef != null) {
                    procInputs.put("llm_output", processContentRef);
                }
                if (processStateRef != null) {
                    procInputs.put("previousState", processStateRef);
                }
                processTask.setInputParameters(procInputs);
                tasks.add(processTask);

                outputRef = processRef + ".output.result";
            } else {
                outputRef = validateRef + ".output.result";
            }
        }

        return new Pipeline(tasks, refPrefix, outputRef);
    }
}
