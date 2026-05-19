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

import com.sendgrid.helpers.eventwebhook.EventWebhook;
import com.sendgrid.helpers.eventwebhook.EventWebhookHeader;
import org.conductoross.conductor.common.utils.ErrorList;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;

@Slf4j
@Component
public class SendGridVerifier implements WebhookVerifier {
    private static final EventWebhook sendgrid = new EventWebhook();

    public SendGridVerifier() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public ErrorList verify(WebhookConfig webhookConfig, IncomingWebhookEvent incomingWebhookEvent) {
        var errors = new ErrorList();

        ECPublicKey key = null;

        try {
            key = sendgrid.ConvertPublicKeyToECDSA(webhookConfig.getSecretKey());
        } catch (NoSuchAlgorithmException e) {
            errors.add("Failed to convert public key: " + e.getMessage());
        } catch (NoSuchProviderException e) {
            errors.add("BouncyCastle provider is not available: " + e.getMessage());
        } catch (InvalidKeySpecException e) {
            errors.add("Invalid public key specification: " + e.getMessage());
        }

        var signature = incomingWebhookEvent.getHeaders().getFirst(EventWebhookHeader.SIGNATURE.name);
        var timestamp = incomingWebhookEvent.getHeaders().getFirst(EventWebhookHeader.TIMESTAMP.name);

        if (signature == null) {
            errors.add(String.format("Header '%s' is missing", EventWebhookHeader.SIGNATURE.name));
        }

        if (timestamp == null) {
            errors.add(String.format("Header '%s' is missing", EventWebhookHeader.TIMESTAMP.name));
        }

        if (errors.isEmpty()) {
            try {
                if (!sendgrid.VerifySignature(key, incomingWebhookEvent.getBody(), signature, timestamp)) {
                    errors.add("Signature verification failed.");
                }
            } catch (NoSuchAlgorithmException e) {
                errors.add("Failed to convert public key", e);
            } catch (SignatureException e) {
                errors.add("Signature verification failed", e);
            } catch (IOException e) {
                errors.add("Failed to read the request body", e);
            } catch (NoSuchProviderException e) {
                errors.add("BouncyCastle provider is not available: ", e);
            } catch (InvalidKeyException e) {
                errors.add("Invalid public key: ", e);
            }
        }

        return errors;
    }

    @Override
    public String getType() {
        return WebhookConfig.Verifier.SENDGRID.toString();
    }
}
