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
package com.netflix.conductor.core.execution.evaluators;

import org.springframework.stereotype.Component;

/**
 * A decision a native workflow's code already made: the SWITCH it draws names the case it took, and
 * the server only reads that value (the input parameter the expression names, as {@code
 * value-param} does). Nothing is evaluated against workflow data. Native workflows use it for every
 * SWITCH, so the evaluator type says who decided.
 */
@Component(NativeCodeEvaluator.NAME)
public class NativeCodeEvaluator implements Evaluator {

    public static final String NAME = "native-code";

    private final ValueParamEvaluator recorded = new ValueParamEvaluator();

    @Override
    public Object evaluate(String expression, Object input) {
        return recorded.evaluate(expression, input);
    }
}
