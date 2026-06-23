/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.models;

import java.util.Map;

import lombok.Data;

@Data
public class ToolSpec {
    private String name;
    private String type;
    private Map<String, Object> configParams;
    private Map<String, String> integrationNames;
    private String description;
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;
    /**
     * When true, this spec is complete as delivered: pass it to the LLM
     * as-is. Consumers must not resolve, enrich, or replace it by name
     * against integrations, services, or task definitions. Set by
     * producers that compile full inline tool specs (e.g. AgentSpan).
     */
    private boolean selfDescribing;
}
