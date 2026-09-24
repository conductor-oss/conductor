/*
 * Copyright 2020 Conductor Authors.
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
package org.conductoross.conductor.webhook.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder(toBuilder = true)
@RequiredArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookConfig {

    private String name;

    private String id;

    private Map<String, Integer> receiverWorkflowNamesToVersions;

    private Map<String, Object> workflowsToStart;

    private boolean urlVerified;

    private String sourcePlatform;

    private Verifier verifier;

    private Map<String, String> headers;

    private String headerKey; // Required for signature_based verifier.

    private String secretKey;

    private String secretValue;

    private String createdBy;

    private String expression;
    private String evaluatorType;

    public enum Verifier {
        SLACK_BASED,
        SIGNATURE_BASED,
        HEADER_BASED,
        STRIPE,
        TWITTER,
        HMAC_BASED,
        SENDGRID
    }

    public void accept(WebhookConfigVisitor visitor) {
        visitor.visit(this);
    }

    public interface WebhookConfigVisitor {
        default void visit(WebhookConfig webhookConfig) {}
    }
}
