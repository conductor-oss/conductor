/*
 * Copyright 2020 Netflix, Inc.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}
