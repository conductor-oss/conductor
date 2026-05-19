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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.conductoross.conductor.webhook.utils.HashUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TwitterVerifier extends SignatureBasedVerifier {
    @Override
    protected String computeSignature(String key, String message) throws NoSuchAlgorithmException, InvalidKeyException {
        return HashUtils.computeBase64HmacSha256(key, message);
    }

    @Override
    public String getType() {
        return WebhookConfig.Verifier.TWITTER.toString();
    }

    @SneakyThrows
    @Override
    public String handlePing(WebhookConfig webhookConfig, Map<String, Object> requestParams) {
        if (requestParams == null) return null;

        Object crcToken = requestParams.get("crc_token");

        if (crcToken == null) {
            return null;
        }

        return "{\"response_token\":\"sha256=" + HashUtils.computeBase64HmacSha256(webhookConfig.getSecretValue(), crcToken.toString()) + "\"}";
    }
}
