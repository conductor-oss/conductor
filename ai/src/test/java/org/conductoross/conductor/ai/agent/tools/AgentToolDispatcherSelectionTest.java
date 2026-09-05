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
package org.conductoross.conductor.ai.agent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.service.WorkflowService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Exactly one dispatcher, and the right one.
 *
 * <p>A2AWorkers resolves the dispatcher by type, so two of them is not a preference conflict - it
 * is a failure to find any, and an agent that quietly stops running its tools. Unit tests exercise
 * both implementations happily without ever asking whether the container would produce one.
 */
class AgentToolDispatcherSelectionTest {

    @Configuration
    @Import({InlineAgentToolDispatcher.class, SubWorkflowAgentToolDispatcher.class})
    static class Dispatchers {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(WorkflowService.class, () -> mock(WorkflowService.class))
                    .withBean(WorkflowExecutor.class, () -> mock(WorkflowExecutor.class))
                    .withBean(ExecutionDAOFacade.class, () -> mock(ExecutionDAOFacade.class))
                    .withUserConfiguration(Dispatchers.class);

    @Test
    void toolsRunInTheAgentsOwnWorkflowByDefault() {
        runner.withPropertyValues("conductor.integrations.ai.enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(AgentToolDispatcher.class);
                            assertThat(ctx).hasSingleBean(InlineAgentToolDispatcher.class);
                            assertThat(ctx).doesNotHaveBean(SubWorkflowAgentToolDispatcher.class);
                        });
    }

    @Test
    void thechildWorkflowDispatcherIsChosenByProperty() {
        runner.withPropertyValues(
                        "conductor.integrations.ai.enabled=true",
                        "conductor.integrations.ai.agent.tool-execution=subworkflow")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(AgentToolDispatcher.class);
                            assertThat(ctx).hasSingleBean(SubWorkflowAgentToolDispatcher.class);
                            assertThat(ctx).doesNotHaveBean(InlineAgentToolDispatcher.class);
                        });
    }

    @Test
    void neitherIsPresentWithoutTheAiIntegration() {
        // An SDK worker has no engine to schedule on; the delegate falls back to handing tool
        // calls to the workflow when no dispatcher exists.
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(AgentToolDispatcher.class));
    }
}
