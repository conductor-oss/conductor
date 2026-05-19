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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SlackVerifier implements WebhookVerifier {

    private static String challenge = "challenge";

    @Override
    public ErrorList verify(WebhookConfig webhookConfig, IncomingWebhookEvent incomingWebhookEvent) {
        try {
            if (webhookConfig.isUrlVerified()) {
                // These are actual set of events
                return ErrorList.empty();
            }

            String requestBody = incomingWebhookEvent.getBody();
            DocumentContext jsonContext = JsonPath.parse(requestBody);
            String body = jsonContext.read("challenge");

            if (StringUtils.isEmpty(body)) {
                return ErrorList.singleton("Challenge is not present in the body");
            } else {
                log.debug("Challenge is verified successfully for webhook: {}", incomingWebhookEvent.getId());

                return ErrorList.empty();
            }
        } catch (Exception e) {
            return ErrorList.singleton(
                    "challenge is not present in the header "
                            + incomingWebhookEvent.getWebhookId());
        }
    }

    @Override
    public String getType() {
        return WebhookConfig.Verifier.SLACK_BASED.toString();
    }

    public String extractChallenge(IncomingWebhookEvent incomingWebhookEvent, WebhookConfig webhookConfig) {
        try {
            // Before parsing check for challenge parameter
            String requestBody = incomingWebhookEvent.getBody();
            if (requestBody.contains(challenge)) {
                log.info("Slack url verification initiated for webhook " + webhookConfig.getId());
                DocumentContext jsonContext = JsonPath.parse(requestBody);
                return jsonContext.read(challenge);
            }
        } catch (Exception e) {
            log.error(
                    "challenge is not present in the header "
                            + incomingWebhookEvent.getWebhookId());

        }
        return null;
    }
}
