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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.core.exception.TerminateWorkflowException;

import static org.junit.Assert.assertEquals;

public class PythonEvaluatorTest {

    private Evaluator pythonEvaluator = new PythonEvaluator();
    private static final String POLICY_VIOLATION_MESSAGE =
            "Script execution is restricted due to policy violations.";

    @Test
    public void testImportsRestrictionOs() {
        String testPythonScript =
                "import os\n"
                        + "os.system('rm -rf /')\n"
                        + "print(\"Test statement\")"; // Malicious: It deletes all files and
        // directories in the root filesystem
        Object result = null;
        try {
            result = pythonEvaluator.evaluate(testPythonScript, Map.of());
        } catch (TerminateWorkflowException terminateWorkflowException) {
            assertEquals(terminateWorkflowException.getMessage(), POLICY_VIOLATION_MESSAGE);
        }
    }

    @Test
    public void testImportsRestrictionSys() {
        String testPythonScript = "import sys\n" + "import subprocess";
        Object result = null;
        try {
            result = pythonEvaluator.evaluate(testPythonScript, Map.of());
        } catch (TerminateWorkflowException terminateWorkflowException) {
            assertEquals(terminateWorkflowException.getMessage(), POLICY_VIOLATION_MESSAGE);
        }
    }

    @Test
    public void testRestrictedInBuiltFunctionEval() {
        String testPythonScript =
                "print(eval('1+1'))"; // Eval is a malicious function and code lead to remote code
        // executions
        Object result = null;
        try {
            result = pythonEvaluator.evaluate(testPythonScript, Map.of());
        } catch (TerminateWorkflowException terminateWorkflowException) {
            assertEquals(terminateWorkflowException.getMessage(), POLICY_VIOLATION_MESSAGE);
        }
    }

    @Test
    public void testRestrictedInBuiltFunctionExec() {
        String testPythonScript =
                "code = '''\n"
                        + "def add(a, b):\n"
                        + "    return a + b\n"
                        + "\n"
                        + "result = add(1, 1)\n"
                        + "print(result)'''\n"
                        + "\n"
                        + "exec(code)"; // Exec is a malicious function and code lead to remote code
        // executions
        Object result = null;
        try {
            result = pythonEvaluator.evaluate(testPythonScript, Map.of());
        } catch (TerminateWorkflowException terminateWorkflowException) {
            assertEquals(terminateWorkflowException.getMessage(), POLICY_VIOLATION_MESSAGE);
        }
    }

    @Test
    public void testRestrictedInBuiltFunctionOpen() {
        String testPythonScript =
                "with open('example.txt', 'r') as file:\n"
                        + "    content = file.read()\n"
                        + "    print(content)\n"; // File crud operations should be restricted
        Object result = null;
        try {
            result = pythonEvaluator.evaluate(testPythonScript, Map.of());
        } catch (TerminateWorkflowException terminateWorkflowException) {
            assertEquals(terminateWorkflowException.getMessage(), POLICY_VIOLATION_MESSAGE);
        }
    }

    @Test
    public void testSimpleAddFunction() {
        String testPythonScript =
                "def add(a, b):\n"
                        + "    return a+b\n"
                        + "\n"
                        + "sum = add(10, 2)\n"
                        + "sum"; // File crud operations should be restricted
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("evaluatorType", "python");
        inputs.put("expression", testPythonScript);
        inputs.put("outputIdentifier", "sum");
        Object result = pythonEvaluator.evaluate(testPythonScript, inputs);
        assertEquals(result.toString(), "12");
    }

    @Test
    public void testPythonLoop() {
        String testPythonScript =
                "arr = [1, 2, 3, 4, 5]\n"
                        + "sumOfEven = 0\n"
                        + "for i in range(len(arr)):\n"
                        + "    if arr[i] % 2 == 0:\n"
                        + "        sumOfEven = sumOfEven + arr[i]\n"
                        + "sumOfEven";
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("evaluatorType", "python");
        inputs.put("expression", testPythonScript);
        inputs.put("outputIdentifier", "sumOfEven");
        Object result = pythonEvaluator.evaluate(testPythonScript, inputs);
        assertEquals(result.toString(), "6");
    }

    @Test
    public void testReplacingIntegerParameters() {
        String testPythonScript =
                "def isEven():\n"
                        + "    num = $.num\n"
                        + "    return num % 2 == 0\n"
                        + "\n"
                        + "evenFlag = isEven();\n"
                        + "evenFlag";
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("evaluatorType", "python");
        inputs.put("num", 2); // $.num is a parameter in above test script
        inputs.put("expression", testPythonScript);
        inputs.put("outputIdentifier", "evenFlag");
        Object result = pythonEvaluator.evaluate(testPythonScript, inputs);
        assertEquals(result.toString(), "True"); // True is boolean representation in python
    }

    @Test
    public void testReplacingArrays() {
        String testPythonScript =
                "arr = $.arr\n"
                        + "sum = 0\n"
                        + "for item in arr:\n"
                        + "    sum = sum + item\n"
                        + "sum";
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("evaluatorType", "python");
        inputs.put("arr", List.of(1, 2, 3, 4)); // $.arr is an array parameter in above test script
        inputs.put("expression", testPythonScript);
        inputs.put("outputIdentifier", "sum");
        Object result = pythonEvaluator.evaluate(testPythonScript, inputs);
        assertEquals(result.toString(), "10");
    }

    @Test
    public void testReplacingStrings() {
        String testPythonScript =
                "name = \"$.name\"\n"
                        + // ' We used $.name inside "" since we directly replace variable with
                        // value '
                        "name";
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("evaluatorType", "python");
        inputs.put("name", "Foo"); // $.name is a parameter in above test script
        inputs.put("expression", testPythonScript);
        inputs.put("outputIdentifier", "name");
        Object result = pythonEvaluator.evaluate(testPythonScript, inputs);
        assertEquals(result.toString(), "Foo");
    }

    @Test
    public void testReplacingNestedObjects() {
        String testPythonScript =
                "def greet():\n"
                        + "    name = \"$.jsonObj.name\"\n"
                        + "    age = $.jsonObj.age\n"
                        + "    return \"Greetings \" + name + \" having age = \" + str(age)\n"
                        + "\n"
                        + "message = greet()\n"
                        + "message";
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("evaluatorType", "python");
        inputs.put("jsonObj", Map.of("name", "John", "age", 27));
        inputs.put("expression", testPythonScript);
        inputs.put("outputIdentifier", "message");
        Object result = pythonEvaluator.evaluate(testPythonScript, inputs);
        assertEquals(result.toString(), "Greetings John having age = 27");
    }

    @Test
    public void testReplacingNestedObjectWithList() {
        String testPythonScript =
                "def greet():\n"
                        + "    name = $.jsonObj.var[0]\n"
                        + // In case of list we don't wrap variable inside ""
                        "    return \"Greetings \" + name\n"
                        + "\n"
                        + "message = greet()\n"
                        + "message";
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("evaluatorType", "python");
        inputs.put("jsonObj", Map.of("var", List.of("Foo", "John")));
        inputs.put("expression", testPythonScript);
        inputs.put("outputIdentifier", "message");
        Object result = pythonEvaluator.evaluate(testPythonScript, inputs);
        assertEquals(result.toString(), "Greetings Foo");
    }
}
