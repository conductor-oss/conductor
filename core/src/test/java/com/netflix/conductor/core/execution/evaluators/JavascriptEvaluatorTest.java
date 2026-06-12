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
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;

public class JavascriptEvaluatorTest {

    private final JavascriptEvaluator evaluator = new JavascriptEvaluator();

    @Test
    public void testBasicEvaluation() {
        Map<String, Object> input = new HashMap<>();
        input.put("value", 42);

        String expression = "$.value * 2";
        Object result = evaluator.evaluate(expression, input);

        assertEquals(84, ((Number) result).intValue());
    }

    @Test
    public void testDeepCopyProtection() {
        // This test verifies the deep copy protection feature from Enterprise
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> nested = new HashMap<>();
        nested.put("original", "value");
        input.put("data", nested);

        // Script that modifies the input
        String expression =
                """
                (function() {
                    $.data.newKey = 'new value';
                    return $.data.newKey;
                })()
                """;

        Object result = evaluator.evaluate(expression, input);
        assertEquals("new value", result);

        // Verify original input was NOT modified (deep copy protection)
        assertFalse(
                "Original input should not be modified due to deep copy",
                nested.containsKey("newKey"));
        assertEquals("value", nested.get("original"));
    }

    @Test
    public void testNestedObjectAccess() {
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> level1 = new HashMap<>();
        Map<String, Object> level2 = new HashMap<>();
        level2.put("value", "deep");
        level1.put("level2", level2);
        input.put("level1", level1);

        String expression = "$.level1.level2.value";
        Object result = evaluator.evaluate(expression, input);

        assertEquals("deep", result);
    }

    @Test
    public void testComplexExpression() {
        Map<String, Object> input = new HashMap<>();
        input.put("a", 10);
        input.put("b", 20);
        input.put("c", 30);

        String expression = "($.a + $.b) * $.c";
        Object result = evaluator.evaluate(expression, input);

        assertEquals(900, ((Number) result).intValue());
    }

    @Test
    public void testES6Features() {
        Map<String, Object> input = new HashMap<>();
        input.put("name", "Conductor");

        String expression =
                """
                (function() {
                    const greeting = 'Hello';
                    return `${greeting}, ${$.name}!`;
                })()
                """;

        Object result = evaluator.evaluate(expression, input);
        assertEquals("Hello, Conductor!", result);
    }

    @Test
    public void testArrayOperations() {
        Map<String, Object> input = new HashMap<>();
        input.put("numbers", new int[] {1, 2, 3, 4, 5});

        String expression = "$.numbers.filter(n => n > 2).map(n => n * 2)";
        Object result = evaluator.evaluate(expression, input);

        assertTrue(result instanceof java.util.List);
        java.util.List<?> resultList = (java.util.List<?>) result;
        assertEquals(3, resultList.size());
        assertEquals(6, ((Number) resultList.get(0)).intValue());
        assertEquals(8, ((Number) resultList.get(1)).intValue());
        assertEquals(10, ((Number) resultList.get(2)).intValue());
    }

    @Test
    public void testObjectReturn() {
        Map<String, Object> input = new HashMap<>();
        input.put("value", 42);

        String expression =
                """
                (function() {
                    return {
                        result: $.value,
                        doubled: $.value * 2,
                        message: 'success'
                    };
                })()
                """;

        Object result = evaluator.evaluate(expression, input);
        assertTrue(result instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(42, ((Number) resultMap.get("result")).intValue());
        assertEquals(84, ((Number) resultMap.get("doubled")).intValue());
        assertEquals("success", resultMap.get("message"));
    }

    @Test
    public void testNullSafety() {
        Map<String, Object> input = new HashMap<>();
        input.put("value", null);

        String expression = "$.value === null ? 'null value' : $.value";
        Object result = evaluator.evaluate(expression, input);

        assertEquals("null value", result);
    }
}
