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
package org.conductoross.conductor.ai.decision;

import java.util.Map;

import com.netflix.conductor.tasks.http.HttpTask;

import com.fasterxml.jackson.databind.JsonNode;

/** Maps a provider API shape to the shared structured decision contract. */
public interface DecisionApiAdapter {
    String name();

    Map<String, Object> encode(DecisionRequest request);

    /**
     * Builds the provider request for {@link HttpTask}. Authentication, headers, HTTP method and
     * body are part of the wire contract and therefore belong to the adapter; timeouts are set by
     * {@link DecisionTask}.
     */
    HttpTask.Input createRequest(DecisionRequest request, DecisionConfiguration.Route route);

    DecisionResult decode(JsonNode response, long latencyMs, String provider);
}
