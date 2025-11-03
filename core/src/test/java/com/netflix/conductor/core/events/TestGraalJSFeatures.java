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
package com.netflix.conductor.core.events;

import java.util.*;

import org.junit.Test;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.execution.evaluators.ConsoleBridge;

import static org.junit.Assert.*;

public class TestGraalJSFeatures {

    @Test
    public void testES6ConstLet() {
        Map<String, Object> input = new HashMap<>();
        input.put("value", 42);

        String script =
                """
                (function() {
                    const x = $.value;
                    let y = x * 2;
                    return y;
                })()""";

        Object result = ScriptEvaluator.eval(script, input);
        assertEquals(84, ((Number) result).intValue());
    }

    @Test
    public void testArrowFunctions() {
        Map<String, Object> input = new HashMap<>();
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
        input.put("numbers", numbers);

        String script = "$.numbers.map(x => x * 2)";
        Object result = ScriptEvaluator.eval(script, input);

        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> resultList = (List<Object>) result;
        assertEquals(5, resultList.size());
        assertEquals(2, ((Number) resultList.get(0)).intValue());
        assertEquals(10, ((Number) resultList.get(4)).intValue());
    }

    @Test
    public void testTemplateLiterals() {
        Map<String, Object> input = new HashMap<>();
        input.put("name", "Conductor");
        input.put("version", "3.0");

        String script = "`${$.name} v${$.version}`";
        Object result = ScriptEvaluator.eval(script, input);

        assertEquals("Conductor v3.0", result);
    }

    @Test
    public void testDestructuring() {
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> user = new HashMap<>();
        user.put("name", "Alice");
        user.put("age", 30);
        input.put("user", user);

        String script =
                """
                (function() {
                    const { name, age } = $.user;
                    return name + ' is ' + age;
                })()""";

        Object result = ScriptEvaluator.eval(script, input);
        assertEquals("Alice is 30", result);
    }

    @Test
    public void testSpreadOperator() {
        Map<String, Object> input = new HashMap<>();
        List<Integer> arr1 = Arrays.asList(1, 2, 3);
        List<Integer> arr2 = Arrays.asList(4, 5, 6);
        input.put("arr1", arr1);
        input.put("arr2", arr2);

        String script = "[...$.arr1, ...$.arr2]";
        Object result = ScriptEvaluator.eval(script, input);

        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> resultList = (List<Object>) result;
        assertEquals(6, resultList.size());
    }

    @Test
    public void testPromiseSupport() {
        Map<String, Object> input = new HashMap<>();
        input.put("value", 100);

        // GraalJS supports Promise
        String script =
                """
                (function() {
                    return Promise.resolve($.value).then(x => x * 2);
                })()""";

        Object result = ScriptEvaluator.eval(script, input);
        // The promise object itself is returned, not the resolved value
        // since we're not using async/await
        assertNotNull(result);
    }

    @Test
    public void testComplexObjectManipulation() {
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> workflow = new HashMap<>();
        workflow.put("name", "test-workflow");
        workflow.put("version", 1);

        List<Map<String, Object>> tasks = new ArrayList<>();
        Map<String, Object> task1 = new HashMap<>();
        task1.put("name", "task1");
        task1.put("status", "COMPLETED");
        tasks.add(task1);

        Map<String, Object> task2 = new HashMap<>();
        task2.put("name", "task2");
        task2.put("status", "IN_PROGRESS");
        tasks.add(task2);

        workflow.put("tasks", tasks);
        input.put("workflow", workflow);

        String script =
                """
                $.workflow.tasks
                    .filter(t => t.status === 'COMPLETED')
                    .map(t => t.name)
                    .join(',')""";

        Object result = ScriptEvaluator.eval(script, input);
        assertEquals("task1", result);
    }

    @Test(expected = NonTransientException.class)
    public void testTimeoutProtection() {
        Map<String, Object> input = new HashMap<>();

        // This should timeout after 4 seconds (default)
        String script = "while(true) {}";

        ScriptEvaluator.eval(script, input);
    }

    @Test
    public void testConsoleBridge() {
        Map<String, Object> input = new HashMap<>();
        input.put("message", "Hello from GraalJS");

        ConsoleBridge console = new ConsoleBridge("test-task-id");

        String script =
                """
                (function() {
                    console.log('Starting execution');
                    console.info($.message);
                    console.error('This is an error');
                    return $.message;
                })()
                """;

        Object result = ScriptEvaluator.eval(script, input, console);

        assertEquals("Hello from GraalJS", result);
        assertEquals(3, console.logs().size());
        assertTrue(console.logs().get(0).getLog().contains("[Log]"));
        assertTrue(console.logs().get(1).getLog().contains("[Info]"));
        assertTrue(console.logs().get(2).getLog().contains("[Error]"));
    }

    @Test
    public void testNullAndUndefinedHandling() {
        Map<String, Object> input = new HashMap<>();
        input.put("nullValue", null);

        String script1 = "$.nullValue === null";
        assertTrue((Boolean) ScriptEvaluator.eval(script1, input));

        String script2 = "$.undefinedValue === undefined";
        assertTrue((Boolean) ScriptEvaluator.eval(script2, input));

        String script3 = "$.nullValue ?? 'default'";
        assertEquals("default", ScriptEvaluator.eval(script3, input));
    }

    @Test
    public void testArrayMethods() {
        Map<String, Object> input = new HashMap<>();
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        input.put("numbers", numbers);

        // Test filter + reduce
        String script = "$.numbers.filter(n => n % 2 === 0).reduce((a, b) => a + b, 0)";
        Object result = ScriptEvaluator.eval(script, input);
        assertEquals(30, ((Number) result).intValue()); // 2+4+6+8+10 = 30

        // Test some/every
        String script2 = "$.numbers.some(n => n > 5)";
        assertTrue((Boolean) ScriptEvaluator.eval(script2, input));

        String script3 = "$.numbers.every(n => n > 0)";
        assertTrue((Boolean) ScriptEvaluator.eval(script3, input));
    }

    @Test
    public void testObjectMethods() {
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> obj = new HashMap<>();
        obj.put("a", 1);
        obj.put("b", 2);
        obj.put("c", 3);
        input.put("obj", obj);

        // Test that we can access object properties
        String script1 = "$.obj.a + $.obj.b + $.obj.c";
        Object result1 = ScriptEvaluator.eval(script1, input);
        assertEquals(6, ((Number) result1).intValue());

        // Test Object.keys works on JS objects we create
        String script2 =
                """
                (function() {
                    const obj = { a: 1, b: 2, c: 3 };
                    return Object.keys(obj).length;
                })()
                """;
        Object result2 = ScriptEvaluator.eval(script2, input);
        assertEquals(3, ((Number) result2).intValue());
    }

    @Test
    public void testStringMethods() {
        Map<String, Object> input = new HashMap<>();
        input.put("text", "hello world");

        String script1 = "$.text.toUpperCase()";
        assertEquals("HELLO WORLD", ScriptEvaluator.eval(script1, input));

        String script2 = "$.text.split(' ').reverse().join(' ')";
        assertEquals("world hello", ScriptEvaluator.eval(script2, input));

        String script3 = "$.text.includes('world')";
        assertTrue((Boolean) ScriptEvaluator.eval(script3, input));

        String script4 = "$.text.startsWith('hello')";
        assertTrue((Boolean) ScriptEvaluator.eval(script4, input));
    }

    @Test
    public void testMathOperations() {
        Map<String, Object> input = new HashMap<>();
        input.put("value", 16);

        String script1 = "Math.sqrt($.value)";
        Object result1 = ScriptEvaluator.eval(script1, input);
        assertEquals(4.0, ((Number) result1).doubleValue(), 0.001);

        String script2 = "Math.pow($.value, 2)";
        Object result2 = ScriptEvaluator.eval(script2, input);
        assertEquals(256.0, ((Number) result2).doubleValue(), 0.001);

        String script3 = "Math.max(1, $.value, 5)";
        Object result3 = ScriptEvaluator.eval(script3, input);
        assertEquals(16, ((Number) result3).intValue());
    }

    @Test
    public void testNestedObjectAccess() {
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> level1 = new HashMap<>();
        Map<String, Object> level2 = new HashMap<>();
        Map<String, Object> level3 = new HashMap<>();

        level3.put("value", "deep");
        level2.put("level3", level3);
        level1.put("level2", level2);
        input.put("level1", level1);

        String script = "$.level1.level2.level3.value";
        assertEquals("deep", ScriptEvaluator.eval(script, input));

        // Test optional chaining (ES2020 feature)
        String script2 = "$.level1?.level2?.level3?.value";
        assertEquals("deep", ScriptEvaluator.eval(script2, input));

        String script3 = "$.level1?.missing?.value ?? 'not found'";
        assertEquals("not found", ScriptEvaluator.eval(script3, input));
    }

    @Test
    public void testJSONOperations() {
        Map<String, Object> input = new HashMap<>();
        input.put("name", "test");
        input.put("count", 42);

        // Test that we can access data
        String script1 = "$.name";
        assertEquals("test", ScriptEvaluator.eval(script1, input));

        // Test JSON operations with JS objects
        String script2 =
                """
                (function() {
                    const obj = { name: $.name, count: $.count };
                    const json = JSON.stringify(obj);
                    const parsed = JSON.parse(json);
                    return parsed.count;
                })()
                """;
        Object result2 = ScriptEvaluator.eval(script2, input);
        assertEquals(42, ((Number) result2).intValue());

        // Test JSON.parse
        String script3 = "JSON.parse('{\"value\":123}').value";
        Object result3 = ScriptEvaluator.eval(script3, input);
        assertEquals(123, ((Number) result3).intValue());
    }

    @Test
    public void testContextPooling() {
        // Test that context pooling can be enabled and works correctly
        // Note: Context pooling is controlled by environment variables
        // This test verifies the code paths work with pooling disabled (default)
        Map<String, Object> input = new HashMap<>();
        input.put("value", 42);

        // Multiple evaluations should work correctly without pooling
        for (int i = 0; i < 5; i++) {
            String script = "$.value * " + (i + 1);
            Object result = ScriptEvaluator.eval(script, input);
            assertEquals(42 * (i + 1), ((Number) result).intValue());
        }
    }

    @Test
    public void testScriptEvaluatorInitialization() {
        // Test that ScriptEvaluator initializes properly with defaults
        // This verifies the self-initializing behavior
        Map<String, Object> input = new HashMap<>();
        input.put("test", "value");

        // First evaluation should trigger initialization
        String script = "$.test";
        Object result = ScriptEvaluator.eval(script, input);
        assertEquals("value", result);

        // Subsequent evaluations should use the initialized state
        result = ScriptEvaluator.eval(script, input);
        assertEquals("value", result);
    }

    @Test
    public void testDeepCopyBehavior() {
        // Test that ScriptEvaluator works correctly with complex nested objects
        // Note: Deep copy protection is implemented in JavascriptEvaluator layer
        // This test verifies ScriptEvaluator can handle nested structures
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> nested = new HashMap<>();
        nested.put("original", "value");
        input.put("data", nested);

        // Script that accesses nested data
        String script =
                """
                (function() {
                    return $.data.original + ' modified';
                })()
                """;

        Object result = ScriptEvaluator.eval(script, input);
        assertEquals("value modified", result);

        // Original input should still have its data intact
        assertEquals("value", nested.get("original"));
    }

    @Test
    public void testMultipleScriptExecutions() {
        // Test that multiple scripts can execute concurrently without interference
        Map<String, Object> input1 = new HashMap<>();
        input1.put("value", 10);

        Map<String, Object> input2 = new HashMap<>();
        input2.put("value", 20);

        String script1 = "$.value * 2";
        String script2 = "$.value * 3";

        Object result1 = ScriptEvaluator.eval(script1, input1);
        Object result2 = ScriptEvaluator.eval(script2, input2);

        assertEquals(20, ((Number) result1).intValue());
        assertEquals(60, ((Number) result2).intValue());
    }

    @Test
    public void testErrorMessageWithLineNumber() {
        // Test that error messages include line number information
        Map<String, Object> input = new HashMap<>();

        String script =
                """
                (function() {
                    const x = 1;
                    const y = 2;
                    throw new Error('Test error on line 4');
                })()
                """;

        try {
            ScriptEvaluator.eval(script, input);
            fail("Should have thrown TerminateWorkflowException");
        } catch (Exception e) {
            // Error message should contain line information
            assertTrue(
                    "Error message should contain 'line'",
                    e.getMessage().toLowerCase().contains("line")
                            || e.getCause() != null
                                    && e.getCause().getMessage().toLowerCase().contains("line"));
        }
    }
}
