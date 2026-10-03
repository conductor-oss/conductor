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
package org.conductoross.conductor.ai.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/** Turns the arguments a model wrote into a task's input, safely. */
@Slf4j
public final class AgentToolArguments {

    private AgentToolArguments() {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Parsed arguments, with any Conductor expression in them reduced to text. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(Object arguments) {
        return (Map<String, Object>) asLiteralText(parseRaw(arguments));
    }

    private static Map<String, Object> parseRaw(Object arguments) {
        if (arguments == null) {
            return Map.of();
        }
        if (arguments instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(String.valueOf(key), value));
            return copy;
        }
        String raw = arguments.toString().trim();
        if (raw.isEmpty()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(raw, Map.class);
        } catch (Exception e) {
            // A tool whose arguments are not a JSON object still gets them, verbatim, under a
            // predictable key - better than failing the turn over a shape we did not expect.
            log.debug("Tool arguments were not a JSON object; passing through as 'arguments'");
            return Map.of("arguments", raw);
        }
    }

    /**
     * Escapes Conductor expressions out of values a model produced.
     *
     * <p>Tool arguments become a task's input parameters, and the engine resolves {@code ${...}} in
     * those against the running workflow. The arguments are written by a model, from a prompt that
     * may itself carry text from anywhere - so left alone, {@code ${workflow.input.customer_ssn}}
     * in a tool argument is a request the engine happily fulfils, handing workflow data to the tool
     * as if the workflow author had asked for it.
     *
     * <p>Doubling the dollar is the engine's own escape, so the tool receives the text the model
     * actually wrote and nothing more.
     */
    static Object asLiteralText(Object value) {
        if (value instanceof String text) {
            return text.contains("${") ? text.replace("${", "$${") : text;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> copy.put(String.valueOf(key), asLiteralText(entry)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(entry -> copy.add(asLiteralText(entry)));
            return copy;
        }
        return value;
    }
}
