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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ScriptEvaluator {

    private static ScriptEngine engine;
    private static final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

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
     * <p>Normalizes JavaScript engine wrapper objects (ScriptObjectMirror) to plain Java types to
     * ensure compatibility with instanceof checks and JSON serialization.
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
        Object result = engine.eval(script, bindings);
        return normalizeJavaScriptResult(result);
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

    /**
     * Normalizes JavaScript engine wrapper objects to plain Java types. Engine-agnostic approach
     * using reflection to work with both Nashorn and GraalJS.
     *
     * <p>This ensures JavaScript objects become plain Java Maps and arrays become Java Lists,
     * making them compatible with instanceof checks and JSON serialization.
     *
     * @param result The raw result from the script engine
     * @return A normalized plain Java object (Map, List, or primitive)
     */
    private static Object normalizeJavaScriptResult(Object result) {
        if (result == null) {
            return null;
        }

        // Use reflection to detect JavaScript arrays (engine-agnostic)
        try {
            Method isArrayMethod = result.getClass().getMethod("isArray");
            Boolean isArray = (Boolean) isArrayMethod.invoke(result);
            if (isArray != null && isArray) {
                List<Object> list = new ArrayList<>();
                Method valuesMethod = result.getClass().getMethod("values");
                Object valuesCollection = valuesMethod.invoke(result);
                if (valuesCollection instanceof Iterable) {
                    for (Object value : (Iterable<?>) valuesCollection) {
                        list.add(normalizeJavaScriptResult(value));
                    }
                }
                return list;
            }
        } catch (Exception e) {
            // Not a JavaScript array
        }

        // Serialize/deserialize to convert JavaScript objects to plain Java types
        try {
            String json = objectMapper.writeValueAsString(result);
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return result;
        }
    }
}
