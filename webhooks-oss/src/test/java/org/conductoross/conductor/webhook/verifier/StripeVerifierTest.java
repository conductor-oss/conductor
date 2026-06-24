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
package org.conductoross.conductor.webhook.verifier;

import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

class StripeVerifierTest {

    private final StripeVerifier verifier = new StripeVerifier();

    @Test
    void verify_acceptsBodyWithoutApiVersion() throws Exception {
        // Reproduces a real-world delivery the smoke harness uncovered: a valid
        // Stripe-signed event whose body omits api_version. Prior to the
        // null-guard in StripeVerifier, this NPEs on rawApiVersion.split().
        String secret = "whsec_test_secret";
        String body =
                "{\"id\":\"evt_x\",\"object\":\"event\",\"type\":\"test\",\"data\":{\"object\":{}}}";
        long ts = System.currentTimeMillis() / 1000;
        String sig = "t=" + ts + ",v1=" + hmacHex(secret, ts + "." + body);

        var errors = verifier.verify(configWith(secret), eventWith(body, sig));

        assertThat(errors.isEmpty()).isTrue();
    }

    @Test
    void verify_rejectsBadSignature() throws Exception {
        String body = "{\"id\":\"evt_x\",\"object\":\"event\",\"api_version\":\"2024-06-20\"}";
        long ts = System.currentTimeMillis() / 1000;
        String sig = "t=" + ts + ",v1=" + hmacHex("wrong_secret", ts + "." + body);

        var errors = verifier.verify(configWith("right_secret"), eventWith(body, sig));

        assertThat(errors.isEmpty()).isFalse();
    }

    @Test
    void verify_rejectsMissingHeader() {
        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setBody("{}");
        event.setHeaders(new HttpHeaders());

        var errors = verifier.verify(configWith("secret"), event);

        assertThat(errors.isEmpty()).isFalse();
    }

    private static WebhookConfig configWith(String secret) {
        WebhookConfig c = new WebhookConfig();
        c.setName("test");
        c.setSecretValue(secret);
        return c;
    }

    private static IncomingWebhookEvent eventWith(String body, String signatureHeader) {
        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setBody(body);
        HttpHeaders headers = new HttpHeaders();
        headers.put("Stripe-Signature", List.of(signatureHeader));
        event.setHeaders(headers);
        return event;
    }

    private static String hmacHex(String key, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(), "HmacSHA256"));
        byte[] digest = mac.doFinal(message.getBytes());
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
