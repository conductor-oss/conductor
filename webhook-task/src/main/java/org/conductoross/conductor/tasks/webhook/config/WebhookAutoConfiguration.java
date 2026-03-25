/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.tasks.webhook.config;

import java.util.List;

import org.conductoross.conductor.tasks.webhook.IncomingWebhookResource;
import org.conductoross.conductor.tasks.webhook.IncomingWebhookService;
import org.conductoross.conductor.tasks.webhook.WebhookConfigDAO;
import org.conductoross.conductor.tasks.webhook.WebhookConfigService;
import org.conductoross.conductor.tasks.webhook.WebhookHashingService;
import org.conductoross.conductor.tasks.webhook.WebhookOrgContextProvider;
import org.conductoross.conductor.tasks.webhook.WebhookTaskDAO;
import org.conductoross.conductor.tasks.webhook.WebhookVerifier;
import org.conductoross.conductor.tasks.webhook.WebhooksConfigResource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.MetadataDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring auto-configuration for the {@code webhook-task} module.
 *
 * <p>The in-memory DAO beans ({@code InMemoryWebhookTaskDAO}, {@code InMemoryWebhookConfigDAO}),
 * the system task ({@code WaitForWebhookTask}), and the verifier ({@code HeaderBasedVerifier})
 * carry {@code @Component} annotations and are discovered via the server's component scan of {@code
 * org.conductoross.conductor}. This class wires the service and REST layer beans, which carry no
 * {@code @Component} annotation of their own.
 *
 * <p>Enterprise deployments replace DAO beans or inject alternative verifiers/providers as Spring
 * beans — the {@code @ConditionalOnMissingBean} guards ensure the defaults are skipped.
 */
@AutoConfiguration
public class WebhookAutoConfiguration {

    /**
     * No-op {@link WebhookOrgContextProvider} for single-tenant OSS deployments. Orkes Enterprise
     * registers its own implementation that extracts orgId from the webhook ID and sets {@code
     * OrkesRequestContext}.
     */
    @Bean
    @ConditionalOnMissingBean
    public WebhookOrgContextProvider noOpWebhookOrgContextProvider() {
        return webhookId -> {}; // single-tenant: no org context needed
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({WebhookConfigDAO.class, MetadataDAO.class})
    public WebhookConfigService webhookConfigService(
            WebhookConfigDAO webhookConfigDAO, MetadataDAO metadataDAO) {
        return new WebhookConfigService(webhookConfigDAO, metadataDAO);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(WebhookConfigService.class)
    public WebhooksConfigResource webhooksConfigResource(
            WebhookConfigService webhookConfigService) {
        return new WebhooksConfigResource(webhookConfigService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
        WebhookConfigDAO.class,
        WebhookTaskDAO.class,
        WebhookHashingService.class,
        ExecutionDAOFacade.class,
        WorkflowExecutor.class
    })
    public IncomingWebhookService incomingWebhookService(
            WebhookConfigDAO webhookConfigDAO,
            WebhookTaskDAO webhookTaskDAO,
            WebhookHashingService webhookHashingService,
            List<WebhookVerifier> verifiers,
            ExecutionDAOFacade executionDAOFacade,
            WorkflowExecutor workflowExecutor,
            ObjectMapper objectMapper) {
        return new IncomingWebhookService(
                webhookConfigDAO,
                webhookTaskDAO,
                webhookHashingService,
                verifiers,
                executionDAOFacade,
                workflowExecutor,
                objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({IncomingWebhookService.class, WebhookOrgContextProvider.class})
    public IncomingWebhookResource incomingWebhookResource(
            IncomingWebhookService incomingWebhookService,
            WebhookOrgContextProvider orgContextProvider) {
        return new IncomingWebhookResource(incomingWebhookService, orgContextProvider);
    }
}
