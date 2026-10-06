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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.conductoross.conductor.common.metadata.agent.GuardrailConfig;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskGuardrailsCompilerTest {

    private static final Map<String, Object> REDACT_PII =
            Map.of("guardrail", "pii", "action", "REDACT");

    private final AgentCompiler compiler = enforcingCompiler();

    @Test
    void serverWithoutEnforcementRejectsTheAgent() {
        AgentConfig config = agent("guarded").taskGuardrails(List.of("pii")).build();

        assertThatThrownBy(() -> new AgentCompiler().compile(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("guarded")
                .hasMessageContaining("does not enforce");
    }

    @Test
    void agentWithoutServerGuardrailsCompilesAsBefore() {
        WorkflowDef wf = new AgentCompiler().compile(agent("plain").build());

        assertThat(llmTasks(wf))
                .allSatisfy(
                        llm ->
                                assertThat(llm.getInputParameters())
                                        .doesNotContainKey(TaskGuardrails.INPUT));
    }

    @Test
    void theAgentsLlmTaskBindsEachGuardrailOnce() {
        WorkflowDef wf =
                compiler.compile(
                        agent("guarded").taskGuardrails(List.of("pii", REDACT_PII)).build());

        assertThat(llmTasks(wf))
                .singleElement()
                .satisfies(
                        llm ->
                                assertThat(llm.getInputParameters().get(TaskGuardrails.INPUT))
                                        .isEqualTo(List.of("pii")));
    }

    @Test
    void aGuardrailJudgeIsNotGuarded() {
        GuardrailConfig judge =
                GuardrailConfig.builder()
                        .name("polite")
                        .guardrailType("llm")
                        .position("output")
                        .onFail("retry")
                        .model("openai/gpt-4o-mini")
                        .policy("Be polite.")
                        .build();
        AgentConfig config =
                agent("guarded").guardrails(List.of(judge)).taskGuardrails(List.of("pii")).build();

        List<WorkflowTask> llms = llmTasks(compiler.compile(config));

        assertThat(llms)
                .filteredOn(llm -> llm.getTaskReferenceName().equals("guarded_llm"))
                .singleElement()
                .satisfies(
                        llm ->
                                assertThat(llm.getInputParameters().get(TaskGuardrails.INPUT))
                                        .isEqualTo(List.of("pii")));
        assertThat(llms)
                .filteredOn(llm -> !llm.getTaskReferenceName().equals("guarded_llm"))
                .isNotEmpty()
                .allSatisfy(
                        llm ->
                                assertThat(llm.getInputParameters().get(TaskGuardrails.INPUT))
                                        .isEqualTo(List.of()));
    }

    @Test
    void subAgentsInheritTheParentsBindingsAndKeepTheirOwn() {
        AgentConfig writer = agent("writer").taskGuardrails(List.of("secrets", REDACT_PII)).build();
        AgentConfig reviewer = agent("reviewer").build();
        AgentConfig pipeline =
                agent("pipeline")
                        .strategy(AgentConfig.Strategy.SEQUENTIAL)
                        .agents(List.of(writer, reviewer))
                        .taskGuardrails(List.of("pii", "toxicity"))
                        .build();

        List<WorkflowTask> llms = llmTasks(compiler.compile(pipeline));

        assertThat(llms)
                .filteredOn(llm -> llm.getTaskReferenceName().startsWith("writer"))
                .isNotEmpty()
                .allSatisfy(
                        llm ->
                                assertThat(llm.getInputParameters().get(TaskGuardrails.INPUT))
                                        .isEqualTo(List.of("secrets", REDACT_PII, "toxicity")));
        assertThat(llms)
                .filteredOn(llm -> llm.getTaskReferenceName().startsWith("reviewer"))
                .isNotEmpty()
                .allSatisfy(
                        llm ->
                                assertThat(llm.getInputParameters().get(TaskGuardrails.INPUT))
                                        .isEqualTo(List.of("pii", "toxicity")));
    }

    private static AgentCompiler enforcingCompiler() {
        AgentCompiler compiler = new AgentCompiler();
        compiler.setTaskGuardrailSupport(new TaskGuardrailSupport() {});
        return compiler;
    }

    private static AgentConfig.AgentConfigBuilder agent(String name) {
        return AgentConfig.builder().name(name).model("openai/gpt-4o").instructions("Help.");
    }

    /** Every LLM task in the workflow, including those in inline sub-workflow definitions. */
    private static List<WorkflowTask> llmTasks(WorkflowDef wf) {
        List<WorkflowTask> found = new ArrayList<>();
        for (WorkflowTask top : wf.getTasks()) {
            for (WorkflowTask task : top.collectTasks()) {
                if (task.getSubWorkflowParam() != null
                        && task.getSubWorkflowParam().getWorkflowDefinition()
                                instanceof WorkflowDef inline) {
                    found.addAll(llmTasks(inline));
                }
                if ("LLM_CHAT_COMPLETE".equals(task.getType())) {
                    found.add(task);
                }
            }
        }
        return found;
    }
}
