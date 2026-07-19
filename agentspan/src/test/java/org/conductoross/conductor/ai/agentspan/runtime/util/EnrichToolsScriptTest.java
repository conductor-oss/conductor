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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the dynamic-fork enrichment script that turns LLM-emitted {@code toolCalls} into
 * Conductor task definitions. The critical contract: a tool name the LLM hallucinated (i.e. not in
 * the configured tool list) must NOT become a SCHEDULED-with-no-poller task. It should become an
 * INLINE error task that returns a model-visible error result.
 */
class EnrichToolsScriptTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private Context graalCtx;

    @BeforeEach
    void setUp() {
        graalCtx = Context.newBuilder("js").allowAllAccess(true).build();
    }

    @AfterEach
    void tearDown() {
        graalCtx.close();
    }

    private List<Map<String, Object>> enrich(String knownNamesJson, String toolCallsJson)
            throws Exception {
        // All optional config maps are empty so every name falls through to the
        // generic SIMPLE-or-unknown branch. That's the path the harness uses.
        return enrichWithConfigs("{}", "{}", knownNamesJson, toolCallsJson);
    }

    private List<Map<String, Object>> enrichWithAgentTools(
            String agentToolJson, String knownNamesJson, String toolCallsJson) throws Exception {
        return enrichWithConfigs("{}", agentToolJson, knownNamesJson, toolCallsJson);
    }

    private List<Map<String, Object>> enrichWithConfigs(
            String httpJson, String agentToolJson, String knownNamesJson, String toolCallsJson)
            throws Exception {
        return enrichWithHttpMcp(httpJson, "{}", agentToolJson, knownNamesJson, toolCallsJson);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> enrichWithHttpMcp(
            String httpJson,
            String mcpJson,
            String agentToolJson,
            String knownNamesJson,
            String toolCallsJson)
            throws Exception {
        String script =
                JavaScriptBuilder.enrichToolsScript(
                        httpJson,
                        mcpJson,
                        "{}",
                        agentToolJson,
                        "{}",
                        "{}",
                        "{}",
                        "{}",
                        knownNamesJson);
        // Wrap so the script's IIFE return is captured AND we get a JSON string
        // back — Graal's Value.toString() is JS source, not JSON.
        String wrapped =
                "var $ = {"
                        + "toolCalls: "
                        + toolCallsJson
                        + ","
                        + "agentState: {},"
                        + "userPrompt: 'test'"
                        + "}; JSON.stringify("
                        + script
                        + ");";
        Value v = graalCtx.eval("js", wrapped);
        String json = v.asString();
        Map<String, Object> outer = MAPPER.readValue(json, Map.class);
        Object tasks =
                outer.containsKey("dynamicTasks") ? outer.get("dynamicTasks") : outer.get("tasks");
        return (List<Map<String, Object>>) tasks;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> enrichDynamic(
            String httpJson, String knownNamesJson, String toolCallsJson) throws Exception {
        String script =
                JavaScriptBuilder.enrichToolsScriptDynamic(
                        httpJson, "{}", "{}", "{}", "{}", "{}", knownNamesJson);
        String wrapped =
                "var $ = {toolCalls: "
                        + toolCallsJson
                        + ", agentState: {}, userPrompt: 'test', mcpConfig: {}, apiConfig: {}}; JSON.stringify("
                        + script
                        + ");";
        Map<String, Object> outer =
                MAPPER.readValue(graalCtx.eval("js", wrapped).asString(), Map.class);
        return (List<Map<String, Object>>) outer.get("dynamicTasks");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> evaluateWithJavaInputs(String script, Map<String, Object> inputs)
            throws Exception {
        // Bind Java Maps/Lists exactly as INLINE tasks do. This catches regressions where a
        // generated script works with JSON literals but drops data at the Graal host-object
        // boundary.
        graalCtx.getBindings("js").putMember("$", inputs);
        String json = graalCtx.eval("js", "JSON.stringify(" + script + ");").asString();
        return MAPPER.readValue(json, Map.class);
    }

    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put((String) entries[i], entries[i + 1]);
        }
        return result;
    }

    @Test
    void unknownToolBecomesInlineErrorTask() throws Exception {
        // Configure two known tools; have the LLM call a third name.
        String known = "{\"shell\": true, \"read_file\": true}";
        String toolCalls =
                "[{\"name\": \"find\", \"taskReferenceName\": \"call_1\","
                        + " \"inputParameters\": {\"path\": \"/tmp\"}}]";

        List<Map<String, Object>> tasks = enrich(known, toolCalls);
        assertThat(tasks).hasSize(1);
        Map<String, Object> t = tasks.get(0);
        assertThat(t.get("type")).isEqualTo("INLINE");
        Map<String, Object> ip = (Map<String, Object>) t.get("inputParameters");
        assertThat(ip.get("evaluatorType")).isEqualTo("graaljs");
        String errMsg = (String) ip.get("errorMessage");
        assertThat(errMsg).contains("Unknown tool 'find'");
        assertThat(errMsg).contains("shell");
        assertThat(errMsg).contains("read_file");
    }

    @Test
    void knownToolStaysAsSimpleTask() throws Exception {
        String known = "{\"shell\": true}";
        String toolCalls =
                "[{\"name\": \"shell\", \"taskReferenceName\": \"call_1\","
                        + " \"inputParameters\": {\"command\": \"echo hi\"}}]";

        List<Map<String, Object>> tasks = enrich(known, toolCalls);
        assertThat(tasks).hasSize(1);
        Map<String, Object> t = tasks.get(0);
        assertThat(t.get("type")).isEqualTo("SIMPLE");
        assertThat(t.get("name")).isEqualTo("shell");
    }

    @Test
    void emptyKnownNamesRejectsAllToolCalls() throws Exception {
        // An agent with ``tools=[]`` exposes NO callable tools to the LLM.
        // Any hallucinated tool_call must be rejected as unknown. The
        // previous behavior (passthrough as SIMPLE) was the prefill-only
        // leak: tools registered for prefill execution would dispatch
        // hallucinated calls because the unknown-name check was bypassed
        // whenever knownNames was empty. New contract: empty knownNames
        // means EVERY name is unknown.
        String known = "{}";
        String toolCalls =
                "[{\"name\": \"anything\", \"taskReferenceName\": \"c1\","
                        + " \"inputParameters\": {}}]";

        List<Map<String, Object>> tasks = enrich(known, toolCalls);
        assertThat(tasks).hasSize(1);
        Map<String, Object> t = tasks.get(0);
        assertThat(t.get("type"))
                .as("empty knownNames must produce an INLINE error task, not SIMPLE")
                .isEqualTo("INLINE");
        @SuppressWarnings("unchecked")
        Map<String, Object> ip = (Map<String, Object>) t.get("inputParameters");
        assertThat((String) ip.get("errorMessage")).contains("Unknown tool 'anything'");
    }

    @Test
    void prefillOnlyToolHallucinationRejected() throws Exception {
        // Deterministic e2e for the prefill-only leak. Agent declares ONE
        // LLM-callable tool (``write_task_brief``). The model hallucinates
        // a call to ``contextbook_read`` — a tool that's only in
        // ``prefill_tools`` (so a worker IS registered for it, but the LLM
        // was never told about it). The dispatch must NOT route the
        // hallucinated call to the registered prefill worker; it must
        // produce an unknown-tool error visible to the model.
        String known = "{\"write_task_brief\": true}";
        String toolCalls =
                "[{\"name\": \"contextbook_read\", \"taskReferenceName\": \"call_halluc\","
                        + " \"inputParameters\": {\"section\": \"issue_pr\"}}]";

        List<Map<String, Object>> tasks = enrich(known, toolCalls);
        assertThat(tasks).hasSize(1);
        Map<String, Object> t = tasks.get(0);
        assertThat(t.get("type"))
                .as(
                        "prefill-only tool hallucinated by LLM must NOT dispatch as SIMPLE — "
                                + "if it did, the prefill worker registration would execute the call")
                .isEqualTo("INLINE");
        @SuppressWarnings("unchecked")
        Map<String, Object> ip = (Map<String, Object>) t.get("inputParameters");
        String err = (String) ip.get("errorMessage");
        assertThat(err).contains("Unknown tool 'contextbook_read'");
        assertThat(err)
                .as(
                        "error message lists the agent's actual callable tools, so the model "
                                + "knows what it CAN call going forward")
                .contains("write_task_brief")
                .doesNotContain("contextbook_read'. Available tools: contextbook_read");
    }

    @Test
    void prefillToolAlsoInDeclaredToolsIsCallable() throws Exception {
        // Some agents legitimately list a tool in BOTH prefill_tools AND
        // tools=[..] (the prefill is for first-turn priming; subsequent
        // turns let the LLM call it on demand). Such tools must remain
        // callable — only prefill-ONLY names are blocked.
        String known = "{\"contextbook_read\": true, \"write_task_brief\": true}";
        String toolCalls =
                "[{\"name\": \"contextbook_read\", \"taskReferenceName\": \"call_1\","
                        + " \"inputParameters\": {\"section\": \"issue_pr\"}}]";

        List<Map<String, Object>> tasks = enrich(known, toolCalls);
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).get("type")).isEqualTo("SIMPLE");
        assertThat(tasks.get(0).get("name")).isEqualTo("contextbook_read");
    }

    @Test
    void agentToolDispatchInjectsTodayDateInput() throws Exception {
        // Sub-agents whose prompts anchor relative dates ("recent", "last
        // week") reference ${workflow.input.__today__}. The dispatch script
        // runs per execution, so the date it injects is the actual current
        // date — unlike anything computed at compile/boot time, which
        // drifts on a long-running server.
        String agentTools = "{\"helper_agent\": {\"workflowName\": \"_helper_agent\"}}";
        String known = "{\"helper_agent\": true}";
        String toolCalls =
                "[{\"name\": \"helper_agent\", \"taskReferenceName\": \"c1\","
                        + " \"inputParameters\": {\"request\": \"find recent alerts\"}}]";

        List<Map<String, Object>> tasks = enrichWithAgentTools(agentTools, known, toolCalls);
        assertThat(tasks).hasSize(1);
        Map<String, Object> t = tasks.get(0);
        assertThat(t.get("type")).isEqualTo("SUB_WORKFLOW");
        @SuppressWarnings("unchecked")
        Map<String, Object> ip = (Map<String, Object>) t.get("inputParameters");
        assertThat((String) ip.get("__today__"))
                .as("dispatch must inject today's UTC date as __today__ sub-workflow input")
                .matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void mixedKnownAndUnknownInOneTurn() throws Exception {
        String known = "{\"shell\": true}";
        String toolCalls =
                "["
                        + "{\"name\": \"shell\", \"taskReferenceName\": \"c1\", \"inputParameters\": {}},"
                        + "{\"name\": \"find\",  \"taskReferenceName\": \"c2\", \"inputParameters\": {}}"
                        + "]";
        List<Map<String, Object>> tasks = enrich(known, toolCalls);
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).get("type")).isEqualTo("SIMPLE");
        assertThat(tasks.get(1).get("type")).isEqualTo("INLINE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void httpPathTemplateBuildsUriAndPrunesBody() throws Exception {
        // OCG-style tool: GET with a path param and query params. The script
        // must URL-encode args into the URI and drop consumed args from the
        // body.
        String httpCfg =
                "{\"ocg_get_entity\": {"
                        + "\"url\": \"https://us.ocg.example.com\","
                        + "\"method\": \"GET\","
                        + "\"pathTemplate\": \"/api/v1/entities/{entity_id}\","
                        + "\"queryParams\": [\"depth\", \"limit\"],"
                        + "\"headers\": {\"Authorization\": \"Bearer #{OCG_US_KEY}\"}}}";
        String toolCalls =
                "[{\"name\": \"ocg_get_entity\", \"taskReferenceName\": \"call_1\","
                        + " \"inputParameters\": {\"entity_id\": \"entity_01/AB C\", \"depth\": 2}}]";

        List<Map<String, Object>> tasks =
                enrichWithConfigs(httpCfg, "{}", "{\"ocg_get_entity\": true}", toolCalls);

        assertThat(tasks).hasSize(1);
        Map<String, Object> task = tasks.get(0);
        assertThat(task.get("type")).isEqualTo("HTTP");
        Map<String, Object> req =
                (Map<String, Object>)
                        ((Map<String, Object>) task.get("inputParameters")).get("http_request");
        assertThat(req.get("uri"))
                .isEqualTo("https://us.ocg.example.com/api/v1/entities/entity_01%2FAB%20C?depth=2");
        assertThat(req.get("method")).isEqualTo("GET");
        assertThat((Map<String, Object>) req.get("headers"))
                .containsEntry("Authorization", "Bearer ${workflow.secrets.OCG_US_KEY}");
        // entity_id and depth were consumed; GET requests carry no body.
        assertThat(req).doesNotContainKey("body");
    }

    @Test
    @SuppressWarnings("unchecked")
    void httpWithoutTemplateKeepsArgsAsBody() throws Exception {
        // Established http_tool shape: static uri, all args as body.
        String httpCfg =
                "{\"weather\": {\"url\": \"https://api.weather.com\", \"method\": \"POST\"}}";
        String toolCalls =
                "[{\"name\": \"weather\", \"taskReferenceName\": \"call_1\","
                        + " \"inputParameters\": {\"city\": \"SF\", \"method\": \"weather\"}}]";

        List<Map<String, Object>> tasks =
                enrichWithConfigs(httpCfg, "{}", "{\"weather\": true}", toolCalls);

        Map<String, Object> req =
                (Map<String, Object>)
                        ((Map<String, Object>) tasks.get(0).get("inputParameters"))
                                .get("http_request");
        assertThat(req.get("uri")).isEqualTo("https://api.weather.com");
        assertThat((Map<String, Object>) req.get("body"))
                .containsEntry("city", "SF")
                .doesNotContainKey("method");
    }

    @Test
    @SuppressWarnings("unchecked")
    void bodylessHttpMethodsPutArgumentsInTheQueryForBothDispatchers() throws Exception {
        String httpCfg =
                "{\"weather\": {\"url\": \"https://api.example/weather?source=agent#details\", \"method\": \"GET\"}}";
        String toolCalls =
                "[{\"name\": \"weather\", \"taskReferenceName\": \"call_1\","
                        + " \"inputParameters\": {\"city\": \"New York/NY\", \"active\": false, \"count\": 0, \"method\": \"weather\", \"empty\": \"\", \"missing\": null}}]";

        for (List<Map<String, Object>> tasks :
                List.of(
                        enrichWithConfigs(httpCfg, "{}", "{\"weather\": true}", toolCalls),
                        enrichDynamic(httpCfg, "{\"weather\": true}", toolCalls))) {
            Map<String, Object> request =
                    (Map<String, Object>)
                            ((Map<String, Object>) tasks.get(0).get("inputParameters"))
                                    .get("http_request");
            assertThat(request.get("uri"))
                    .isEqualTo(
                            "https://api.example/weather?source=agent&city=New%20York%2FNY&active=false&count=0#details");
            assertThat(request).doesNotContainKey("body");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void javaMapToolArgumentsDoNotLeakMapMethodsIntoGetQuery() throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("city", "London");
        args.put("method", "get_current_weather_http");
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("name", "get_current_weather_http");
        call.put("taskReferenceName", "call_weather");
        call.put("inputParameters", args);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("toolCalls", List.of(call));
        input.put("agentState", Map.of());
        input.put("userPrompt", "weather");
        input.put("mcpConfig", Map.of());
        input.put("apiConfig", Map.of());
        graalCtx.getBindings("js").putMember("$", input);

        String script =
                JavaScriptBuilder.enrichToolsScript(
                        "{\"get_current_weather_http\":{\"url\":\"http://localhost:3001/api/weather\",\"method\":\"GET\"}}",
                        "{}",
                        "{}",
                        "{}",
                        "{}",
                        "{}",
                        "{}",
                        "{}",
                        "{\"get_current_weather_http\":true}");
        String dynamicScript =
                JavaScriptBuilder.enrichToolsScriptDynamic(
                        "{\"get_current_weather_http\":{\"url\":\"http://localhost:3001/api/weather\",\"method\":\"GET\"}}",
                        "{}",
                        "{}",
                        "{}",
                        "{}",
                        "{}",
                        "{\"get_current_weather_http\":true}");
        for (String candidate : List.of(script, dynamicScript)) {
            Map<String, Object> outer =
                    MAPPER.readValue(
                            graalCtx.eval("js", "JSON.stringify(" + candidate + ");").asString(),
                            Map.class);
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) outer.get("dynamicTasks");
            Map<String, Object> request =
                    (Map<String, Object>)
                            ((Map<String, Object>) tasks.get(0).get("inputParameters"))
                                    .get("http_request");
            assertThat(request.get("uri"))
                    .isEqualTo("http://localhost:3001/api/weather?city=London");
            assertThat(request).doesNotContainKey("body");
        }
    }

    // ── Credential markers: #{NAME} in configs must be emitted as ${workflow.secrets.NAME} ──
    // The marker form is inert to BOTH ParametersUtils passes (the general ${...} binding and
    // substituteSecrets), so it can ride through the enrich INLINE's input without being resolved
    // to plaintext there. The script converts it to the real secret reference only at emission
    // into the dynamic task's inputParameters, where conductor resolves it wire-only at that
    // task's own hand-off.

    @Test
    @SuppressWarnings("unchecked")
    void httpHeaderMarker_emittedAsSecretReference() throws Exception {
        String httpCfg =
                "{\"weather\": {\"url\": \"https://api.weather.com\", \"method\": \"POST\","
                        + "\"headers\": {\"Authorization\": \"Bearer #{API_KEY}\"}}}";
        String toolCalls =
                "[{\"name\": \"weather\", \"taskReferenceName\": \"call_1\","
                        + " \"inputParameters\": {\"city\": \"SF\"}}]";

        List<Map<String, Object>> tasks =
                enrichWithConfigs(httpCfg, "{}", "{\"weather\": true}", toolCalls);

        Map<String, Object> req =
                (Map<String, Object>)
                        ((Map<String, Object>) tasks.get(0).get("inputParameters"))
                                .get("http_request");
        assertThat((Map<String, Object>) req.get("headers"))
                .containsEntry("Authorization", "Bearer ${workflow.secrets.API_KEY}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dottedHeaderMarker_emittedAsSecretReferenceWithJsonPath() throws Exception {
        String httpCfg =
                "{\"gcp\": {\"url\": \"https://gcp.example.com\", \"method\": \"GET\","
                        + "\"headers\": {\"X-Project\": \"#{GCP_SVC.project_id}\"}}}";
        String toolCalls =
                "[{\"name\": \"gcp\", \"taskReferenceName\": \"call_1\", \"inputParameters\": {}}]";

        List<Map<String, Object>> tasks =
                enrichWithConfigs(httpCfg, "{}", "{\"gcp\": true}", toolCalls);

        Map<String, Object> req =
                (Map<String, Object>)
                        ((Map<String, Object>) tasks.get(0).get("inputParameters"))
                                .get("http_request");
        assertThat((Map<String, Object>) req.get("headers"))
                .containsEntry("X-Project", "${workflow.secrets.GCP_SVC.project_id}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mcpHeaderMarker_emittedAsSecretReference() throws Exception {
        String mcpCfg =
                "{\"notion_search\": {\"mcpServer\": \"https://mcp.notion.com/mcp\","
                        + "\"headers\": {\"Authorization\": \"Bearer #{NOTION_KEY}\"}}}";
        String toolCalls =
                "[{\"name\": \"notion_search\", \"taskReferenceName\": \"call_1\", \"inputParameters\": {}}]";

        List<Map<String, Object>> tasks =
                enrichWithHttpMcp("{}", mcpCfg, "{}", "{\"notion_search\": true}", toolCalls);

        Map<String, Object> t = tasks.get(0);
        assertThat(t.get("type")).isEqualTo("CALL_MCP_TOOL");
        Map<String, Object> ip = (Map<String, Object>) t.get("inputParameters");
        assertThat((Map<String, Object>) ip.get("headers"))
                .containsEntry("Authorization", "Bearer ${workflow.secrets.NOTION_KEY}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void discoveredMcpSchemaAndMarkerSurvivePrepareFilterAndResolve() throws Exception {
        Map<String, Object> schema =
                map(
                        "type",
                        "object",
                        "properties",
                        map(
                                "city",
                                map("type", "string", "enum", List.of("London", "Paris")),
                                "days",
                                map("type", "array", "items", map("type", "integer"))),
                        "required",
                        List.of("city"));
        Map<String, Object> discovered =
                map("name", "get_weather", "description", "Lookup weather", "inputSchema", schema);

        Map<String, Object> prepared =
                evaluateWithJavaInputs(
                        JavaScriptBuilder.mcpPrepareScript(
                                "[]",
                                1,
                                "[{\"serverUrl\":\"http://localhost:3001/mcp\",\"headers\":{}}]",
                                0),
                        map("discovered_0", List.of(discovered)));
        Map<String, Object> preparedTool =
                ((List<Map<String, Object>>) prepared.get("tools")).get(0);
        assertThat(preparedTool.get("inputSchema")).isEqualTo(schema);
        assertThat(preparedTool.get("selfDescribing")).isEqualTo(true);
        assertThat((Map<String, Object>) preparedTool.get("configParams"))
                .containsEntry("selfDescribing", true)
                .containsEntry("mcpServer", "http://localhost:3001/mcp");

        Map<String, Object> filtered =
                evaluateWithJavaInputs(
                        JavaScriptBuilder.filterToolsScriptDynamic(),
                        map(
                                "allTools",
                                prepared.get("tools"),
                                "selectedNames",
                                "[\"get_weather\"]"));
        Map<String, Object> resolved =
                evaluateWithJavaInputs(
                        JavaScriptBuilder.mcpResolveScript(),
                        map(
                                "filtered_tools",
                                filtered.get("tools"),
                                "prepared_tools",
                                prepared.get("tools"),
                                "mcpConfig",
                                prepared.get("mcpConfig"),
                                "apiConfig",
                                Map.of()));
        Map<String, Object> resolvedTool =
                ((List<Map<String, Object>>) resolved.get("tools")).get(0);
        assertThat(resolvedTool.get("inputSchema")).isEqualTo(schema);
        assertThat((Map<String, Object>) resolvedTool.get("configParams"))
                .containsEntry("selfDescribing", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void discoveredApiSchemaIsSelfDescribingBeforeLlmReceivesIt() throws Exception {
        Map<String, Object> schema =
                map(
                        "type",
                        "object",
                        "properties",
                        map(
                                "city",
                                map("type", "string"),
                                "includeForecast",
                                map("type", "boolean")),
                        "required",
                        List.of("city"));
        Map<String, Object> operation =
                map(
                        "name",
                        "get_weather",
                        "description",
                        "Lookup weather",
                        "method",
                        "GET",
                        "path",
                        "/weather",
                        "inputSchema",
                        schema);
        Map<String, Object> discoveredApi =
                map("baseUrl", "http://localhost:3001/api", "tools", List.of(operation));

        Map<String, Object> prepared =
                evaluateWithJavaInputs(
                        JavaScriptBuilder.apiPrepareScript(
                                "[]", 0, "[]", 1, "[{\"headers\":{}}]", 32),
                        map("api_discovered_0", discoveredApi));
        Map<String, Object> tool = ((List<Map<String, Object>>) prepared.get("tools")).get(0);
        assertThat(tool.get("type")).isEqualTo("HTTP");
        assertThat(tool.get("inputSchema")).isEqualTo(schema);
        assertThat(tool.get("selfDescribing")).isEqualTo(true);
        assertThat((Map<String, Object>) tool.get("configParams"))
                .containsEntry("selfDescribing", true);
    }

    @Test
    void scriptSource_neverContainsContiguousSecretReferencePattern() {
        // The leak tripwire: if the contiguous literal '${workflow.secrets.' appears anywhere in
        // the enrich script SOURCE (which is the INLINE task's input), conductor's
        // substituteSecrets resolves it to plaintext at the INLINE's hand-off and the plaintext
        // persists via the script's output into the forked tasks' inputs.
        String httpCfg =
                "{\"weather\": {\"url\": \"https://api.weather.com\", \"method\": \"POST\","
                        + "\"headers\": {\"Authorization\": \"Bearer #{API_KEY}\"}}}";
        String script =
                JavaScriptBuilder.enrichToolsScript(
                        httpCfg, "{}", "{}", "{}", "{}", "{}", "{}", "{}", "{\"weather\": true}");
        assertThat(script).doesNotContain("${workflow.secrets.");

        String dynScript =
                JavaScriptBuilder.enrichToolsScriptDynamic(
                        httpCfg, "{}", "{}", "{}", "{}", "{}", "{\"weather\": true}");
        assertThat(dynScript).doesNotContain("${workflow.secrets.");
    }
}
