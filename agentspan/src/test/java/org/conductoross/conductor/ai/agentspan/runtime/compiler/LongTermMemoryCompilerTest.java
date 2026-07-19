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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.conductoross.conductor.common.metadata.agent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the long-term (OCG) memory compilation path: pre-loop retrieval (search/format/set
 * context), the LTM system message, and post-loop distill/save/feedback tasks.
 */
class LongTermMemoryCompilerTest {

    private AgentCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new AgentCompiler();
    }

    private static ToolConfig searchTool() {
        return ToolConfig.builder()
                .name("search")
                .description("Search the web")
                .inputSchema(
                        Map.of(
                                "type",
                                "object",
                                "properties",
                                Map.of("query", Map.of("type", "string"))))
                .toolType("worker")
                .build();
    }

    private static LongTermMemoryConfig ltm() {
        return LongTermMemoryConfig.builder()
                .ocgUrl("https://ocg.example.com")
                .credential("OCG_PUBLIC_KEY")
                .agent("agent:ce-ticket-resolution")
                .user("user:alice")
                .maxResults(3)
                .summaryModel("openai/gpt-4o-mini")
                .build();
    }

    private static AgentConfig.AgentConfigBuilder ltmAgent() {
        return AgentConfig.builder()
                .name("ltm_agent")
                .model("openai/gpt-4o")
                .instructions("Resolve tickets.")
                .tools(List.of(searchTool()))
                .longTermMemory(ltm());
    }

    private static Optional<WorkflowTask> taskByRef(WorkflowDef wf, String ref) {
        return wf.getTasks().stream().filter(t -> ref.equals(t.getTaskReferenceName())).findFirst();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> httpRequest(WorkflowTask task) {
        return (Map<String, Object>) task.getInputParameters().get("http_request");
    }

    @Test
    void testRetrievalTasksCompiledBeforeLoop() {
        WorkflowDef wf = compiler.compile(ltmAgent().build());

        List<String> refs = wf.getTasks().stream().map(WorkflowTask::getTaskReferenceName).toList();
        assertThat(refs)
                .containsSubsequence(
                        "ltm_agent_ltm_search",
                        "ltm_agent_ltm_format",
                        "ltm_agent_ltm_set_context",
                        "ltm_agent_loop");

        WorkflowTask search = taskByRef(wf, "ltm_agent_ltm_search").orElseThrow();
        assertThat(search.getType()).isEqualTo("HTTP");
        assertThat(search.isOptional()).isTrue();
        Map<String, Object> httpReq = httpRequest(search);
        assertThat(httpReq.get("uri")).isEqualTo("https://ocg.example.com/api/v1/memories/search");
        assertThat(httpReq.get("method")).isEqualTo("POST");
        Map<String, Object> body = (Map<String, Object>) httpReq.get("body");
        assertThat(body.get("query")).isEqualTo("${workflow.input.prompt}");
        assertThat(body.get("agent")).isEqualTo("agent:ce-ticket-resolution");
        assertThat(body.get("limit")).isEqualTo(3);
        assertThat(body.get("include_shared")).isEqualTo(true);
        assertThat(body.get("user")).isEqualTo("user:alice");

        WorkflowTask format = taskByRef(wf, "ltm_agent_ltm_format").orElseThrow();
        assertThat(format.getType()).isEqualTo("INLINE");
        assertThat(format.isOptional()).isTrue();
        assertThat(format.getInputParameters().get("memories"))
                .isEqualTo("${ltm_agent_ltm_search.output.response.body.memories}");

        WorkflowTask setCtx = taskByRef(wf, "ltm_agent_ltm_set_context").orElseThrow();
        assertThat(setCtx.getType()).isEqualTo("SET_VARIABLE");
        assertThat(setCtx.isOptional()).isTrue();
        assertThat(setCtx.getInputParameters().get("_ltm_context"))
                .isEqualTo("${ltm_agent_ltm_format.output.result}");
    }

    @Test
    void testCredentialHeaderResolvesViaWorkflowSecrets() {
        WorkflowDef wf = compiler.compile(ltmAgent().build());

        WorkflowTask search = taskByRef(wf, "ltm_agent_ltm_search").orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) httpRequest(search).get("headers");
        assertThat(headers.get("Authorization"))
                .isEqualTo("Bearer ${workflow.secrets.OCG_PUBLIC_KEY}");
        assertThat(headers.get("Content-Type")).isEqualTo("application/json");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSaveTasksCompiledAfterOutputSynthesis() {
        WorkflowDef wf = compiler.compile(ltmAgent().build());

        List<String> refs = wf.getTasks().stream().map(WorkflowTask::getTaskReferenceName).toList();
        assertThat(refs)
                .containsSubsequence(
                        "ltm_agent_synth_output",
                        "ltm_agent_ltm_distill",
                        "ltm_agent_ltm_build_value",
                        "ltm_agent_ltm_save",
                        "ltm_agent_ltm_feedback_links");

        WorkflowTask distill = taskByRef(wf, "ltm_agent_ltm_distill").orElseThrow();
        assertThat(distill.getType()).isEqualTo("LLM_CHAT_COMPLETE");
        assertThat(distill.isOptional()).isTrue();
        assertThat(distill.getInputParameters().get("llmProvider")).isEqualTo("openai");
        assertThat(distill.getInputParameters().get("model")).isEqualTo("gpt-4o-mini");
        assertThat(distill.getInputParameters().get("jsonOutput")).isEqualTo(true);
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) distill.getInputParameters().get("messages");
        assertThat(messages.get(1).get("message").toString())
                .contains("${ltm_agent_synth_output.output.result}");

        WorkflowTask buildValue = taskByRef(wf, "ltm_agent_ltm_build_value").orElseThrow();
        assertThat(buildValue.getType()).isEqualTo("INLINE");
        // The distiller's result is a JSON *string*; the INLINE parses it.
        assertThat(buildValue.getInputParameters().get("distilled"))
                .isEqualTo("${ltm_agent_ltm_distill.output.result}");

        WorkflowTask save = taskByRef(wf, "ltm_agent_ltm_save").orElseThrow();
        Map<String, Object> saveBody = (Map<String, Object>) httpRequest(save).get("body");
        assertThat(saveBody.get("key")).isEqualTo("conversation:${workflow.workflowId}");
        assertThat(saveBody.get("value"))
                .isEqualTo("${ltm_agent_ltm_build_value.output.result.value}");
        assertThat(saveBody.get("scope")).isEqualTo("agent");
        assertThat(saveBody.get("source")).isEqualTo("agent_inferred");
        assertThat(saveBody.get("user")).isEqualTo("user:alice");

        WorkflowTask links = taskByRef(wf, "ltm_agent_ltm_feedback_links").orElseThrow();
        assertThat(httpRequest(links).get("uri").toString())
                .isEqualTo(
                        "https://ocg.example.com/api/v1/memories/conversation:${workflow.workflowId}"
                                + "/feedback-links?agent=agent:ce-ticket-resolution&user=user:alice");
        assertThat(httpRequest(links)).doesNotContainKey("body");
    }

    @Test
    void testFeedbackSinkTaskEmittedOnlyWhenConfigured() {
        WorkflowDef withoutSink = compiler.compile(ltmAgent().build());
        assertThat(taskByRef(withoutSink, "ltm_agent_feedback_sink")).isEmpty();

        WorkflowDef withSink =
                compiler.compile(
                        ltmAgent()
                                .feedbackSink(WorkerRef.builder().taskName("zendesk_sink").build())
                                .build());
        WorkflowTask sink = taskByRef(withSink, "ltm_agent_feedback_sink").orElseThrow();
        assertThat(sink.getType()).isEqualTo("SIMPLE");
        assertThat(sink.getName()).isEqualTo("zendesk_sink");
        assertThat(sink.isOptional()).isTrue();
        assertThat(sink.getInputParameters().get("good_url"))
                .isEqualTo("${ltm_agent_ltm_feedback_links.output.response.body.good_url}");
        assertThat(sink.getInputParameters().get("bad_url"))
                .isEqualTo("${ltm_agent_ltm_feedback_links.output.response.body.bad_url}");
        assertThat(sink.getInputParameters().get("summary"))
                .isEqualTo("${ltm_agent_ltm_build_value.output.result.summary}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testLtmContextInjectedIntoLlmSystemMessages() {
        WorkflowDef wf = compiler.compile(ltmAgent().build());

        WorkflowTask initState = taskByRef(wf, "ltm_agent_init_state").orElseThrow();
        assertThat(initState.getInputParameters().get("_ltm_context")).isEqualTo("");

        WorkflowTask loop = taskByRef(wf, "ltm_agent_loop").orElseThrow();
        WorkflowTask llm =
                loop.getLoopOver().stream()
                        .filter(t -> "LLM_CHAT_COMPLETE".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) llm.getInputParameters().get("messages");
        assertThat(messages)
                .anySatisfy(
                        m -> {
                            assertThat(m.get("role")).isEqualTo("system");
                            assertThat(m.get("message"))
                                    .isEqualTo("${workflow.variables._ltm_context}");
                        });
    }

    @Test
    void testSummaryModelFallsBackToAgentModel() {
        LongTermMemoryConfig noSummaryModel = ltm();
        noSummaryModel.setSummaryModel(null);
        WorkflowDef wf = compiler.compile(ltmAgent().longTermMemory(noSummaryModel).build());

        WorkflowTask distill = taskByRef(wf, "ltm_agent_ltm_distill").orElseThrow();
        assertThat(distill.getInputParameters().get("llmProvider")).isEqualTo("openai");
        assertThat(distill.getInputParameters().get("model")).isEqualTo("gpt-4o");
    }

    @Test
    void testNoLtmTasksWhenLongTermMemoryAbsent() {
        WorkflowDef wf = compiler.compile(ltmAgent().longTermMemory(null).build());

        assertThat(wf.getTasks()).noneMatch(t -> t.getTaskReferenceName().contains("_ltm_"));
        WorkflowTask initState = taskByRef(wf, "ltm_agent_init_state").orElseThrow();
        assertThat(initState.getInputParameters()).doesNotContainKey("_ltm_context");
    }

    @Test
    void testMediaDefaultsToEmptyListViaInputTemplate() {
        WorkflowDef wf = compiler.compile(ltmAgent().build());
        assertThat(wf.getInputTemplate()).containsEntry("media", List.of());
    }
}
