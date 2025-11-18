/*
 * Copyright 2024 Conductor Authors.
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.exception.TerminateWorkflowException;

@Component(PythonEvaluator.NAME)
public class PythonEvaluator implements Evaluator {
    public static final String NAME = "python";
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonEvaluator.class);

    @Override
    public Object evaluate(String expression, Object input) {
        try (Context context = Context.newBuilder("python").allowAllAccess(true).build()) {
            if (input instanceof Map) {
                Map<String, Object> inputMap = (Map<String, Object>) input;

                // Set inputs as variables in the GraalVM context
                for (Map.Entry<String, Object> entry : inputMap.entrySet()) {
                    context.getBindings("python").putMember(entry.getKey(), entry.getValue());
                }

                // Build the global declaration dynamically
                StringBuilder globalDeclaration = new StringBuilder("def evaluate():\n    global ");
                for (Map.Entry<String, Object> entry : inputMap.entrySet()) {
                    globalDeclaration.append(entry.getKey()).append(", ");
                }

                // Remove the trailing comma and space, and add a newline
                if (globalDeclaration.length() > 0) {
                    globalDeclaration.setLength(globalDeclaration.length() - 2);
                }
                globalDeclaration.append("\n");

                // Wrap the expression in a function to handle multi-line statements
                StringBuilder wrappedExpression = new StringBuilder(globalDeclaration);
                for (String line : expression.split("\n")) {
                    wrappedExpression.append("    ").append(line).append("\n");
                }

                // Add the call to the function and capture the result
                wrappedExpression.append("\nresult = evaluate()");

                // Execute the wrapped expression
                context.eval("python", wrappedExpression.toString());

                // Get the result
                Value result = context.getBindings("python").getMember("result");

                // Convert the result to a Java object and return it
                return result.as(Object.class);
            } else {
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("Error evaluating expression: {}", e.getMessage(), e);
            throw new TerminateWorkflowException(e.getMessage());
        }
    }
}
