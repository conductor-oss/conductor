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
 * A stateful unit of work created by a remote agent in response to a message.
 *
 * <p>Named {@code A2ATask} to avoid confusion with Conductor's own {@code Task}/{@code TaskModel}.
 * The {@link #status} carries the lifecycle {@link TaskState}; {@link #artifacts} the outputs;
 * {@link #history} the conversation turns.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class A2ATask {

    private String id;
    private String contextId;
    private TaskStatus status;
    private List<A2AMessage> history;
    private List<Artifact> artifacts;
    private Map<String, Object> metadata;
    private String kind;
}
