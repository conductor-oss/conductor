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
package org.conductoross.conductor.webhook.service;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.service.webhook.TargetWorkflowCollector;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.utils.IDGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookConfigServiceTest {

    @Mock private WebhookDAO webhookDAO;
    @Mock private TargetWorkflowCollector targetWorkflowCollector;
    @Mock private IDGenerator idGenerator;

    private WebhookConfigService service;

    @BeforeEach
    void setUp() {
        service = new WebhookConfigService(webhookDAO, targetWorkflowCollector, idGenerator);
    }

    @Test
    void createWebhook_withoutId_generatesIdAndStores() {
        WebhookConfig config = new WebhookConfig();
        when(idGenerator.generate()).thenReturn("generated-id");

        service.createWebhook(config);

        assertThat(config.getId()).isEqualTo("generated-id");
        verify(webhookDAO).createWebhook("generated-id", config);
        verify(webhookDAO).createMatchers(any(WebhookConfig.class), any());
    }

    @Test
    void createWebhook_withExistingId_throwsConflict() {
        WebhookConfig config = new WebhookConfig();
        config.setId("existing");
        when(webhookDAO.getWebhook("existing")).thenReturn(new WebhookConfig());

        assertThatThrownBy(() -> service.createWebhook(config))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("existing");
        verify(idGenerator, never()).generate();
        verify(webhookDAO, never()).createWebhook(anyString(), any());
    }

    @Test
    void createWebhook_withFreshId_usesProvidedId() {
        WebhookConfig config = new WebhookConfig();
        config.setId("user-supplied");

        service.createWebhook(config);

        verify(idGenerator, never()).generate();
        verify(webhookDAO).createWebhook("user-supplied", config);
    }

    @Test
    void removeWebhook_removesMatchersThenConfig() {
        service.removeWebhook("hook-1");

        verify(webhookDAO).removeMatchers("hook-1");
        verify(webhookDAO).removeWebhook("hook-1");
    }

    @Test
    void getWebhooks_sanitizesSecretValues() {
        WebhookConfig c1 = new WebhookConfig();
        c1.setSecretValue("real-secret-1");
        WebhookConfig c2 = new WebhookConfig();
        c2.setSecretValue("real-secret-2");
        when(webhookDAO.getAllWebhooks()).thenReturn(List.of(c1, c2));

        List<WebhookConfig> result = service.getWebhooks();

        assertThat(result)
                .extracting(WebhookConfig::getSecretValue)
                .containsOnly(WebhookConfigService.SECRET);
        // Regression: stored configs must not be mutated by sanitization.
        // Found via smoke test against running server: in-memory DAO returns
        // shared references, so prior in-place setSecretValue("***") corrupted
        // the persisted secret and broke subsequent HMAC verification.
        assertThat(c1.getSecretValue()).isEqualTo("real-secret-1");
        assertThat(c2.getSecretValue()).isEqualTo("real-secret-2");
    }

    @Test
    void updateWebhook_redactedSecret_preservesExistingSecret() {
        WebhookConfig existing = new WebhookConfig();
        existing.setId("hook-1");
        existing.setSecretValue("real-secret");
        existing.setUrlVerified(true);
        when(webhookDAO.getWebhook("hook-1")).thenReturn(existing);

        WebhookConfig update = new WebhookConfig();
        update.setId("hook-1");
        update.setSecretValue(WebhookConfigService.SECRET);

        service.updateWebhook(update);

        ArgumentCaptor<WebhookConfig> captor = ArgumentCaptor.forClass(WebhookConfig.class);
        verify(webhookDAO).createWebhook(anyString(), captor.capture());
        assertThat(captor.getValue().getSecretValue()).isEqualTo("real-secret");
        assertThat(captor.getValue().isUrlVerified()).isTrue();
    }

    @Test
    void updateWebhook_realSecret_overwrites() {
        WebhookConfig existing = new WebhookConfig();
        existing.setId("hook-1");
        existing.setSecretValue("old-secret");
        when(webhookDAO.getWebhook("hook-1")).thenReturn(existing);

        WebhookConfig update = new WebhookConfig();
        update.setId("hook-1");
        update.setSecretValue("new-secret");

        service.updateWebhook(update);

        ArgumentCaptor<WebhookConfig> captor = ArgumentCaptor.forClass(WebhookConfig.class);
        verify(webhookDAO).createWebhook(anyString(), captor.capture());
        assertThat(captor.getValue().getSecretValue()).isEqualTo("new-secret");
    }

    @Test
    void updateWebhook_unknownId_throwsNotFound() {
        WebhookConfig update = new WebhookConfig();
        update.setId("missing");
        when(webhookDAO.getWebhook("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.updateWebhook(update))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("missing");
        verify(webhookDAO, never()).createWebhook(anyString(), any());
    }

    @Test
    void createWebhook_callsTargetWorkflowCollectorVisit() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        Map<String, Integer> workflows = Map.of("wf-a", 1);
        when(targetWorkflowCollector.getWorkflowsToCompleteWebhooks()).thenReturn(workflows);

        service.createWebhook(config);

        verify(targetWorkflowCollector).visit(config);
        verify(webhookDAO).createMatchers(config, workflows);
    }
}
