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
import org.conductoross.conductor.webhook.dao.memory.InMemoryMetadataDAO;
import org.conductoross.conductor.webhook.dao.memory.InMemoryQueueDAO;
import org.conductoross.conductor.webhook.dao.memory.InMemoryWebhookDAO;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.conductoross.conductor.webhook.verifier.WebhookVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.utils.IDGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.conductoross.conductor.webhook.WebhookWorkerProperties.WEBHOOK_QUEUE;

class IncomingWebhookServiceTest {

    // Deterministic ID so tests can reference the generated event ID by name.
    private static final String EVENT_ID = "test-event-id";

    private final IDGenerator idGenerator =
            new IDGenerator() {
                @Override
                public String generate() {
                    return EVENT_ID;
                }
            };

    private InMemoryWebhookDAO webhookDAO;
    private InMemoryQueueDAO queueDAO;

    private WebhookVerifier passingVerifier;
    private WebhookVerifier failingVerifier;
    private IncomingWebhookService service;

    @BeforeEach
    void setUp() {
        webhookDAO = new InMemoryWebhookDAO(new InMemoryMetadataDAO());
        queueDAO = new InMemoryQueueDAO();

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
        assertThatThrownBy(
                        () ->
                                service.handleWebhook(
                                        "hook-1", "{}", Map.of(), new HttpHeaders()))
                .isInstanceOf(NotFoundException.class);

        assertThat(queueDAO.getSize(WEBHOOK_QUEUE)).isZero();
    }

    @Test
    void handleWebhook_noVerifierRegistered_throwsNonTransientException() {
        WebhookConfig config = configWith(WebhookConfig.Verifier.STRIPE);
        webhookDAO.createWebhook("hook-1", config);

        // Service was built with only HMAC_BASED verifier; STRIPE is unregistered.
        assertThatThrownBy(
                        () ->
                                service.handleWebhook(
                                        "hook-1", "{}", Map.of(), new HttpHeaders()))
                .isInstanceOf(NonTransientException.class)
                .hasMessageContaining("STRIPE");

        assertThat(queueDAO.getSize(WEBHOOK_QUEUE)).isZero();
    }

    @Test
    void handleWebhook_verificationFails_throwsNonTransientException() {
        service =
                new IncomingWebhookService(
                        webhookDAO, Set.of(failingVerifier), queueDAO, idGenerator);

        WebhookConfig config = configWith(WebhookConfig.Verifier.HMAC_BASED);
        webhookDAO.createWebhook("hook-1", config);

        assertThatThrownBy(
                        () ->
                                service.handleWebhook(
                                        "hook-1", "{}", Map.of(), new HttpHeaders()))
                .isInstanceOf(NonTransientException.class);

        assertThat(queueDAO.getSize(WEBHOOK_QUEUE)).isZero();
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
        webhookDAO.createWebhook("hook-1", config);
        // Pre-record the signature so the service sees it as already-seen (replay).
        webhookDAO.tryRecordSignature("hook-1", "sig-abc", Duration.ofMinutes(5));

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
        webhookDAO.createWebhook("hook-1", config);

        String result = service.handleWebhook("hook-1", "{}", Map.of(), new HttpHeaders());

        assertThat(result).isEqualTo("challenge-token");
        assertThat(queueDAO.getSize(WEBHOOK_QUEUE)).isZero();
    }

    @Test
    void handleWebhook_happyPath_storesEventAndPushesToQueue() {
        WebhookConfig config = configWith(WebhookConfig.Verifier.HMAC_BASED);
        config.setUrlVerified(true);
        webhookDAO.createWebhook("hook-1", config);

        service.handleWebhook("hook-1", "{\"k\":\"v\"}", Map.of(), new HttpHeaders());

        assertThat(webhookDAO.getWebhookEvent(EVENT_ID)).isNotNull();
        assertThat(queueDAO.contains(WEBHOOK_QUEUE, EVENT_ID)).isTrue();
    }

    @Test
    void handleWebhook_firstEvent_setsUrlVerifiedAndUpdatesDAO() {
        WebhookConfig config = configWith(WebhookConfig.Verifier.HMAC_BASED);
        config.setUrlVerified(false);
        webhookDAO.createWebhook("hook-1", config);

        service.handleWebhook("hook-1", "{}", Map.of(), new HttpHeaders());

        assertThat(webhookDAO.getWebhook("hook-1").isUrlVerified()).isTrue();
    }

    @Test
    void handleWebhook_alreadyVerified_doesNotUpdateDAO() {
        TrackingWebhookDAO tracking = new TrackingWebhookDAO();
        WebhookConfig config = configWith(WebhookConfig.Verifier.HMAC_BASED);
        config.setUrlVerified(true);
        tracking.createWebhook("hook-1", config);
        tracking.resetUpdateCount(); // don't count the setup call

        IncomingWebhookService svc =
                new IncomingWebhookService(tracking, Set.of(passingVerifier), queueDAO, idGenerator);
        svc.handleWebhook("hook-1", "{}", Map.of(), new HttpHeaders());

        assertThat(tracking.updateCount).isZero();
    }

    // --- handlePing ---

    @Test
    void handlePing_webhookNotFound_returnsNull() {
        String result = service.handlePing("hook-1", Map.of());

        assertThat(result).isNull();
        assertThat(queueDAO.getSize(WEBHOOK_QUEUE)).isZero();
    }

    @Test
    void handlePing_noVerifierRegistered_throwsNonTransientException() {
        WebhookConfig config = configWith(WebhookConfig.Verifier.STRIPE);
        webhookDAO.createWebhook("hook-1", config);

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
        webhookDAO.createWebhook("hook-1", config);

        String result = service.handlePing("hook-1", Map.of("challenge", "xyz"));

        assertThat(result).isEqualTo("pong");
        assertThat(webhookDAO.getWebhook("hook-1").isUrlVerified()).isTrue();
        assertThat(queueDAO.getSize(WEBHOOK_QUEUE)).isZero();
    }

    @Test
    void handlePing_webhookEvent_storesEventAndPushesToQueue() {
        WebhookConfig config = configWith(WebhookConfig.Verifier.HMAC_BASED);
        webhookDAO.createWebhook("hook-1", config);

        service.handlePing("hook-1", Map.of("action", "push"));

        assertThat(webhookDAO.getWebhookEvent(EVENT_ID)).isNotNull();
        assertThat(webhookDAO.getWebhookEvent(EVENT_ID).getRequestParams())
                .containsEntry("action", "push");
        assertThat(queueDAO.contains(WEBHOOK_QUEUE, EVENT_ID)).isTrue();
    }

    // --- helpers ---

    private static WebhookConfig configWith(WebhookConfig.Verifier verifier) {
        WebhookConfig c = new WebhookConfig();
        c.setId("hook-1");
        c.setVerifier(verifier);
        return c;
    }

    /** Counts post-setup {@code createWebhook} calls to detect unnecessary DAO writes. */
    private class TrackingWebhookDAO extends InMemoryWebhookDAO {

        int updateCount = 0;

        TrackingWebhookDAO() {
            super(new InMemoryMetadataDAO());
        }

        @Override
        public void createWebhook(String id, WebhookConfig config) {
            updateCount++;
            super.createWebhook(id, config);
        }

        void resetUpdateCount() {
            updateCount = 0;
        }
    }
}
