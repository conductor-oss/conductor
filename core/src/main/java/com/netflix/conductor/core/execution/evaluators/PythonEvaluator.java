/*
 * Copyright 2024 Conductor Authors.
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

import java.lang.reflect.Array;
import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.exception.TerminateWorkflowException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;

@Component(PythonEvaluator.NAME)
public class PythonEvaluator implements Evaluator {
    public static final String NAME = "python";
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonEvaluator.class);
    private static final int MAX_EXECUTION_SECONDS = 4;
    private static final int MAX_PENDING_EVALUATIONS = 10;
    private static final int MAX_VALUE_NESTING_DEPTH = 100;
    private static final Pattern PYTHON_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> PYTHON_KEYWORDS =
            Set.of(
                    "False",
                    "None",
                    "True",
                    "and",
                    "as",
                    "assert",
                    "async",
                    "await",
                    "break",
                    "class",
                    "continue",
                    "def",
                    "del",
                    "elif",
                    "else",
                    "except",
                    "finally",
                    "for",
                    "from",
                    "global",
                    "if",
                    "import",
                    "in",
                    "is",
                    "lambda",
                    "nonlocal",
                    "not",
                    "or",
                    "pass",
                    "raise",
                    "return",
                    "try",
                    "while",
                    "with",
                    "yield",
                    "__debug__");
    private static final String INPUTS_BINDING = "__conductor_inputs__";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_CONCURRENT_EVALUATIONS =
            Math.max(10, Math.min(16, Runtime.getRuntime().availableProcessors()));
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();

    private final Engine engine = Engine.newBuilder().build();
    private final ThreadPoolExecutor executor = createExecutor();
    private final Set<Context> activeContexts = ConcurrentHashMap.newKeySet();
    private final Object lifecycleMonitor = new Object();
    private boolean shuttingDown;
    private boolean engineClosed;

    public PythonEvaluator() {
        try {
            try (Context context = createContext()) {
                context.eval("python", "pass");
            }
        } catch (RuntimeException e) {
            shutdown();
            throw e;
        }
    }

    @Override
    public Object evaluate(String expression, Object input) {
        if (!(input instanceof Map)) {
            return null;
        }

        Context context = null;
        Future<?> evaluation = null;
        try {
            String wrappedExpression = wrapExpression(expression, (Map<?, ?>) input);
            synchronized (lifecycleMonitor) {
                if (shuttingDown) {
                    throw new TerminateWorkflowException("Python evaluator is shutting down");
                }
                context = createContext();
                activeContexts.add(context);
                Context evaluationContext = context;
                try {
                    evaluation =
                            executor.submit(
                                    () -> evaluationContext.eval("python", wrappedExpression));
                } catch (RejectedExecutionException e) {
                    throw new TerminateWorkflowException(
                            "Python evaluator is overloaded or shutting down");
                }
            }
            evaluation.get(MAX_EXECUTION_SECONDS, TimeUnit.SECONDS);

            Value result = context.getBindings("python").getMember("result");
            return toJavaValue(result);
        } catch (TimeoutException e) {
            cancelEvaluation(evaluation, context);
            throw new TerminateWorkflowException(
                    "Python evaluation exceeded the maximum execution time of "
                            + MAX_EXECUTION_SECONDS
                            + " seconds");
        } catch (InterruptedException e) {
            cancelEvaluation(evaluation, context);
            Thread.currentThread().interrupt();
            throw new TerminateWorkflowException("Python evaluation was interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            LOGGER.error(
                    "Error evaluating Python expression: {}",
                    cause == null ? e.getMessage() : cause.getMessage(),
                    cause == null ? e : cause);
            throw new TerminateWorkflowException(
                    cause == null ? e.getMessage() : cause.getMessage());
        } catch (TerminateWorkflowException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Error evaluating Python expression: {}", e.getMessage(), e);
            throw new TerminateWorkflowException(e.getMessage());
        } finally {
            if (context != null) {
                closeContext(context);
            }
        }
    }

    private static ThreadPoolExecutor createExecutor() {
        ThreadPoolExecutor executor =
                new ThreadPoolExecutor(
                        MAX_CONCURRENT_EVALUATIONS,
                        MAX_CONCURRENT_EVALUATIONS,
                        60L,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(MAX_PENDING_EVALUATIONS),
                        runnable -> {
                            Thread thread =
                                    new Thread(
                                            runnable,
                                            "conductor-python-evaluator-"
                                                    + THREAD_NUMBER.incrementAndGet());
                            thread.setDaemon(true);
                            return thread;
                        },
                        new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static String wrapExpression(String expression, Map<?, ?> inputMap)
            throws JsonProcessingException {
        Map<String, Object> safeInputs = sanitizeInputMap(inputMap);

        List<String> identifiers = new ArrayList<>();
        for (String key : safeInputs.keySet()) {
            if (isBindableIdentifier(key)) {
                identifiers.add(key);
            }
        }

        StringBuilder wrappedExpression =
                new StringBuilder()
                        .append(INPUTS_BINDING)
                        .append(" = ")
                        .append(toPythonLiteral(safeInputs))
                        .append("\n");

        for (String identifier : identifiers) {
            wrappedExpression
                    .append(identifier)
                    .append(" = ")
                    .append(INPUTS_BINDING)
                    .append("['")
                    .append(identifier)
                    .append("']\n");
        }

        wrappedExpression.append("def evaluate():\n");
        if (!identifiers.isEmpty()) {
            wrappedExpression
                    .append("    global ")
                    .append(String.join(", ", identifiers))
                    .append("\n");
        }
        for (String line : expression.split("\\n")) {
            wrappedExpression.append("    ").append(line).append("\n");
        }
        wrappedExpression.append("\nresult = evaluate()");
        return wrappedExpression.toString();
    }

    // JSON-escape all data strings so input keys and values remain Python literals, never code.
    private static String toPythonLiteral(Object value) throws JsonProcessingException {
        if (value == null) {
            return "None";
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue ? "True" : "False";
        }
        if (value instanceof String || value instanceof Character) {
            return OBJECT_MAPPER.writeValueAsString(value.toString());
        }
        if (value instanceof Double doubleValue && !Double.isFinite(doubleValue)) {
            throw new IllegalArgumentException("Python inputs cannot contain non-finite numbers");
        }
        if (value instanceof Float floatValue && !Float.isFinite(floatValue)) {
            throw new IllegalArgumentException("Python inputs cannot contain non-finite numbers");
        }
        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof java.math.BigInteger
                || value instanceof java.math.BigDecimal
                || value instanceof Float
                || value instanceof Double) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder literal = new StringBuilder("{");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (literal.length() > 1) {
                    literal.append(", ");
                }
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException(
                            "Python input map keys must be non-null strings");
                }
                literal.append(OBJECT_MAPPER.writeValueAsString(key))
                        .append(": ")
                        .append(toPythonLiteral(entry.getValue()));
            }
            return literal.append("}").toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder literal = new StringBuilder("[");
            for (Object item : list) {
                if (literal.length() > 1) {
                    literal.append(", ");
                }
                literal.append(toPythonLiteral(item));
            }
            return literal.append("]").toString();
        }
        throw new IllegalArgumentException(
                "Unsupported Python input value type: " + value.getClass().getName());
    }

    private static Object toJavaValue(Value value) {
        return toJavaValue(value, 0);
    }

    private static Object toJavaValue(Value value, int depth) {
        if (depth > MAX_VALUE_NESTING_DEPTH) {
            throw new IllegalArgumentException("Python result nesting is too deep");
        }
        if (value.isNull()) {
            return null;
        }
        if (value.isHostObject()) {
            throw new IllegalArgumentException("Python results cannot expose host objects");
        }
        if (value.hasArrayElements()) {
            List<Object> result = new ArrayList<>((int) value.getArraySize());
            for (long i = 0; i < value.getArraySize(); i++) {
                result.add(toJavaValue(value.getArrayElement(i), depth + 1));
            }
            return result;
        }
        if (value.hasHashEntries()) {
            Map<Object, Object> result = new LinkedHashMap<>();
            Value keys = value.getHashKeysIterator();
            while (keys.hasIteratorNextElement()) {
                Object key = toJavaValue(keys.getIteratorNextElement(), depth + 1);
                result.put(key, toJavaValue(value.getHashValue(key), depth + 1));
            }
            return result;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isNumber()) {
            return value.as(Object.class);
        }
        throw new IllegalArgumentException("Unsupported Python result type");
    }

    private static boolean isBindableIdentifier(String key) {
        return !INPUTS_BINDING.equals(key)
                && PYTHON_IDENTIFIER.matcher(key).matches()
                && !PYTHON_KEYWORDS.contains(key);
    }

    private static Map<String, Object> sanitizeInputMap(Map<?, ?> inputMap) {
        Map<String, Object> safeInputs = new LinkedHashMap<>();
        IdentityHashMap<Object, Boolean> ancestors = new IdentityHashMap<>();
        ancestors.put(inputMap, Boolean.TRUE);
        for (Map.Entry<?, ?> entry : inputMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(
                        "Python input map keys must be non-null strings");
            }
            safeInputs.put(key, sanitizeValue(entry.getValue(), ancestors, 1));
        }
        return safeInputs;
    }

    private static Object sanitizeValue(
            Object value, IdentityHashMap<Object, Boolean> ancestors, int depth) {
        if (depth > MAX_VALUE_NESTING_DEPTH) {
            throw new IllegalArgumentException("Python input nesting is too deep");
        }
        if (value == null
                || value instanceof String
                || value instanceof Character
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }

        if (ancestors.containsKey(value)) {
            throw new IllegalArgumentException("Python inputs cannot contain cyclic values");
        }
        ancestors.put(value, Boolean.TRUE);
        try {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> sanitized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        throw new IllegalArgumentException(
                                "Python input map keys must be non-null strings");
                    }
                    sanitized.put(key, sanitizeValue(entry.getValue(), ancestors, depth + 1));
                }
                return sanitized;
            }
            if (value instanceof List<?> list) {
                List<Object> sanitized = new ArrayList<>(list.size());
                for (Object item : list) {
                    sanitized.add(sanitizeValue(item, ancestors, depth + 1));
                }
                return sanitized;
            }
            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                List<Object> sanitized = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    sanitized.add(sanitizeValue(Array.get(value, i), ancestors, depth + 1));
                }
                return sanitized;
            }
            throw new IllegalArgumentException(
                    "Unsupported Python input value type: " + value.getClass().getName());
        } finally {
            ancestors.remove(value);
        }
    }

    private Context createContext() {
        return Context.newBuilder("python")
                .engine(engine)
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(className -> false)
                .allowHostClassLoading(false)
                .allowNativeAccess(false)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowIO(IOAccess.NONE)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .build();
    }

    private void closeContext(Context context) {
        try {
            context.close(true);
        } catch (RuntimeException e) {
            LOGGER.error("Error closing Python evaluation context", e);
            return;
        }
        synchronized (lifecycleMonitor) {
            activeContexts.remove(context);
            closeEngineIfQuiescent();
        }
    }

    private static void cancelEvaluation(Future<?> evaluation, Context context) {
        if (evaluation != null) {
            evaluation.cancel(true);
        }
        if (context != null) {
            interruptContext(context);
        }
    }

    private static void interruptContext(Context context) {
        try {
            context.interrupt(Duration.ZERO);
        } catch (TimeoutException e) {
            LOGGER.debug("Python context interruption is still in progress");
        } catch (RuntimeException e) {
            LOGGER.warn("Error interrupting Python evaluation context", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        synchronized (lifecycleMonitor) {
            if (shuttingDown) {
                return;
            }
            shuttingDown = true;
            executor.shutdown();
        }

        boolean terminated = awaitTermination(5, TimeUnit.SECONDS);
        if (!terminated) {
            for (Context context : activeContexts) {
                interruptContext(context);
            }
            executor.shutdownNow();
            awaitTermination(1, TimeUnit.SECONDS);
        }

        synchronized (lifecycleMonitor) {
            closeEngineIfQuiescent();
            if (!engineClosed) {
                LOGGER.error(
                        "Python evaluator shutdown did not quiesce within the allotted time; "
                                + "the engine will close after active evaluations finish");
            }
        }
    }

    private boolean awaitTermination(long timeout, TimeUnit unit) {
        try {
            return executor.awaitTermination(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void closeEngineIfQuiescent() {
        if (shuttingDown && executor.isTerminated() && activeContexts.isEmpty() && !engineClosed) {
            try {
                engine.close();
                engineClosed = true;
            } catch (RuntimeException e) {
                LOGGER.error("Error closing Python evaluator engine", e);
            }
        }
    }
}
