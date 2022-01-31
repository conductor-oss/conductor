/*
 * Copyright 2022 Netflix, Inc.
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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.exception.TerminateWorkflowException;

@Component(ValueParamEvaluator.NAME)
public class ValueParamEvaluator implements Evaluator {

    public static final String NAME = "value-param";
    private static final Logger LOGGER = LoggerFactory.getLogger(ValueParamEvaluator.class);

    @SuppressWarnings("unchecked")
    @Override
    public Object evaluate(String expression, Object input) {
        LOGGER.debug("ValueParam evaluator -- evaluating: {}", expression);
        if (input instanceof Map) {
            Object result = ((Map<String, Object>) input).get(expression);
            LOGGER.debug("ValueParam evaluator -- result: {}", result);
            return result;
        } else {
            String errorMsg = String.format("Input has to be a JSON object: %s", input.getClass());
            LOGGER.error(errorMsg);
            throw new TerminateWorkflowException(errorMsg);
        }
    }
}
