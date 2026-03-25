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

import org.springframework.http.HttpHeaders;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a single inbound webhook HTTP request captured at {@code POST /webhook/{id}}.
 *
 * <p>Ported from Orkes Enterprise with no org-specific fields. The {@code headers} field uses
 * Spring's {@link HttpHeaders} so that header lookup is case-insensitive, matching HTTP semantics.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingWebhookEvent {

    private String id;
    private String webhookId;
    private String body;
    private HttpHeaders headers;
    private Map<String, Object> requestParams;
    private long timeStamp;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWebhookId() {
        return webhookId;
    }

    public void setWebhookId(String webhookId) {
        this.webhookId = webhookId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public void setHeaders(HttpHeaders headers) {
        this.headers = headers;
    }

    public Map<String, Object> getRequestParams() {
        return requestParams;
    }

    public void setRequestParams(Map<String, Object> requestParams) {
        this.requestParams = requestParams;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }
}
