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
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.conductoross.conductor.common.utils.ErrorList;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class HMACVerifier implements WebhookVerifier {

    public static final String ALGORITHM = "HmacSHA256";

    @Override
    public ErrorList verify(
            WebhookConfig webhookConfig, IncomingWebhookEvent incomingWebhookEvent) {
        var header = webhookConfig.getHeaderKey();
        var headerValues = incomingWebhookEvent.getHeaders().get(header);

        if (headerValues == null || headerValues.isEmpty()) {
            return ErrorList.singleton(header + " is not present in the header");
        } else if (headerValues.size() > 1) {
            return ErrorList.singleton("Multiple " + header + " is present in the header");
        }

        byte[] bodyBytes = incomingWebhookEvent.getBody().getBytes(StandardCharsets.UTF_8);

        byte[] keyBytes = decodeFromString(webhookConfig.getSecretValue());

        byte[] calculatedHmacValue;

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            mac.init(secretKeySpec);
            calculatedHmacValue = mac.doFinal(bodyBytes);
        } catch (InvalidKeyException e) {
            return ErrorList.singleton("Invalid public key", e);
        } catch (NoSuchAlgorithmException e) {
            return ErrorList.singleton("Failed to convert public key", e);
        }

        String computedSignature = new String(encode(calculatedHmacValue));

        String requestSignature = headerValues.getFirst().replace("HMAC", "").stripLeading();

        byte[] computedBytes = computedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] requestBytes = requestSignature.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(computedBytes, requestBytes)) {
            return ErrorList.singleton("Computed signature does not match the request signature.");
        }

        return ErrorList.empty();
    }

    @Override
    public String getType() {
        return WebhookConfig.Verifier.HMAC_BASED.toString();
    }

    @Override
    public String dedupKey(WebhookConfig webhookConfig, IncomingWebhookEvent event) {
        var values = event.getHeaders() == null ? null
                : event.getHeaders().get(webhookConfig.getHeaderKey());
        return (values == null || values.isEmpty()) ? null : values.getFirst();
    }

    public static byte[] decodeFromString(String src) {
        if (src.isEmpty()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(src);
    }

    public static String encodeToString(byte[] src) {
        if (src.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(src);
    }

    public static byte[] encode(byte[] src) {
        if (src.length == 0) {
            return src;
        }
        return Base64.getEncoder().encode(src);
    }
}
