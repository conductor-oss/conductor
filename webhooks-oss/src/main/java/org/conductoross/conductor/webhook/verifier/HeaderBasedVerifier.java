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
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class HeaderBasedVerifier implements WebhookVerifier {
    @Override
    public ErrorList verify(
            WebhookConfig webhookConfig, IncomingWebhookEvent incomingWebhookEvent) {
        var errors = new ErrorList();

        for (Map.Entry<String, String> entry : webhookConfig.getHeaders().entrySet()) {
            if (incomingWebhookEvent.getHeaders() == null) {
                errors.add(
                        "Header "
                                + entry.getKey()
                                + " is not present in the incoming webhook event");

                continue;
            }

            var headerValues = incomingWebhookEvent.getHeaders().get(entry.getKey());

            if (headerValues == null || headerValues.isEmpty()) {
                errors.add(
                        "Header "
                                + entry.getKey()
                                + " is not present in the incoming webhook event");

                continue;
            }

            if (!entry.getValue().equals(headerValues.getFirst())) {
                errors.add(
                        "Header "
                                + entry.getKey()
                                + " is not equal to the value configured in webhook");
            }

            if (headerValues.size() > 1) {
                errors.add("Multiple values are present in the header " + entry.getKey());
            }
        }

        return errors;
    }

    @Override
    public String getType() {
        return WebhookConfig.Verifier.HEADER_BASED.toString();
    }
}
