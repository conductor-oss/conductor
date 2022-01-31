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

public interface Evaluator {
    /**
     * Evaluate the expression using the inputs provided, if required. Evaluation of the expression
     * depends on the type of the evaluator.
     *
     * @param expression Expression to be evaluated.
     * @param input Input object to the evaluator to help evaluate the expression.
     * @return Return the evaluation result.
     */
    Object evaluate(String expression, Object input);
}
