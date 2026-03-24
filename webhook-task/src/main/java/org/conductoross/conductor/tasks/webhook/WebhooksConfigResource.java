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
package org.conductoross.conductor.tasks.webhook;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.core.exception.NotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST API for managing {@link WebhookConfig} objects.
 *
 * <p>Mounted at {@code /api/metadata/webhook}. No authentication or authorization is applied in OSS
 * Conductor — Orkes Enterprise adds security at this layer via its own REST adapter or Spring
 * Security configuration.
 */
@RestController
@RequestMapping(value = "/api/metadata/webhook", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Webhook Config", description = "Webhook configuration management API")
public class WebhooksConfigResource {

    private final WebhookConfigService webhookConfigService;

    public WebhooksConfigResource(WebhookConfigService webhookConfigService) {
        this.webhookConfigService = webhookConfigService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a new webhook configuration")
    public WebhookConfig createWebhook(@RequestBody WebhookConfig webhookConfig) {
        validateWebhookConfig(webhookConfig);
        webhookConfigService.createWebhook(webhookConfig);
        return webhookConfig;
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update an existing webhook configuration")
    public WebhookConfig updateWebhook(
            @PathVariable("id") String id, @RequestBody WebhookConfig webhookConfig) {
        webhookConfig.setId(id);
        validateWebhookConfig(webhookConfig);
        webhookConfigService.updateWebhook(webhookConfig);
        return webhookConfig;
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a webhook configuration")
    public void deleteWebhook(@PathVariable("id") String id) {
        webhookConfigService.deleteWebhook(id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a webhook configuration by id")
    public WebhookConfig getWebhook(@PathVariable("id") String id) {
        WebhookConfig config = webhookConfigService.getWebhook(id);
        if (config == null) {
            throw new NotFoundException("No webhook config with id: " + id);
        }
        return config;
    }

    @GetMapping
    @Operation(summary = "List all webhook configurations")
    public List<WebhookConfig> getAllWebhooks() {
        return webhookConfigService.getAllWebhooks();
    }

    private void validateWebhookConfig(WebhookConfig config) {
        if (config.getExpression() == null
                && config.getReceiverWorkflowNamesToVersions() == null
                && config.getWorkflowsToStart() == null) {
            throw new IllegalArgumentException(
                    "At least one of: expression, receiverWorkflowNamesToVersions, or"
                            + " workflowsToStart must be provided");
        }
        if (config.getVerifier() == WebhookConfig.Verifier.HEADER_BASED
                && CollectionUtils.isEmpty(config.getHeaders())) {
            throw new IllegalArgumentException(
                    "At least one header must be configured for HEADER_BASED verifier");
        }
    }
}
