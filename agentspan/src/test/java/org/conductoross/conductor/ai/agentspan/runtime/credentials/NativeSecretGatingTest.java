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
package org.conductoross.conductor.ai.agentspan.runtime.credentials;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.netflix.conductor.core.secrets.NoopSecretsDAO;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses Conductor's concrete no-op secret store to verify the embedded-mode bean contract. */
class NativeSecretGatingTest {

    @Configuration
    @Import(CredentialResolutionService.class)
    static class NativeBeans {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withPropertyValues("conductor.secrets.type=noop")
                    .withBean(NoopSecretsDAO.class, NoopSecretsDAO::new)
                    .withUserConfiguration(NativeBeans.class);

    @Test
    void nativeResolutionIsAbsentWhenEmbeddedModeIsNotEnabled() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(CredentialResolutionService.class));
    }

    @Test
    void nativeResolutionUsesTheConcreteSecretBackendWhenEmbeddedModeIsEnabled() {
        runner.withPropertyValues("agentspan.embedded=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(CredentialResolutionService.class));
    }
}
