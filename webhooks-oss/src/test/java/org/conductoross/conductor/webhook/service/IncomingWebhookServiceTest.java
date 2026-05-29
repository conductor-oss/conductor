/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.conductoross.conductor.webhook.service;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.conductoross.conductor.common.utils.ErrorList;
import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.conductoross.conductor.webhook.verifier.WebhookVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.QueueDAO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.conductoross.conductor.webhook.WebhookWorkerProperties.WEBHOOK_QUEUE;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncomingWebhookServiceTest {

    @Mock private WebhookDAO webhookDAO;
    @Mock private QueueDAO queueDAO;
    @Mock private IDGenerator idGenerator;

    private WebhookVerifier passingVerifier;
    private WebhookVerifier failingVerifier;
    private IncomingWebhookService service;

    @BeforeEach
    void setUp() {
        lenient().when(idGenerator.generate()).thenReturn("event-id-1");

        passingVerifier =
                new WebhookVerifier() {
                    @Override
                    public ErrorList verify(WebhookConfig c, IncomingWebhookEvent e) {
                        return ErrorList.empty();
                    }

                    @Override
                    public String getType() {
                        return WebhookConfig.Verifier.HMAC_BASED.toString();
                    }
                };

        failingVerifier =
                new WebhookVerifier() {
                    @Override
                    public ErrorList verify(WebhookConfig c, IncomingWebhookEvent e) {
                        return ErrorList.singleton("bad signature");
                    }

                    @Override
                    public String getType() {
                        return WebhookConfig.Verifier.HMAC_BASED.toString();
                    }
                };

        service =
                new IncomingWebhookService(
                        webhookDAO, Set.of(passingVerifier), queueDAO, idGenerator);
    }

    // --- handleWebhook ---

    @Test
    void handleWebhook_webhookNotFound_throwsNotFoundException() {
        when(webhookDAO.getWebhook("hook-1")).thenReturn(null);

        assertThatThrownBy(
                        () ->
                                service.handleWebhook(
                                        "hook-1", "{}", Map.of(), new HttpHeaders()))
                .isInstanceOf(NotFoundException.class);

        verify(queueDAO, never()).push(anyString(), anyString(), anyInt());
    }

    @Test
    void handleWebhook_noVerifierRegistered_throwsNonTransientException() {
        WebhookConfig config = configWith(WebhookConfig.Verifier.STRIPE);
        when(webhookDAO.getWebhook("hook-1")).thenReturn(config);

        // Service was built with only HMAC_BASED verifier; STRIPE is unregistered.
        assertThatThrownBy(
                        () ->
                                service.handleWebhook(
                                        "hook-1", "{}", Map.of(), new HttpHeaders()))
                .isInstanceOf(NonTransientException.class)
                .hasMessageContaining("STRIPE");

        verify(queueDAO, never()).push(anyString(), anyString(), anyInt());
    }

    @Test
    void handleWebhook_verificationFails_throwsNonTransientException() {
        service =
                new IncomingWebhookService(
                        webhookDAO, Set.of(failingVerifier), queueDAO, idGenerator);

        WebhookConfig config = configWith(WebhookConfig.Verifier.HMAC_BASED);
        when(webhookDAO.getWebhook("hook-1")).thenReturn(config);

        assertThatThrownBy(
                        () ->
                                service.handleWebhook(
                                        "hook-1", "{}", Map.of(), new HttpHeaders()))
                .isInstanceOf(NonTransientException.class);

        verify(queueDAO, never()).push(anyString(), anyString(), anyInt());
    }

    @Test
    void handleWebhook_replayDetected_throwsNonTransientException() {
        WebhookVerifier replayableVerifier =
                new WebhookVerifier() {
                    @Override
                    public ErrorList verify(WebhookConfig c, IncomingWebhookEvent e) {
                        return ErrorList.empty();
                    }

                    @Override
                    public String getType() {
                        return WebhookConfig.Verifier.HMAC_BASED.toString();
                    }

                    @Override
                    public String dedupKey(WebhookConfig c, IncomingWebhookEvent e) {
                        return "sig-abc";
                    }
                };

        service =
                new IncomingWebhookService(
                        webhookDAO, Set.of(replayableVerifier), queueDAO, idGenerator);

        WebhookConfig config = configWith(WebhookConfig.Verifier.HMAC_BASED);
        when(webhookDAO.getWebhook("hook-1")).thenReturn(config);
        // First call: already seen — replay.
        when(webhookDAO.tryRecordSignature(eq("hook-1"), eq("sig-abc"), any(Duration.class)))
                .thenReturn(false);

        assertThatThrownBy(
                        () ->
                                service.handleWebhook(
                                        "hook-1", "{}", Map.of(), new HttpHeaders()))
                .isInstanceOf(NonTransientException.class)
                .hasMessageContaining("Replay");
    }

    @Test
    void handleWebhook_challengePath_returnsChallengeWithoutQueuing() {
        WebhookVerifier challengeVerifier =
                new WebhookVerifier() {
                    @Override
                    public ErrorList verify(WebhookConfig c, IncomingWebhookEvent e) {
                        return ErrorList.empty();
                    }

                    @Override
                    public String getType() {
                        return WebhookConfig.Verifier.HMAC_BASED.toString();
                    }

                    @Override
                    public String extractChallenge(IncomingWebhookEvent e, WebhookConfig c) {
                        return "challenge-token";
                    }
                };

        service =
                new IncomingWebhookService(
                        webhookDAO, Set.of(challengeVerifier), queueDAO, idGenerator);

        WebhookConfig config = configWith(WebhookConfig.Verifier.HMAC_BASED);
        when(webhookDAO.getWebhook("hook-1")).thenReturn(config);

        String result = service.handleWebhook("hook-1", "{}", Map.of(), new HttpHeaders());

        assertThat(result).isEqualTo("challenge-token");
        verify(queueDAO, never()).push(anyString(), anyString(), anyInt());
    }

    @Test
    void handleWebhook_happyPath_storesEventAndPushesToQueue() {
        WebhookConfig config = configWith(WebhookConfig.Verifier.HMAC_BASED);
        config.setUrlVerified(true);
        when(webhookDAO.getWebhook("hook-1")).thenReturn(config);

        service.handleWebhook("hook-1", "{\"k\":\"v\"}", Map.of(), new HttpHeaders());

        verify(webhookDAO).createIncomingWebhookEvent(eq("event-id-1"), any());
        verify(queueDAO).push(WEBHOOK_QUEUE, "event-id-1", 0);
    }

    @Test
    void handleWebhook_firstEvent_setsUrlVerifiedAndUpdatesDAO() {
        WebhookConfig config = configWith(WebhookConfig.Verifier.HMAC_BASED);
        config.setUrlVerified(false);
        when(webhookDAO.getWebhook("hook-1")).thenReturn(config);

        service.handleWebhook("hook-1", "{}", Map.of(), new HttpHeaders());

        assertThat(config.isUrlVerified()).isTrue();
        verify(webhookDAO).createWebhook(eq("hook-1"), same(config));
    }

    @Test
    void handleWebhook_alreadyVerified_doesNotUpdateDAO() {
        WebhookConfig config = configWith(WebhookConfig.Verifier.HMAC_BASED);
        config.setUrlVerified(true);
        when(webhookDAO.getWebhook("hook-1")).thenReturn(config);

        service.handleWebhook("hook-1", "{}", Map.of(), new HttpHeaders());

        // createWebhook should NOT be called just to re-persist urlVerified=true.
        verify(webhookDAO, never()).createWebhook(anyString(), any());
    }

    // --- handlePing ---

    @Test
    void handlePing_webhookNotFound_returnsNull() {
        when(webhookDAO.getWebhook("hook-1")).thenReturn(null);

        String result = service.handlePing("hook-1", Map.of());

        assertThat(result).isNull();
        verify(queueDAO, never()).push(anyString(), anyString(), anyInt());
    }

    @Test
    void handlePing_noVerifierRegistered_throwsNonTransientException() {
        WebhookConfig config = configWith(WebhookConfig.Verifier.STRIPE);
        when(webhookDAO.getWebhook("hook-1")).thenReturn(config);

        assertThatThrownBy(() -> service.handlePing("hook-1", Map.of()))
                .isInstanceOf(NonTransientException.class)
                .hasMessageContaining("STRIPE");
    }

    @Test
    void handlePing_pingResponse_setsUrlVerifiedAndReturnsResponse() {
        WebhookVerifier pingVerifier =
                new WebhookVerifier() {
                    @Override
                    public ErrorList verify(WebhookConfig c, IncomingWebhookEvent e) {
                        return ErrorList.empty();
                    }

                    @Override
                    public String getType() {
                        return WebhookConfig.Verifier.HMAC_BASED.toString();
                    }

                    @Override
                    public String handlePing(WebhookConfig c, Map<String, Object> params) {
                        return "pong";
                    }
                };

        service =
                new IncomingWebhookService(
                        webhookDAO, Set.of(pingVerifier), queueDAO, idGenerator);

        WebhookConfig config = configWith(WebhookConfig.Verifier.HMAC_BASED);
        config.setUrlVerified(false);
        when(webhookDAO.getWebhook("hook-1")).thenReturn(config);

        String result = service.handlePing("hook-1", Map.of("challenge", "xyz"));

        assertThat(result).isEqualTo("pong");
        assertThat(config.isUrlVerified()).isTrue();
        verify(webhookDAO).createWebhook("hook-1", config);
        verify(queueDAO, never()).push(anyString(), anyString(), anyInt());
    }

    @Test
    void handlePing_webhookEvent_storesEventAndPushesToQueue() {
        // handlePing with verifier.handlePing() returning null = webhook event, not a ping.
        WebhookConfig config = configWith(WebhookConfig.Verifier.HMAC_BASED);
        when(webhookDAO.getWebhook("hook-1")).thenReturn(config);

        service.handlePing("hook-1", Map.of("action", "push"));

        ArgumentCaptor<IncomingWebhookEvent> eventCaptor =
                ArgumentCaptor.forClass(IncomingWebhookEvent.class);
        verify(webhookDAO).createIncomingWebhookEvent(eq("event-id-1"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getRequestParams()).containsEntry("action", "push");
        verify(queueDAO).push(WEBHOOK_QUEUE, "event-id-1", 0);
    }

    // --- helpers ---

    private static WebhookConfig configWith(WebhookConfig.Verifier verifier) {
        WebhookConfig c = new WebhookConfig();
        c.setId("hook-1");
        c.setVerifier(verifier);
        return c;
    }
}
