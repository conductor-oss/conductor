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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncomingWebhookResourceTest {

    @Mock private IncomingWebhookService service;
    private IncomingWebhookResource resource;

    @BeforeEach
    void setUp() {
        resource = new IncomingWebhookResource(service);
    }

    @Test
    void handleWebhook_delegatesAndReturnsServiceResult() {
        HttpHeaders headers = new HttpHeaders();
        Map<String, Object> params = Map.of("foo", "bar");
        when(service.handleWebhook(eq("hook-1"), eq("body"), eq(params), any(HttpHeaders.class)))
                .thenReturn("challenge");

        String result = resource.handleWebhook("hook-1", "body", params, headers);

        assertThat(result).isEqualTo("challenge");
        verify(service).handleWebhook("hook-1", "body", params, headers);
    }

    @Test
    void handlePing_delegatesAndReturnsServiceResult() {
        Map<String, Object> params = Map.of("challenge", "abc");
        when(service.handlePing("hook-1", params)).thenReturn("abc");

        String result = resource.handlePing("hook-1", params);

        assertThat(result).isEqualTo("abc");
        verify(service).handlePing("hook-1", params);
    }
}
