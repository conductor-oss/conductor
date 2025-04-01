/*
 * Copyright 2022 Conductor Authors.
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
import javax.script.ScriptException;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

public class ScriptEvaluator {

    private static ScriptEngine engine;

    private ScriptEvaluator() {}

    /**
     * Evaluates the script with the help of input provided but converts the result to a boolean
     * value. Set environment variable CONDUCTOR_NASHORN_ES6_ENABLED=true for Nashorn ES6 support.
     *
     * @param script Script to be evaluated.
     * @param input Input parameters.
     * @throws ScriptException
     * @return True or False based on the result of the evaluated expression.
     */
    public static Boolean evalBool(String script, Object input) throws ScriptException {
        return toBoolean(eval(script, input));
    }

    /**
     * Evaluates the script with the help of input provided. Set environment variable
     * CONDUCTOR_NASHORN_ES6_ENABLED=true for Nashorn ES6 support.
     *
     * @param script Script to be evaluated.
     * @param input Input parameters.
     * @throws ScriptException
     * @return Generic object, the result of the evaluated expression.
     */
    public static Object eval(String script, Object input) throws ScriptException {
        initEngine(false);
        Bindings bindings = engine.createBindings();
        bindings.put("$", input);
        return engine.eval(script, bindings);
    }

    // to mock in a test
    public static String getEnv(String name) {
        return System.getenv(name);
    }

    public static void initEngine(boolean reInit) {
        if (engine == null || reInit) {
            NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
            if ("true".equalsIgnoreCase(getEnv("CONDUCTOR_NASHORN_ES6_ENABLED"))) {
                engine = factory.getScriptEngine("--language=es6", "--no-java");
            } else {
                engine = factory.getScriptEngine("--no-java");
            }
        }
        if (engine == null) {
            throw new RuntimeException(
                    "missing nashorn engine.  Ensure you are running supported JVM");
        }
    }

    /**
     * Converts a generic object into boolean value. Checks if the Object is of type Boolean and
     * returns the value of the Boolean object. Checks if the Object is of type Number and returns
     * True if the value is greater than 0.
     *
     * @param input Generic object that will be inspected to return a boolean value.
     * @return True or False based on the input provided.
     */
    public static Boolean toBoolean(Object input) {
        if (input instanceof Boolean) {
            return ((Boolean) input);
        } else if (input instanceof Number) {
            return ((Number) input).doubleValue() > 0;
        }
        return false;
    }
}
