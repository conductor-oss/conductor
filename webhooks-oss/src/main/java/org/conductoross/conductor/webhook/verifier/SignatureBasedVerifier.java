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

import org.conductoross.conductor.common.utils.ErrorList;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.conductoross.conductor.webhook.utils.HashUtils;
import org.springframework.stereotype.Component;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;

import lombok.extern.slf4j.Slf4j;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Component
public class SignatureBasedVerifier implements WebhookVerifier {

    private static final String SHA_256 = "sha256=";

    @Override
    public ErrorList verify(WebhookConfig webhookConfig, IncomingWebhookEvent incomingWebhookEvent) {
        var header = webhookConfig.getHeaderKey();
        var headerValues = incomingWebhookEvent.getHeaders().get(header);

        if (headerValues == null || headerValues.isEmpty()) {
            return ErrorList.singleton(header + " is not present in the header");
        } else if (headerValues.size() > 1) {
            return ErrorList.singleton("Multiple " + header + " is present in the header");
        }

        try {
            var requestSignature = headerValues.getFirst().substring(SHA_256.length());
            var computedSignature = this.computeSignature(
                    webhookConfig.getSecretValue(), incomingWebhookEvent.getBody());

            log.debug("requestSignature: {} computedSignature :{}", requestSignature, computedSignature);

            if (!computedSignature.equals(requestSignature)) {
                return ErrorList.singleton("Computed signature does not match the request signature.");
            }
        } catch (NoSuchAlgorithmException e) {
            return ErrorList.singleton("Failed to convert public key", e);
        } catch (InvalidKeyException e) {
            return ErrorList.singleton("Invalid public key", e);
        }

        return ErrorList.empty();
    }

    protected String computeSignature(String key, String message) throws NoSuchAlgorithmException, InvalidKeyException {
        return HashUtils.computeHexHmacSha256(key, message);
    }

    @Override
    public String getType() {
        return WebhookConfig.Verifier.SIGNATURE_BASED.toString();
    }
}
