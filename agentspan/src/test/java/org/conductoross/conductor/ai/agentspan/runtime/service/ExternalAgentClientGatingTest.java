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

import org.conductoross.conductor.ai.a2a.A2AService;
import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.netflix.conductor.core.secrets.NoopSecretsDAO;

import okhttp3.OkHttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The external agent clients register together with {@link CredentialResolutionService}, which they
 * both require, and neither needs credentials to be configured — a deployment using only Bedrock,
 * only Azure Foundry, both, or neither picks a runtime per workflow via {@code agentType}.
 *
 * <p>Guards the wiring rather than the runtimes: before this was gated, the clients registered
 * while the credential service did not, so a host that component-scanned this package failed to
 * start.
 */
class ExternalAgentClientGatingTest {

    @Configuration
    @Import({
        BedrockAgentClient.class,
        AzureFoundryAgentClient.class,
        OpenAiAssistantsAgentClient.class,
        CredentialResolutionService.class
    })
    static class ExternalAgentBeans {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withPropertyValues("conductor.secrets.type=noop")
                    .withBean(NoopSecretsDAO.class, NoopSecretsDAO::new)
                    .withBean("conductorAiHttpClient", OkHttpClient.class, OkHttpClient::new)
                    .withUserConfiguration(ExternalAgentBeans.class);

    @Test
    void clientsAreAbsentWhenAiIntegrationIsDisabled() {
        runner.run(
                ctx -> {
                    assertThat(ctx).doesNotHaveBean(BedrockAgentClient.class);
                    assertThat(ctx).doesNotHaveBean(AzureFoundryAgentClient.class);
                    assertThat(ctx).doesNotHaveBean(OpenAiAssistantsAgentClient.class);
                    assertThat(ctx).doesNotHaveBean(CredentialResolutionService.class);
                });
    }

    @Test
    void clientsRegisterWithTheirCredentialServiceWhenAiIntegrationIsEnabled() {
        runner.withPropertyValues("conductor.integrations.ai.enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(BedrockAgentClient.class);
                            assertThat(ctx).hasSingleBean(AzureFoundryAgentClient.class);
                            assertThat(ctx).hasSingleBean(OpenAiAssistantsAgentClient.class);
                            assertThat(ctx).hasSingleBean(CredentialResolutionService.class);
                        });
    }

    /** No credentials are configured here, so construction must not depend on them. */
    @Test
    void clientsReportDistinctAgentTypesWithoutAnyCredentials() {
        runner.withPropertyValues("conductor.integrations.ai.enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx.getBean(BedrockAgentClient.class).agentType())
                                    .isEqualTo(A2AService.AGENT_TYPE_BEDROCK);
                            assertThat(ctx.getBean(AzureFoundryAgentClient.class).agentType())
                                    .isEqualTo(A2AService.AGENT_TYPE_AZURE_FOUNDRY);
                            assertThat(ctx.getBean(OpenAiAssistantsAgentClient.class).agentType())
                                    .isEqualTo(A2AService.AGENT_TYPE_OPENAI_ASSISTANTS);
                        });
    }

    /** A second OkHttpClient bean must not make the Azure Foundry injection ambiguous. */
    @Test
    void azureFoundryResolvesItsHttpClientAlongsideAnotherOkHttpClientBean() {
        runner.withPropertyValues("conductor.integrations.ai.enabled=true")
                .withBean("someOtherHttpClient", OkHttpClient.class, OkHttpClient::new)
                .run(ctx -> assertThat(ctx).hasSingleBean(AzureFoundryAgentClient.class));
    }
}
