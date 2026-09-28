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
package org.conductoross.conductor.ai.a2a.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * One turn of communication between an A2A client and a remote agent.
 *
 * <p>{@code role} is {@code "user"} (from the client) or {@code "agent"} (from the remote agent).
 * Named {@code A2AMessage} to avoid confusion with Conductor's own message types.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class A2AMessage {

    private String role;
    private List<Part> parts;
    private String messageId;
    private String taskId;
    private String contextId;
    private String kind;
    private Map<String, Object> metadata;
}
