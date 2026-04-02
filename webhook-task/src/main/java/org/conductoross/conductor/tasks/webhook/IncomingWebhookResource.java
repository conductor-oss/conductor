/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.tasks.webhook;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST endpoint for receiving inbound webhook events.
 *
 * <p>Mounted at {@code /webhook}. Each webhook config is addressed by its id:
 *
 * <ul>
 *   <li>{@code POST /webhook/{id}} — inbound event; verifies, completes waiting tasks
 *   <li>{@code GET /webhook/{id}} — ping/challenge; used by providers for URL verification
 * </ul>
 *
 * <p>Ported from Orkes Enterprise. The enterprise version extracts {@code orgId} from the webhook
 * ID and sets it on {@code OrkesRequestContext}. Here, that operation is delegated to {@link
 * WebhookOrgContextProvider}, which is a no-op in OSS. Orkes Enterprise supplies its own
 * implementation as a Spring bean to override the default.
 */
@RestController
@RequestMapping(value = "/webhook", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Webhook", description = "Inbound webhook event endpoint")
public class IncomingWebhookResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncomingWebhookResource.class);

    private final IncomingWebhookService incomingWebhookService;
    private final WebhookOrgContextProvider orgContextProvider;

    public IncomingWebhookResource(
            IncomingWebhookService incomingWebhookService,
            WebhookOrgContextProvider orgContextProvider) {
        this.incomingWebhookService = incomingWebhookService;
        this.orgContextProvider = orgContextProvider;
    }

    @PostMapping(value = "/{id}")
    @Operation(summary = "Receive an inbound webhook event")
    public String handleWebhook(
            @PathVariable("id") String id,
            @RequestBody(required = false) String bodyStr,
            @RequestParam Map<String, Object> requestParams,
            @RequestHeader HttpHeaders headers) {
        LOGGER.debug("Inbound webhook event id={} params={}", id, requestParams);
        orgContextProvider.applyContext(id);
        return incomingWebhookService.handleWebhook(
                id, bodyStr != null ? bodyStr : "{}", requestParams, headers);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Handle a webhook ping or challenge request")
    public String handlePing(
            @PathVariable("id") String id, @RequestParam Map<String, Object> requestParams) {
        LOGGER.debug("Webhook ping id={} params={}", id, requestParams);
        orgContextProvider.applyContext(id);
        return incomingWebhookService.handlePing(id, requestParams);
    }
}
