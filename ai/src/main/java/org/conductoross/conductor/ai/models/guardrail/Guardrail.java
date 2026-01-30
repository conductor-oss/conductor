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
package org.conductoross.conductor.ai.models.guardrail;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME, // use symbolic names
        include = JsonTypeInfo.As.EXISTING_PROPERTY, // the field already exists
        property = "type",
        visible = true // keep “type” visible to setters/getters
        )
@JsonSubTypes({
    @JsonSubTypes.Type(value = HumanGuardrail.class, name = "HUMAN"),
    @JsonSubTypes.Type(value = LLMGuardrail.class, name = "LLM"),
    @JsonSubTypes.Type(value = ScriptGuardrail.class, name = "SCRIPT"),
    @JsonSubTypes.Type(value = ScriptGuardrail.class, name = "HTTP")
})
@Data
public abstract class Guardrail {
    public enum Type {
        HUMAN,
        LLM,
        SCRIPT,
        HTTP
    }

    private Type type;
    private Map<String, Object> inputParameters = new HashMap<>();
}
