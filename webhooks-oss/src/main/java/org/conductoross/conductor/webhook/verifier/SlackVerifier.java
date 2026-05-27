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

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.common.utils.ErrorList;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.springframework.stereotype.Component;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;

/**
 * Verifies Slack webhook events using Slack's signing-secret protocol:
 *
 * <pre>{@code
 *   X-Slack-Signature: v0=<hex_hmac_sha256(timestamp + ":" + body)>
 *   X-Slack-Request-Timestamp: <unix_seconds>
 * }</pre>
 *
 * <p>The URL-verification handshake (the {@code challenge} field on first delivery) is still
 * honored — {@link #extractChallenge} returns the challenge so {@link
 * org.conductoross.conductor.webhook.service.IncomingWebhookService} can echo it back — but
 * passing the handshake no longer disables signing-secret checks. Every subsequent event is
 * verified against the configured signing secret.
 *
 * <p>References: Slack's docs at
 * https://api.slack.com/authentication/verifying-requests-from-slack
 */
@Slf4j
@Component
public class SlackVerifier implements WebhookVerifier {

    public static final String SIGNATURE_HEADER = "X-Slack-Signature";
    public static final String TIMESTAMP_HEADER = "X-Slack-Request-Timestamp";
    public static final String SIGNATURE_VERSION = "v0";
    public static final String ALGORITHM = "HmacSHA256";

    /** Slack's recommended replay-tolerance window. */
    public static final long DEFAULT_TIMESTAMP_TOLERANCE_SECONDS = 60L * 5;

    private static final String CHALLENGE_FIELD = "challenge";

    @Override
    public ErrorList verify(
            WebhookConfig webhookConfig, IncomingWebhookEvent incomingWebhookEvent) {
        if (incomingWebhookEvent.getHeaders() == null) {
            return ErrorList.singleton("Slack request headers are missing");
        }

        List<String> sigValues = incomingWebhookEvent.getHeaders().get(SIGNATURE_HEADER);
        List<String> tsValues = incomingWebhookEvent.getHeaders().get(TIMESTAMP_HEADER);

        if (sigValues == null || sigValues.isEmpty()) {
            return ErrorList.singleton(SIGNATURE_HEADER + " is not present in the header");
        }
        if (tsValues == null || tsValues.isEmpty()) {
            return ErrorList.singleton(TIMESTAMP_HEADER + " is not present in the header");
        }

        String requestSignature = sigValues.getFirst();
        String timestamp = tsValues.getFirst();

        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return ErrorList.singleton(TIMESTAMP_HEADER + " is not a unix-seconds integer");
        }

        long now = System.currentTimeMillis() / 1000L;
        if (Math.abs(now - ts) > DEFAULT_TIMESTAMP_TOLERANCE_SECONDS) {
            return ErrorList.singleton(
                    TIMESTAMP_HEADER + " is outside the replay-tolerance window");
        }

        String signingSecret = webhookConfig.getSecretValue();
        if (StringUtils.isEmpty(signingSecret)) {
            return ErrorList.singleton("Slack signing secret is not configured");
        }

        String body = incomingWebhookEvent.getBody() == null ? "" : incomingWebhookEvent.getBody();
        String basestring = SIGNATURE_VERSION + ":" + timestamp + ":" + body;

        byte[] expectedHmac;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            expectedHmac = mac.doFinal(basestring.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            return ErrorList.singleton("Failed to compute HMAC: algorithm unavailable", e);
        } catch (InvalidKeyException e) {
            return ErrorList.singleton("Failed to compute HMAC: invalid signing secret", e);
        }

        String expectedSignature = SIGNATURE_VERSION + "=" + Hex.encodeHexString(expectedHmac);

        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] requestBytes = requestSignature.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, requestBytes)) {
            return ErrorList.singleton("Computed signature does not match the request signature.");
        }

        return ErrorList.empty();
    }

    @Override
    public String getType() {
        return WebhookConfig.Verifier.SLACK_BASED.toString();
    }

    @Override
    public String dedupKey(WebhookConfig webhookConfig, IncomingWebhookEvent event) {
        var values =
                event.getHeaders() == null ? null : event.getHeaders().get(SIGNATURE_HEADER);
        return (values == null || values.isEmpty()) ? null : values.getFirst();
    }

    public String extractChallenge(
            IncomingWebhookEvent incomingWebhookEvent, WebhookConfig webhookConfig) {
        try {
            String requestBody = incomingWebhookEvent.getBody();
            if (requestBody != null && requestBody.contains(CHALLENGE_FIELD)) {
                log.info(
                        "Slack url verification initiated for webhook {}", webhookConfig.getId());
                DocumentContext jsonContext = JsonPath.parse(requestBody);
                return jsonContext.read(CHALLENGE_FIELD);
            }
        } catch (Exception e) {
            log.error(
                    "Failed to extract Slack challenge for webhook {}",
                    incomingWebhookEvent.getWebhookId());
        }
        return null;
    }
}
