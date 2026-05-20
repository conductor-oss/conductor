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

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;

public class TestScriptEval {

    @Test
    public void testScript() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> app = new HashMap<>();
        app.put("name", "conductor");
        app.put("version", 2.0);
        app.put("license", "Apache 2.0");

        payload.put("app", app);
        payload.put("author", "Netflix");
        payload.put("oss", true);

        String script1 = "$.app.name == 'conductor'"; // true
        String script2 = "$.version > 3"; // false
        String script3 = "$.oss"; // true
        String script4 = "$.author == 'me'"; // false

        assertTrue(ScriptEvaluator.evalBool(script1, payload));
        assertFalse(ScriptEvaluator.evalBool(script2, payload));
        assertTrue(ScriptEvaluator.evalBool(script3, payload));
        assertFalse(ScriptEvaluator.evalBool(script4, payload));
    }

    @Test
    public void testES6Support() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> app = new HashMap<>();
        app.put("name", "conductor");
        app.put("version", 2.0);
        app.put("license", "Apache 2.0");

        payload.put("app", app);
        payload.put("author", "Netflix");
        payload.put("oss", true);

        // GraalJS supports ES6 by default, no need for environment variable
        String script1 =
                """
                (function(){\s
                const variable = 1; // const support => es6\s
                return $.app.name == 'conductor';})();"""; // true

        assertTrue(ScriptEvaluator.evalBool(script1, payload));
    }

    @Test
    public void testArrayAndObjectHandling() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("numbers", new int[] {1, 2, 3, 4, 5});

        String script = "$.numbers.length > 3";
        assertTrue(ScriptEvaluator.evalBool(script, payload));

        String sumScript = "$.numbers.reduce((a, b) => a + b, 0)";
        Object result = ScriptEvaluator.eval(sumScript, payload);
        assertEquals(15, ((Number) result).intValue());
    }

    @Test
    public void testNullHandling() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("value", null);

        String script = "$.value == null";
        assertTrue(ScriptEvaluator.evalBool(script, payload));
    }
}
