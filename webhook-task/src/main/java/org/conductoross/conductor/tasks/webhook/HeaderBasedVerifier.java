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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.webhook.WebhookConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link WebhookVerifier} that checks for the presence and exact value of one or more HTTP headers.
 *
 * <p>The expected headers are configured on the {@link WebhookConfig} via {@link
 * WebhookConfig#getHeaders()}. Every configured header must appear in the inbound request with an
 * exact value match (case-sensitive). If a header is present multiple times, only the first value
 * is compared; having more than one value for a header is treated as an error.
 *
 * <p>Ported from Orkes Enterprise; no org-specific concerns.
 */
@Component
public class HeaderBasedVerifier implements WebhookVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeaderBasedVerifier.class);

    @Override
    public List<String> verify(WebhookConfig config, IncomingWebhookEvent event) {
        List<String> errors = new ArrayList<>();

        Map<String, String> expectedHeaders = config.getHeaders();
        if (expectedHeaders == null || expectedHeaders.isEmpty()) {
            errors.add("No headers configured on webhook — cannot verify HEADER_BASED webhook");
            return errors;
        }

        for (Map.Entry<String, String> entry : expectedHeaders.entrySet()) {
            String headerName = entry.getKey();
            String expectedValue = entry.getValue();

            if (event.getHeaders() == null) {
                errors.add(
                        "Header " + headerName + " is not present in the incoming webhook event");
                continue;
            }

            List<String> headerValues = event.getHeaders().get(headerName);

            if (headerValues == null || headerValues.isEmpty()) {
                errors.add(
                        "Header " + headerName + " is not present in the incoming webhook event");
                continue;
            }

            if (!expectedValue.equals(headerValues.get(0))) {
                errors.add(
                        "Header "
                                + headerName
                                + " value does not match the value configured in the webhook");
            }

            if (headerValues.size() > 1) {
                errors.add("Multiple values are present for header " + headerName);
            }
        }

        return errors;
    }

    @Override
    public String getType() {
        return WebhookConfig.Verifier.HEADER_BASED.toString();
    }
}
