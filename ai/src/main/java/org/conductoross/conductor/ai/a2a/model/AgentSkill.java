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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** A discrete capability a remote agent advertises on its {@link AgentCard}. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentSkill {

    private String id;
    private String name;
    private String description;
    private List<String> tags;
    private List<String> examples;
    private List<String> inputModes;
    private List<String> outputModes;
}
