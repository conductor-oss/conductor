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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Properties;

import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.springframework.stereotype.Component;

@Component(PythonEvaluator.NAME)
public class PythonEvaluator implements Evaluator {
    public static final String NAME = "python";

    @Override
    public Object evaluate(String expression, Object inputs) {

        PythonInterpreter interpreter = null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            Properties props = new Properties();
            props.setProperty("python.import.site", "false");
            PythonInterpreter.initialize(System.getProperties(), props, new String[] {});

            interpreter = new PythonInterpreter();
            interpreter.setOut(new PrintStream(outputStream));

            if (inputs instanceof Map) {
                Map<String, Object> input = (Map<String, Object>) inputs;

                // Set variables in the interpreter
                for (Map.Entry<String, Object> entry : input.entrySet()) {
                    interpreter.set(entry.getKey(), entry.getValue());
                }
                // Build the global declaration dynamically
                StringBuilder globalDeclaration = new StringBuilder("def evaluate():\n    global ");

                for (Map.Entry<String, Object> entry : input.entrySet()) {
                    globalDeclaration.append(entry.getKey()).append(", ");
                }

                // Remove the trailing comma and space, and add a newline
                if (globalDeclaration.length() > 0) {
                    globalDeclaration.setLength(globalDeclaration.length() - 2);
                }
                globalDeclaration.append("\n");

                // Wrap the expression in a function to handle multi-line statements
                String wrappedExpression =
                        globalDeclaration.toString() // Add global declaration line
                                + "    "
                                + expression.replace(
                                        "\n", "\n    ") // Indent the original expression
                                + "\n"
                                + "result = evaluate()";

                // Execute the wrapped expression
                interpreter.exec(wrappedExpression);

                // Get the result
                PyObject result = interpreter.get("result");
                return result.__tojava__(Object.class);
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (interpreter != null) {
                interpreter.close();
            }
        }
    }
}
