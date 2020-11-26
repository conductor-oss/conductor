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

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class ScriptEvaluator {

    private static final ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

    private ScriptEvaluator() {
    }

    public static Boolean evalBool(String script, Object input) throws ScriptException {
        Object ret = eval(script, input);

        if (ret instanceof Boolean) {
            return ((Boolean) ret);
        } else if (ret instanceof Number) {
            return ((Number) ret).doubleValue() > 0;
        }
        return false;
    }

    public static Object eval(String script, Object input) throws ScriptException {
        Bindings bindings = engine.createBindings();
        bindings.put("$", input);
        return engine.eval(script, bindings);
    }
}
