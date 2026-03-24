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

import org.conductoross.conductor.tasks.webhook.WebhookConfigDAO;
import org.conductoross.conductor.tasks.webhook.WebhookConfigService;
import org.conductoross.conductor.tasks.webhook.WebhooksConfigResource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring auto-configuration for the {@code webhook-task} module.
 *
 * <p>The in-memory DAO beans ({@code InMemoryWebhookTaskDAO}, {@code InMemoryWebhookConfigDAO}) and
 * the system task ({@code WaitForWebhookTask}) carry {@code @Component} annotations and are
 * discovered via the server's component scan of {@code org.conductoross.conductor}. This class only
 * wires the service and REST layer, which have no {@code @Component} annotation of their own.
 *
 * <p>Enterprise deployments replace the DAO beans with durable implementations (Postgres, Redis) as
 * Spring beans — the {@code @ConditionalOnMissingBean} guards on the in-memory implementations
 * ensure they are skipped automatically.
 */
@AutoConfiguration
public class WebhookAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(WebhookConfigDAO.class)
    public WebhookConfigService webhookConfigService(WebhookConfigDAO webhookConfigDAO) {
        return new WebhookConfigService(webhookConfigDAO);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(WebhookConfigService.class)
    public WebhooksConfigResource webhooksConfigResource(
            WebhookConfigService webhookConfigService) {
        return new WebhooksConfigResource(webhookConfigService);
    }
}
