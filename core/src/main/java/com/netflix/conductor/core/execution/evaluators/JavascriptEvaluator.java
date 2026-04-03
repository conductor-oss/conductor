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

@Component(JavascriptEvaluator.NAME)
public class JavascriptEvaluator implements Evaluator {

    public static final String NAME = "javascript";
    private static final Logger LOGGER = LoggerFactory.getLogger(JavascriptEvaluator.class);
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    @Override
    public Object evaluate(String expression, Object input) {
        LOGGER.debug("Javascript evaluator -- expression: {}", expression);

        Object inputCopy = new HashMap<>();
        // We make a deep copy because there is a way to make it error out otherwise:
        // e.g. there's an input parameter (an empty map) 'myParam',
        // and an expression which has `$.myParam = {"a":"b"}`; It will put a 'PolyglotMap' from
        // GraalVM into input map
        // and that PolyglotMap can't be evaluated because the context is already closed.
        // this caused a workflow with INLINE task to be undecideable due to Exception in
        // TaskModelProtoMapper
        // on 'to.setInputData(convertToJsonMap(from.getInputData()))' call
        try {
            inputCopy =
                    objectMapper.readValue(
                            objectMapper.writeValueAsString(input), new TypeReference<>() {});
        } catch (Exception e) {
            LOGGER.error("Error making a deep copy of input: {}", expression, e);
        }

        // Evaluate the expression by using the GraalJS evaluation engine.
        Object result = ScriptEvaluator.eval(expression, inputCopy);
        LOGGER.debug("Javascript evaluator -- result: {}", result);
        return result;
    }
}
