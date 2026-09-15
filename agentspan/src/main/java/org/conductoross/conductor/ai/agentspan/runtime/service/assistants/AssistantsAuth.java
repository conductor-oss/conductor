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
package org.conductoross.conductor.ai.agentspan.runtime.service.assistants;

/**
 * How a request to an Assistants-compatible endpoint authenticates.
 *
 * <p>An abstraction rather than a bearer string because the endpoints differ: OpenAI takes a bearer
 * API key, while Azure accepts an {@code api-key} header, a token from any of several Entra ID
 * credential types, or a token exchanged on a caller's behalf. The header is resolved per request,
 * so an implementation backed by a refreshing credential hands over a current token each time.
 */
public interface AssistantsAuth {

    /** Header to carry the credential — {@code Authorization} or {@code api-key}. */
    String headerName();

    /** Value for that header, resolved now. */
    String headerValue();

    /** Auth by bearer token, the shape OpenAI and pre-exchanged Entra tokens both use. */
    static AssistantsAuth bearer(String token) {
        return new AssistantsAuth() {
            @Override
            public String headerName() {
                return "Authorization";
            }

            @Override
            public String headerValue() {
                return "Bearer " + token;
            }
        };
    }
}
