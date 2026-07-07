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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * A single content unit within an A2A {@link A2AMessage} or {@link Artifact}.
 *
 * <p>The {@code kind} discriminator selects the variant (A2A v0.3.x wire model): {@code "text"}
 * (uses {@link #text}), {@code "data"} (uses {@link #data}), or {@code "file"} (uses {@link #file},
 * a {@code FileWithBytes} or {@code FileWithUri} object). Unknown fields are ignored so newer
 * protocol revisions parse without error.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Part {

    private String kind;
    private String text;
    private Object data;
    private Object file;
    private Map<String, Object> metadata;
}
