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
package com.netflix.conductor.core.execution.evaluators;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.core.events.ScriptEvaluator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * GraalJS evaluator - an alias for JavaScript evaluator using GraalJS engine. This allows explicit
 * specification of "graaljs" as the evaluator type while maintaining backward compatibility with
 * "javascript".
 */
@Component(GraalJSEvaluator.NAME)
public class GraalJSEvaluator implements Evaluator {

    public static final String NAME = "graaljs";
    private static final Logger LOGGER = LoggerFactory.getLogger(GraalJSEvaluator.class);
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    @Override
    public Object evaluate(String expression, Object input) {
        LOGGER.debug("GraalJS evaluator -- expression: {}", expression);

        Object inputCopy = new HashMap<>();
        // Deep copy to prevent PolyglotMap issues (same as JavascriptEvaluator)
        try {
            inputCopy =
                    objectMapper.readValue(
                            objectMapper.writeValueAsString(input), new TypeReference<>() {});
        } catch (Exception e) {
            LOGGER.error("Error making a deep copy of input: {}", expression, e);
        }

        // Evaluate using the same GraalJS evaluation engine
        Object result = ScriptEvaluator.eval(expression, inputCopy);
        LOGGER.debug("GraalJS evaluator -- result: {}", result);
        return result;
    }
}
