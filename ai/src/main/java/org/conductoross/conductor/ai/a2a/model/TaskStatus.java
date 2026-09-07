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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Current status of an A2A {@link A2ATask}: a {@link TaskState} string, an optional message (e.g.
 * the agent's reply or its prompt for more input), and a timestamp.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskStatus {

    private String state;
    private A2AMessage message;
    private String timestamp;
}
