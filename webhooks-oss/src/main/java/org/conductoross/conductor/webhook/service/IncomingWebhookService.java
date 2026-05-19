/*
 * Copyright 2022 Orkes, Inc.
 * <p>
 * Licensed under the Orkes Enterprise License (the "License"); you may not use this file except in compliance with
 * the License.
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.webhook.service;

import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.dao.QueueDAO;
import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.common.metadata.EventMessage;
import org.conductoross.conductor.id.TimeBasedUUIDGenerator;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.conductoross.conductor.webhook.verifier.WebhookVerifier;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.conductoross.conductor.webhook.WebhookWorkerProperties.WEBHOOK_QUEUE;
import static org.conductoross.conductor.common.metadata.EventMessage.DEAD_LETTER_QUEUE;


@Service
@Slf4j
public class IncomingWebhookService {

    private final WebhookDAO webhookDAO;

    private final QueueDAO queueDAO;

    private final Map<String, WebhookVerifier> webhookVerifiers;
    private final ExecutionDAOFacade executionDAOFacade;
    private final TimeBasedUUIDGenerator timeBasedUUIDGenerator;

    public IncomingWebhookService(
            WebhookDAO webhookDAO,
            Set<WebhookVerifier> webhookVerifiersSet,
            QueueDAO queueDAO,
            TimeBasedUUIDGenerator timeBasedUUIDGenerator,
            ExecutionDAOFacade executionDAOFacade) {
        this.webhookDAO = webhookDAO;
        this.timeBasedUUIDGenerator = timeBasedUUIDGenerator;
        this.queueDAO = queueDAO;
        this.webhookVerifiers =
                webhookVerifiersSet.stream()
                        .collect(Collectors.toMap(WebhookVerifier::getType, Function.identity()));
        this.executionDAOFacade = executionDAOFacade;
    }

    public String handleWebhook(String id, String bodyStr, Map<String, Object> requestParams, HttpHeaders headers) {
        String eventId = timeBasedUUIDGenerator.generate();
        IncomingWebhookEvent incomingWebhookEvent =
                IncomingWebhookEvent.builder()
                        .body(bodyStr)
                        .timeStamp(System.currentTimeMillis())
                        .headers(headers)
                        .requestParams(requestParams)
                        .webhookId(id)
                        .id(eventId)
                        .build();

        WebhookConfig webhookConfig = webhookDAO.getWebhook(id);
        if (webhookConfig == null) {
            EventMessage eventMessage = new EventMessage();
            String description = "Webhook " + id + " does not exist in conductor.";
            eventMessage
                    .setId(incomingWebhookEvent.getId())
                    .setEventTarget(DEAD_LETTER_QUEUE)
                    .setEventType(EventMessage.EventType.WEBHOOK)
                    .setPayload(incomingWebhookEvent.getBody())
                    .setFullPayload(incomingWebhookEvent)
                    .setCreatedAt(incomingWebhookEvent.getTimeStamp())
                    .setStatus(EventMessage.EventMessageStatus.REJECTED)
                    .setStatusDescription(description);

            log.warn("Rejected webhook event {}: {}", eventMessage.getId(), description);
            return null;
        }

        String verifierName = webhookConfig.getVerifier().toString();
        WebhookVerifier verifier = this.webhookVerifiers.get(verifierName);

        var verificationErrors = verifier.verify(webhookConfig, incomingWebhookEvent);

        if (verificationErrors.arePresent()) {
            String verificationFailure = "Request verification failed for webhookevent '" + id
                    + "': " + verificationErrors.getMessage();

            log.error(verificationFailure);

            EventMessage eventMessage = new EventMessage();
            eventMessage
                    .setId(incomingWebhookEvent.getId())
                    .setEventTarget(webhookConfig.getId())
                    .setEventType(EventMessage.EventType.WEBHOOK)
                    .setPayload(incomingWebhookEvent.getBody())
                    .setFullPayload(incomingWebhookEvent)
                    .setCreatedAt(incomingWebhookEvent.getTimeStamp())
                    .setStatus(EventMessage.EventMessageStatus.REJECTED)
                    .setStatusDescription(verificationFailure);

            log.warn("Rejected webhook event {}: {}", eventMessage.getId(), verificationFailure);

            throw new NonTransientException(verificationFailure);
        }

        boolean update = false;

        if (!webhookConfig.isUrlVerified()) {
            webhookConfig.setUrlVerified(true);
            update = true;
        }

        String webhookChallenge = verifier.extractChallenge(incomingWebhookEvent, webhookConfig);
        if (webhookChallenge == null) {
            // This message should be processed and added to the queue
            storeWebhook(incomingWebhookEvent);
        }
        // Just update the URL that it is verified
        if (update) {
            webhookDAO.createWebhook(webhookConfig.getId(), webhookConfig);
        }

        return webhookChallenge;
    }

    private void storeWebhook(IncomingWebhookEvent incomingWebhookEvent) {
        webhookDAO.createIncomingWebhookEvent(incomingWebhookEvent.getId(), incomingWebhookEvent);
        queueDAO.push(WEBHOOK_QUEUE, incomingWebhookEvent.getId(), 0);
    }

    @SneakyThrows
    public String handlePing(String id, Map<String, Object> requestParams) {
        WebhookConfig webhookConfig = webhookDAO.getWebhook(id);
        if (webhookConfig == null) {
            log.trace("Webhook with id {} does not exist", id);
            return null;
        }
        String verifierName = webhookConfig.getVerifier().toString();
        WebhookVerifier verifier = this.webhookVerifiers.get(verifierName);
        String response = verifier.handlePing(webhookConfig, requestParams);
        if (response != null) {
            // Ping event
            webhookConfig.setUrlVerified(true);
            webhookDAO.createWebhook(id, webhookConfig);
        } else {
            // Webhook event
            String eventId = timeBasedUUIDGenerator.generate();
            IncomingWebhookEvent incomingWebhookEvent =
                    IncomingWebhookEvent.builder()
                            .body("{}")
                            .timeStamp(System.currentTimeMillis())
                            .webhookId(webhookConfig.getId())
                            .requestParams(requestParams)
                            .id(eventId)
                            .build();
            storeWebhook(incomingWebhookEvent);
        }
        return response;
    }
}
