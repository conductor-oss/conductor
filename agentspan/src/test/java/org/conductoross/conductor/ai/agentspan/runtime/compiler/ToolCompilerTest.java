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

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.metadata.agent.GuardrailConfig;
import org.conductoross.conductor.common.metadata.agent.ToolConfig;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.assertj.core.api.Assertions.*;

class ToolCompilerTest {

    @Test
    void testCompileToolSpecs_Worker() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("search")
                        .description("Search the web")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        ToolCompiler tc = new ToolCompiler();
        List<Map<String, Object>> specs = tc.compileToolSpecs(List.of(tool));

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).get("name")).isEqualTo("search");
        assertThat(specs.get(0).get("type")).isEqualTo("SIMPLE");
    }

    @Test
    void testCompileToolSpecs_Http() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("weather_api")
                        .description("Get weather")
                        .toolType("http")
                        .config(Map.of("url", "https://api.weather.com", "method", "GET"))
                        .build();

        ToolCompiler tc = new ToolCompiler();
        List<Map<String, Object>> specs = tc.compileToolSpecs(List.of(tool));

        assertThat(specs.get(0).get("type")).isEqualTo("HTTP");
    }

    @Test
    void testCompileToolSpecs_Mcp() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("mcp_tool")
                        .description("MCP tool")
                        .toolType("mcp")
                        .config(
                                Map.of(
                                        "server_url",
                                        "http://mcp.example.com",
                                        "headers",
                                        Map.of("auth", "key")))
                        .build();

        ToolCompiler tc = new ToolCompiler();
        List<Map<String, Object>> specs = tc.compileToolSpecs(List.of(tool));

        assertThat(specs.get(0).get("type")).isEqualTo("CALL_MCP_TOOL");
        @SuppressWarnings("unchecked")
        Map<String, Object> configParams = (Map<String, Object>) specs.get(0).get("configParams");
        assertThat(configParams.get("mcpServer")).isEqualTo("http://mcp.example.com");
    }

    @Test
    void buildApiDiscoveryTasksUsesListApiToolsForEachUniqueSpec() {
        ToolConfig first = apiTool("https://api.example.test/openapi.json");
        ToolConfig duplicate = apiTool("https://api.example.test/openapi.json");

        ToolCompiler.DiscoveryResult result =
                new ToolCompiler()
                        .buildApiDiscoveryTasks(
                                "support agent",
                                List.of(first, duplicate),
                                List.of(),
                                "openai/gpt-4o");

        assertThat(result.getPreTasks())
                .filteredOn(task -> "LIST_API_TOOLS".equals(task.getType()))
                .singleElement()
                .satisfies(
                        task -> {
                            assertThat(task.getTaskReferenceName())
                                    .isEqualTo("support_agent_list_api_0");
                            assertThat(task.getInputParameters())
                                    .containsEntry(
                                            "specUrl", "https://api.example.test/openapi.json");
                            assertThat(task.getInputParameters().get("headers"))
                                    .isEqualTo(
                                            Map.of(
                                                    "Authorization",
                                                    "Bearer ${workflow.secrets.API_TOKEN}"));
                        });
        assertThat(result.getPreTasks()).noneMatch(task -> "HTTP".equals(task.getType()));
        assertThat(result.getPreTasks())
                .noneMatch(task -> task.getInputParameters().containsKey("specBody"));

        WorkflowTask prepare = taskByRef(result.getPreTasks(), "support_agent_api_prepare");
        assertThat(prepare.getInputParameters())
                .containsEntry("api_discovered_0", "${support_agent_list_api_0.output}");
    }

    @Test
    void buildDiscoveryTasksFeedsApiToolOutputToCombinedPreparation() {
        ToolConfig mcp =
                ToolConfig.builder()
                        .toolType("mcp")
                        .config(Map.of("server_url", "https://mcp.example.test"))
                        .build();

        ToolCompiler.DiscoveryResult result =
                new ToolCompiler()
                        .buildDiscoveryTasks(
                                "support agent",
                                List.of(mcp),
                                List.of(apiTool("https://api.example.test/openapi.json")),
                                List.of(),
                                "openai/gpt-4o");

        assertThat(result.getPreTasks())
                .filteredOn(task -> "LIST_API_TOOLS".equals(task.getType()))
                .singleElement()
                .satisfies(
                        task -> {
                            assertThat(task.getTaskReferenceName())
                                    .isEqualTo("support_agent_list_api_0");
                            assertThat(task.getInputParameters().get("headers"))
                                    .isEqualTo(
                                            Map.of(
                                                    "Authorization",
                                                    "Bearer ${workflow.secrets.API_TOKEN}"));
                        });
        assertThat(result.getPreTasks()).noneMatch(task -> "HTTP".equals(task.getType()));
        assertThat(result.getPreTasks())
                .noneMatch(task -> task.getInputParameters().containsKey("specBody"));

        WorkflowTask prepare = taskByRef(result.getPreTasks(), "support_agent_discovery_prepare");
        assertThat(prepare.getInputParameters())
                .containsEntry("api_discovered_0", "${support_agent_list_api_0.output}");
    }

    private ToolConfig apiTool(String specUrl) {
        return ToolConfig.builder()
                .toolType("api")
                .config(
                        Map.of(
                                "url",
                                specUrl,
                                "headers",
                                Map.of("Authorization", "Bearer ${API_TOKEN}")))
                .build();
    }

    private WorkflowTask taskByRef(List<WorkflowTask> tasks, String referenceName) {
        return tasks.stream()
                .filter(task -> referenceName.equals(task.getTaskReferenceName()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void testBuildToolCallRouting() {
        ToolCompiler tc = new ToolCompiler();
        WorkflowTask router = tc.buildToolCallRouting("agent", "agent_llm", null, false, "");

        assertThat(router.getType()).isEqualTo("SWITCH");
        assertThat(router.getTaskReferenceName()).isEqualTo("agent_tool_router");
        assertThat(router.getDecisionCases()).containsKey("tool_call");
    }

    @Test
    void testBuildDynamicFork() {
        ToolCompiler tc = new ToolCompiler();
        WorkflowTask fork = tc.buildDynamicFork("agent", "${ref}", "");

        assertThat(fork.getType()).isEqualTo("FORK_JOIN_DYNAMIC");
        assertThat(fork.getDynamicForkTasksParam()).isEqualTo("dynamicTasks");
    }

    @Test
    void testCompileToolSpecs_AgentTool() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("research_agent")
                        .description("Invoke the research agent")
                        .toolType("agent_tool")
                        .inputSchema(
                                Map.of(
                                        "type", "object",
                                        "properties", Map.of("request", Map.of("type", "string")),
                                        "required", List.of("request")))
                        .config(Map.of("workflowName", "research_agent_wf"))
                        .build();

        ToolCompiler tc = new ToolCompiler();
        List<Map<String, Object>> specs = tc.compileToolSpecs(List.of(tool));

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).get("name")).isEqualTo("research_agent");
        assertThat(specs.get(0).get("type")).isEqualTo("SUB_WORKFLOW");
    }

    @Test
    void testApprovalRejectionUsesCompletedStatus() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("send_email")
                        .description("Send an email")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .approvalRequired(true)
                        .build();

        ToolCompiler tc = new ToolCompiler();
        WorkflowTask router =
                tc.buildToolCallRouting("agent", "agent_llm", List.of(tool), true, "openai/gpt-4o");

        // Navigate into the tool_call case -> approval routing
        List<WorkflowTask> toolCallTasks = router.getDecisionCases().get("tool_call");
        assertThat(toolCallTasks).isNotEmpty();

        // Find the TERMINATE task for rejection (search recursively through the task tree)
        WorkflowTask terminateTask = findTaskByRef(toolCallTasks, "agent_approval_reject");
        assertThat(terminateTask).isNotNull();
        assertThat(terminateTask.getType()).isEqualTo("TERMINATE");
        assertThat(terminateTask.getInputParameters().get("terminationStatus"))
                .isEqualTo("COMPLETED");

        // Find the SET_VARIABLE task for rejection output
        WorkflowTask setVarTask = findTaskByRef(toolCallTasks, "agent_approval_reject_output");
        assertThat(setVarTask).isNotNull();
        assertThat(setVarTask.getType()).isEqualTo("SET_VARIABLE");
        assertThat(setVarTask.getInputParameters().get("finishReason")).isEqualTo("rejected");
    }

    /** Recursively find a task whose reference name exactly matches. */
    private WorkflowTask findTaskByRef(List<WorkflowTask> tasks, String refName) {
        for (WorkflowTask t : tasks) {
            if (refName.equals(t.getTaskReferenceName())) {
                return t;
            }
            // Check decision cases
            if (t.getDecisionCases() != null) {
                for (List<WorkflowTask> caseTasks : t.getDecisionCases().values()) {
                    WorkflowTask found = findTaskByRef(caseTasks, refName);
                    if (found != null) return found;
                }
            }
            // Check default case
            if (t.getDefaultCase() != null) {
                WorkflowTask found = findTaskByRef(t.getDefaultCase(), refName);
                if (found != null) return found;
            }
            // Check loop body
            if (t.getLoopOver() != null) {
                WorkflowTask found = findTaskByRef(t.getLoopOver(), refName);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    void testBuildEnrichTask_AgentTool() {
        ToolConfig agentTool =
                ToolConfig.builder()
                        .name("researcher")
                        .toolType("agent_tool")
                        .config(Map.of("workflowName", "researcher_agent_wf"))
                        .build();

        ToolCompiler tc = new ToolCompiler();
        Object[] result = tc.buildEnrichTask("agent", "agent_llm", List.of(agentTool), "");

        WorkflowTask enrichTask = (WorkflowTask) result[0];
        assertThat(enrichTask.getType()).isEqualTo("INLINE");

        // The enrichment script should contain the agentToolCfg with the workflow name
        String script = (String) enrichTask.getInputParameters().get("expression");
        assertThat(script).contains("agentToolCfg");
        assertThat(script).contains("researcher_agent_wf");
        assertThat(script).contains("SUB_WORKFLOW");
    }

    @Test
    void testBuildEnrichTask_AgentToolWithRetryConfig() {
        ToolConfig agentTool =
                ToolConfig.builder()
                        .name("researcher")
                        .toolType("agent_tool")
                        .config(
                                Map.of(
                                        "workflowName",
                                        "researcher_agent_wf",
                                        "retryCount",
                                        5,
                                        "retryDelaySeconds",
                                        10,
                                        "optional",
                                        false))
                        .build();

        ToolCompiler tc = new ToolCompiler();
        Object[] result = tc.buildEnrichTask("agent", "agent_llm", List.of(agentTool), "");

        WorkflowTask enrichTask = (WorkflowTask) result[0];
        String script = (String) enrichTask.getInputParameters().get("expression");

        // The enrichment script should contain the retry overrides baked into agentToolCfg
        assertThat(script).contains("\"retryCount\":5");
        assertThat(script).contains("\"retryDelaySeconds\":10");
        assertThat(script).contains("\"optional\":false");
    }

    // ── Tool-level guardrail tests ──────────────────────────────────────

    @Test
    void testBuildToolCallRoutingWithResult_hasGuardrails() {
        GuardrailConfig guard =
                GuardrailConfig.builder()
                        .name("no_dangerous_cmds")
                        .guardrailType("regex")
                        .position("input")
                        .onFail("raise")
                        .patterns(List.of("rm\\s+-rf"))
                        .mode("block")
                        .build();

        ToolConfig tool =
                ToolConfig.builder()
                        .name("run_command")
                        .description("Run a CLI command")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .guardrails(List.of(guard))
                        .build();

        ToolCompiler tc = new ToolCompiler();
        ToolCompiler.ToolCallRoutingResult result =
                tc.buildToolCallRoutingWithResult(
                        "agent", "agent_llm", List.of(tool), false, "openai/gpt-4o");

        // Should have a router task
        assertThat(result.getRouterTask().getType()).isEqualTo("SWITCH");

        // Should have guardrail refs
        assertThat(result.getToolGuardrailRefs()).hasSize(1);
        assertThat(result.getToolGuardrailRetryRefs()).hasSize(1);

        // tool_call case should contain format + guardrail tasks before fork chain
        List<WorkflowTask> toolCallTasks =
                result.getRouterTask().getDecisionCases().get("tool_call");
        assertThat(toolCallTasks).isNotEmpty();

        // First task should be the format_tool_calls INLINE
        assertThat(toolCallTasks.get(0).getTaskReferenceName())
                .isEqualTo("agent_format_tool_calls");
        assertThat(toolCallTasks.get(0).getType()).isEqualTo("INLINE");
    }

    @Test
    void testBuildToolCallRoutingWithResult_noGuardrails() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("search")
                        .description("Search")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        ToolCompiler tc = new ToolCompiler();
        ToolCompiler.ToolCallRoutingResult result =
                tc.buildToolCallRoutingWithResult("agent", "agent_llm", List.of(tool), false, "");

        // No guardrails -> empty refs
        assertThat(result.getToolGuardrailRefs()).isEmpty();
        assertThat(result.getToolGuardrailRetryRefs()).isEmpty();

        // tool_call case should start with enrich (not format_tool_calls)
        List<WorkflowTask> toolCallTasks =
                result.getRouterTask().getDecisionCases().get("tool_call");
        assertThat(toolCallTasks.get(0).getTaskReferenceName()).contains("enrich_tools");
    }

    @Test
    void testBuildToolCallRoutingWithResult_guardrailsBeforeApproval() {
        GuardrailConfig guard =
                GuardrailConfig.builder()
                        .name("block_sudo")
                        .guardrailType("regex")
                        .position("input")
                        .onFail("raise")
                        .patterns(List.of("sudo"))
                        .mode("block")
                        .build();

        ToolConfig guardedTool =
                ToolConfig.builder()
                        .name("run_command")
                        .description("Run a CLI command")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .guardrails(List.of(guard))
                        .approvalRequired(true)
                        .build();

        ToolCompiler tc = new ToolCompiler();
        ToolCompiler.ToolCallRoutingResult result =
                tc.buildToolCallRoutingWithResult(
                        "agent", "agent_llm", List.of(guardedTool), true, "openai/gpt-4o");

        List<WorkflowTask> toolCallTasks =
                result.getRouterTask().getDecisionCases().get("tool_call");

        // First task: format_tool_calls (guardrails come first)
        assertThat(toolCallTasks.get(0).getTaskReferenceName())
                .isEqualTo("agent_format_tool_calls");

        // Approval tasks should come after guardrail tasks
        WorkflowTask checkApproval = findTaskByRef(toolCallTasks, "agent_check_approval");
        assertThat(checkApproval).isNotNull();

        // Verify guardrail refs are populated
        assertThat(result.getToolGuardrailRefs()).hasSize(1);
    }

    @Test
    void testBuildToolCallRoutingWithResult_backwardCompatible() {
        // Existing buildToolCallRouting should still work (returns just the task)
        ToolConfig tool =
                ToolConfig.builder()
                        .name("search")
                        .description("Search")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        ToolCompiler tc = new ToolCompiler();
        WorkflowTask router =
                tc.buildToolCallRouting("agent", "agent_llm", List.of(tool), false, "");

        assertThat(router.getType()).isEqualTo("SWITCH");
        assertThat(router.getDecisionCases()).containsKey("tool_call");
    }

    // ── selfDescribing marker (OrkesLLM contract) ────────────────────────

    @Test
    void testCompileToolSpecs_everySpecIsSelfDescribing() {
        // Every AgentSpan-compiled tool spec is complete (name + description +
        // inputSchema inline), so every spec must carry the selfDescribing
        // marker that tells OrkesLLM to skip integration resolution.
        List<ToolConfig> tools =
                List.of(
                        ToolConfig.builder()
                                .name("search")
                                .description("Search")
                                .toolType("worker")
                                .build(),
                        ToolConfig.builder()
                                .name("fetch")
                                .description("Fetch")
                                .toolType("http")
                                .config(Map.of("url", "https://example.com"))
                                .build(),
                        ToolConfig.builder()
                                .name("github")
                                .description("GitHub MCP")
                                .toolType("mcp")
                                .config(Map.of("server_url", "http://localhost:3001/mcp"))
                                .build(),
                        ToolConfig.builder()
                                .name("helper")
                                .description("Sub-agent")
                                .toolType("agent_tool")
                                .config(Map.of("workflowName", "helper_wf"))
                                .build());

        List<Map<String, Object>> specs = new ToolCompiler().compileToolSpecs(tools);

        assertThat(specs).hasSize(4);
        for (Map<String, Object> spec : specs) {
            assertThat(spec.get("selfDescribing"))
                    .as("spec '%s' must be marked selfDescribing", spec.get("name"))
                    .isEqualTo(Boolean.TRUE);
            // The marker must ALSO ride configParams: conductor-ai's ToolSpec
            // has no selfDescribing field, so the top-level key is dropped at
            // deserialization — configParams is a Map<String,Object> that
            // survives, letting OrkesLLM read it without a conductor-oss
            // release.
            assertThat(spec.get("configParams"))
                    .as("spec '%s' must carry the marker in configParams", spec.get("name"))
                    .isInstanceOfSatisfying(
                            Map.class,
                            cp -> assertThat(cp.get("selfDescribing")).isEqualTo(Boolean.TRUE));
        }
    }

    @Test
    void testCompileToolSpecs_markerMergesIntoExistingConfigParams() {
        // MCP tools already populate configParams (mcpServer/headers) — the
        // marker must merge in, not clobber them.
        ToolConfig mcp =
                ToolConfig.builder()
                        .name("github")
                        .description("GitHub MCP")
                        .toolType("mcp")
                        .config(Map.of("server_url", "http://localhost:3001/mcp"))
                        .build();

        Map<String, Object> spec = new ToolCompiler().compileToolSpecs(List.of(mcp)).get(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> cp = (Map<String, Object>) spec.get("configParams");
        assertThat(cp.get("mcpServer")).isEqualTo("http://localhost:3001/mcp");
        assertThat(cp.get("selfDescribing")).isEqualTo(Boolean.TRUE);
    }

    // ── HTTP path templating (used by OCG tools, generally available) ────

    @Test
    void testBuildEnrichTask_httpPathTemplate() {
        // An http tool may declare a pathTemplate + queryParams: the enrich
        // script builds the URI from the LLM's arguments at dispatch time
        // (URL-encoded), consumed args are excluded from the body, and
        // ${NAME} header placeholders are escaped for the credential
        // resolver. This is how OCG tools compile — plain HTTP tasks.
        ToolConfig tool =
                ToolConfig.builder()
                        .name("ocg_get_entity")
                        .description("Fetch one entity")
                        .toolType("http")
                        .config(
                                Map.of(
                                        "url", "https://us.ocg.example.com",
                                        "method", "GET",
                                        "pathTemplate", "/api/v1/entities/{entity_id}",
                                        "queryParams", List.of("depth", "limit"),
                                        "headers", Map.of("Authorization", "Bearer ${OCG_US_KEY}")))
                        .build();

        Object[] result =
                new ToolCompiler().buildEnrichTask("agent", "agent_llm", List.of(tool), "");
        String script = (String) ((WorkflowTask) result[0]).getInputParameters().get("expression");

        assertThat(script).contains("\"url\":\"https://us.ocg.example.com\"");
        assertThat(script).contains("\"pathTemplate\":\"/api/v1/entities/{entity_id}\"");
        assertThat(script).contains("\"queryParams\":[\"depth\",\"limit\"]");
        // ${OCG_US_KEY} rewritten to the inert #{OCG_US_KEY} marker in the script config. The
        // contiguous ${workflow.secrets.NAME} literal must NEVER appear in the enrich script
        // source: the script is the INLINE task's input, and conductor's substituteSecrets would
        // resolve it to plaintext at the INLINE's hand-off — persisting the secret via the
        // script's output into the forked tasks' inputs. The script converts the marker to the
        // real reference only at dynamic-task emission.
        assertThat(script).contains("Bearer #{OCG_US_KEY}");
        assertThat(script).doesNotContain("${workflow.secrets.");
        // The templating machinery itself must be in the script.
        assertThat(script).contains("pathTemplate");
        assertThat(script).contains("encodeURIComponent");
    }

    @Test
    void testBuildEnrichTask_plainHttpToolUnchanged() {
        // http tools without templating keys keep the existing static-uri,
        // args-as-body shape — no behavior change for the established path.
        ToolConfig tool =
                ToolConfig.builder()
                        .name("weather")
                        .description("Get weather")
                        .toolType("http")
                        .config(Map.of("url", "https://api.weather.com", "method", "POST"))
                        .build();

        Object[] result =
                new ToolCompiler().buildEnrichTask("agent", "agent_llm", List.of(tool), "");
        String script = (String) ((WorkflowTask) result[0]).getInputParameters().get("expression");

        assertThat(script).contains("\"url\":\"https://api.weather.com\"");
        assertThat(script).doesNotContain("\"weather\":{\"pathTemplate\"");
    }
}
