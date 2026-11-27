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
package org.conductoross.conductor.ai.agentspan.runtime.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenamedPropertiesCheckTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(RenamedPropertiesCheck.class);

    @Test
    void inAContext_withAiIntegrations_aDeprecatedPropertyStopsStartup() {
        runner.withPropertyValues(
                        "conductor.integrations.ai.enabled=true", "agentspan.embedded=true")
                .run(
                        ctx ->
                                assertThat(ctx)
                                        .hasFailed()
                                        .getFailure()
                                        .rootCause()
                                        .hasMessageContaining(
                                                "agentspan.embedded -> conductor.integrations.ai.enabled"));
    }

    @Test
    void inAContext_withoutAiIntegrations_theCheckDoesNotRun() {
        runner.withPropertyValues("agentspan.embedded=true")
                .run(
                        ctx -> {
                            assertThat(ctx).hasNotFailed();
                            assertThat(ctx).doesNotHaveBean(RenamedPropertiesCheck.class);
                        });
    }

    @Test
    void noDeprecatedProperty_starts() {
        assertThatCode(
                        () ->
                                new RenamedPropertiesCheck(
                                        new MockEnvironment()
                                                .withProperty(
                                                        "conductor.ai.default-context-window",
                                                        "10000")))
                .doesNotThrowAnyException();
    }

    @Test
    void agentspanEmbedded_failsStartup_namingTheAiFlag() {
        assertThatThrownBy(
                        () ->
                                new RenamedPropertiesCheck(
                                        new MockEnvironment()
                                                .withProperty("agentspan.embedded", "true")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agentspan.embedded -> conductor.integrations.ai.enabled");
    }

    @Test
    void renamedProperty_failsStartup_namingTheNewName() {
        assertThatThrownBy(
                        () ->
                                new RenamedPropertiesCheck(
                                        new MockEnvironment()
                                                .withProperty("agentspan.skills.enabled", "true")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agentspan.skills.enabled -> conductor.ai.skills.enabled");
    }

    @Test
    void perModelContextWindow_failsStartup() {
        assertThatThrownBy(
                        () ->
                                new RenamedPropertiesCheck(
                                        new MockEnvironment()
                                                .withProperty(
                                                        "agentspan.model-context-windows.gpt-4.1",
                                                        "1000")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "agentspan.model-context-windows -> conductor.ai.model-context-windows");
    }

    @Test
    void otherAgentspanProperty_isLeftAlone() {
        // Hosts may define their own agentspan.* keys, e.g. Orkes' agentspan.default-user-id.
        assertThatCode(
                        () ->
                                new RenamedPropertiesCheck(
                                        new MockEnvironment()
                                                .withProperty(
                                                        "agentspan.default-user-id", "system")))
                .doesNotThrowAnyException();
    }
}
