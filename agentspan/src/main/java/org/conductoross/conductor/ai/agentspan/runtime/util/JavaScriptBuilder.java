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

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Helper for building JavaScript snippets for Conductor InlineTask scripts. All scripts are wrapped
 * in IIFEs: (function() { ... })()
 */
public class JavaScriptBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Convert a Java object to a JSON string suitable for embedding in JavaScript. */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    /** Wrap a script body in an IIFE. */
    public static String iife(String body) {
        return "(function() {" + body + "})()";
    }

    /**
     * Validate a parsed-args instance against a tool's ``inputSchema``.
     *
     * <p>Closes the dg-review F3 finding: ``Generate.output_schema`` is a shape-hint string baked
     * into the LLM prompt, NOT a real schema, so without this validator a confused or adversarial
     * LLM could emit args of the wrong shape ({@code {"path": "/etc/passwd"}}) and have them flow
     * directly into the downstream SIMPLE worker. PAC now inserts a {@code v_<stepId>_<opIdx>}
     * INLINE that runs this validator between the parse INLINE and the tool task.
     *
     * <p>The script supports a Draft-07 subset:
     *
     * <ul>
     *   <li>{@code type} — object/string/number/integer/boolean/array/null (including union via
     *       array of types)
     *   <li>{@code required} — list of required property names on objects
     *   <li>{@code properties} — recursive schema per property
     *   <li>{@code additionalProperties} — boolean only ({@code false} rejects unknown keys;
     *       default {@code true})
     *   <li>{@code enum} — value must equal one of the listed members
     *   <li>{@code pattern} — regex test on strings
     *   <li>{@code minLength}/{@code maxLength} on strings
     *   <li>{@code minimum}/{@code maximum} on numbers
     *   <li>{@code items} — recursive schema for array elements
     *   <li>{@code minItems}/{@code maxItems} on arrays
     * </ul>
     *
     * <p>Input contract: ``$.parsed`` is the parse INLINE's output (either the parsed args object
     * OR ``{__parse_error: true, ...}`` when parsing failed). ``$.schema`` is the tool's input
     * schema. The script passes ``__parse_error`` through unchanged (so the downstream parseGate
     * sees a single error sentinel for either parse or schema failure) and otherwise either returns
     * the parsed object as-is (validation passed) or sets ``{__parse_error: true, reason: "schema:
     * <details>"}``.
     */
    public static String schemaValidatorScript() {
        return iife(
                // Walk a GraalJS-side Java Map / List value to a JS-native
                // object so JSON-Schema introspection works without the
                // Map keyset weirdness that bit the AML INLINEs.
                "function toJS(v) {"
                        + "  if (v === null || v === undefined) return v;"
                        + "  if (typeof v !== 'object') return v;"
                        + "  if (typeof v.keySet === 'function' && typeof v.get === 'function') {"
                        + "    var out = {}; var it = v.keySet().iterator();"
                        + "    while (it.hasNext()) { var k = it.next(); out[String(k)] = toJS(v.get(k)); }"
                        + "    return out;"
                        + "  }"
                        + "  if (typeof v.iterator === 'function' && typeof v.size === 'function'"
                        + "      && typeof v.keySet !== 'function') {"
                        + "    var arr = []; var lit = v.iterator();"
                        + "    while (lit.hasNext()) arr.push(toJS(lit.next()));"
                        + "    return arr;"
                        + "  }"
                        + "  if (Array.isArray(v)) return v.map(toJS);"
                        + "  var keys = Object.keys(v); var o2 = {};"
                        + "  for (var i = 0; i < keys.length; i++) o2[keys[i]] = toJS(v[keys[i]]);"
                        + "  return o2;"
                        + "}"
                        + "function jsType(v) {"
                        + "  if (v === null) return 'null';"
                        + "  if (Array.isArray(v)) return 'array';"
                        + "  var t = typeof v;"
                        + "  if (t === 'number') return Number.isInteger(v) ? 'integer' : 'number';"
                        + "  return t;"
                        + "}"
                        + "function typeMatches(declared, actual) {"
                        + "  if (declared === undefined || declared === null) return true;"
                        + "  if (Array.isArray(declared)) {"
                        + "    for (var i = 0; i < declared.length; i++) {"
                        + "      if (typeMatches(declared[i], actual)) return true;"
                        + "    }"
                        + "    return false;"
                        + "  }"
                        + "  if (declared === 'number') return actual === 'number' || actual === 'integer';"
                        + "  return declared === actual;"
                        + "}"
                        + "function validate(instance, schema, path, errs) {"
                        + "  if (schema === null || schema === undefined) return;"
                        + "  if (typeof schema !== 'object') return;"
                        + "  var actual = jsType(instance);"
                        + "  if (schema.type !== undefined && !typeMatches(schema.type, actual)) {"
                        + "    errs.push(path + ': type ' + JSON.stringify(schema.type) + ' expected, got ' + actual);"
                        + "    return;"
                        + "  }"
                        + "  if (Array.isArray(schema.enum)) {"
                        + "    var found = false;"
                        + "    for (var i = 0; i < schema.enum.length; i++) {"
                        + "      if (JSON.stringify(schema.enum[i]) === JSON.stringify(instance)) { found = true; break; }"
                        + "    }"
                        + "    if (!found) errs.push(path + ': not in enum ' + JSON.stringify(schema.enum));"
                        + "  }"
                        + "  if (actual === 'string') {"
                        + "    if (typeof schema.minLength === 'number' && instance.length < schema.minLength)"
                        + "      errs.push(path + ': length ' + instance.length + ' < minLength ' + schema.minLength);"
                        + "    if (typeof schema.maxLength === 'number' && instance.length > schema.maxLength)"
                        + "      errs.push(path + ': length ' + instance.length + ' > maxLength ' + schema.maxLength);"
                        + "    if (typeof schema.pattern === 'string') {"
                        + "      try { if (!(new RegExp(schema.pattern)).test(instance))"
                        + "        errs.push(path + ': string does not match pattern ' + JSON.stringify(schema.pattern));"
                        + "      } catch(e) {}"
                        + "    }"
                        + "  }"
                        + "  if (actual === 'number' || actual === 'integer') {"
                        + "    if (typeof schema.minimum === 'number' && instance < schema.minimum)"
                        + "      errs.push(path + ': ' + instance + ' < minimum ' + schema.minimum);"
                        + "    if (typeof schema.maximum === 'number' && instance > schema.maximum)"
                        + "      errs.push(path + ': ' + instance + ' > maximum ' + schema.maximum);"
                        + "  }"
                        + "  if (actual === 'object' && schema.properties && typeof schema.properties === 'object') {"
                        + "    if (Array.isArray(schema.required)) {"
                        + "      for (var ri = 0; ri < schema.required.length; ri++) {"
                        + "        var req = schema.required[ri];"
                        + "        if (instance[req] === undefined) errs.push(path + ': missing required property \\'' + req + '\\'');"
                        + "      }"
                        + "    }"
                        + "    var keys = Object.keys(instance);"
                        + "    for (var ki = 0; ki < keys.length; ki++) {"
                        + "      var k = keys[ki];"
                        + "      if (schema.properties[k] !== undefined) {"
                        + "        validate(instance[k], schema.properties[k], path + '/' + k, errs);"
                        + "      } else if (schema.additionalProperties === false) {"
                        + "        errs.push(path + ': unexpected property \\'' + k + '\\' (additionalProperties: false)');"
                        + "      }"
                        + "    }"
                        + "  }"
                        + "  if (actual === 'array') {"
                        + "    if (typeof schema.minItems === 'number' && instance.length < schema.minItems)"
                        + "      errs.push(path + ': ' + instance.length + ' items < minItems ' + schema.minItems);"
                        + "    if (typeof schema.maxItems === 'number' && instance.length > schema.maxItems)"
                        + "      errs.push(path + ': ' + instance.length + ' items > maxItems ' + schema.maxItems);"
                        + "    if (schema.items && typeof schema.items === 'object') {"
                        + "      for (var ai = 0; ai < instance.length; ai++) {"
                        + "        validate(instance[ai], schema.items, path + '[' + ai + ']', errs);"
                        + "      }"
                        + "    }"
                        + "  }"
                        + "}"
                        // Entry point — pass parse errors through; otherwise
                        // validate ``parsed`` against ``schema`` and emit a
                        // schema-error sentinel on failure.
                        + "var parsed = toJS($.parsed);"
                        + "if (parsed && parsed.__parse_error === true) return parsed;"
                        + "var schema = toJS($.schema);"
                        + "if (!schema || typeof schema !== 'object') return parsed;"
                        + "var errs = []; validate(parsed, schema, '', errs);"
                        + "if (errs.length > 0) {"
                        + "  return {__parse_error: true, reason: 'schema: ' + errs.join('; ')};"
                        + "}"
                        + "return parsed;");
    }

    /**
     * Parse an LLM's text output into a JSON object, with a structured ``__parse_error`` sentinel
     * on failure that the downstream parseGate SWITCH consumes.
     *
     * <p>Input contract: {@code $.llmOut} is the planner LLM's raw output (either an already-parsed
     * Map from JSON-mode or a string).
     *
     * <p>Returns either the parsed object or: {@code {__parse_error: true, reason: '...'}}.
     *
     * <p>Used by every PAC ``generate`` op (PAC compiles to LLM_CHAT_COMPLETE → parse INLINE →
     * SWITCH). /dg #10 extracted it from a string-literal inside ``PlanAndCompileTask.java`` into
     * this method so quoting bugs don't break every plan one typo away.
     */
    public static String parseLlmOutputScript() {
        return "(function(){ var r = $.llmOut; if (r == null || r === '')"
                + " return {__parse_error: true, reason: 'empty LLM output'};"
                + " try { var p = typeof r === 'string' ? JSON.parse(r) : r;"
                + " if (!p || typeof p !== 'object' || Object.keys(p).length === 0)"
                + " return {__parse_error: true, reason: 'empty JSON object'};"
                + " return p; } catch(e) { return {__parse_error: true, reason: 'JSON parse: ' + e.message}; } })()";
    }

    /**
     * Build the planner-context aggregator script.
     *
     * <p>Input contract: {@code $.entries} is a list of per-entry descriptors produced by {@code
     * MultiAgentCompiler.emitPlannerContextBuilder}. Each entry is either:
     *
     * <ul>
     *   <li>{@code {type: 'text', text: <literal>}} — inlined verbatim.
     *   <li>{@code {type: 'url', url, body, statusCode, required, maxBytes}} — the {@code body} and
     *       {@code statusCode} fields are Conductor templates that have already been resolved to
     *       the HTTP fetch output by the time the INLINE script runs.
     * </ul>
     *
     * <p>The script:
     *
     * <ul>
     *   <li>Coerces non-string bodies (JSON-parsed Maps) to strings via {@code JSON.stringify} —
     *       the planner gets text, not a Java Map.
     *   <li>Truncates each body at {@code maxBytes} with a {@code [doc truncated]} marker.
     *   <li>For non-{@code required=false} URLs whose fetch failed (non-2xx status or missing
     *       body), substitutes {@code [doc unavailable]} so the planner sees an explicit gap
     *       instead of silent omission.
     *   <li>Returns {@code {result: <concatenated markdown>}}.
     * </ul>
     */
    public static String plannerContextBuilderScript() {
        return iife(
                // Reuse the toJS walker pattern from schemaValidatorScript —
                // Conductor hands us Java Map/List values whose .keySet /
                // .iterator behave differently than native JS objects.
                "function toJS(v) {"
                        + "  if (v === null || v === undefined) return v;"
                        + "  if (typeof v !== 'object') return v;"
                        + "  if (typeof v.keySet === 'function' && typeof v.get === 'function') {"
                        + "    var out = {}; var it = v.keySet().iterator();"
                        + "    while (it.hasNext()) { var k = it.next(); out[String(k)] = toJS(v.get(k)); }"
                        + "    return out;"
                        + "  }"
                        + "  if (typeof v.iterator === 'function' && typeof v.size === 'function'"
                        + "      && typeof v.keySet !== 'function') {"
                        + "    var arr = []; var lit = v.iterator();"
                        + "    while (lit.hasNext()) arr.push(toJS(lit.next()));"
                        + "    return arr;"
                        + "  }"
                        + "  return v;"
                        + "}"
                        + "function stringify(b) {"
                        + "  if (b === null || b === undefined) return '';"
                        + "  if (typeof b === 'string') return b;"
                        + "  try { return JSON.stringify(b); } catch (e) { return String(b); }"
                        + "}"
                        + "var entries = toJS($.entries) || [];"
                        + "var parts = [];"
                        + "for (var i = 0; i < entries.length; i++) {"
                        + "  var e = entries[i];"
                        + "  if (!e) continue;"
                        + "  if (e.type === 'text') {"
                        + "    if (e.text == null) continue;"
                        + "    parts.push('### Inline note ' + (i+1) + '\\n' + e.text);"
                        + "    continue;"
                        + "  }"
                        + "  if (e.type === 'url') {"
                        + "    var url = e.url || ('doc ' + (i+1));"
                        + "    var status = e.statusCode;"
                        + "    var rawBody = e.body;"
                        // statusCode is a JS Number when set, but Conductor
                        // can leave it as the literal template string when
                        // the underlying HTTP task didn't run (optional
                        // task skipped). Detect both shapes.
                        + "    var statusOk = (status == null) || "
                        + "        (typeof status === 'number' && status >= 200 && status < 300);"
                        // ``$ + {`` is split across two literals so the JS
                        // source string we hand Conductor doesn't itself
                        // contain ``${``. Conductor's ParametersUtils scans
                        // ALL input-parameter values for ``${path}`` and
                        // interpolates them — it doesn't understand JS
                        // quoting, so a literal ``${`` inside our script
                        // would be eaten at task-dispatch time, breaking
                        // the script. Defer the concat to JS runtime.
                        + "    var TPL_OPEN = '$' + '{';"
                        + "    var unresolvedTpl = typeof status === 'string' && status.indexOf(TPL_OPEN) === 0;"
                        + "    if (unresolvedTpl) {"
                        + "      parts.push('### ' + url + '\\n[doc unavailable]');"
                        + "      continue;"
                        + "    }"
                        + "    if (!statusOk) {"
                        + "      if (e.required === false) {"
                        + "        parts.push('### ' + url + '\\n[doc unavailable]');"
                        + "      } else {"
                        + "        parts.push('### ' + url + '\\n[doc fetch failed status=' + status + ']');"
                        + "      }"
                        + "      continue;"
                        + "    }"
                        + "    var body = stringify(rawBody);"
                        // Same ``${`` avoidance — match the unresolved-template
                        // string ``${body}`` by building it at JS runtime.
                        + "    if (!body || body === TPL_OPEN + 'body}') {"
                        + "      parts.push('### ' + url + '\\n[doc unavailable]');"
                        + "      continue;"
                        + "    }"
                        + "    var max = (typeof e.maxBytes === 'number') ? e.maxBytes : 16384;"
                        + "    var truncated = false;"
                        + "    if (body.length > max) {"
                        + "      body = body.substring(0, max);"
                        + "      truncated = true;"
                        + "    }"
                        + "    parts.push('### ' + url + '\\n' + body + (truncated ? '\\n[doc truncated]' : ''));"
                        + "  }"
                        + "}"
                        // Return the joined string directly — Conductor's
                        // INLINE wraps the script's return value as
                        // ``outputData = {result: <return>}`` automatically.
                        // Returning ``{result: …}`` from the script would
                        // produce a double-nested ``output.result.result``.
                        + "return parts.join('\\n\\n');");
    }

    /** Build the regex guardrail JavaScript. */
    public static String regexGuardrailScript(
            String patternsJson,
            String mode,
            String onFail,
            String message,
            int maxRetries,
            String guardrailName) {
        String messageJs = toJson(message);
        String nameJs = toJson(guardrailName);

        return iife(
                "  var content = $.content;"
                        + "  var iteration = $.iteration;"
                        + "  var patterns = "
                        + patternsJson
                        + ";"
                        + "  var mode = "
                        + toJson(mode)
                        + ";"
                        + "  var on_fail = "
                        + toJson(onFail)
                        + ";"
                        + "  var message = "
                        + messageJs
                        + ";"
                        + "  var max_retries = "
                        + maxRetries
                        + ";"
                        + "  var guardrail_name = "
                        + nameJs
                        + ";"
                        + "  var matched = false;"
                        + "  for (var i = 0; i < patterns.length; i++) {"
                        + "    if (new RegExp(patterns[i]).test(content)) { matched = true; break; }"
                        + "  }"
                        + "  var failed = (mode === 'block' && matched) || (mode === 'allow' && !matched);"
                        + "  if (!failed) {"
                        + "    return {passed: true, message: '', on_fail: 'pass',"
                        + "            fixed_output: null, guardrail_name: '', should_continue: false};"
                        + "  }"
                        + "  var actual_fail = on_fail;"
                        + "  if (on_fail === 'retry' && iteration >= max_retries) actual_fail = 'raise';"
                        + "  if (on_fail === 'fix') actual_fail = 'raise';"
                        + "  return {passed: false, message: message, on_fail: actual_fail,"
                        + "          fixed_output: null, guardrail_name: guardrail_name,"
                        + "          should_continue: actual_fail === 'retry'};");
    }

    /** Build the LLM guardrail parser JavaScript. */
    public static String llmGuardrailParserScript(
            String onFail, int maxRetries, String guardrailName) {
        return iife(
                "  var raw = $.llm_result;"
                        + "  var iteration = $.iteration;"
                        + "  var on_fail_mode = "
                        + toJson(onFail)
                        + ";"
                        + "  var max_retries = "
                        + maxRetries
                        + ";"
                        + "  var guardrail_name = "
                        + toJson(guardrailName)
                        + ";"
                        + "  var data;"
                        + "  try { data = typeof raw === 'string' ? JSON.parse(raw) : raw; }"
                        + "  catch(e) { data = {passed: false, reason: 'Unparseable LLM response'}; }"
                        + "  if (!!data.passed) {"
                        + "    return {passed: true, message: '', on_fail: 'pass',"
                        + "            fixed_output: null, guardrail_name: '', should_continue: false};"
                        + "  }"
                        + "  var actual_fail = on_fail_mode;"
                        + "  if (on_fail_mode === 'retry' && iteration >= max_retries) actual_fail = 'raise';"
                        + "  if (on_fail_mode === 'fix') actual_fail = 'raise';"
                        + "  return {passed: false, message: data.reason || data.message || 'LLM guardrail failed',"
                        + "          on_fail: actual_fail, fixed_output: null,"
                        + "          guardrail_name: guardrail_name, should_continue: actual_fail === 'retry'};");
    }

    /**
     * Build JavaScript that formats tool call inputs into a readable string for guardrail
     * evaluation.
     *
     * <p>Input: {@code $.tool_calls} — array of {@code {name, input}} objects. Output: {@code
     * {formatted: "Tool: name\nArguments: {...}\n---\n...", count: N}}
     */
    public static String formatToolCallsScript() {
        return iife(
                "  var tcs = $.tool_calls || [];"
                        + "  var lines = [];"
                        + "  for (var i = 0; i < tcs.length; i++) {"
                        + "    var tc = tcs[i];"
                        + "    var args = tc.inputParameters || tc.input || {};"
                        + "    var cleaned = {};"
                        + "    for (var k in args) { if (k !== 'method') cleaned[k] = args[k]; }"
                        + "    lines.push('Tool: ' + tc.name);"
                        + "    lines.push('Arguments: ' + JSON.stringify(cleaned));"
                        + "    lines.push('---');"
                        + "  }"
                        + "  return {formatted: lines.join('\\n'), count: tcs.length};");
    }

    /** Build the guardrail retry feedback JavaScript. */
    public static String guardrailRetryScript() {
        return iife(
                "  return {result: '[Output validation failed: '"
                        + "    + $.guardrail_message"
                        + "    + '. Please revise your response.]'};");
    }

    /** Build the guardrail fix pass-through JavaScript. */
    public static String guardrailFixScript() {
        return "(function() { return {result: $.fixed_output}; })()";
    }

    /**
     * Normalize a framework/custom guardrail worker result into AgentSpan's internal guardrail
     * contract.
     */
    public static String customGuardrailNormalizeScript() {
        return iife(
                "  var raw = $.worker_output;"
                        + "  var guardrailName = $.guardrail_name || 'guardrail';"
                        + "  var defaultOnFail = $.default_on_fail || 'retry';"
                        + "  if (raw == null) {"
                        + "    return {passed: true, message: '', on_fail: null,"
                        + "            fixed_output: null, guardrail_name: guardrailName,"
                        + "            should_continue: false};"
                        + "  }"
                        + "  if (typeof raw === 'object' && raw.result !== undefined"
                        + "      && raw.on_fail === undefined && raw.onFail === undefined"
                        + "      && raw.tripwire_triggered === undefined && raw.tripwireTriggered === undefined"
                        + "      && raw.output_info === undefined && raw.outputInfo === undefined) {"
                        + "    raw = raw.result;"
                        + "  }"
                        + "  if (raw != null && typeof raw === 'object'"
                        + "      && (raw.on_fail !== undefined || raw.onFail !== undefined"
                        + "          || raw.passed !== undefined || raw.fixed_output !== undefined"
                        + "          || raw.fixedOutput !== undefined)) {"
                        + "    var existingOnFail = raw.on_fail !== undefined ? raw.on_fail : raw.onFail;"
                        + "    var fixedOutput = raw.fixed_output !== undefined ? raw.fixed_output : raw.fixedOutput;"
                        + "    var passed = raw.passed !== false && (existingOnFail == null || existingOnFail === 'pass');"
                        + "    return {passed: passed, message: raw.message || '', on_fail: existingOnFail,"
                        + "            fixed_output: fixedOutput, guardrail_name: raw.guardrail_name || raw.guardrailName || guardrailName,"
                        + "            should_continue: existingOnFail === 'retry'};"
                        + "  }"
                        + "  if (raw != null && typeof raw === 'object'"
                        + "      && (raw.tripwire_triggered !== undefined || raw.tripwireTriggered !== undefined"
                        + "          || raw.output_info !== undefined || raw.outputInfo !== undefined)) {"
                        + "    var tripwire = raw.tripwire_triggered === true || raw.tripwireTriggered === true;"
                        + "    var info = raw.output_info !== undefined ? raw.output_info : raw.outputInfo;"
                        + "    var reason = '';"
                        + "    if (typeof info === 'string') {"
                        + "      reason = info;"
                        + "    } else if (info != null && typeof info === 'object' && info.reason != null) {"
                        + "      reason = String(info.reason);"
                        + "    }"
                        + "    if (!tripwire) {"
                        + "      return {passed: true, message: reason, on_fail: null,"
                        + "              fixed_output: null, guardrail_name: guardrailName,"
                        + "              should_continue: false};"
                        + "    }"
                        + "    return {passed: false, message: reason || (guardrailName + ' triggered'),"
                        + "            on_fail: defaultOnFail, fixed_output: null,"
                        + "            guardrail_name: guardrailName, should_continue: defaultOnFail === 'retry'};"
                        + "  }"
                        + "  return {passed: true, message: '', on_fail: null,"
                        + "          fixed_output: null, guardrail_name: guardrailName,"
                        + "          should_continue: false};");
    }

    /** Normalize a framework callable's instruction result to a plain string. */
    public static String normalizeInstructionsScript() {
        return iife(
                "  var raw = $.worker_output;"
                        + "  if (raw == null) return '';"
                        + "  if (typeof raw === 'string') return raw;"
                        + "  if (typeof raw === 'number' || typeof raw === 'boolean') return String(raw);"
                        + "  if (typeof raw === 'object' && raw.result !== undefined) {"
                        + "    if (raw.result == null) return '';"
                        + "    if (typeof raw.result === 'string') return raw.result;"
                        + "    if (typeof raw.result === 'number' || typeof raw.result === 'boolean') return String(raw.result);"
                        + "    return JSON.stringify(raw.result);"
                        + "  }"
                        + "  return JSON.stringify(raw);");
    }

    /**
     * Build the output resolution JavaScript. Checks if a guardrail fix or human edit stored a
     * replacement output in workflow variables, and uses it instead of the raw LLM output.
     */
    public static String resolveOutputScript() {
        return iife(
                "  var fixed = $.fixed_output;"
                        + "  var edited = $.edited_output;"
                        + "  if (edited != null && edited !== '' && edited !== 'null') {"
                        + "    return {result: edited, finishReason: 'STOP'};"
                        + "  } else if (fixed != null && fixed !== '' && fixed !== 'null') {"
                        + "    return {result: fixed, finishReason: 'STOP'};"
                        + "  } else {"
                        + "    return {result: $.llm_result, finishReason: $.finish_reason};"
                        + "  }");
    }

    /**
     * Build the tool call enrichment JavaScript. Injects {@code _agent_state} from {@code
     * $.agentState} into worker (SIMPLE) tasks so that ToolContext.state is available server-side.
     * Injects {@code _allowed_commands} for CLI (SIMPLE) tasks so that per-agent command whitelists
     * are enforced even when multiple agents share the same worker.
     */
    public static String enrichToolsScript(
            String httpConfigJson,
            String mcpConfigJson,
            String mediaConfigJson,
            String agentToolConfigJson,
            String ragConfigJson,
            String cliConfigJson,
            String humanConfigJson,
            String wmqConfigJson,
            String knownToolNamesJson) {
        return iife(
                "  var httpCfg = "
                        + httpConfigJson
                        + ";"
                        + "  var mcpCfg = "
                        + mcpConfigJson
                        + ";"
                        + "  var mediaCfg = "
                        + mediaConfigJson
                        + ";"
                        + "  var agentToolCfg = "
                        + agentToolConfigJson
                        + ";"
                        + "  var ragCfg = "
                        + ragConfigJson
                        + ";"
                        + "  var cliCfg = "
                        + cliConfigJson
                        + ";"
                        + "  var humanCfg = "
                        + humanConfigJson
                        + ";"
                        + "  var wmqCfg = "
                        + wmqConfigJson
                        + ";"
                        + "  var knownNames = "
                        + knownToolNamesJson
                        + ";"
                        // Convert inert #{NAME} credential markers to ${workflow.secrets.NAME}
                        // references
                        // at dynamic-task emission. The '$' + '{...' split is load-bearing: the
                        // contiguous
                        // secret-reference literal must never appear in this script's SOURCE (the
                        // INLINE
                        // task's input), or conductor's substituteSecrets would resolve it to
                        // plaintext at
                        // the INLINE's hand-off and the plaintext would persist via the script's
                        // output.
                        + "  var _sec = function(h) { var o = {}; h = h || {};"
                        + "    for (var _hk in h) { o[_hk] = String(h[_hk]).replace(/#\\{([A-Za-z_][A-Za-z0-9_.]*)\\}/g,"
                        + "      function(_m, _n) { return '$' + '{workflow.secrets.' + _n + '}'; }); }"
                        + "    return o; };"
                        + "  var _plain = function(v) { var o = {}; if (v == null) return o;"
                        + "    if (typeof v.entrySet === 'function') { var it = v.entrySet().iterator();"
                        + "      while (it.hasNext()) { var e = it.next(); o[String(e.getKey())] = e.getValue(); } return o; }"
                        + "    for (var k in v) { if (Object.prototype.hasOwnProperty.call(v, k)) o[k] = v[k]; } return o; };"
                        + "  var agentState = $.agentState || {};"
                        + "  var tcs = $.toolCalls || [];"
                        + "  var result = [];"
                        + "  for (var i = 0; i < tcs.length; i++) {"
                        + "    var tc = tcs[i]; var n = tc.name;"
                        // Validate the tool name. If the LLM hallucinates a name we
                        // didn't expose, replace the SIMPLE task with an INLINE task
                        // that returns an error to the conversation. Without this the
                        // SIMPLE task gets queued under the unknown name with no worker
                        // polling for it and the workflow hangs forever.
                        + "    var isCfg = !!(httpCfg[n] || mcpCfg[n] || agentToolCfg[n] ||"
                        + "                  mediaCfg[n] || ragCfg[n] || humanCfg[n] || wmqCfg[n]);"
                        // Reject any name not in the agent's declared tools. The
                        // previous gate (``hasKnownNames``) skipped this check when
                        // ``knownNames`` was empty, which allowed an agent declared
                        // with ``tools=[]`` and only ``prefill_tools`` to dispatch
                        // hallucinated calls to the prefill workers (registered for
                        // prefill execution but never advertised to the LLM). With
                        // this tighter check, an empty knownNames means NO tool is
                        // callable by the LLM — exactly the prefill-only contract.
                        + "    var isUnknown = !isCfg && !(knownNames && knownNames[n]);"
                        + "    if (isUnknown) {"
                        + "      var availList = [];"
                        + "      for (var nm in knownNames) availList.push(nm);"
                        + "      var unknownErr = ('Unknown tool \\'' + n + '\\'. Available tools: ' + availList.join(', '));"
                        + "      var errTask = {name: n, taskReferenceName: tc.taskReferenceName || n,"
                        + "                     type: 'INLINE',"
                        + "                     inputParameters: {evaluatorType: 'graaljs',"
                        + "                       expression: 'function e(){return {result: $.errorMessage, is_error: true};} e();',"
                        + "                       errorMessage: unknownErr},"
                        + "                     optional: true};"
                        + "      result.push(errTask);"
                        + "      continue;"
                        + "    }"
                        // A model may call the same tool more than once in a turn. Dynamic fork
                        // references must be unique or JOIN silently aliases results.
                        + "    var t = {name: n, taskReferenceName: (tc.taskReferenceName || n) + '_' + i,"
                        + "             type: tc.type || 'SIMPLE', inputParameters: tc.inputParameters || {},"
                        + "             optional: true,"
                        + "             retryCount: 2, retryLogic: 'LINEAR_BACKOFF',"
                        + "             retryDelaySeconds: 2};"
                        + "    if (httpCfg[n]) {"
                        + "      t.type = 'HTTP';"
                        + "      var hc = httpCfg[n];"
                        + "      var hargs = _plain(tc.inputParameters);"
                        + "      var huri = hc.url || '';"
                        + "      var hmethod = String(hc.method || 'GET').toUpperCase();"
                        + "      var bodyless = hmethod === 'GET' || hmethod === 'HEAD' || hmethod === 'DELETE' || hmethod === 'OPTIONS';"
                        + "      var hbody = {}; for (var ha in hargs) { if (ha !== 'method') hbody[ha] = hargs[ha]; }"
                        // Optional URI templating: pathTemplate consumes {param}s from
                        // the LLM's arguments (URL-encoded); queryParams append the
                        // listed args as a query string. Consumed args leave the body.
                        // Tools without these keys keep the static-uri/args-as-body
                        // shape unchanged.
                        + "      if (hc.pathTemplate || hc.queryParams || bodyless) {"
                        + "        var consumed = {method:true};"
                        + "        if (hc.pathTemplate) {"
                        + "          huri = huri + hc.pathTemplate.replace(/\\{(\\w+)\\}/g,"
                        + "            function(m, k) { consumed[k] = true;"
                        + "              return encodeURIComponent(String(hargs[k] == null ? '' : hargs[k])); });"
                        + "        }"
                        + "        var queryKeys = hc.queryParams || (bodyless ? Object.keys(hargs) : []);"
                        + "        if (queryKeys.length) {"
                        + "          var qs = [];"
                        + "          for (var qi = 0; qi < queryKeys.length; qi++) {"
                        + "            var qk = queryKeys[qi]; var qv = hargs[qk];"
                        + "            if (qk === 'method' || qv == null || qv === '') continue; consumed[qk] = true;"
                        + "            if (Array.isArray(qv)) { for (var qj = 0; qj < qv.length; qj++) { if (qv[qj] != null && qv[qj] !== '') qs.push(encodeURIComponent(qk) + '=' + encodeURIComponent(String(qv[qj]))); } }"
                        + "            else { var qtext = typeof qv === 'object' ? JSON.stringify(qv) : String(qv); if (qtext != null) qs.push(encodeURIComponent(qk) + '=' + encodeURIComponent(qtext)); }"
                        + "          }"
                        + "          if (qs.length) { var hash = huri.indexOf('#'); var base = hash >= 0 ? huri.substring(0, hash) : huri; var frag = hash >= 0 ? huri.substring(hash) : ''; huri = base + (base.indexOf('?') >= 0 && !base.endsWith('?') ? '&' : '?') + qs.join('&') + frag; }"
                        + "        }"
                        + "        hbody = {};"
                        + "        if (!bodyless) { for (var hk in hargs) { if (!consumed[hk]) hbody[hk] = hargs[hk]; } }"
                        + "      }"
                        + "      var hrequest = {"
                        + "        uri: huri,"
                        + "        method: hmethod,"
                        + "        headers: _sec(hc.headers),"
                        + "        accept: hc.accept || 'application/json',"
                        + "        contentType: hc.contentType || 'application/json',"
                        + "        connectionTimeOut: 30000,"
                        + "        readTimeOut: 30000};"
                        + "      if (!bodyless) hrequest.body = hbody;"
                        + "      t.inputParameters = {http_request: hrequest};"
                        + "    } else if (mcpCfg[n]) {"
                        + "      t.type = 'CALL_MCP_TOOL';"
                        + "      t.name = 'call_mcp_tool';"
                        + "      t.inputParameters = {"
                        + "        mcpServer: mcpCfg[n].mcpServer || '',"
                        + "        method: n,"
                        + "        arguments: Object.keys(_plain(tc.inputParameters)).filter(function(k) { return k !== 'method'; }).reduce(function(a, k) { a[k] = _plain(tc.inputParameters)[k]; return a; }, {}),"
                        + "        headers: _sec(mcpCfg[n].headers)};"
                        + "    } else if (agentToolCfg[n]) {"
                        + "      t.type = 'SUB_WORKFLOW';"
                        + "      t.name = agentToolCfg[n].workflowName;"
                        + "      t.subWorkflowParam = {name: agentToolCfg[n].workflowName, version: 1};"
                        + "      var _p = tc.inputParameters || {};"
                        + "      var _req = _p.request || _p.prompt || _p.message || _p.input || _p.query || '';"
                        + "      if (!_req && typeof _p === 'object') {"
                        + "        for (var _k in _p) {"
                        + "          if (_k !== 'method' && typeof _p[_k] === 'string' && _p[_k].length > 0) { _req = _p[_k]; break; }"
                        + "        }"
                        + "      }"
                        + "      if (!_req || _req === '{}' || _req === JSON.stringify(_p)) {"
                        + "        _req = $.userPrompt || '';"
                        + "      }"
                        + "      if (!_req) _req = JSON.stringify(_p);"
                        + "      t.inputParameters = {"
                        + "        prompt: _req,"
                        + "        session_id: $.session_id || '',"
                        // Current UTC date, evaluated when this dispatch script runs —
                        // sub-agent prompts reference ${workflow.input.__today__} to
                        // anchor relative-date queries without drifting from a date
                        // computed at compile/boot time.
                        + "        __today__: new Date().toISOString().slice(0, 10)};"
                        + "      if (agentToolCfg[n].retryCount !== undefined) t.retryCount = agentToolCfg[n].retryCount;"
                        + "      if (agentToolCfg[n].retryDelaySeconds !== undefined) t.retryDelaySeconds = agentToolCfg[n].retryDelaySeconds;"
                        + "      if (agentToolCfg[n].optional !== undefined) t.optional = agentToolCfg[n].optional;"
                        + "    } else if (mediaCfg[n]) {"
                        + "      t.type = mediaCfg[n].taskType;"
                        + "      t.name = mediaCfg[n].taskType.toLowerCase();"
                        + "      var merged = {};"
                        + "      var defs = mediaCfg[n].defaults || {};"
                        + "      for (var k in defs) { merged[k] = defs[k]; }"
                        + "      var inp = tc.inputParameters || {};"
                        + "      for (var k in inp) { merged[k] = inp[k]; }"
                        + "      t.inputParameters = merged;"
                        + "    } else if (ragCfg[n]) {"
                        + "      t.type = ragCfg[n].taskType;"
                        + "      t.name = ragCfg[n].taskType.toLowerCase();"
                        + "      var merged = {};"
                        + "      var defs = ragCfg[n].defaults || {};"
                        + "      for (var k in defs) { merged[k] = defs[k]; }"
                        + "      var inp = tc.inputParameters || {};"
                        + "      for (var k in inp) { merged[k] = inp[k]; }"
                        + "      t.inputParameters = merged;"
                        + "    } else if (humanCfg[n]) {"
                        + "      t.type = 'HUMAN';"
                        + "      t.name = n;"
                        + "      var hDef = {assignmentCompletionStrategy: 'LEAVE_OPEN',"
                        + "                  displayName: humanCfg[n].displayName || n,"
                        + "                  userFormTemplate: {version: 0}};"
                        + "      var hInputs = {__humanTaskDefinition: hDef};"
                        + "      hInputs.response_schema = {type: 'object', properties: {"
                        + "        response: {type: 'string', title: 'Response',"
                        + "                   description: 'Provide your response'}}};"
                        + "      hInputs.response_ui_schema = {'ui:order': ['response'],"
                        + "        response: {'ui:widget': 'textarea'}};"
                        + "      var inp = tc.inputParameters || {};"
                        + "      for (var k in inp) { hInputs[k] = inp[k]; }"
                        + "      if (humanCfg[n].description) hInputs._description = humanCfg[n].description;"
                        + "      t.inputParameters = hInputs;"
                        + "      t.optional = false;"
                        + "    } else if (wmqCfg[n]) {"
                        + "      t.type = 'PULL_WORKFLOW_MESSAGES';"
                        + "      t.name = n;"
                        + "      t.inputParameters = {batchSize: wmqCfg[n].batchSize || 1};"
                        + "      t.retryCount = 0;"
                        + "      t.optional = false;"
                        + "    }"
                        + "    if (t.type === 'SIMPLE') {"
                        + "      t.inputParameters._agent_state = agentState;"
                        + "      if (cliCfg[n]) { t.inputParameters._allowed_commands = cliCfg[n].allowedCommands; }"
                        + "    }"
                        + "    t.inputParameters._agent_tool_name = n;"
                        + "    result.push(t);"
                        + "  }"
                        + "  return {dynamicTasks: result};");
    }

    /** Build tool filter inline script. */
    public static String filterToolsScript(String allSpecsJson) {
        return iife(
                "  var allTools = "
                        + allSpecsJson
                        + ";"
                        + "  var raw = $.selectedNames || '[]';"
                        + "  var selected;"
                        + "  try { selected = typeof raw === 'string' ? JSON.parse(raw) : raw; }"
                        + "  catch(e) { selected = []; }"
                        + "  if (!Array.isArray(selected)) {"
                        + "    if (selected && selected.selected_tools) selected = selected.selected_tools;"
                        + "    else selected = [];"
                        + "  }"
                        + "  var nameSet = {};"
                        + "  for (var i = 0; i < selected.length; i++) nameSet[selected[i]] = true;"
                        + "  var result = [];"
                        + "  for (var i = 0; i < allTools.length; i++) {"
                        + "    if (nameSet[allTools[i].name]) result.push(allTools[i]);"
                        + "  }"
                        + "  if (result.length === 0) result = allTools;"
                        + "  return {tools: result};");
    }

    /** Build round-robin select script (simple: iteration % N). */
    public static String roundRobinSelectScript(int numAgents) {
        return "(function() { return String($.iteration % " + numAgents + "); })()";
    }

    /** Build random select script (simple: random * N). */
    public static String randomSelectScript(int numAgents) {
        return "(function() { return String(Math.floor(Math.random() * " + numAgents + ")); })()";
    }

    /** Build constrained round-robin select script with allowed transitions. */
    public static String constrainedRoundRobinScript(String idxMapJson, int numAgents) {
        return iife(
                "  var allowed = "
                        + idxMapJson
                        + ";"
                        + "  var last = String($.last_agent);"
                        + "  var candidates = allowed[last];"
                        + "  if (!candidates) candidates = Array.from(Array("
                        + numAgents
                        + ").keys());"
                        + "  var idx = $.iteration % candidates.length;"
                        + "  return String(candidates[idx]);");
    }

    /** Build constrained random select script with allowed transitions. */
    public static String constrainedRandomScript(String idxMapJson, int numAgents) {
        return iife(
                "  var allowed = "
                        + idxMapJson
                        + ";"
                        + "  var last = String($.last_agent);"
                        + "  var candidates = allowed[last];"
                        + "  if (!candidates) candidates = Array.from(Array("
                        + numAgents
                        + ").keys());"
                        + "  var pick = candidates[Math.floor(Math.random() * candidates.length)];"
                        + "  return String(pick);");
    }

    /** Build concat script for multi-agent transcript accumulation. */
    public static String concatScript(String agentName) {
        return "(function() { var r = $.response; "
                + "r = (r == null || r === undefined) ? '' : (typeof r === 'object' ? JSON.stringify(r) : String(r)); "
                + "return $.prev + '\\n\\n["
                + agentName
                + "]: ' + r; })()";
    }

    /**
     * Swarm-aware concat script. Unlike {@link #concatScript}, a turn that ended on tool calls
     * (result {@code []}/{@code {}}/empty) contributes nothing instead of polluting the transcript
     * with {@code [agent]: []}, and a handoff is annotated as {@code [agent -> target]: <transfer
     * message>} so the receiving agent sees the delegation intent, not just the routing.
     *
     * <p>Input: {@code prev} → conversation so far, {@code response} → agent result, {@code
     * is_transfer}/{@code transfer_to}/{@code transfer_message} → the agent sub-workflow's transfer
     * outputs. Output: the updated conversation string.
     */
    public static String swarmConcatScript(String agentName) {
        return "(function() { var r = $.response; "
                + "r = (r == null || r === undefined) ? '' : (typeof r === 'object' ? JSON.stringify(r) : String(r)); "
                + "if (r === '[]' || r === '{}') r = ''; "
                + "var out = $.prev; "
                + "if (r !== '') out = out + '\\n\\n["
                + agentName
                + "]: ' + r; "
                + "var isT = ($.is_transfer === true || $.is_transfer === 'true'); "
                + "var t = ($.transfer_to == null) ? '' : String($.transfer_to); "
                + "if (isT && t !== '') { "
                + "var m = ($.transfer_message == null) ? '' : String($.transfer_message); "
                + "out = out + '\\n\\n["
                + agentName
                + " -> ' + t + ']' + (m !== '' ? ': ' + m : ''); } "
                + "return out; })()";
    }

    /**
     * Normalize a coordinator/router decision to a canonical agent name or {@code DONE}.
     *
     * <p>Routers (LLM or user worker) are prompted to answer with a single agent name or DONE, but
     * real outputs arrive wrapped ({@code **backend_dev**}, quoted, trailing period), in the wrong
     * case, embedded in prose ("route to backend_dev"), or hallucinated (an agent that doesn't
     * exist). Before this normalizer, any unrecognized output silently fell into the SWITCH default
     * case and ran the FIRST agent while the conversation annotation recorded the bogus name — fuel
     * for infinite delegation loops.
     *
     * <p>Algorithm (deterministic, no LLM): coerce to string; strip wrapping non-word characters
     * from both ends; case-insensitive exact match against agent names → canonical name;
     * case-insensitive {@code done} → {@code DONE}; otherwise a word-boundary substring scan,
     * longest name first with matched spans blanked (so a name that is a substring of another —
     * {@code dev} vs {@code backend_dev} — can't double-match); exactly one distinct agent matched
     * → that agent; anything else → {@code DONE} (terminating gracefully beats ghost work).
     * Matching uses manual boundary checks, never a regex built from agent names (metacharacter
     * injection).
     *
     * <p>MUST return a plain string: Conductor INLINE output lands under {@code output.result}, and
     * the DO_WHILE term condition compares {@code $.<norm>['result'] != 'DONE'}. A map-shaped
     * return would make that comparison always true and recreate the infinite loop. The raw router
     * output remains visible as this task's {@code raw} input in the execution UI.
     *
     * <p>Input: {@code raw} → the router task's {@code output.result}. Output: canonical agent name
     * or {@code DONE}.
     */
    public static String normalizeRouterDecisionScript(List<String> agentNames) {
        return iife(
                "var agents = "
                        + toJson(agentNames)
                        + ";"
                        + "var raw = $.raw;"
                        + "if (raw == null) return 'DONE';"
                        + "var s = String(raw).trim();"
                        + "var isWord = function(c) {"
                        + "  return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')"
                        + "      || (c >= '0' && c <= '9') || c === '_';"
                        + "};"
                        // Strip wrapping non-word chars from BOTH ends (markdown, quotes, punct).
                        + "var start = 0, end = s.length;"
                        + "while (start < end && !isWord(s.charAt(start))) start++;"
                        + "while (end > start && !isWord(s.charAt(end - 1))) end--;"
                        + "s = s.substring(start, end);"
                        + "if (s === '') return 'DONE';"
                        + "var lower = s.toLowerCase();"
                        // Exact match first — also lets an agent named 'done_checker' route
                        // before the DONE check below.
                        + "for (var i = 0; i < agents.length; i++) {"
                        + "  if (lower === String(agents[i]).toLowerCase()) return agents[i];"
                        + "}"
                        + "if (lower === 'done') return 'DONE';"
                        // Word-boundary substring scan, longest name first; blank matched spans
                        // so shorter names can't re-match inside a longer match.
                        + "var sorted = agents.slice().sort(function(a, b) {"
                        + "  return String(b).length - String(a).length;"
                        + "});"
                        + "var scan = lower;"
                        + "var matched = [];"
                        + "for (var j = 0; j < sorted.length; j++) {"
                        + "  var name = String(sorted[j]);"
                        + "  var nl = name.toLowerCase();"
                        + "  var from = 0; var found = false;"
                        + "  while (true) {"
                        + "    var idx = scan.indexOf(nl, from);"
                        + "    if (idx < 0) break;"
                        + "    var beforeOk = idx === 0 || !isWord(scan.charAt(idx - 1));"
                        + "    var afterIdx = idx + nl.length;"
                        + "    var afterOk = afterIdx >= scan.length || !isWord(scan.charAt(afterIdx));"
                        + "    if (beforeOk && afterOk) {"
                        + "      found = true;"
                        + "      scan = scan.substring(0, idx)"
                        + "          + Array(nl.length + 1).join('#') + scan.substring(afterIdx);"
                        + "      from = afterIdx;"
                        + "    } else {"
                        + "      from = idx + 1;"
                        + "    }"
                        + "  }"
                        + "  if (found) matched.push(name);"
                        + "}"
                        + "if (matched.length === 1) return matched[0];"
                        + "return 'DONE';");
    }

    /**
     * Extract the {@code message} argument from the first {@code _transfer_to_} tool call in an
     * LLM's {@code toolCalls} output. Mirrors the SDK check_transfer worker's first-wins selection
     * so the extracted message always belongs to the transfer that actually happens.
     *
     * <p>Input: {@code tool_calls} → {@code <llm>.output.toolCalls}. Output: the transfer message
     * string, or {@code ''} when there is no transfer call or it carries no message.
     */
    public static String extractTransferMessageScript() {
        return extractTransferMessageScript(null);
    }

    /**
     * Extract a transfer message only for an exact compiler-generated transfer name.
     *
     * <p>The allow-list is important: user tools are allowed to contain the transfer-looking
     * substring in their name, and those ordinary tools must never annotate or route a swarm
     * handoff merely because of their spelling.
     */
    public static String extractTransferMessageScript(Map<String, String> allowedTools) {
        String allowedCheck =
                allowedTools == null
                        ? "name.indexOf('_transfer_to_') < 0"
                        : "allowed[name] === undefined";
        // Java interop: tool call entries are Java Maps — use .get(k) with property fallback,
        // matching flatMergeContextScript.
        return iife(
                "var allowed="
                        + toJson(allowedTools == null ? Map.of() : allowedTools)
                        + ";var calls = $.tool_calls;"
                        + "if (calls == null) return '';"
                        + "for (var i = 0; i < calls.length; i++) {"
                        + "  var c = calls[i]; if (c == null) continue;"
                        + "  var name = (c.get ? c.get('name') : c.name);"
                        + "  name = (name == null) ? '' : String(name);"
                        + "  if ("
                        + allowedCheck
                        + ") continue;"
                        + "  var params = (c.get ? c.get('inputParameters') : c.inputParameters);"
                        + "  if (params == null) return '';"
                        + "  var m = (params.get ? params.get('message') : params.message);"
                        + "  return (m == null) ? '' : String(m);"
                        + "}"
                        + "return '';");
    }

    /**
     * Return the first valid generated SWARM transfer call. The allow-list is compiled from the
     * source agent's permitted transitions, so a hallucinated or disallowed transfer is never a
     * routing signal. The map shape is intentionally compiler-local; no agent-definition wire
     * contract is involved.
     */
    public static String detectTransferScript(Map<String, String> allowedTools) {
        return iife(
                "var allowed="
                        + toJson(allowedTools)
                        + ";var calls=$.tool_calls||[];"
                        + "for(var i=0;i<calls.length;i++){var c=calls[i]||{};"
                        + "var n=c.get?c.get('name'):c.name; n=n==null?'':String(n);"
                        + "if(allowed[n]===undefined) continue;"
                        + "var p=c.get?c.get('inputParameters'):c.inputParameters;"
                        + "var m=p==null?'':(p.get?p.get('message'):p.message);"
                        + "return {is_transfer:true,transfer_to:allowed[n],transfer_message:m==null?'':String(m)};}"
                        + "return {is_transfer:false,transfer_to:'',transfer_message:''};");
    }

    /** Build human task validation script for guardrails. */
    public static String humanValidateScript() {
        return iife(
                "  var raw = $.human_output;"
                        + "  if (!raw) return {needs_normalize: true, raw_text: ''};"
                        + "  var raw_text;"
                        + "  if (typeof raw === 'string') { raw_text = raw; }"
                        + "  else if (typeof raw.result === 'string') { raw_text = raw.result; }"
                        + "  else { var p = []; for (var k in raw) { p.push(k + ': ' + raw[k]); }"
                        + "         raw_text = p.join(', '); }"
                        + "  if (typeof raw === 'object' && typeof raw.approved === 'boolean') {"
                        + "    return {"
                        + "      needs_normalize: false,"
                        + "      approved: raw.approved,"
                        + "      edited_output: raw.edited_output || null,"
                        + "      reason: raw.reason || null,"
                        + "      raw_text: raw_text"
                        + "    };"
                        + "  }"
                        + "  if (typeof raw === 'object' && typeof raw.approved === 'string') {"
                        + "    var a = raw.approved.toLowerCase().trim();"
                        + "    if (a === 'true' || a === 'yes' || a === 'y') {"
                        + "      return {needs_normalize: false, approved: true,"
                        + "              edited_output: raw.edited_output || null, reason: raw.reason || null,"
                        + "              raw_text: raw_text};"
                        + "    }"
                        + "    if (a === 'false' || a === 'no' || a === 'n') {"
                        + "      return {needs_normalize: false, approved: false,"
                        + "              edited_output: null, reason: raw.reason || null,"
                        + "              raw_text: raw_text};"
                        + "    }"
                        + "  }"
                        + "  return {needs_normalize: true, raw_text: raw_text};");
    }

    /** Build human process script for guardrail decision merging. */
    public static String humanProcessScript() {
        return iife(
                "  var validated = $.validated;"
                        + "  var normalized = $.normalized;"
                        + "  var data = (validated && !validated.needs_normalize) ? validated : (normalized || {});"
                        + "  if (data.approved) {"
                        + "    return {action: 'approve', result: $.llm_output};"
                        + "  } else if (data.edited_output) {"
                        + "    return {action: 'edit', result: data.edited_output};"
                        + "  } else {"
                        + "    var reason = data.reason || 'Rejected by human reviewer';"
                        + "    return {action: 'reject', reason: reason};"
                        + "  }");
    }

    /**
     * Build approval validate script for tool approval flow. Like humanValidateScript but without
     * edited_output field.
     */
    public static String approvalValidateScript() {
        return iife(
                "  var raw = $.human_output;"
                        + "  if (!raw) return {needs_normalize: true, raw_text: '', extra: {}};"
                        + "  var raw_text;"
                        + "  if (typeof raw === 'string') { raw_text = raw; }"
                        + "  else if (typeof raw.result === 'string') { raw_text = raw.result; }"
                        + "  else { var p = []; for (var k in raw) { p.push(k + ': ' + raw[k]); }"
                        + "         raw_text = p.join(', '); }"
                        +
                        // Collect extra fields (everything except approved/reason)
                        "  var extra = {};"
                        + "  if (typeof raw === 'object') {"
                        + "    for (var k in raw) {"
                        + "      if (k !== 'approved' && k !== 'reason') { extra[k] = raw[k]; }"
                        + "    }"
                        + "  }"
                        + "  if (typeof raw === 'object' && typeof raw.approved === 'boolean') {"
                        + "    return {needs_normalize: false, approved: raw.approved,"
                        + "            reason: raw.reason || null, raw_text: raw_text, extra: extra};"
                        + "  }"
                        + "  if (typeof raw === 'object' && typeof raw.approved === 'string') {"
                        + "    var a = raw.approved.toLowerCase().trim();"
                        + "    if (a === 'true' || a === 'yes' || a === 'y') {"
                        + "      return {needs_normalize: false, approved: true, reason: raw.reason || null,"
                        + "              raw_text: raw_text, extra: extra};"
                        + "    }"
                        + "    if (a === 'false' || a === 'no' || a === 'n') {"
                        + "      return {needs_normalize: false, approved: false, reason: raw.reason || null,"
                        + "              raw_text: raw_text, extra: extra};"
                        + "    }"
                        + "  }"
                        + "  return {needs_normalize: true, raw_text: raw_text, extra: extra};");
    }

    /**
     * Build the MCP prepare/merge script that combines static tool specs with dynamically
     * discovered MCP tools from LIST_MCP_TOOLS tasks.
     *
     * <p>At runtime this script:
     *
     * <ol>
     *   <li>Parses static (non-MCP) tool specs from a baked-in JSON literal
     *   <li>Reads discovered tools from each LIST_MCP_TOOLS task output
     *   <li>Converts each discovered tool into a tool spec with {@code type: "CALL_MCP_TOOL"}
     *   <li>Builds an {@code mcpConfig} map (tool name → server URL + headers)
     *   <li>Checks whether total tool count exceeds the threshold
     * </ol>
     *
     * @param staticSpecsJson JSON array of static tool specs (baked in at compile time)
     * @param serverCount number of MCP servers (each provides $.discovered_N input)
     * @param serversJson JSON array of [{serverUrl, headers}, ...] for each server
     * @param maxTools threshold for filtering
     */
    public static String mcpPrepareScript(
            String staticSpecsJson, int serverCount, String serversJson, int maxTools) {
        StringBuilder discoveredReads = new StringBuilder();
        for (int i = 0; i < serverCount; i++) {
            discoveredReads
                    .append("  var d")
                    .append(i)
                    .append(" = $.discovered_")
                    .append(i)
                    .append(" || [];");
        }
        StringBuilder mergeLoop = new StringBuilder();
        for (int i = 0; i < serverCount; i++) {
            mergeLoop.append(
                    "  for (var i = 0; i < d"
                            + i
                            + ".length; i++) {"
                            + "    var t = d"
                            + i
                            + "[i];"
                            + "    var s = servers["
                            + i
                            + "];"
                            + "    specs.push({name: t.name, type: 'CALL_MCP_TOOL',"
                            + "      description: t.description || '',"
                            + "      inputSchema: _json(t.inputSchema || {type:'object',properties:{}}),"
                            // OrkesLLM resolves unmarked tools by name against integrations and
                            // task
                            // definitions, which can replace (or drop) the schema returned by the
                            // MCP
                            // server. Discovery results are already complete tool definitions.
                            + "      selfDescribing: true,"
                            + "      configParams: {mcpServer: s.serverUrl, headers: s.headers || {}, selfDescribing: true}});"
                            + "    mcpCfg[t.name] = {mcpServer: s.serverUrl, headers: s.headers || {}};"
                            + "  }");
        }

        return iife(
                "  var specs = "
                        + staticSpecsJson
                        + ";"
                        + "  var servers = "
                        + serversJson
                        + ";"
                        + "  var mcpCfg = {};"
                        // INLINE receives task output as Java Map/List host objects. Returning one
                        // directly makes Graal serialize it as {}, silently stripping nested JSON
                        // Schema fields before LLM_CHAT_COMPLETE receives the tool spec.
                        + "  var _json = function(v) {"
                        + "    if (v == null || typeof v !== 'object') return v;"
                        + "    if (Array.isArray(v)) { var a = []; for (var ai = 0; ai < v.length; ai++) a.push(_json(v[ai])); return a; }"
                        + "    if (typeof v.entrySet === 'function') { var o = {}; var it = v.entrySet().iterator();"
                        + "      while (it.hasNext()) { var e = it.next(); o[String(e.getKey())] = _json(e.getValue()); } return o; }"
                        + "    if (typeof v.iterator === 'function') { var l = []; var li = v.iterator();"
                        + "      while (li.hasNext()) l.push(_json(li.next())); return l; }"
                        + "    var p = {}; for (var k in v) { if (Object.prototype.hasOwnProperty.call(v, k)) p[k] = _json(v[k]); } return p;"
                        + "  };"
                        + discoveredReads
                        + mergeLoop
                        + "  return {tools: specs, mcpConfig: mcpCfg,"
                        + "    needsFilter: specs.length > "
                        + maxTools
                        + "};");
    }

    /**
     * Build the API prepare/merge script that combines static tool specs with dynamically
     * discovered API tools from LIST_API_TOOLS tasks, alongside any existing MCP discovery.
     *
     * <p>At runtime this script:
     *
     * <ol>
     *   <li>Parses static (non-API) tool specs from a baked-in JSON literal
     *   <li>Reads discovered tools from each LIST_API_TOOLS task output
     *   <li>Converts each discovered tool into a tool spec with {@code type: "HTTP"}
     *   <li>Builds an {@code apiConfig} map (tool name &rarr; baseUrl + method + path + headers)
     *   <li>Merges with MCP config from any parallel MCP discovery
     *   <li>Checks whether total tool count exceeds the threshold
     * </ol>
     *
     * @param staticSpecsJson JSON array of static tool specs (baked in at compile time)
     * @param mcpServerCount number of MCP servers (each provides $.mcp_discovered_N input)
     * @param mcpServersJson JSON array of [{serverUrl, headers}, ...] for each MCP server
     * @param apiServerCount number of API sources (each provides $.api_discovered_N input)
     * @param apiServersJson JSON array of [{headers}, ...] for each API source
     * @param maxTools threshold for filtering
     */
    public static String apiPrepareScript(
            String staticSpecsJson,
            int mcpServerCount,
            String mcpServersJson,
            int apiServerCount,
            String apiServersJson,
            int maxTools) {
        // ── MCP discovered reads ──
        StringBuilder mcpDiscoveredReads = new StringBuilder();
        for (int i = 0; i < mcpServerCount; i++) {
            mcpDiscoveredReads
                    .append("  var md")
                    .append(i)
                    .append(" = $.mcp_discovered_")
                    .append(i)
                    .append(" || [];");
        }
        StringBuilder mcpMergeLoop = new StringBuilder();
        for (int i = 0; i < mcpServerCount; i++) {
            mcpMergeLoop.append(
                    "  for (var i = 0; i < md"
                            + i
                            + ".length; i++) {"
                            + "    var t = md"
                            + i
                            + "[i];"
                            + "    var s = mcpServers["
                            + i
                            + "];"
                            + "    specs.push({name: t.name, type: 'CALL_MCP_TOOL',"
                            + "      description: t.description || '',"
                            + "      inputSchema: _json(t.inputSchema || {type:'object',properties:{}}),"
                            // Keep the dynamic MCP path equivalent to static AgentSpan tool specs.
                            + "      selfDescribing: true,"
                            + "      configParams: {mcpServer: s.serverUrl, headers: s.headers || {}, selfDescribing: true}});"
                            + "    mcpCfg[t.name] = {mcpServer: s.serverUrl, headers: s.headers || {}};"
                            + "  }");
        }

        // ── API discovered reads ──
        StringBuilder apiDiscoveredReads = new StringBuilder();
        for (int i = 0; i < apiServerCount; i++) {
            apiDiscoveredReads
                    .append("  var ad")
                    .append(i)
                    .append(" = $.api_discovered_")
                    .append(i)
                    .append(" || {};");
        }
        StringBuilder apiMergeLoop = new StringBuilder();
        for (int i = 0; i < apiServerCount; i++) {
            apiMergeLoop.append(
                    "  var apiTools"
                            + i
                            + " = ad"
                            + i
                            + ".tools || [];"
                            + "  var apiBase"
                            + i
                            + " = ad"
                            + i
                            + ".baseUrl || '';"
                            + "  var apiSrv"
                            + i
                            + " = apiServers["
                            + i
                            + "];"
                            + "  for (var i = 0; i < apiTools"
                            + i
                            + ".length; i++) {"
                            + "    var at = apiTools"
                            + i
                            + "[i];"
                            + "    specs.push({name: at.name, type: 'HTTP',"
                            + "      description: at.description || '',"
                            // API discovery has the same complete-schema contract as MCP discovery.
                            + "      inputSchema: _json(at.inputSchema || {type:'object',properties:{}}),"
                            + "      selfDescribing: true, configParams: {selfDescribing: true}});"
                            + "    apiCfg[at.name] = {baseUrl: apiBase"
                            + i
                            + ","
                            + "      method: at.method || 'GET', path: at.path || '',"
                            + "      headers: apiSrv"
                            + i
                            + ".headers || {}};"
                            + "  }");
        }

        return iife(
                "  var specs = "
                        + staticSpecsJson
                        + ";"
                        + "  var mcpServers = "
                        + mcpServersJson
                        + ";"
                        + "  var apiServers = "
                        + apiServersJson
                        + ";"
                        + "  var mcpCfg = {};"
                        + "  var apiCfg = {};"
                        // Materialize Java-backed discovery output before it becomes an INLINE
                        // result; nested JSON Schema maps otherwise serialize as {}.
                        + "  var _json = function(v) {"
                        + "    if (v == null || typeof v !== 'object') return v;"
                        + "    if (Array.isArray(v)) { var a = []; for (var ai = 0; ai < v.length; ai++) a.push(_json(v[ai])); return a; }"
                        + "    if (typeof v.entrySet === 'function') { var o = {}; var it = v.entrySet().iterator();"
                        + "      while (it.hasNext()) { var e = it.next(); o[String(e.getKey())] = _json(e.getValue()); } return o; }"
                        + "    if (typeof v.iterator === 'function') { var l = []; var li = v.iterator();"
                        + "      while (li.hasNext()) l.push(_json(li.next())); return l; }"
                        + "    var p = {}; for (var k in v) { if (Object.prototype.hasOwnProperty.call(v, k)) p[k] = _json(v[k]); } return p;"
                        + "  };"
                        + mcpDiscoveredReads
                        + mcpMergeLoop
                        + apiDiscoveredReads
                        + apiMergeLoop
                        + "  return {tools: specs, mcpConfig: mcpCfg, apiConfig: apiCfg,"
                        + "    needsFilter: specs.length > "
                        + maxTools
                        + "};");
    }

    /**
     * Build tool enrichment script with dynamic MCP config from runtime input.
     *
     * <p>Like {@link #enrichToolsScript} but reads {@code mcpCfg} from {@code $.mcpConfig} (runtime
     * input from the prepare task) instead of a baked-in JSON literal. HTTP and media configs are
     * still baked in since they're known at compile time.
     */
    public static String enrichToolsScriptDynamic(
            String httpConfigJson,
            String mediaConfigJson,
            String agentToolConfigJson,
            String ragConfigJson,
            String humanConfigJson,
            String wmqConfigJson,
            String knownToolNamesJson) {
        return iife(
                "  var httpCfg = "
                        + httpConfigJson
                        + ";"
                        + "  var mcpCfg = $.mcpConfig || {};"
                        + "  var apiCfg = $.apiConfig || {};"
                        + "  var mediaCfg = "
                        + mediaConfigJson
                        + ";"
                        + "  var agentToolCfg = "
                        + agentToolConfigJson
                        + ";"
                        + "  var ragCfg = "
                        + ragConfigJson
                        + ";"
                        + "  var humanCfg = "
                        + humanConfigJson
                        + ";"
                        + "  var wmqCfg = "
                        + wmqConfigJson
                        + ";"
                        + "  var knownNames = "
                        + knownToolNamesJson
                        + ";"
                        // See enrichToolsScript: markers -> secret refs at emission; the '$' +
                        // '{...'
                        // split keeps the contiguous reference literal out of the script source.
                        + "  var _sec = function(h) { var o = {}; h = h || {};"
                        + "    for (var _hk in h) { o[_hk] = String(h[_hk]).replace(/#\\{([A-Za-z_][A-Za-z0-9_.]*)\\}/g,"
                        + "      function(_m, _n) { return '$' + '{workflow.secrets.' + _n + '}'; }); }"
                        + "    return o; };"
                        + "  var _plain = function(v) { var o = {}; if (v == null) return o;"
                        + "    if (typeof v.entrySet === 'function') { var it = v.entrySet().iterator();"
                        + "      while (it.hasNext()) { var e = it.next(); o[String(e.getKey())] = e.getValue(); } return o; }"
                        + "    for (var k in v) { if (Object.prototype.hasOwnProperty.call(v, k)) o[k] = v[k]; } return o; };"
                        + "  var agentState = $.agentState || {};"
                        + "  var tcs = $.toolCalls || [];"
                        + "  var result = [];"
                        + "  for (var i = 0; i < tcs.length; i++) {"
                        + "    var tc = tcs[i]; var n = tc.name;"
                        // Reject hallucinated tool names (see enrichToolsScript above
                        // for context). Without this the SIMPLE task gets queued under
                        // an unknown name and the workflow hangs forever.
                        + "    var isCfg = !!(httpCfg[n] || mcpCfg[n] || apiCfg[n] || agentToolCfg[n] ||"
                        + "                  mediaCfg[n] || ragCfg[n] || humanCfg[n] || wmqCfg[n]);"
                        // See ``enrichToolsScript`` above — empty knownNames means
                        // NO tool is callable by the LLM (locks down the prefill-only
                        // leak path).
                        + "    var isUnknown = !isCfg && !(knownNames && knownNames[n]);"
                        + "    if (isUnknown) {"
                        + "      var availList = [];"
                        + "      for (var nm in knownNames) availList.push(nm);"
                        + "      var unknownErr = ('Unknown tool \\'' + n + '\\'. Available tools: ' + availList.join(', '));"
                        + "      var errTask = {name: n, taskReferenceName: tc.taskReferenceName || n,"
                        + "                     type: 'INLINE',"
                        + "                     inputParameters: {evaluatorType: 'graaljs',"
                        + "                       expression: 'function e(){return {result: $.errorMessage, is_error: true};} e();',"
                        + "                       errorMessage: unknownErr},"
                        + "                     optional: true};"
                        + "      result.push(errTask);"
                        + "      continue;"
                        + "    }"
                        + "    var t = {name: n, taskReferenceName: tc.taskReferenceName || n,"
                        + "             type: tc.type || 'SIMPLE', inputParameters: tc.inputParameters || {},"
                        + "             optional: true,"
                        + "             retryCount: 2, retryLogic: 'LINEAR_BACKOFF',"
                        + "             retryDelaySeconds: 2};"
                        + "    if (httpCfg[n]) {"
                        + "      t.type = 'HTTP';"
                        + "      var hc = httpCfg[n];"
                        + "      var hargs = _plain(tc.inputParameters);"
                        + "      var huri = hc.url || '';"
                        + "      var hmethod = String(hc.method || 'GET').toUpperCase();"
                        + "      var bodyless = hmethod === 'GET' || hmethod === 'HEAD' || hmethod === 'DELETE' || hmethod === 'OPTIONS';"
                        + "      var hbody = {}; for (var ha in hargs) { if (ha !== 'method') hbody[ha] = hargs[ha]; }"
                        // Optional URI templating: pathTemplate consumes {param}s from
                        // the LLM's arguments (URL-encoded); queryParams append the
                        // listed args as a query string. Consumed args leave the body.
                        // Tools without these keys keep the static-uri/args-as-body
                        // shape unchanged.
                        + "      if (hc.pathTemplate || hc.queryParams || bodyless) {"
                        + "        var consumed = {method:true};"
                        + "        if (hc.pathTemplate) {"
                        + "          huri = huri + hc.pathTemplate.replace(/\\{(\\w+)\\}/g,"
                        + "            function(m, k) { consumed[k] = true;"
                        + "              return encodeURIComponent(String(hargs[k] == null ? '' : hargs[k])); });"
                        + "        }"
                        + "        var queryKeys = hc.queryParams || (bodyless ? Object.keys(hargs) : []);"
                        + "        if (queryKeys.length) {"
                        + "          var qs = [];"
                        + "          for (var qi = 0; qi < queryKeys.length; qi++) {"
                        + "            var qk = queryKeys[qi]; var qv = hargs[qk];"
                        + "            if (qk === 'method' || qv == null || qv === '') continue; consumed[qk] = true;"
                        + "            if (Array.isArray(qv)) { for (var qj = 0; qj < qv.length; qj++) { if (qv[qj] != null && qv[qj] !== '') qs.push(encodeURIComponent(qk) + '=' + encodeURIComponent(String(qv[qj]))); } }"
                        + "            else { var qtext = typeof qv === 'object' ? JSON.stringify(qv) : String(qv); if (qtext != null) qs.push(encodeURIComponent(qk) + '=' + encodeURIComponent(qtext)); }"
                        + "          }"
                        + "          if (qs.length) { var hash = huri.indexOf('#'); var base = hash >= 0 ? huri.substring(0, hash) : huri; var frag = hash >= 0 ? huri.substring(hash) : ''; huri = base + (base.indexOf('?') >= 0 && !base.endsWith('?') ? '&' : '?') + qs.join('&') + frag; }"
                        + "        }"
                        + "        hbody = {};"
                        + "        if (!bodyless) { for (var hk in hargs) { if (!consumed[hk]) hbody[hk] = hargs[hk]; } }"
                        + "      }"
                        + "      var hrequest = {"
                        + "        uri: huri,"
                        + "        method: hmethod,"
                        + "        headers: _sec(hc.headers),"
                        + "        accept: hc.accept || 'application/json',"
                        + "        contentType: hc.contentType || 'application/json',"
                        + "        connectionTimeOut: 30000,"
                        + "        readTimeOut: 30000};"
                        + "      if (!bodyless) hrequest.body = hbody;"
                        + "      t.inputParameters = {http_request: hrequest};"
                        + "    } else if (mcpCfg[n]) {"
                        + "      t.type = 'CALL_MCP_TOOL';"
                        + "      t.name = 'call_mcp_tool';"
                        + "      t.inputParameters = {"
                        + "        mcpServer: mcpCfg[n].mcpServer || '',"
                        + "        method: n,"
                        + "        arguments: Object.keys(_plain(tc.inputParameters)).filter(function(k) { return k !== 'method'; }).reduce(function(a, k) { a[k] = _plain(tc.inputParameters)[k]; return a; }, {}),"
                        + "        headers: _sec(mcpCfg[n].headers)};"
                        + "    } else if (apiCfg[n]) {"
                        + "      var api = apiCfg[n];"
                        + "      var uri = api.baseUrl + api.path;"
                        + "      var params = {};"
                        + "      var inp = _plain(tc.inputParameters);"
                        + "      for (var k in inp) { if (k !== 'method') params[k] = inp[k]; }"
                        + "      var pathParams = (uri.match(/\\{(\\w+)\\}/g) || []);"
                        + "      for (var j = 0; j < pathParams.length; j++) {"
                        + "        var key = pathParams[j].replace(/[{}]/g, '');"
                        + "        if (params[key] !== undefined) {"
                        + "          uri = uri.replace(pathParams[j], encodeURIComponent(String(params[key])));"
                        + "          delete params[key];"
                        + "        }"
                        + "      }"
                        + "      var method = api.method.toUpperCase();"
                        + "      if (method === 'GET' || method === 'DELETE' || method === 'HEAD' || method === 'OPTIONS') {"
                        + "        var qs = Object.keys(params).filter(function(k) { return params[k] != null && params[k] !== ''; }).map(function(k) {"
                        + "          return encodeURIComponent(k) + '=' + encodeURIComponent(typeof params[k] === 'object' ? JSON.stringify(params[k]) : String(params[k]));"
                        + "        }).join('&');"
                        + "        if (qs) { var hash = uri.indexOf('#'); var base = hash >= 0 ? uri.substring(0, hash) : uri; var frag = hash >= 0 ? uri.substring(hash) : ''; uri = base + (base.indexOf('?') >= 0 && !base.endsWith('?') ? '&' : '?') + qs + frag; }"
                        + "        t.type = 'HTTP';"
                        + "        t.inputParameters = {http_request: {uri: uri, method: method,"
                        + "          headers: _sec(api.headers), accept: 'application/json',"
                        + "          contentType: 'application/json',"
                        + "          connectionTimeOut: 30000, readTimeOut: 30000}};"
                        + "      } else {"
                        + "        t.type = 'HTTP';"
                        + "        t.inputParameters = {http_request: {uri: uri, method: method,"
                        + "          headers: _sec(api.headers), body: params,"
                        + "          accept: 'application/json', contentType: 'application/json',"
                        + "          connectionTimeOut: 30000, readTimeOut: 30000}};"
                        + "      }"
                        + "    } else if (agentToolCfg[n]) {"
                        + "      t.type = 'SUB_WORKFLOW';"
                        + "      t.name = agentToolCfg[n].workflowName;"
                        + "      t.subWorkflowParam = {name: agentToolCfg[n].workflowName, version: 1};"
                        + "      var _p = tc.inputParameters || {};"
                        + "      var _req = _p.request || _p.prompt || _p.message || _p.input || _p.query || '';"
                        + "      if (!_req && typeof _p === 'object') {"
                        + "        for (var _k in _p) {"
                        + "          if (_k !== 'method' && typeof _p[_k] === 'string' && _p[_k].length > 0) { _req = _p[_k]; break; }"
                        + "        }"
                        + "      }"
                        + "      if (!_req || _req === '{}' || _req === JSON.stringify(_p)) {"
                        + "        _req = $.userPrompt || '';"
                        + "      }"
                        + "      if (!_req) _req = JSON.stringify(_p);"
                        + "      t.inputParameters = {"
                        + "        prompt: _req,"
                        + "        session_id: $.session_id || '',"
                        // Current UTC date, evaluated when this dispatch script runs —
                        // sub-agent prompts reference ${workflow.input.__today__} to
                        // anchor relative-date queries without drifting from a date
                        // computed at compile/boot time.
                        + "        __today__: new Date().toISOString().slice(0, 10)};"
                        + "      if (agentToolCfg[n].retryCount !== undefined) t.retryCount = agentToolCfg[n].retryCount;"
                        + "      if (agentToolCfg[n].retryDelaySeconds !== undefined) t.retryDelaySeconds = agentToolCfg[n].retryDelaySeconds;"
                        + "      if (agentToolCfg[n].optional !== undefined) t.optional = agentToolCfg[n].optional;"
                        + "    } else if (mediaCfg[n]) {"
                        + "      t.type = mediaCfg[n].taskType;"
                        + "      t.name = mediaCfg[n].taskType.toLowerCase();"
                        + "      var merged = {};"
                        + "      var defs = mediaCfg[n].defaults || {};"
                        + "      for (var k in defs) { merged[k] = defs[k]; }"
                        + "      var inp = tc.inputParameters || {};"
                        + "      for (var k in inp) { merged[k] = inp[k]; }"
                        + "      t.inputParameters = merged;"
                        + "    } else if (ragCfg[n]) {"
                        + "      t.type = ragCfg[n].taskType;"
                        + "      t.name = ragCfg[n].taskType.toLowerCase();"
                        + "      var merged = {};"
                        + "      var defs = ragCfg[n].defaults || {};"
                        + "      for (var k in defs) { merged[k] = defs[k]; }"
                        + "      var inp = tc.inputParameters || {};"
                        + "      for (var k in inp) { merged[k] = inp[k]; }"
                        + "      t.inputParameters = merged;"
                        + "    } else if (humanCfg[n]) {"
                        + "      t.type = 'HUMAN';"
                        + "      t.name = n;"
                        + "      var hDef = {assignmentCompletionStrategy: 'LEAVE_OPEN',"
                        + "                  displayName: humanCfg[n].displayName || n,"
                        + "                  userFormTemplate: {version: 0}};"
                        + "      var hInputs = {__humanTaskDefinition: hDef};"
                        + "      hInputs.response_schema = {type: 'object', properties: {"
                        + "        response: {type: 'string', title: 'Response',"
                        + "                   description: 'Provide your response'}}};"
                        + "      hInputs.response_ui_schema = {'ui:order': ['response'],"
                        + "        response: {'ui:widget': 'textarea'}};"
                        + "      var inp = tc.inputParameters || {};"
                        + "      for (var k in inp) { hInputs[k] = inp[k]; }"
                        + "      if (humanCfg[n].description) hInputs._description = humanCfg[n].description;"
                        + "      t.inputParameters = hInputs;"
                        + "      t.optional = false;"
                        + "    } else if (wmqCfg[n]) {"
                        + "      t.type = 'PULL_WORKFLOW_MESSAGES';"
                        + "      t.name = n;"
                        + "      t.inputParameters = {batchSize: wmqCfg[n].batchSize || 1};"
                        + "      t.retryCount = 0;"
                        + "      t.optional = false;"
                        + "    }"
                        + "    if (t.type === 'SIMPLE') {"
                        + "      t.inputParameters._agent_state = agentState;"
                        + "    }"
                        + "    result.push(t);"
                        + "  }"
                        + "  return {dynamicTasks: result};");
    }

    /**
     * Build tool filter script that reads the tool list from a runtime input instead of a baked-in
     * JSON literal.
     */
    public static String filterToolsScriptDynamic() {
        return iife(
                "  var allTools = $.allTools || [];"
                        + "  var _json = function(v) {"
                        + "    if (v == null || typeof v !== 'object') return v;"
                        + "    if (Array.isArray(v)) { var a = []; for (var ai = 0; ai < v.length; ai++) a.push(_json(v[ai])); return a; }"
                        + "    if (typeof v.entrySet === 'function') { var o = {}; var it = v.entrySet().iterator();"
                        + "      while (it.hasNext()) { var e = it.next(); o[String(e.getKey())] = _json(e.getValue()); } return o; }"
                        + "    if (typeof v.iterator === 'function') { var l = []; var li = v.iterator();"
                        + "      while (li.hasNext()) l.push(_json(li.next())); return l; }"
                        + "    var p = {}; for (var k in v) { if (Object.prototype.hasOwnProperty.call(v, k)) p[k] = _json(v[k]); } return p;"
                        + "  };"
                        + "  var raw = $.selectedNames || '[]';"
                        + "  var selected;"
                        + "  try { selected = typeof raw === 'string' ? JSON.parse(raw) : raw; }"
                        + "  catch(e) { selected = []; }"
                        + "  if (!Array.isArray(selected)) {"
                        + "    if (selected && selected.selected_tools) selected = selected.selected_tools;"
                        + "    else selected = [];"
                        + "  }"
                        + "  var nameSet = {};"
                        + "  for (var i = 0; i < selected.length; i++) nameSet[selected[i]] = true;"
                        + "  var result = [];"
                        + "  for (var i = 0; i < allTools.length; i++) {"
                        + "    if (nameSet[allTools[i].name]) result.push(_json(allTools[i]));"
                        + "  }"
                        + "  if (result.length === 0) result = _json(allTools);"
                        + "  return {tools: result};");
    }

    /**
     * Build the MCP resolve script that picks the final tools and mcpConfig from either the filter
     * output or the prepare output, depending on which path was taken in the threshold SWITCH.
     */
    public static String mcpResolveScript() {
        return iife(
                "  var filtered = $.filtered_tools;"
                        + "  var prepared = $.prepared_tools;"
                        + "  var mcpConfig = $.mcpConfig;"
                        + "  var apiConfig = $.apiConfig;"
                        + "  var _json = function(v) {"
                        + "    if (v == null || typeof v !== 'object') return v;"
                        + "    if (Array.isArray(v)) { var a = []; for (var ai = 0; ai < v.length; ai++) a.push(_json(v[ai])); return a; }"
                        + "    if (typeof v.entrySet === 'function') { var o = {}; var it = v.entrySet().iterator();"
                        + "      while (it.hasNext()) { var e = it.next(); o[String(e.getKey())] = _json(e.getValue()); } return o; }"
                        + "    if (typeof v.iterator === 'function') { var l = []; var li = v.iterator();"
                        + "      while (li.hasNext()) l.push(_json(li.next())); return l; }"
                        + "    var p = {}; for (var k in v) { if (Object.prototype.hasOwnProperty.call(v, k)) p[k] = _json(v[k]); } return p;"
                        + "  };"
                        + "  var tools = (filtered && filtered.length > 0) ? filtered : prepared;"
                        + "  return {tools: _json(tools), mcpConfig: _json(mcpConfig), apiConfig: _json(apiConfig)};");
    }

    /**
     * Build the catalog text inline script for dynamic tool filtering. Builds the LLM prompt
     * catalog from the runtime tools list.
     */
    public static String filterCatalogScript(int maxTools) {
        return iife(
                "  var tools = $.tools || [];"
                        + "  var lines = [];"
                        + "  for (var i = 0; i < tools.length; i++) {"
                        + "    lines.push('- ' + tools[i].name + ': ' + (tools[i].description || ''));"
                        + "  }"
                        + "  return {catalog: lines.join('\\n'), maxTools: "
                        + maxTools
                        + "};");
    }

    /**
     * Format custom data from a human approval response into a readable system message for the LLM.
     * Returns an empty string if no extra data.
     */
    public static String formatHumanFeedbackScript() {
        return iife(
                "  var extra = $.extra || {};"
                        + "  var reason = $.reason || '';"
                        + "  var parts = [];"
                        + "  for (var k in extra) { parts.push(k + ': ' + JSON.stringify(extra[k])); }"
                        + "  if (parts.length === 0 && !reason) return '';"
                        + "  var msg = 'Human reviewer feedback:';"
                        + "  if (reason) msg += ' Reason: ' + reason + '.';"
                        + "  if (parts.length > 0) msg += ' Additional context: ' + parts.join(', ') + '.';"
                        + "  return msg;");
    }

    /**
     * Validate human output for graph-node HUMAN tasks.
     *
     * <p>Extracts key-value fields from the human output. If the output is already a structured
     * JSON object, captures all fields (except internal ones like {@code __humanTaskDefinition}).
     * If it's a string, flags for LLM normalization.
     */
    public static String graphNodeValidateScript() {
        return iife(
                "  var raw = $.human_output;"
                        + "  if (!raw) return {needs_normalize: true, raw_text: '', fields: {}};"
                        + "  var raw_text;"
                        + "  if (typeof raw === 'string') { raw_text = raw; }"
                        + "  else if (typeof raw.result === 'string') { raw_text = raw.result; }"
                        + "  else { var p = []; for (var k in raw) { if (k !== '__humanTaskDefinition' && k !== 'response_schema'"
                        + "      && k !== 'response_ui_schema' && k !== '_prompt' && k !== 'state')"
                        + "      p.push(k + ': ' + JSON.stringify(raw[k])); }"
                        + "    raw_text = p.join(', '); }"
                        +
                        // Check if raw is a structured object with user-provided fields
                        "  if (typeof raw === 'object') {"
                        + "    var fields = {}; var hasFields = false;"
                        + "    for (var k in raw) {"
                        + "      if (k === '__humanTaskDefinition' || k === 'response_schema'"
                        + "          || k === 'response_ui_schema' || k === '_prompt' || k === 'state') continue;"
                        + "      fields[k] = raw[k]; hasFields = true;"
                        + "    }"
                        + "    if (hasFields) {"
                        + "      return {needs_normalize: false, fields: fields, raw_text: raw_text};"
                        + "    }"
                        + "  }"
                        + "  return {needs_normalize: true, raw_text: raw_text, fields: {}};");
    }

    /**
     * Merge validated/normalized human output into graph state.
     *
     * <p>Takes the validated fields (or LLM-normalized JSON), merges them into the previous graph
     * state, and returns the updated state.
     */
    public static String graphNodeProcessScript() {
        return iife(
                "  var validated = $.validated;"
                        + "  var normalized = $.normalized;"
                        + "  var state = $.previousState || {};"
                        + "  var fields;"
                        + "  if (validated && !validated.needs_normalize) {"
                        + "    fields = validated.fields || {};"
                        + "  } else if (normalized) {"
                        +
                        // normalized is LLM output — parse if string
                        "    if (typeof normalized === 'string') {"
                        + "      try { fields = JSON.parse(normalized); } catch(e) { fields = {}; }"
                        + "    } else { fields = normalized; }"
                        + "  } else { fields = {}; }"
                        +
                        // Merge fields into state
                        "  for (var k in fields) {"
                        + "    if (k !== '__humanTaskDefinition') state[k] = fields[k];"
                        + "  }"
                        + "  return {state: state, result: JSON.stringify(state)};");
    }

    /** Build approval check script for tool approval flow. */
    public static String approvalCheckScript() {
        return iife(
                "  var validated = $.validated;"
                        + "  var normalized = $.normalized;"
                        + "  var data = (validated && !validated.needs_normalize) ? validated : (normalized || {});"
                        + "  var extra = (validated && validated.extra) ? validated.extra : {};"
                        + "  return {approved: data.approved === true, reason: data.reason || '', extra: extra};");
    }

    /**
     * Build the state merge script that collects {@code _state_updates} from all forked tool task
     * outputs, appends them to the durable ordered tool-result history, and merges state updates
     * into a single state dict.
     *
     * <p>Reads {@code $.currentState} (the existing workflow variable), {@code
     * $.previousToolResults} (the accumulated history), and {@code $.joinOutput} (the JOIN task
     * output containing this turn's tool results). Each tool may include {@code _state_updates} in
     * its output; these are shallow-merged onto the base state.
     */
    public static String stateMergeScript() {
        return iife(
                "  var base = $.currentState || {};"
                        + "  var previousToolResults = $.previousToolResults || [];"
                        + "  var joinOutput = $.joinOutput || {};"
                        + "  var toolResults = [];"
                        // A workflow variable may arrive as either a native JS array or a Java
                        // List proxy. Use indexed access in both forms so every prior observation
                        // remains visible to later ReAct turns.
                        + "  var previousCount = previousToolResults.size ? previousToolResults.size() : (previousToolResults.length || 0);"
                        + "  for (var p = 0; p < previousCount; p++) {"
                        + "    var prior = previousToolResults.get ? previousToolResults.get(p) : previousToolResults[p];"
                        + "    if (prior != null) toolResults.push(prior);"
                        + "  }"
                        + "  for (var key in joinOutput) {"
                        + "    var taskOut = joinOutput[key] || {};"
                        + "    var rawOutput = taskOut._agent_tool_output || taskOut;"
                        + "    var toolName = taskOut._agent_tool_name || rawOutput._agent_tool_name || key.replace(/_[0-9]+$/, '');"
                        + "    toolResults.push({name: String(toolName), output: rawOutput});"
                        + "    var updates = rawOutput._state_updates || taskOut._state_updates;"
                        + "    if (updates) {"
                        + "      for (var k in updates) {"
                        + "        var bv = base[k]; var uv = updates[k];"
                        + "        if (Array.isArray(bv) && Array.isArray(uv)) {"
                        +
                        // Array merge: concat unique items from update that aren't in base
                        "          for (var i = 0; i < uv.length; i++) {"
                        + "            var found = false;"
                        + "            for (var j = 0; j < bv.length; j++) {"
                        + "              if (JSON.stringify(bv[j]) === JSON.stringify(uv[i])) { found = true; break; }"
                        + "            }"
                        + "            if (!found) bv.push(uv[i]);"
                        + "          }"
                        + "        } else { base[k] = uv; }"
                        + "      }"
                        + "    }"
                        + "  }"
                        + "  return {mergedState: base, toolResults: toolResults};");
    }

    /**
     * Build a JavaScript snippet that checks whether all required tools have been called. Scans the
     * loop output for completed task reference names containing each required tool name.
     */
    public static String requiredToolsCheckScript(List<String> requiredTools) {
        StringBuilder sb = new StringBuilder();
        sb.append("(function() {");
        sb.append("  var required = ").append(toJson(requiredTools)).append(";");
        sb.append("  var output = $.completedTaskNames;");
        sb.append("  var outputStr = JSON.stringify(output);");
        sb.append("  var missing = [];");
        sb.append("  for (var i = 0; i < required.length; i++) {");
        sb.append("    if (outputStr.indexOf(required[i]) < 0) missing.push(required[i]);");
        sb.append("  }");
        sb.append("  if (missing.length > 0) {");
        sb.append("    return { satisfied: false, missing: missing,");
        sb.append(
                "             message: 'You MUST call these tools before completing: ' + missing.join(', ') };");
        sb.append("  }");
        sb.append("  return { satisfied: true };");
        sb.append("})()");
        return sb.toString();
    }

    // ── Context passing helpers ──────────────────────────────

    /**
     * Null-coalescing script: returns {@code $.ctx} if truthy, else empty object. Used for INLINE
     * tasks that resolve context with null fallback. Input: {@code ctx} → the reference to resolve
     * (e.g. {@code ${workflow.input.context}}). Output: {@code result} → the resolved context dict.
     */
    public static String nullCoalesceScript() {
        return iife("return $.ctx || {};");
    }

    /**
     * Flat-merge context script: merges child context into parent context. Used after each
     * sequential sub-workflow step. Input: {@code parent} → parent context, {@code child} → child
     * output.context. Output: merged context dict.
     */
    public static String flatMergeContextScript() {
        // Java Map interop: for-in iterates entries but hasOwnProperty checks
        // Java object properties (not map entries). Use .get(k) for values.
        return iife(
                "var parent = $.parent || {};"
                        + "var child = $.child || {};"
                        + "var merged = {};"
                        + "for (var k in parent) { var v = parent.get ? parent.get(k) : parent[k]; if (v != null) merged[k] = '' + v; }"
                        + "for (var k in child) { var v = child.get ? child.get(k) : child[k]; if (v != null) merged[k] = '' + v; }"
                        + "return merged;");
    }

    /**
     * Context injection script: builds the state/signals prefix for the user prompt.
     *
     * <p>Returns ONLY the context prefix (state JSON + signals), with a trailing {@code "\n\n"}
     * separator when the prefix is non-empty so the caller can append the base prompt without
     * injecting a separator literal. The caller concatenates via Conductor template resolution as
     * {@code ${ctx_inject.output.result}${workflow.input.prompt}} (no literal separator). When
     * state and signals are both empty, this returns the empty string — so the LLM sees the prompt
     * unchanged, not a {@code "\n\n<prompt>"} leading-whitespace artifact that would shift token
     * alignment at temperature 0.
     *
     * <p>This split avoids storing the full prompt (which never changes) in every iteration's task
     * output, reducing workflow payload by ~N × prompt_size.
     *
     * <p>Input: {@code state} → the _agent_state dict, {@code signals} → signal injection string,
     * {@code toolResults} → completed results from the immediately preceding tool turn, {@code
     * maxSize} → max total context bytes, {@code maxValueSize} → max per-value bytes.
     *
     * <p>Output: the context prefix string with trailing {@code "\n\n"} (or empty).
     */
    public static String contextInjectionScript() {
        return iife(
                // GraalJS interop: Conductor passes workflow variables as raw Java
                // LinkedHashMap objects. Object.keys() returns Java method names,
                // JSON.stringify() returns "{}". However for-in DOES iterate map
                // entries. Do NOT use hasOwnProperty — it checks Java object
                // properties, not map entries. Use state.get(k) for value access
                // since bracket notation may not work for Java Maps.
                "var rawState = $.state;"
                        + "var signals = $.signals || '';"
                        + "var toolResults = $.toolResults;"
                        + "if (!rawState && !signals && !toolResults) return '';"
                        + "var maxSize = $.maxSize || 32768;"
                        + "var maxValueSize = $.maxValueSize || 4096;"
                        // Collect map entries via for-in (works on Java Maps in GraalJS)
                        + "var state = {};"
                        + "if (rawState) {"
                        + "  for (var k in rawState) {"
                        + "    var v = rawState.get(k);"
                        + "    if (v != null) state[k] = '' + v;"
                        + "  }"
                        + "}"
                        + "var keys = Object.keys(state);"
                        // Per-value truncation
                        + "var truncated = {};"
                        + "for (var i = 0; i < keys.length; i++) {"
                        + "  var k = keys[i]; var s = state[k];"
                        + "  if (s.length > maxValueSize) {"
                        + "    truncated[k] = s.substring(0, maxValueSize) + '[truncated]';"
                        + "  } else { truncated[k] = s; }"
                        + "}"
                        // Total size budget — drop oldest keys if over
                        + "var json = JSON.stringify(truncated);"
                        + "var tKeys = Object.keys(truncated);"
                        + "while (json.length > maxSize && tKeys.length > 0) {"
                        + "  delete truncated[tKeys.shift()];"
                        + "  json = JSON.stringify(truncated);"
                        + "}"
                        // Build prefix: signals (if any) + context (if any).
                        // Trailing '\n\n' is part of the prefix so the message
                        // template can be ${ctx.result}${prompt} without
                        // injecting a leading-whitespace artifact when empty.
                        + "var parts = [];"
                        + "if (signals) { parts.push('[SIGNALS]\\n' + signals + '\\n[/SIGNALS]'); }"
                        // Materialize Java collection proxies before JSON serialization. GraalJS
                        // serializes a Java List of Java Maps as [{}, ...], which hides the
                        // completed observation from the next ReAct turn and can cause repeated
                        // tool calls.
                        + "var toPlain=function(v){"
                        + " if(v==null||typeof v!=='object')return v;"
                        + " if(Array.isArray(v)){var nativeArray=[];for(var ai=0;ai<v.length;ai++)nativeArray.push(toPlain(v[ai]));return nativeArray;}"
                        + " if(v.entrySet){var obj={};var it=v.entrySet().iterator();while(it.hasNext()){var entry=it.next();obj[String(entry.getKey())]=toPlain(entry.getValue());}return obj;}"
                        + " if(v.size&&v.get){var javaArray=[];var count=Number(v.size());for(var li=0;li<count;li++)javaArray.push(toPlain(v.get(li)));return javaArray;}"
                        + " return v;"
                        + "};"
                        + "if (toolResults) {"
                        + "  var renderedTools = '';"
                        + "  try { renderedTools = JSON.stringify(toPlain(toolResults)); } catch (e) {}"
                        + "  if (!renderedTools || renderedTools === '{}' || renderedTools === '[]') renderedTools = '' + toolResults;"
                        + "  if (renderedTools && renderedTools !== '[]') parts.push('[TOOL RESULTS]\\n' + renderedTools + '\\n[/TOOL RESULTS]');"
                        + "}"
                        + "if (Object.keys(truncated).length > 0) {"
                        + "  parts.push('Context:\\n```json\\n' + JSON.stringify(truncated, null, 2) + '\\n```');"
                        + "}"
                        + "if (parts.length === 0) return '';"
                        + "return parts.join('\\n\\n') + '\\n\\n';");
    }

    /**
     * Namespaced parallel merge script: merges parent context with each child's context namespaced
     * under its agent name. Input: {@code parentCtx} → parent context, {@code agentNames} → array
     * of names, {@code child_0}, {@code child_1}, ... → each child's output.context. Output: merged
     * context with child contexts namespaced.
     */
    public static String namespacedMergeContextScript() {
        return iife(
                "var parent = $.parentCtx || {};"
                        + "var merged = {};"
                        + "for (var k in parent) { var v = parent.get ? parent.get(k) : parent[k]; if (v != null) merged[k] = '' + v; }"
                        + "var agents = $.agentNames || [];"
                        + "for (var i = 0; i < agents.length; i++) {"
                        + "  merged[agents[i]] = $['child_' + i] || {};"
                        + "}"
                        + "return merged;");
    }

    /**
     * Extract a JSON plan from the planner's output.
     *
     * <p>Handles two cases:
     *
     * <ol>
     *   <li>The LLM returned a JSON object directly (no markdown) — detected by checking if {@code
     *       $.rawResult} is an object with a {@code steps} key.
     *   <li>The LLM returned Markdown with an embedded {@code ```json} fence — extracted via regex
     *       from {@code $.coercedResult} (the stringified version).
     * </ol>
     *
     * <p>Input: {@code $.rawResult} — the raw sub-workflow result (may be Java Map), {@code
     * $.coercedResult} — the stringified version, {@code $.planReaderContent} — optional content
     * from plan_source tool (deterministic fallback).
     *
     * <p>Output: {@code {plan_json: "<JSON string>", markdown_plan: "<full text>"}} Returns {@code
     * plan_json: null} when no valid plan is found.
     */
    public static String extractJsonFenceScript() {
        return iife(
                // Helper: convert a Java Map / JS object to a proper JS object
                // GraalJS Java Maps don't serialize with JSON.stringify, so we
                // manually copy entries into a plain JS object.
                "function toJS(obj) {"
                        + "  if (obj == null) return null;"
                        + "  if (typeof obj !== 'object') return obj;"
                        + "  if (Array.isArray(obj)) {"
                        + "    var arr = []; for (var i = 0; i < obj.length; i++) arr.push(toJS(obj[i])); return arr;"
                        + "  }"
                        + "  var out = {};"
                        + "  var keys = obj.keySet ? obj.keySet().toArray() : Object.keys(obj);"
                        + "  for (var i = 0; i < keys.length; i++) {"
                        + "    var k = keys[i]; var v = obj.get ? obj.get(k) : obj[k];"
                        + "    out[k] = toJS(v);"
                        + "  }"
                        + "  return out;"
                        + "}"

                        // Helper: scan ``text`` from ``openIdx`` (at a ``{``) and return the
                        // index of the matching ``}``, accounting for string literals so
                        // braces inside string values don't miscount. Returns -1 if no
                        // matching brace is found. Handles backslash-escaped quotes.
                        + "function findMatchingBrace(text, openIdx) {"
                        + "  var depth = 0;"
                        + "  var inStr = false;"
                        + "  var prev = '';"
                        + "  for (var ci = openIdx; ci < text.length; ci++) {"
                        + "    var cc = text[ci];"
                        + "    if (inStr) {"
                        + "      if (cc === '\\\\') { prev = (prev === '\\\\') ? '' : '\\\\'; }"
                        + "      else if (cc === '\"' && prev !== '\\\\') { inStr = false; prev = ''; }"
                        + "      else { prev = cc; }"
                        + "    } else {"
                        + "      if (cc === '\"') { inStr = true; prev = ''; }"
                        + "      else if (cc === '{') { depth++; }"
                        + "      else if (cc === '}') { depth--; if (depth === 0) return ci; }"
                        + "    }"
                        + "  }"
                        + "  return -1;"
                        + "}"

                        // Case 0 (highest priority): static_plan from workflow input.
                        // The SDK's ``runtime.run(harness, plan=...)`` plumbs a
                        // user-supplied plan dict/Plan into ``workflow.input.static_plan``;
                        // the planner LLM still runs (the workflow shape is fixed at
                        // compile time) but its output is ignored. This makes
                        // deterministic plans first-class — no more plan_source tool
                        // dance to inject a fixed plan.
                        + "var sp = $.staticPlan;"
                        + "if (sp != null) {"
                        + "  if (typeof sp === 'object') {"
                        + "    var hasStepsSp = false;"
                        + "    try { hasStepsSp = sp.steps != null || (sp.get && sp.get('steps') != null); } catch(e) {}"
                        + "    if (hasStepsSp) {"
                        + "      var planSp = toJS(sp);"
                        + "      return {plan_json: JSON.stringify(planSp), markdown_plan: '[static plan]'};"
                        + "    }"
                        + "  } else if (typeof sp === 'string' && sp.length > 2) {"
                        + "    try {"
                        + "      var parsedSp = JSON.parse(sp);"
                        + "      if (parsedSp && parsedSp.steps) {"
                        + "        return {plan_json: JSON.stringify(parsedSp), markdown_plan: '[static plan]'};"
                        + "      }"
                        + "    } catch(e) {}"
                        + "  }"
                        + "}"

                        // Case 1: rawResult is already a plan object (has "steps" key)
                        // markdown_plan is the original planner text when available — the
                        // fallback agent benefits from seeing the LLM's actual prose, not a
                        // re-stringified pretty-print of the parsed object.
                        + "var raw = $.rawResult;"
                        + "if (raw != null && typeof raw === 'object') {"
                        + "  var hasSteps = false;"
                        + "  try { hasSteps = raw.steps != null || (raw.get && raw.get('steps') != null); } catch(e) {}"
                        + "  if (hasSteps) {"
                        + "    var plan = toJS(raw);"
                        + "    var origText = ($.coercedResult && String($.coercedResult).length > 0) ? String($.coercedResult) : JSON.stringify(plan);"
                        + "    return {plan_json: JSON.stringify(plan), markdown_plan: origText};"
                        + "  }"
                        + "}"

                        // Case 2: coercedResult is a JSON string (the LLM output was pure JSON
                        // text)
                        + "var coerced = $.coercedResult || '';"
                        + "if (typeof coerced === 'string' && coerced.length > 2) {"
                        + "  try {"
                        + "    var parsed = JSON.parse(coerced);"
                        + "    if (parsed && parsed.steps) {"
                        + "      return {plan_json: JSON.stringify(parsed), markdown_plan: coerced};"
                        + "    }"
                        + "  } catch(e) {}"
                        + "}"

                        // Case 3: coercedResult is Markdown with a ```json fence
                        // Try multiple fence patterns: with/without newlines, with/without space
                        + "var text = String(coerced);"
                        + "var fencePatterns = ["
                        + "  /```json\\s*\\n([\\s\\S]*?)\\n\\s*```/," // standard: ```json\n...\n```
                        + "  /```json\\s*([\\s\\S]*?)```/," // lenient: no newline required
                        + "  /```\\s*\\n(\\{[\\s\\S]*?\\})\\n\\s*```/" // plain fence with JSON
                        // object
                        + "];"
                        + "for (var pi = 0; pi < fencePatterns.length; pi++) {"
                        + "  var fmatch = text.match(fencePatterns[pi]);"
                        + "  if (fmatch) {"
                        + "    try {"
                        + "      var fenced = JSON.parse(fmatch[1].trim());"
                        + "      if (fenced && fenced.steps) {"
                        + "        return {plan_json: JSON.stringify(fenced), markdown_plan: text};"
                        + "      }"
                        + "    } catch(e) {}"
                        + "  }"
                        + "}"

                        // Case 4: Find JSON object with "steps" key anywhere in text via
                        // string-aware brace matching (see findMatchingBrace helper above).
                        + "var stepsIdx = text.indexOf('\"steps\"');"
                        + "if (stepsIdx >= 0) {"
                        + "  var openIdx = text.lastIndexOf('{', stepsIdx);"
                        + "  if (openIdx >= 0) {"
                        + "    var closeIdx = findMatchingBrace(text, openIdx);"
                        + "    if (closeIdx > openIdx) {"
                        + "      try {"
                        + "        var extracted = JSON.parse(text.substring(openIdx, closeIdx + 1));"
                        + "        if (extracted && extracted.steps) {"
                        + "          return {plan_json: JSON.stringify(extracted), markdown_plan: text};"
                        + "        }"
                        + "      } catch(e) {}"
                        + "    }"
                        + "  }"
                        + "}"

                        // Case 5: planReaderContent — deterministic fallback from plan_source tool
                        // If the planner text failed extraction, try the external source content.
                        + "var readerText = $.planReaderContent ? String($.planReaderContent) : '';"
                        + "if (readerText && readerText.length > 2) {"
                        // 5a: direct JSON parse
                        + "  try {"
                        + "    var rParsed = JSON.parse(readerText);"
                        + "    if (rParsed && rParsed.steps) {"
                        + "      return {plan_json: JSON.stringify(rParsed), markdown_plan: readerText};"
                        + "    }"
                        + "  } catch(e) {}"
                        // 5b: ```json fence in reader content
                        + "  for (var ri = 0; ri < fencePatterns.length; ri++) {"
                        + "    var rmatch = readerText.match(fencePatterns[ri]);"
                        + "    if (rmatch) {"
                        + "      try {"
                        + "        var rfenced = JSON.parse(rmatch[1].trim());"
                        + "        if (rfenced && rfenced.steps) {"
                        + "          return {plan_json: JSON.stringify(rfenced), markdown_plan: readerText};"
                        + "        }"
                        + "      } catch(e) {}"
                        + "    }"
                        + "  }"
                        // 5c: string-aware brace-matching in reader content (uses the
                        // same findMatchingBrace helper as Case 4 — keeps both extraction
                        // paths in sync for braces inside string values).
                        + "  var rStepsIdx = readerText.indexOf('\"steps\"');"
                        + "  if (rStepsIdx >= 0) {"
                        + "    var rOpenIdx = readerText.lastIndexOf('{', rStepsIdx);"
                        + "    if (rOpenIdx >= 0) {"
                        + "      var rCloseIdx = findMatchingBrace(readerText, rOpenIdx);"
                        + "      if (rCloseIdx > rOpenIdx) {"
                        + "        try {"
                        + "          var rExtracted = JSON.parse(readerText.substring(rOpenIdx, rCloseIdx + 1));"
                        + "          if (rExtracted && rExtracted.steps) {"
                        + "            return {plan_json: JSON.stringify(rExtracted), markdown_plan: readerText};"
                        + "          }"
                        + "        } catch(e) {}"
                        + "      }"
                        + "    }"
                        + "  }"
                        + "}"

                        // Nothing found
                        + "return {plan_json: null, markdown_plan: text};");
    }
}
