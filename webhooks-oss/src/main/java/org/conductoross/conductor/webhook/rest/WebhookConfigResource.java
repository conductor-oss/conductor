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
package org.conductoross.conductor.webhook.rest;

import java.util.List;

import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.conductoross.conductor.webhook.service.WebhookConfigService;
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

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(
        value = "/api/metadata/webhook",
        produces = {MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@Slf4j
public class WebhookConfigResource {

    private final WebhookConfigService webhookConfigService;

    @PostMapping
    public WebhookConfig createWebhook(@RequestBody WebhookConfig webhookConfig) {
        validate(webhookConfig);
        webhookConfigService.createWebhook(webhookConfig);
        return webhookConfig;
    }

    @PutMapping("/{id}")
    public WebhookConfig updateWebhook(
            @PathVariable("id") String id, @RequestBody WebhookConfig webhookConfig) {
        webhookConfig.setId(id);
        validate(webhookConfig);
        webhookConfigService.updateWebhook(webhookConfig);
        return webhookConfig;
    }

    @DeleteMapping("/{id}")
    public void deleteWebhook(@PathVariable("id") String id) {
        WebhookConfig existing = webhookConfigService.getWebhook(id);
        if (existing == null) {
            throw new NotFoundException("Webhook with id " + id + " does not exist");
        }
        webhookConfigService.removeWebhook(id);
    }

    @GetMapping("/{id}")
    public WebhookConfig getWebhook(@PathVariable("id") String id) {
        WebhookConfig webhookConfig = webhookConfigService.getWebhook(id);
        if (webhookConfig == null) {
            throw new NotFoundException("Webhook with id " + id + " does not exist");
        }
        return webhookConfig.toBuilder().secretValue(WebhookConfigService.SECRET).build();
    }

    @GetMapping
    public List<WebhookConfig> getAllWebhooks() {
        return webhookConfigService.getWebhooks();
    }

    private void validate(WebhookConfig webhookConfig) {
        if (webhookConfig.getExpression() == null
                && webhookConfig.getReceiverWorkflowNamesToVersions() == null
                && webhookConfig.getWorkflowsToStart() == null) {
            throw new NonTransientException(
                    "Either a script expression, receiver workflow or a workflow to start MUST be provided");
        }
        if (webhookConfig.getVerifier() == WebhookConfig.Verifier.HEADER_BASED
                && CollectionUtils.isEmpty(webhookConfig.getHeaders())) {
            throw new NonTransientException(
                    "At least one header must be present for header-based webhook verifier");
        }
    }
}
