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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.events.ScriptEvaluator;

@Component(JavascriptEvaluator.NAME)
public class JavascriptEvaluator implements Evaluator {

    public static final String NAME = "javascript";
    private static final Logger LOGGER = LoggerFactory.getLogger(JavascriptEvaluator.class);

    @Override
    public Object evaluate(String expression, Object input) {
        LOGGER.debug("Javascript evaluator -- expression: {}", expression);
        // Defensive deep copy so script-side mutations don't leak into the caller's input and so
        // any PolyglotMap/PolyglotList references created during eval cannot escape a closed
        // Context (see TaskModelProtoMapper.convertToJsonMap regression that motivated this).
        Object inputCopy = ScriptEvaluator.deepCopy(input);
        Object result = ScriptEvaluator.eval(expression, inputCopy);
        LOGGER.debug("Javascript evaluator -- result: {}", result);
        return result;
    }
}
