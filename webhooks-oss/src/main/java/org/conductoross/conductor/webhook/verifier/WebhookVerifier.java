/*
 * Copyright 2022 Conductor Authors.
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
package org.conductoross.conductor.webhook.verifier;

import java.util.Map;

import org.conductoross.conductor.common.utils.ErrorList;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;

public interface WebhookVerifier {
    /**
     * @param webhookConfig
     * @param incomingWebhookEvent
     * @return an error message if the verification fails, empty if the verification is successful.
     */
    ErrorList verify(WebhookConfig webhookConfig, IncomingWebhookEvent incomingWebhookEvent);

    /**
     * @return Tyep of the verifier. e.g. SLACK, FACEBOOK etc
     */
    String getType();

    /**
     * @param incomingWebhookEvent
     * @param webhookConfig
     * @return Extracts the challenge from the webhook and returns the value. Null if not required.
     */
    default String extractChallenge(
            IncomingWebhookEvent incomingWebhookEvent, WebhookConfig webhookConfig) {
        return null;
    }

    default String handlePing(WebhookConfig webhookConfig, Map<String, Object> requestParams) {
        return null;
    }
}
