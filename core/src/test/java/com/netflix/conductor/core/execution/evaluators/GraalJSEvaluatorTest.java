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

/**
 * Test for GraalJSEvaluator - verifies it works identically to JavascriptEvaluator since they use
 * the same underlying GraalJS engine.
 */
public class GraalJSEvaluatorTest {

    private final GraalJSEvaluator evaluator = new GraalJSEvaluator();

    @Test
    public void testBasicEvaluation() {
        Map<String, Object> input = new HashMap<>();
        input.put("value", 42);

        String expression = "$.value * 2";
        Object result = evaluator.evaluate(expression, input);

        assertEquals(84, ((Number) result).intValue());
    }

    @Test
    public void testES6Support() {
        Map<String, Object> input = new HashMap<>();
        input.put("name", "GraalJS");

        String expression =
                """
                (function() {
                    const greeting = 'Hello';
                    let engine = $.name;
                    return `${greeting}, ${engine}!`;
                })()
                """;

        Object result = evaluator.evaluate(expression, input);
        assertEquals("Hello, GraalJS!", result);
    }

    @Test
    public void testDeepCopyProtection() {
        // GraalJSEvaluator should have the same deep copy protection as JavascriptEvaluator
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> nested = new HashMap<>();
        nested.put("original", "value");
        input.put("data", nested);

        String expression =
                """
                (function() {
                    $.data.modified = 'new value';
                    return $.data.modified;
                })()
                """;

        Object result = evaluator.evaluate(expression, input);
        assertEquals("new value", result);

        // Verify original input was NOT modified
        assertFalse(
                "Original input should not be modified due to deep copy",
                nested.containsKey("modified"));
    }

    @Test
    public void testComplexObject() {
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        config.put("timeout", 4);
        config.put("retries", 3);
        input.put("config", config);

        String expression = "$.config.timeout * $.config.retries";
        Object result = evaluator.evaluate(expression, input);

        assertEquals(12, ((Number) result).intValue());
    }

    @Test
    public void testArrayOperations() {
        Map<String, Object> input = new HashMap<>();
        input.put("values", new int[] {10, 20, 30, 40, 50});

        String expression = "$.values.reduce((sum, val) => sum + val, 0)";
        Object result = evaluator.evaluate(expression, input);

        assertEquals(150, ((Number) result).intValue());
    }

    @Test
    public void testConditionalLogic() {
        Map<String, Object> input = new HashMap<>();
        input.put("status", "COMPLETED");

        String expression =
                """
                (function() {
                    if ($.status === 'COMPLETED') {
                        return { success: true, message: 'Task completed' };
                    } else {
                        return { success: false, message: 'Task pending' };
                    }
                })()
                """;

        Object result = evaluator.evaluate(expression, input);
        assertTrue(result instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue((Boolean) resultMap.get("success"));
        assertEquals("Task completed", resultMap.get("message"));
    }

    @Test
    public void testIdenticalToJavascriptEvaluator() {
        // Verify GraalJSEvaluator produces identical results to JavascriptEvaluator
        JavascriptEvaluator jsEval = new JavascriptEvaluator();
        GraalJSEvaluator graalEval = new GraalJSEvaluator();

        Map<String, Object> input = new HashMap<>();
        input.put("a", 5);
        input.put("b", 10);

        String expression = "$.a + $.b";

        Object jsResult = jsEval.evaluate(expression, input);
        Object graalResult = graalEval.evaluate(expression, input);

        assertEquals(
                "Results should be identical",
                ((Number) jsResult).intValue(),
                ((Number) graalResult).intValue());
    }
}
