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
package org.conductoross.conductor.ai.agentspan.runtime.credentials;

import org.conductoross.conductor.dao.SecretsDAO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the native secret mechanism toggles on {@code agentspan.embedded}: {@link
 * CredentialResolutionService} is present in standalone mode (flag absent or {@code false}) and
 * gated OFF when embedded ({@code agentspan.embedded=true}), where the host delivers secrets
 * instead.
 */
class NativeSecretGatingTest {

    /**
     * Registers the gated native bean (its class-level {@code @ConditionalOnProperty} is evaluated
     * on import) and supplies a mock collaborator so it can be constructed when the condition
     * allows.
     */
    @Configuration
    @Import({CredentialResolutionService.class})
    static class NativeBeans {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(SecretsDAO.class, () -> mock(SecretsDAO.class))
                    .withUserConfiguration(NativeBeans.class);

    @Test
    void nativeBeans_present_whenFlagAbsent() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(CredentialResolutionService.class));
    }

    @Test
    void nativeBeans_present_whenStandalone() {
        runner.withPropertyValues("agentspan.embedded=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(CredentialResolutionService.class));
    }

    @Test
    void nativeBeans_dormant_whenEmbedded() {
        runner.withPropertyValues("agentspan.embedded=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(CredentialResolutionService.class));
    }
}
