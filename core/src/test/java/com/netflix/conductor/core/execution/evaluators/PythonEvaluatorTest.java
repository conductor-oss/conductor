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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Test;

import com.netflix.conductor.core.exception.TerminateWorkflowException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PythonEvaluatorTest {

    private static final PythonEvaluator EVALUATOR = new PythonEvaluator();

    @AfterClass
    public static void closeEvaluator() {
        EVALUATOR.shutdown();
    }

    @Test
    public void testBasicEvaluationWithInputBindings() {
        Object result = EVALUATOR.evaluate("return value * 2", Map.of("value", 21));

        assertEquals(42, ((Number) result).intValue());
    }

    @Test
    public void testInputIdentifierCanStillBeReassigned() {
        Object result = EVALUATOR.evaluate("value = value + 1\nreturn value", Map.of("value", 41));

        assertEquals(42, ((Number) result).intValue());
    }

    @Test
    public void testReservedWordInputKeyCanBeReadAsData() {
        Object result =
                EVALUATOR.evaluate("return __conductor_inputs__['class']", Map.of("class", "v1"));

        assertEquals("v1", result);
    }

    @Test
    public void testInvalidIdentifierInputKeyCanBeReadAsData() {
        String key = "bad-key'].__import__('os').system('true') #";
        Object result =
                EVALUATOR.evaluate(
                        "return __conductor_inputs__[\"bad-key'].__import__('os').system('true') #\"]",
                        Map.of(key, 17));

        assertEquals(17, ((Number) result).intValue());
    }

    @Test(expected = TerminateWorkflowException.class)
    public void testRejectsNullInputMapKey() {
        Map<Object, Object> input = new HashMap<>();
        input.put(null, "value");

        EVALUATOR.evaluate("return 1", input);
    }

    @Test(expected = TerminateWorkflowException.class)
    public void testRejectsNonStringInputMapKey() {
        Map<Object, Object> input = new HashMap<>();
        input.put(1, "value");

        EVALUATOR.evaluate("return 1", input);
    }

    @Test
    public void testEmptyInputMap() {
        Object result = EVALUATOR.evaluate("return 42", Map.of());

        assertEquals(42, ((Number) result).intValue());
    }

    @Test
    public void testNestedMapInput() {
        Map<String, Object> input = Map.of("outer", Map.of("inner", Map.of("value", 23)));

        Object result = EVALUATOR.evaluate("return outer['inner']['value'] * 2", input);

        assertEquals(46, ((Number) result).intValue());
    }

    @Test
    public void testNestedInputMutationDoesNotChangeOriginalJavaMap() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("value", 23);
        Map<String, Object> input = Map.of("nested", nested);

        Object result =
                EVALUATOR.evaluate("nested['changed'] = 42\nreturn nested['changed']", input);

        assertEquals(42, ((Number) result).intValue());
        assertFalse("Guest mutations must not change host input", nested.containsKey("changed"));
    }

    @Test
    public void testNestedInputIsAStandardPythonCollection() {
        Object result =
                EVALUATOR.evaluate(
                        "return nested.__class__.__module__",
                        Map.of("nested", Map.of("value", 23)));

        assertEquals("builtins", result);
    }

    @Test
    public void testNestedListInput() {
        Map<String, Object> input = Map.of("values", List.of(1, List.of(2, 3)));

        Object result = EVALUATOR.evaluate("return values[1][0] + values[0]", input);

        assertEquals(3, ((Number) result).intValue());
    }

    @Test
    public void testPrimitiveArrayInput() {
        Object result =
                EVALUATOR.evaluate(
                        "return values[0] + values[2]", Map.of("values", new int[] {4, 9, 12}));

        assertEquals(16, ((Number) result).intValue());
    }

    @Test
    public void testBooleanInputsAndResult() {
        Object result =
                EVALUATOR.evaluate(
                        "return enabled and not disabled",
                        Map.of("enabled", true, "disabled", false));

        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testMultipleInputValues() {
        Object result =
                EVALUATOR.evaluate(
                        "return first + second * third",
                        Map.of("first", 4, "second", 5, "third", 6));

        assertEquals(34, ((Number) result).intValue());
    }

    @Test
    public void testNullInputValue() {
        Map<String, Object> input = new HashMap<>();
        input.put("value", null);

        Object result = EVALUATOR.evaluate("return value is None", input);

        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testPythonCollectionResultConversion() {
        Object result = EVALUATOR.evaluate("return {'answer': [42]}", Map.of());

        assertTrue(result instanceof Map);
        Object answer = ((Map<?, ?>) result).get("answer");
        assertTrue(answer instanceof List);
        assertEquals(42, ((Number) ((List<?>) answer).get(0)).intValue());
    }

    @Test(expected = TerminateWorkflowException.class)
    public void testRejectsUnsupportedHostObjectInput() {
        EVALUATOR.evaluate("return 1", Map.of("hostObject", new Object()));
    }

    @Test(expected = TerminateWorkflowException.class)
    public void testCannotStartHostProcess() {
        EVALUATOR.evaluate("import os\nos.system('true')\nreturn value", Map.of("value", 1));
    }

    @Test(expected = TerminateWorkflowException.class)
    public void testCannotLookUpJavaHostClasses() {
        EVALUATOR.evaluate(
                "from java.lang import System\nreturn System.getProperty('user.home')", Map.of());
    }

    @Test(expected = TerminateWorkflowException.class)
    public void testCannotReadEnvironmentVariables() {
        EVALUATOR.evaluate("import os\nreturn os.environ['PATH']", Map.of());
    }

    @Test
    public void testCannotReadHostFile() throws Exception {
        Path file = Files.createTempFile("python-evaluator-sandbox", ".txt");
        try {
            Files.writeString(file, "test-only data");
            EVALUATOR.evaluate("return open(path).read(1)", Map.of("path", file.toString()));
            fail("Python evaluation should not be able to read host files");
        } catch (TerminateWorkflowException expected) {
            assertFalse(expected.getMessage().isEmpty());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test(expected = TerminateWorkflowException.class)
    public void testCannotCreateHostThreads() {
        EVALUATOR.evaluate(
                "import threading\nthreading.Thread(target=lambda: None).start()\nreturn True",
                Map.of());
    }

    @Test(expected = TerminateWorkflowException.class)
    public void testCannotAccessAnotherPolyglotContext() {
        EVALUATOR.evaluate("import polyglot\nreturn polyglot.eval('python', '1')", Map.of());
    }

    @Test(timeout = 10000, expected = TerminateWorkflowException.class)
    public void testInfiniteLoopIsInterrupted() {
        EVALUATOR.evaluate("while True:\n    pass\nreturn value", Map.of("value", 1));
    }

    @Test
    public void testConcurrentEvaluationsDoNotRejectOrdinaryLoad() throws Exception {
        int evaluationCount = 10;
        CountDownLatch ready = new CountDownLatch(evaluationCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(evaluationCount);
        List<Future<Object>> evaluations = new ArrayList<>();

        try {
            for (int i = 0; i < evaluationCount; i++) {
                final int input = i;
                evaluations.add(
                        callers.submit(
                                () -> {
                                    ready.countDown();
                                    if (!start.await(5, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException(
                                                "Concurrent evaluation start timed out");
                                    }
                                    return EVALUATOR.evaluate(
                                            "return value * 2", Map.of("value", input));
                                }));
            }

            assertTrue("All evaluation threads should be ready", ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (int i = 0; i < evaluationCount; i++) {
                assertEquals(
                        i * 2, ((Number) evaluations.get(i).get(10, TimeUnit.SECONDS)).intValue());
            }
        } finally {
            start.countDown();
            callers.shutdownNow();
            assertTrue(
                    "Test executor should terminate",
                    callers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 20000)
    public void testExecutorSaturationIsReportedExplicitly() throws Exception {
        int submissionCount = 21;
        CountDownLatch ready = new CountDownLatch(submissionCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(submissionCount);
        List<Future<Boolean>> submissions = new ArrayList<>();

        try {
            for (int i = 0; i < submissionCount; i++) {
                submissions.add(
                        callers.submit(
                                () -> {
                                    ready.countDown();
                                    if (!start.await(5, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException(
                                                "Saturation test start timed out");
                                    }
                                    try {
                                        EVALUATOR.evaluate(
                                                "while True:\n    pass\nreturn value",
                                                Map.of("value", 1));
                                        return false;
                                    } catch (TerminateWorkflowException e) {
                                        return "Python evaluator is overloaded or shutting down"
                                                .equals(e.getMessage());
                                    }
                                }));
            }

            assertTrue("All saturation callers should be ready", ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int rejectedForCapacity = 0;
            for (Future<Boolean> submission : submissions) {
                if (submission.get(15, TimeUnit.SECONDS)) {
                    rejectedForCapacity++;
                }
            }
            assertTrue(
                    "Full executor capacity should produce an explicit overload result",
                    rejectedForCapacity > 0);
        } finally {
            start.countDown();
            callers.shutdownNow();
            assertTrue(
                    "Saturation test callers should terminate",
                    callers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testEvaluatorCanBeShutDownAndRejectsNewEvaluations() {
        PythonEvaluator evaluator = new PythonEvaluator();
        evaluator.shutdown();

        try {
            evaluator.evaluate("return value", Map.of("value", 1));
            fail("A stopped evaluator should reject new evaluations");
        } catch (TerminateWorkflowException expected) {
            assertEquals("Python evaluator is shutting down", expected.getMessage());
        }
    }

    @Test
    public void testRepeatedEvaluatorLifecycles() {
        for (int i = 0; i < 2; i++) {
            PythonEvaluator evaluator = new PythonEvaluator();
            try {
                assertEquals(
                        42,
                        ((Number) evaluator.evaluate("return value * 2", Map.of("value", 21)))
                                .intValue());
            } finally {
                evaluator.shutdown();
            }
        }
    }
}
