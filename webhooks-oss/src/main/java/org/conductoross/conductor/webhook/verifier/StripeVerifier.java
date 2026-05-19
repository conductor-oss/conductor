/*
 * Copyright 2022 Orkes, Inc.
 * <p>
 * Licensed under the Orkes Enterprise License (the "License"); you may not use this file except in compliance with
 * the License.
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.webhook.verifier;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.net.Webhook;
import org.conductoross.conductor.common.utils.ErrorList;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.stereotype.Component;
import com.stripe.model.Event;

import java.text.ParseException;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
public class StripeVerifier implements WebhookVerifier {

    private static final String STRIPE_SIGNATURE = "Stripe-Signature";

    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private final Date eventDeserializerDate;

    @SneakyThrows
    public StripeVerifier() {
        this.eventDeserializerDate = DateUtils.parseDate("2019-03-06", DATE_PATTERN);
    }

    @Override
    public ErrorList verify(WebhookConfig webhookConfig, IncomingWebhookEvent incomingWebhookEvent) {
        var header = webhookConfig.getSecretValue();
        var headerValues = incomingWebhookEvent.getHeaders().get(STRIPE_SIGNATURE);

        if (headerValues == null || headerValues.isEmpty()) {
            return ErrorList.singleton(header + " is not present in the header");
        } else if (headerValues.size() > 1) {
            return ErrorList.singleton("Multiple " + STRIPE_SIGNATURE + " is present in the header");
        }

        var requestSignature = headerValues.getFirst();

        Event event;

        try {
            event = Webhook.constructEvent(incomingWebhookEvent.getBody(), requestSignature, header);
        } catch (SignatureVerificationException e) {
            return ErrorList.singleton("Failed to verify Stripe signature: " + e.getMessage());
        }

        Date apiDate;

        try {
            String rawApiVersion = event.getApiVersion(); // e.g., "2025-07-30.basil"
            String datePart = rawApiVersion.split("[^0-9-]")[0];
            apiDate = DateUtils.parseDate(datePart, DATE_PATTERN);
        } catch (ParseException e) {
            return ErrorList.singleton("Failed to parse date from: " + event.getApiVersion() + ". Expected format: " + DATE_PATTERN);
        }

        if (apiDate.before(eventDeserializerDate)) {
            // This is old api which does not have deserializer support.
            return ErrorList.empty();
        }

        // If the event is created it means signature verification is successful.
        return ErrorList.empty();
    }

    @Override
    public String getType() {
        return WebhookConfig.Verifier.STRIPE.toString();
    }
}
