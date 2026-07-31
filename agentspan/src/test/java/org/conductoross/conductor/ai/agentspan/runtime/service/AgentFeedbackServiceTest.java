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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.netflix.conductor.common.metadata.workflow.WorkflowClassifier;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.model.WorkflowModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentFeedbackServiceTest {

    private final AgentFeedbackService service = new AgentFeedbackService(null);

    @Test
    void eligibleExecutionIsDisabledUntilOcgTurnFeedbackContractExists() {
        AgentFeedbackState state = service.state(workflow());

        assertThat(state.enabled()).isFalse();
        assertThat(state.reason()).isEqualTo(AgentFeedbackService.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void rejectsChildNonTerminalNonAgentAndMissingMemoryExecutions() {
        WorkflowModel child = workflow();
        child.setParentWorkflowId("parent");
        assertThat(service.state(child).reason()).isEqualTo("CHILD_EXECUTION");

        WorkflowModel running = workflow();
        running.setStatus(WorkflowModel.Status.RUNNING);
        assertThat(service.state(running).reason()).isEqualTo("EXECUTION_NOT_TERMINAL");

        WorkflowModel ordinary = workflow();
        ordinary.getWorkflowDefinition().setMetadata(Map.of());
        assertThat(service.state(ordinary).reason()).isEqualTo("NOT_AGENT_EXECUTION");

        WorkflowModel withoutMemory = workflow();
        withoutMemory
                .getWorkflowDefinition()
                .setMetadata(Map.of("classifier", WorkflowClassifier.AGENT));
        assertThat(service.state(withoutMemory).reason()).isEqualTo("OCG_MEMORY_NOT_CONFIGURED");
    }

    @Test
    void invalidRatingReturnsStableClientErrorBeforeAnyUpstreamCall() {
        assertThatThrownBy(() -> service.set("execution", "useful"))
                .isInstanceOfSatisfying(
                        AgentFeedbackException.class,
                        error -> {
                            assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(error.getCode()).isEqualTo("INVALID_FEEDBACK_RATING");
                        });
    }

    private static WorkflowModel workflow() {
        Map<String, Object> memory =
                Map.of(
                        "ocgUrl", "https://ocg.example",
                        "credential", "OCG_KEY",
                        "agent", "agentspan");
        Map<String, Object> agentDefinition = new LinkedHashMap<>();
        agentDefinition.put("longTermMemory", memory);
        WorkflowDef definition = new WorkflowDef();
        definition.setMetadata(
                Map.of("classifier", WorkflowClassifier.AGENT, "agentDef", agentDefinition));

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("turn");
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.setWorkflowDefinition(definition);
        return workflow;
    }
}
