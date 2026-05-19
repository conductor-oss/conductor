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
package org.conductoross.conductor.webhook.rest;

import java.util.Map;

import org.conductoross.conductor.webhook.service.IncomingWebhookService;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(
        value = "/webhook",
        produces = {MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@Slf4j
public class IncomingWebhookResource {

    private final IncomingWebhookService incomingWebhookService;

    @PostMapping("/{id}")
    public String handleWebhook(
            @PathVariable String id,
            @RequestBody String bodyStr,
            @RequestParam Map<String, Object> requestParams,
            @RequestHeader HttpHeaders headers) {
        log.debug("Webhook Event id={} payload={} params={} headers={}",
                id, bodyStr, requestParams, headers);
        return incomingWebhookService.handleWebhook(id, bodyStr, requestParams, headers);
    }

    @GetMapping("/{id}")
    public String handlePing(
            @PathVariable String id,
            @RequestParam Map<String, Object> requestParams) {
        return incomingWebhookService.handlePing(id, requestParams);
    }
}
