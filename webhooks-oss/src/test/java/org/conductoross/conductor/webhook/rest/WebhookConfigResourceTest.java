/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.conductoross.conductor.webhook.rest;

import java.util.Map;

import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.conductoross.conductor.webhook.service.WebhookConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookConfigResourceTest {

    @Mock private WebhookConfigService service;
    private WebhookConfigResource resource;

    @BeforeEach
    void setUp() {
        resource = new WebhookConfigResource(service);
    }

    @Test
    void createWebhook_missingAllTargets_throws() {
        WebhookConfig config = new WebhookConfig();
        config.setVerifier(WebhookConfig.Verifier.HMAC_BASED);

        assertThatThrownBy(() -> resource.createWebhook(config))
                .isInstanceOf(NonTransientException.class)
                .hasMessageContaining("expression");
        verify(service, never()).createWebhook(config);
    }

    @Test
    void createWebhook_headerBasedWithoutHeaders_throws() {
        WebhookConfig config = new WebhookConfig();
        config.setVerifier(WebhookConfig.Verifier.HEADER_BASED);
        config.setWorkflowsToStart(Map.of("wf-a", 1));

        assertThatThrownBy(() -> resource.createWebhook(config))
                .isInstanceOf(NonTransientException.class)
                .hasMessageContaining("header");
    }

    @Test
    void createWebhook_valid_delegates() {
        WebhookConfig config = new WebhookConfig();
        config.setVerifier(WebhookConfig.Verifier.HMAC_BASED);
        config.setWorkflowsToStart(Map.of("wf-a", 1));

        WebhookConfig result = resource.createWebhook(config);

        verify(service).createWebhook(config);
        assertThat(result).isSameAs(config);
    }

    @Test
    void updateWebhook_setsIdFromPath() {
        WebhookConfig config = new WebhookConfig();
        config.setVerifier(WebhookConfig.Verifier.HMAC_BASED);
        config.setWorkflowsToStart(Map.of("wf-a", 1));

        resource.updateWebhook("path-id", config);

        assertThat(config.getId()).isEqualTo("path-id");
        verify(service).updateWebhook(config);
    }

    @Test
    void deleteWebhook_missing_throwsNotFound() {
        when(service.getWebhook("missing")).thenReturn(null);

        assertThatThrownBy(() -> resource.deleteWebhook("missing"))
                .isInstanceOf(NotFoundException.class);
        verify(service, never()).removeWebhook("missing");
    }

    @Test
    void deleteWebhook_present_delegates() {
        when(service.getWebhook("hook-1")).thenReturn(new WebhookConfig());

        resource.deleteWebhook("hook-1");

        verify(service).removeWebhook("hook-1");
    }

    @Test
    void getWebhook_missing_throwsNotFound() {
        when(service.getWebhook("missing")).thenReturn(null);

        assertThatThrownBy(() -> resource.getWebhook("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getWebhook_present_sanitizesSecret() {
        WebhookConfig config = new WebhookConfig();
        config.setSecretValue("real-secret");
        when(service.getWebhook("hook-1")).thenReturn(config);

        WebhookConfig result = resource.getWebhook("hook-1");

        assertThat(result.getSecretValue()).isEqualTo(WebhookConfigService.SECRET);
    }
}
