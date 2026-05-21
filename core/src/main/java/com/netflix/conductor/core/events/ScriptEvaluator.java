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

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import org.graalvm.polyglot.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.evaluators.ConsoleBridge;

public class ScriptEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptEvaluator.class);

    private static final int DEFAULT_MAX_EXECUTION_SECONDS = 4;
    private static final int DEFAULT_CONTEXT_POOL_SIZE = 10;
    // Pool reuse changes JS semantics: a Context's global scope persists across calls, so a
    // user script with top-level `const x = ...` or `let x = ...` throws SyntaxError on the
    // second call (identifier already declared). Default off; users who fully control their
    // scripts can opt in via CONDUCTOR_SCRIPT_CONTEXT_POOL_ENABLED=true. The shared Engine and
    // Source cache below still apply when the pool is disabled, so Context creation is much
    // cheaper than before.
    private static final boolean DEFAULT_CONTEXT_POOL_ENABLED = false;
    private static final int DEFAULT_SOURCE_CACHE_SIZE = 1024;

    private static Duration maxExecutionTimeSeconds;
    private static ExecutorService executorService;
    private static BlockingQueue<ScriptExecutionContext> contextPool;
    private static boolean contextPoolEnabled;
    private static boolean initialized = false;
    private static int sourceCacheMaxSize = DEFAULT_SOURCE_CACHE_SIZE;

    /**
     * Shared GraalVM Engine reused across all Contexts so the Truffle compilation cache (and JIT
     * code, when a compiling Truffle runtime is on the classpath) is shared instead of duplicated
     * per Context.
     */
    private static final Engine ENGINE = buildEngine();

    private static Engine buildEngine() {
        Engine engine =
                Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build();
        // Log once so operators can confirm whether the optimizing runtime is engaged.
        // "GraalVM" => JIT-compiling Truffle runtime; "Default" => interpreter-only fallback.
        LOGGER.info(
                "GraalVM polyglot engine: implementation={}, version={}",
                engine.getImplementationName(),
                engine.getVersion());
        return engine;
    }

    /**
     * Cache of compiled JS Sources keyed by raw script text. Pairing a stable Source instance with
     * the shared {@link #ENGINE} lets GraalJS reuse parsed/compiled code across invocations of the
     * same expression.
     */
    private static final ConcurrentMap<String, Source> SOURCE_CACHE = new ConcurrentHashMap<>();

    private ScriptEvaluator() {}

    /**
     * Initialize the script evaluator with configuration. This should be called once at startup.
     *
     * @param maxSeconds Maximum execution time in seconds (default: 4)
     * @param contextPoolSize Size of the context pool (default: 10)
     * @param poolEnabled Whether to enable context pooling (default: false)
     * @param executor ExecutorService for script execution
     */
    public static synchronized void initialize(
            int maxSeconds, int contextPoolSize, boolean poolEnabled, ExecutorService executor) {
        if (initialized) {
            LOGGER.warn("ScriptEvaluator already initialized, skipping re-initialization");
            return;
        }

        maxExecutionTimeSeconds = Duration.ofSeconds(maxSeconds);
        executorService = executor != null ? executor : Executors.newCachedThreadPool();
        contextPoolEnabled = poolEnabled;

        if (!contextPoolEnabled) {
            LOGGER.warn(
                    "Script execution context pool is disabled. Each script execution will create a new context.");
            contextPool = null;
        } else {
            contextPool = new LinkedBlockingQueue<>(contextPoolSize);
            // Pre-fill the pool
            for (int i = 0; i < contextPoolSize; i++) {
                Context context = createNewContext();
                contextPool.offer(new ScriptExecutionContext(context));
            }
            LOGGER.info(
                    "Script execution context pool initialized with {} contexts", contextPoolSize);
        }

        initialized = true;
    }

    /** Initialize with default values from environment variables or defaults. */
    public static synchronized void initializeWithDefaults() {
        if (initialized) {
            return;
        }

        int maxSeconds =
                Integer.parseInt(
                        getEnv(
                                "CONDUCTOR_SCRIPT_MAX_EXECUTION_SECONDS",
                                String.valueOf(DEFAULT_MAX_EXECUTION_SECONDS)));
        int poolSize =
                Integer.parseInt(
                        getEnv(
                                "CONDUCTOR_SCRIPT_CONTEXT_POOL_SIZE",
                                String.valueOf(DEFAULT_CONTEXT_POOL_SIZE)));
        boolean poolEnabled =
                Boolean.parseBoolean(
                        getEnv(
                                "CONDUCTOR_SCRIPT_CONTEXT_POOL_ENABLED",
                                String.valueOf(DEFAULT_CONTEXT_POOL_ENABLED)));
        sourceCacheMaxSize =
                Integer.parseInt(
                        getEnv(
                                "CONDUCTOR_SCRIPT_SOURCE_CACHE_SIZE",
                                String.valueOf(DEFAULT_SOURCE_CACHE_SIZE)));

        initialize(maxSeconds, poolSize, poolEnabled, null);
    }

    private static String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null ? value : defaultValue;
    }

    private static void ensureInitialized() {
        if (!initialized) {
            initializeWithDefaults();
        }
    }

    private static Context createNewContext() {
        HostAccess hostAccess =
                HostAccess.newBuilder(HostAccess.ALL)
                        .denyAccess(Class.class)
                        .denyAccess(ClassLoader.class)
                        .denyAccess(java.lang.reflect.Method.class)
                        .denyAccess(java.lang.reflect.Field.class)
                        .denyAccess(java.lang.reflect.Constructor.class)
                        .denyAccess(java.lang.reflect.Array.class)
                        .denyAccess(Runtime.class)
                        .denyAccess(ProcessBuilder.class)
                        .denyAccess(Process.class)
                        .denyAccess(System.class)
                        .denyAccess(Thread.class)
                        .denyAccess(ThreadGroup.class)
                        .build();
        return Context.newBuilder("js").engine(ENGINE).allowHostAccess(hostAccess).build();
    }

    /**
     * Returns a defensive deep copy of {@code input} so script-side mutations (e.g. {@code $.data.x
     * = 1}) cannot leak back into the caller's data structures and so {@code PolyglotMap} /{@code
     * PolyglotList} references created during evaluation cannot escape a closed Context. Recurses
     * through Maps, Lists, Sets, and arrays; immutable scalars (String, Number, Boolean, etc.) are
     * shared by reference. Cheaper than a JSON round-trip.
     */
    public static Object deepCopy(Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof Map<?, ?> m) {
            Map<Object, Object> copy = new LinkedHashMap<>(m.size());
            for (Map.Entry<?, ?> e : m.entrySet()) {
                copy.put(e.getKey(), deepCopy(e.getValue()));
            }
            return copy;
        }
        if (input instanceof List<?> l) {
            List<Object> copy = new ArrayList<>(l.size());
            for (Object item : l) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        if (input instanceof Set<?> s) {
            Set<Object> copy = new LinkedHashSet<>(s.size());
            for (Object item : s) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        Class<?> cls = input.getClass();
        if (cls.isArray()) {
            int len = java.lang.reflect.Array.getLength(input);
            List<Object> copy = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                copy.add(deepCopy(java.lang.reflect.Array.get(input, i)));
            }
            return copy;
        }
        // Immutable scalars (String, Number, Boolean, enums, etc.) — safe to share.
        return input;
    }

    /**
     * Returns a cached compiled {@link Source} for the given script, creating it on first use.
     * Bounded by {@link #sourceCacheMaxSize}; on overflow the cache is cleared (workflow scripts
     * are typically a small, stable set, so the simplest strategy suffices).
     */
    private static Source getSource(String script) {
        Source cached = SOURCE_CACHE.get(script);
        if (cached != null) {
            return cached;
        }
        if (SOURCE_CACHE.size() >= sourceCacheMaxSize) {
            SOURCE_CACHE.clear();
        }
        Source source = Source.newBuilder("js", script, "inline").cached(true).buildLiteral();
        Source existing = SOURCE_CACHE.putIfAbsent(script, source);
        return existing != null ? existing : source;
    }

    /**
     * Evaluates the script with the help of input provided but converts the result to a boolean
     * value.
     *
     * @param script Script to be evaluated.
     * @param input Input parameters.
     * @return True or False based on the result of the evaluated expression.
     */
    public static Boolean evalBool(String script, Object input) {
        return toBoolean(eval(script, input));
    }

    /**
     * Evaluates the script with the help of input provided.
     *
     * @param script Script to be evaluated.
     * @param input Input parameters.
     * @return Generic object, the result of the evaluated expression.
     */
    public static Object eval(String script, Object input) {
        return eval(script, input, null);
    }

    /**
     * Evaluates the script with the help of input provided.
     *
     * @param script Script to be evaluated.
     * @param input Input parameters.
     * @param console ConsoleBridge that can be used to get the calls to console.log() and others.
     * @return Generic object, the result of the evaluated expression.
     */
    public static Object eval(String script, Object input, ConsoleBridge console) {
        ensureInitialized();

        final Source source = getSource(script);

        if (contextPoolEnabled) {
            // Context pool implementation
            ScriptExecutionContext scriptContext = null;
            try {
                scriptContext = contextPool.take();
                final ScriptExecutionContext finalScriptContext = scriptContext;
                finalScriptContext.prepareBindings(input, console);
                Future<Value> futureResult =
                        executorService.submit(() -> finalScriptContext.getContext().eval(source));
                Value value =
                        futureResult.get(maxExecutionTimeSeconds.getSeconds(), TimeUnit.SECONDS);
                return getObject(value);
            } catch (TimeoutException e) {
                if (scriptContext != null) {
                    interrupt(scriptContext.getContext());
                }
                throw new NonTransientException(
                        String.format(
                                "Script not evaluated within %d seconds, interrupted.",
                                maxExecutionTimeSeconds.getSeconds()));
            } catch (ExecutionException ee) {
                handlePolyglotException(ee);
                return null;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new NonTransientException("Script execution interrupted: " + ie.getMessage());
            } finally {
                if (scriptContext != null) {
                    scriptContext.clearBindings();
                    if (!contextPool.offer(scriptContext)) {
                        scriptContext.getContext().close();
                        LOGGER.warn(
                                "ScriptExecutionContext pool is full, context closed and not returned to pool.");
                    }
                }
            }
        } else {
            // No context pool - create new context for each execution
            try (Context context = createNewContext()) {
                final Value jsBindings = context.getBindings("js");
                jsBindings.putMember("$", input);
                if (console != null) {
                    jsBindings.putMember("console", console);
                }
                final Future<Value> futureResult =
                        executorService.submit(() -> context.eval(source));
                Value value =
                        futureResult.get(maxExecutionTimeSeconds.getSeconds(), TimeUnit.SECONDS);
                return getObject(value);
            } catch (TimeoutException e) {
                throw new NonTransientException(
                        String.format(
                                "Script not evaluated within %d seconds, interrupted.",
                                maxExecutionTimeSeconds.getSeconds()));
            } catch (ExecutionException ee) {
                handlePolyglotException(ee);
                return null;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new NonTransientException("Script execution interrupted: " + ie.getMessage());
            }
        }
    }

    private static void handlePolyglotException(ExecutionException ee) {
        if (ee.getCause() instanceof PolyglotException pe) {
            SourceSection sourceSection = pe.getSourceLocation();
            if (sourceSection == null) {
                throw new TerminateWorkflowException(
                        "Error evaluating the script `" + pe.getMessage() + "`");
            } else {
                throw new TerminateWorkflowException(
                        "Error evaluating the script `"
                                + pe.getMessage()
                                + "` at line "
                                + sourceSection.getStartLine());
            }
        }
        throw new TerminateWorkflowException("Error evaluating the script " + ee.getMessage());
    }

    private static Object getObject(Value value) {
        if (value.isNull()) return null;
        if (value.isBoolean()) return value.asBoolean();
        if (value.isString()) return value.asString();
        if (value.isNumber()) {
            if (value.fitsInInt()) return value.asInt();
            if (value.fitsInLong()) return value.asLong();
            if (value.fitsInDouble()) return value.asDouble();
        }
        if (value.hasArrayElements()) {
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < value.getArraySize(); i++) {
                items.add(getObject(value.getArrayElement(i)));
            }
            return items;
        }

        // Convert map
        Map<Object, Object> output = new HashMap<>();
        if (value.hasHashEntries()) {
            Value keys = value.getHashKeysIterator();
            while (keys.hasIteratorNextElement()) {
                Value key = keys.getIteratorNextElement();
                output.put(getObject(key), getObject(value.getHashValue(key)));
            }
        } else {
            for (String key : value.getMemberKeys()) {
                output.put(key, getObject(value.getMember(key)));
            }
        }
        return output;
    }

    private static void interrupt(Context context) {
        try {
            context.interrupt(Duration.ZERO);
        } catch (TimeoutException ignored) {
            // Expected when interrupting
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

    /** Script execution context holder for context pooling. */
    private static class ScriptExecutionContext {
        private final Context context;
        private final Value bindings;

        public ScriptExecutionContext(Context context) {
            this.context = context;
            this.bindings = context.getBindings("js");
        }

        public Context getContext() {
            return context;
        }

        public void prepareBindings(Object input, Object console) {
            bindings.putMember("$", input);
            if (console != null) {
                bindings.putMember("console", console);
            }
        }

        public void clearBindings() {
            bindings.removeMember("$");
            bindings.removeMember("console");
        }
    }
}
