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
import java.util.Map;

import org.conductoross.conductor.common.webhook.WebhookConfig;
import org.springframework.stereotype.Component;

/**
 * {@link WebhookVerifier} that accepts all inbound requests without checking any headers or
 * signatures.
 *
 * <p>Use {@code "verifier": "NONE"} in a {@link WebhookConfig} for development, testing, or demos
 * where credential setup is not needed. This verifier must not be used in production deployments
 * that receive untrusted external traffic.
 *
 * <p>Registered as a Spring bean; auto-discovered by component scan. Enterprise deployments can
 * disable this verifier type at the validation layer by overriding {@link
 * WebhooksConfigResource#validateWebhookConfig} via a subclass bean.
 */
@Component
public class NoOpVerifier implements WebhookVerifier {

    @Override
    public List<String> verify(WebhookConfig config, IncomingWebhookEvent event) {
        return List.of(); // always passes
    }

    @Override
    public String getType() {
        return WebhookConfig.Verifier.NONE.toString();
    }

    /** No challenge/response needed — always returns null. */
    @Override
    public String extractChallenge(IncomingWebhookEvent event, WebhookConfig config) {
        return null;
    }

    /** No ping handling needed — always returns null (treat as real event). */
    @Override
    public String handlePing(WebhookConfig config, Map<String, Object> requestParams) {
        return null;
    }
}
