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

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.sendgrid.helpers.eventwebhook.EventWebhookHeader;

import static org.assertj.core.api.Assertions.assertThat;

class SendGridVerifierTest {

    private static final String SIG_HEADER = EventWebhookHeader.SIGNATURE.name;
    private static final String TS_HEADER = EventWebhookHeader.TIMESTAMP.name;

    private static KeyPair KEY_PAIR;
    private static String PUBLIC_KEY_B64;

    private final SendGridVerifier verifier = new SendGridVerifier();

    @BeforeAll
    static void generateKeyPair() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC", "BC");
        gen.initialize(new ECGenParameterSpec("P-256"), new SecureRandom());
        KEY_PAIR = gen.generateKeyPair();
        PUBLIC_KEY_B64 = Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded());
    }

    @Test
    void verify_missingSignatureHeader_returnsError() {
        var event = eventWith("{}");
        event.getHeaders().add(TS_HEADER, "1234567890");

        var errors = verifier.verify(configWith(PUBLIC_KEY_B64), event);

        assertThat(errors.isEmpty()).isFalse();
        assertThat(errors.getMessage()).contains(SIG_HEADER);
    }

    @Test
    void verify_missingTimestampHeader_returnsError() {
        var event = eventWith("{}");
        event.getHeaders().add(SIG_HEADER, "dGVzdA==");

        var errors = verifier.verify(configWith(PUBLIC_KEY_B64), event);

        assertThat(errors.isEmpty()).isFalse();
        assertThat(errors.getMessage()).contains(TS_HEADER);
    }

    @Test
    void verify_bothHeadersMissing_returnsMultipleErrors() {
        var event = eventWith("{}");

        var errors = verifier.verify(configWith(PUBLIC_KEY_B64), event);

        assertThat(errors.isEmpty()).isFalse();
        // Both header names appear in the combined error message.
        assertThat(errors.getMessage()).contains(SIG_HEADER).contains(TS_HEADER);
    }

    @Test
    void verify_invalidPublicKey_returnsError() {
        var event = eventWith("{}");
        event.getHeaders().add(SIG_HEADER, "dGVzdA==");
        event.getHeaders().add(TS_HEADER, "1234567890");

        // Valid base64 but not a real EC key — ConvertPublicKeyToECDSA throws
        // InvalidKeySpecException.
        String badKey = Base64.getEncoder().encodeToString("not-a-real-ec-key-bytes".getBytes());
        var errors = verifier.verify(configWith(badKey), event);

        assertThat(errors.isEmpty()).isFalse();
    }

    @Test
    void verify_validSignature_returnsNoErrors() throws Exception {
        String body = "{\"type\":\"test\",\"data\":{}}";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        var event = eventWith(body);
        event.getHeaders().add(SIG_HEADER, sign(timestamp, body));
        event.getHeaders().add(TS_HEADER, timestamp);

        var errors = verifier.verify(configWith(PUBLIC_KEY_B64), event);

        assertThat(errors.isEmpty()).isTrue();
    }

    @Test
    void verify_wrongSignature_returnsError() throws Exception {
        String body = "{\"type\":\"test\"}";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        var event = eventWith(body);
        // Sign a different payload — signature won't match the actual body.
        event.getHeaders().add(SIG_HEADER, sign(timestamp, "tampered-payload"));
        event.getHeaders().add(TS_HEADER, timestamp);

        var errors = verifier.verify(configWith(PUBLIC_KEY_B64), event);

        assertThat(errors.isEmpty()).isFalse();
    }

    // --- helpers ---

    private static WebhookConfig configWith(String publicKey) {
        WebhookConfig c = new WebhookConfig();
        c.setSecretKey(publicKey);
        return c;
    }

    private static IncomingWebhookEvent eventWith(String body) {
        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setBody(body);
        event.setHeaders(new HttpHeaders());
        return event;
    }

    /** Mirrors SendGrid's VerifySignature: SHA256withECDSA over (timestamp + body). */
    private static String sign(String timestamp, String body) throws Exception {
        Signature sig = Signature.getInstance("SHA256withECDSA", "BC");
        sig.initSign(KEY_PAIR.getPrivate());
        sig.update((timestamp + body).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sig.sign());
    }
}
