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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.graalvm.polyglot.HostAccess;
import org.junit.BeforeClass;
import org.junit.Test;

import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.evaluators.ConsoleBridge;

import static org.junit.Assert.*;

public class TestScriptEvaluatorSandbox {

    @BeforeClass
    public static void configureRestrictedHostAccess() {
        ScriptEvaluator.configureHostAccess(true);
    }

    @Test
    public void testUnlistedHostMethodsCannotBeCalled() {
        HostCapability capability = new HostCapability();
        Map<String, Object> input = Map.of("nested", Map.of("capability", capability));

        assertThrows(
                TerminateWorkflowException.class,
                () -> ScriptEvaluator.eval("$.nested.capability.readSecret()", input));
        assertFalse(capability.invoked);
    }

    @Test
    public void testHostFieldsCannotBeRead() {
        assertEquals(
                "undefined",
                ScriptEvaluator.eval(
                        "typeof $.capability.secret", Map.of("capability", new HostCapability())));
    }

    @Test
    public void testCollectionInteropDoesNotExportUnlistedJavaMethods() {
        Map<String, Object> input =
                new LinkedHashMap<>(Map.of("items", new ArrayList<>(List.of(1, 2, 3))));

        // Data-only getters remain available without exposing arbitrary Java member access.
        assertEquals(List.of(1, 2, 3), ScriptEvaluator.eval("$.get('items')", input));
        assertEquals(1, ScriptEvaluator.eval("$.items.get(0)", input));
        assertThrows(
                TerminateWorkflowException.class,
                () -> ScriptEvaluator.eval("$.put('other', 4)", input));
        assertThrows(
                TerminateWorkflowException.class,
                () -> ScriptEvaluator.eval("$.getClass()", input));
        assertThrows(
                TerminateWorkflowException.class,
                () -> ScriptEvaluator.eval("$.items.clear()", input));
        assertThrows(
                TerminateWorkflowException.class,
                () -> ScriptEvaluator.eval("$.items.getClass()", input));
        assertFalse(input.containsKey("other"));
        assertEquals(List.of(1, 2, 3), input.get("items"));
    }

    @Test
    public void testReadOnlyCollectionInteropPreservesNestedData() {
        Map<String, Object> config =
                new LinkedHashMap<>(
                        Map.of(
                                "isError",
                                false,
                                "mcpServer",
                                "http://localhost/mcp",
                                "headers",
                                Map.of("Authorization", "test-token")));
        Map<String, Object> input = Map.of("config", config, "items", List.of(config));

        // Compiled agent scripts materialize Java maps through these data-only accessors.
        String script =
                """
                (function() {
                    function plain(value) {
                        if (value === null || typeof value !== 'object') return value;
                        if (Array.isArray(value)) return value.map(plain);
                        var result = {};
                        var entries = value.entrySet().iterator();
                        while (entries.hasNext()) {
                            var entry = entries.next();
                            result[String(entry.getKey())] = plain(entry.getValue());
                        }
                        return result;
                    }
                    return {config: plain($.config), items: plain($.items)};
                })()
                """;
        assertEquals(input, ScriptEvaluator.eval(script, input));
        assertEquals(1, ScriptEvaluator.eval("$.items.size()", input));
        assertEquals(
                List.of("config", "items"),
                ScriptEvaluator.eval("Array.from($.keySet()).sort()", input));
        assertEquals(2, ScriptEvaluator.eval("$.keySet().toArray().length", input));
        assertThrows(
                TerminateWorkflowException.class,
                () ->
                        ScriptEvaluator.eval(
                                "(function(){var i = $.config.entrySet().iterator(); i.next(); i.remove();})()",
                                input));
        assertThrows(
                TerminateWorkflowException.class,
                () ->
                        ScriptEvaluator.eval(
                                "$.config.entrySet().iterator().next().setValue('changed')",
                                input));
        assertThrows(
                TerminateWorkflowException.class,
                () -> ScriptEvaluator.eval("$.config.entrySet().iterator().getClass()", input));
    }

    @Test
    public void testCollectionSubclassesDoNotExportAdditionalMethods() {
        HostMap input = new HostMap();
        input.put("value", 42);

        assertEquals(42, ScriptEvaluator.eval("$.value", input));
        assertThrows(
                TerminateWorkflowException.class,
                () -> ScriptEvaluator.eval("$.readSecret()", input));
    }

    @Test
    public void testConsoleOnlyExportsLoggingMethods() {
        ConsoleBridge console = new ConsoleBridge("sandbox-task");

        ScriptEvaluator.eval(
                "console.log('log'); console.info('info'); console.error('error');",
                Map.of(),
                console);

        assertEquals(3, console.logs().size());
        assertEquals("sandbox-task", console.logs().getFirst().getTaskId());
        assertThrows(
                TerminateWorkflowException.class,
                () -> ScriptEvaluator.eval("console.logs()", Map.of(), console));
        assertThrows(
                TerminateWorkflowException.class,
                () -> ScriptEvaluator.eval("console.getClass()", Map.of(), console));
    }

    @Test
    public void testHostClassLookupAndScriptIoRemainDisabled() {
        for (String script :
                List.of(
                        "Java.type('java.lang.System')",
                        "Packages.java.lang.System.getProperty('user.home')",
                        "Polyglot.import('host')",
                        "load('file:///sandbox-test.js')",
                        "loadWithNewGlobal('file:///sandbox-test.js')",
                        "print('not permitted')")) {
            assertThrows(
                    script,
                    TerminateWorkflowException.class,
                    () -> ScriptEvaluator.eval(script, Map.of()));
        }
    }

    @Test
    public void testNestedDataAndArrayOperationsRemainAvailable() {
        Map<String, Object> input =
                Map.of(
                        "data",
                        Map.of("items", List.of(Map.of("value", 2), Map.of("value", 3))),
                        "array",
                        new int[] {4, 5},
                        "set",
                        new LinkedHashSet<>(List.of(6, 7)));

        assertEquals(
                List.of(4, 6, 4, 5, 6, 7),
                ScriptEvaluator.eval(
                        "[...$.data.items.map(item => item.value * 2), ...$.array, ...$.set]",
                        input));
    }

    @Test
    public void testDataRoundTripPreservesNullAndEmptyCollections() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("nullValue", null);
        input.put("items", Arrays.asList("text", 42, true, null, Map.of("nested", "value")));
        input.put("emptyMap", Map.of());
        input.put("emptyList", List.of());

        assertEquals(input, ScriptEvaluator.eval("$", input));
        assertNull(ScriptEvaluator.eval("$", null));
        assertEquals(List.of(2, 4), ScriptEvaluator.eval("$.map(n => n * 2)", List.of(1, 2)));
    }

    @Test
    public void testHostAccessPolicyCannotChangeAfterInitialization() {
        assertEquals(1, ScriptEvaluator.eval("1", Map.of()));
        assertThrows(IllegalStateException.class, () -> ScriptEvaluator.configureHostAccess(false));
    }

    @Test
    public void testConcurrentEvaluationsKeepTheSameHostBoundary() throws Exception {
        // Initialize before launching callers so the test exercises concurrent evaluation, not
        // setup.
        ScriptEvaluator.eval("1", Map.of());
        try (var executor = Executors.newFixedThreadPool(4)) {
            var results =
                    executor.invokeAll(
                            IntStream.range(0, 12)
                                    .<Callable<Object>>mapToObj(
                                            value -> () -> evaluateWithHostBoundary(value))
                                    .toList());
            for (int i = 0; i < results.size(); i++) {
                assertEquals(i * 2, results.get(i).get());
            }
        }
    }

    private Object evaluateWithHostBoundary(int value) {
        HostCapability capability = new HostCapability();
        Map<String, Object> input = Map.of("value", value, "capability", capability);
        assertThrows(
                TerminateWorkflowException.class,
                () -> ScriptEvaluator.eval("$.capability.readSecret()", input));
        assertFalse(capability.invoked);
        return ScriptEvaluator.eval("$.value * 2", input);
    }

    public static class HostCapability {
        public final String secret = "host-only";
        private boolean invoked;

        // Even Graal's generic export annotation must not expand the evaluator's explicit
        // allowlist.
        @HostAccess.Export
        public String readSecret() {
            invoked = true;
            return secret;
        }
    }

    public static class HostMap extends LinkedHashMap<String, Object> {
        public String readSecret() {
            return "host-only";
        }
    }
}
