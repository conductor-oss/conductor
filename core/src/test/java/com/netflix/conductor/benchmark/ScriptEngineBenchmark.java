/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.netflix.conductor.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.netflix.conductor.benchmark.BenchmarkScripts.Case;

/**
 * Standalone benchmark comparing JS engines for Conductor's Inline-task workload:
 *
 * <ul>
 *   <li>GraalJS via {@link com.netflix.conductor.core.events.ScriptEvaluator} (interpreter mode on
 *       stock OpenJDK; this is what production runs today).
 *   <li>Mozilla Rhino 1.8.0 with optimization level 9 (compiles to JVM bytecode).
 *   <li>Javet 4.1.2 (JNI-bound V8) with proxy-based Java/JS interop.
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :conductor-core:benchmarkScripts}
 */
public final class ScriptEngineBenchmark {

    private static final int WARMUP_ITERS = 5000;
    private static final long WARMUP_TIME_CAP_NANOS = 5_000_000_000L; // 5s cap on warmup
    private static final long MEASURE_TIME_NANOS = 7_500_000_000L; // 7.5s measure per (case, engine)
    private static final int MIN_MEASURE_ITERS = 250;

    public static void main(String[] args) throws Exception {
        System.out.println("Conductor JS Engine Benchmark");
        System.out.println("==============================");
        System.out.printf(
                "JVM: %s %s%n",
                System.getProperty("java.vm.name"), System.getProperty("java.version"));
        System.out.printf(
                "OS:  %s %s%n",
                System.getProperty("os.name"), System.getProperty("os.arch"));
        System.out.printf(
                "Warmup: %d iters; Measure: up to %.1fs or %d iters per case per engine%n%n",
                WARMUP_ITERS, MEASURE_TIME_NANOS / 1e9, MIN_MEASURE_ITERS);

        List<EngineDriver> engines = new ArrayList<>();
        engines.add(new GraalJsDriver());
        engines.add(new RhinoDriver());
        try {
            engines.add(new JavetDriver());
        } catch (Throwable t) {
            System.out.println(
                    "WARNING: Javet driver unavailable on this host: " + t.getMessage());
        }

        // Header
        StringBuilder header = new StringBuilder();
        header.append(String.format("%-38s", "case"));
        for (EngineDriver e : engines) {
            header.append(String.format("%18s", e.name() + " ns/op"));
            header.append(String.format("%14s", e.name() + " ops/s"));
        }
        System.out.println(header);
        System.out.println(repeat('-', header.length()));

        List<Result[]> allResults = new ArrayList<>();
        for (Case c : BenchmarkScripts.CASES) {
            Result[] row = new Result[engines.size()];
            for (int i = 0; i < engines.size(); i++) {
                row[i] = benchmark(engines.get(i), c);
            }
            allResults.add(row);
            StringBuilder line = new StringBuilder();
            line.append(String.format("%-38s", c.name));
            for (Result r : row) {
                if (r.error != null) {
                    line.append(String.format("%18s", "ERR"));
                    line.append(String.format("%14s", "-"));
                } else {
                    line.append(String.format("%18s", formatNs(r.nanosPerOp)));
                    line.append(String.format("%14s", formatOps(r.opsPerSec)));
                }
            }
            System.out.println(line);
        }

        // Relative speedup vs GraalJS (column 0).
        System.out.println();
        System.out.println("Relative speedup vs GraalJS (higher = faster)");
        StringBuilder relHeader = new StringBuilder();
        relHeader.append(String.format("%-38s", "case"));
        for (EngineDriver e : engines) {
            relHeader.append(String.format("%14s", e.name()));
        }
        System.out.println(relHeader);
        System.out.println(repeat('-', relHeader.length()));
        for (int i = 0; i < BenchmarkScripts.CASES.size(); i++) {
            Case c = BenchmarkScripts.CASES.get(i);
            Result[] row = allResults.get(i);
            StringBuilder line = new StringBuilder();
            line.append(String.format("%-38s", c.name));
            double baseline = row[0].error == null ? row[0].nanosPerOp : Double.NaN;
            for (Result r : row) {
                if (r.error != null || Double.isNaN(baseline)) {
                    line.append(String.format("%14s", "-"));
                } else {
                    double speedup = baseline / r.nanosPerOp;
                    line.append(String.format("%14s", String.format(Locale.ROOT, "%.2fx", speedup)));
                }
            }
            System.out.println(line);
        }

        // Print any errors collected
        boolean anyError = false;
        for (Result[] row : allResults) {
            for (Result r : row) {
                if (r.error != null) {
                    if (!anyError) {
                        System.out.println();
                        System.out.println("Errors:");
                        anyError = true;
                    }
                    System.out.println("  " + r.engine + " / " + r.caseName + ": " + r.error);
                }
            }
        }

        for (EngineDriver e : engines) {
            try {
                e.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    private static Result benchmark(EngineDriver engine, Case c) {
        try {
            engine.prepare(c.script);
            // Warmup: bounded by both iteration count and wall-clock cap so heavy scripts
            // (case 11 at hundreds of ms/op) don't take minutes per (case, engine).
            long warmupStart = System.nanoTime();
            long warmupDeadline = warmupStart + WARMUP_TIME_CAP_NANOS;
            for (int i = 0; i < WARMUP_ITERS; i++) {
                engine.eval(c.input);
                if (i >= 50 && System.nanoTime() >= warmupDeadline) {
                    break;
                }
            }
            // Measure
            long start = System.nanoTime();
            long deadline = start + MEASURE_TIME_NANOS;
            int iters = 0;
            while (true) {
                engine.eval(c.input);
                iters++;
                if (iters >= MIN_MEASURE_ITERS && System.nanoTime() >= deadline) {
                    break;
                }
            }
            long elapsed = System.nanoTime() - start;
            engine.cleanup();
            double nsPerOp = (double) elapsed / iters;
            double opsPerSec = 1e9 / nsPerOp;
            Result r = new Result();
            r.engine = engine.name();
            r.caseName = c.name;
            r.iters = iters;
            r.nanosPerOp = nsPerOp;
            r.opsPerSec = opsPerSec;
            return r;
        } catch (Throwable t) {
            Result r = new Result();
            r.engine = engine.name();
            r.caseName = c.name;
            r.error = t.getClass().getSimpleName() + ": " + t.getMessage();
            try {
                engine.cleanup();
            } catch (Exception ignored) {
                // best effort
            }
            return r;
        }
    }

    private static String formatNs(double ns) {
        if (ns < 1_000) return String.format(Locale.ROOT, "%.0f ns", ns);
        if (ns < 1_000_000) return String.format(Locale.ROOT, "%.2f us", ns / 1_000.0);
        return String.format(Locale.ROOT, "%.2f ms", ns / 1_000_000.0);
    }

    private static String formatOps(double ops) {
        if (ops >= 1_000_000) return String.format(Locale.ROOT, "%.2fM", ops / 1_000_000.0);
        if (ops >= 1_000) return String.format(Locale.ROOT, "%.1fk", ops / 1_000.0);
        return String.format(Locale.ROOT, "%.0f", ops);
    }

    private static String repeat(char c, int n) {
        return String.join("", Collections.nCopies(n, String.valueOf(c)));
    }

    static final class Result {
        String engine;
        String caseName;
        int iters;
        double nanosPerOp;
        double opsPerSec;
        String error;
    }

    /** Common interface for an engine + a prepared (compiled, where possible) script. */
    interface EngineDriver extends AutoCloseable {
        String name();

        void prepare(String script) throws Exception;

        Object eval(Map<String, Object> input) throws Exception;

        void cleanup() throws Exception;

        @Override
        default void close() throws Exception {}
    }

    // ---------- GraalJS via ScriptEvaluator (production path) ----------
    static final class GraalJsDriver implements EngineDriver {
        private String script;

        @Override
        public String name() {
            return "GraalJS";
        }

        @Override
        public void prepare(String script) {
            this.script = script;
        }

        @Override
        public Object eval(Map<String, Object> input) {
            // Mirror the production call site (JavascriptEvaluator):
            //   deepCopy(input) -> ScriptEvaluator.eval()
            // ScriptEvaluator already pools Contexts and caches compiled Sources.
            Object copy = com.netflix.conductor.core.events.ScriptEvaluator.deepCopy(input);
            return com.netflix.conductor.core.events.ScriptEvaluator.eval(script, copy);
        }

        @Override
        public void cleanup() {}
    }

    // ---------- Rhino ----------
    static final class RhinoDriver implements EngineDriver {
        private final ThreadLocal<org.mozilla.javascript.Context> contextHolder = new ThreadLocal<>();
        private org.mozilla.javascript.Script compiled;

        @Override
        public String name() {
            return "Rhino";
        }

        @Override
        public void prepare(String script) {
            org.mozilla.javascript.Context cx = org.mozilla.javascript.Context.enter();
            try {
                cx.setLanguageVersion(org.mozilla.javascript.Context.VERSION_ES6);
                cx.setOptimizationLevel(9); // maximum optimization (compiles to JVM bytecode)
                compiled = cx.compileString(script, "inline", 1, null);
            } finally {
                org.mozilla.javascript.Context.exit();
            }
        }

        @Override
        public Object eval(Map<String, Object> input) {
            org.mozilla.javascript.Context cx = contextHolder.get();
            if (cx == null) {
                cx = org.mozilla.javascript.Context.enter();
                cx.setLanguageVersion(org.mozilla.javascript.Context.VERSION_ES6);
                cx.setOptimizationLevel(9);
                contextHolder.set(cx);
            }
            Object inputCopy = com.netflix.conductor.core.events.ScriptEvaluator.deepCopy(input);
            org.mozilla.javascript.Scriptable scope = cx.initStandardObjects();
            // Deep-convert Java Map/List to Rhino native NativeObject/NativeArray so JS
            // property syntax ($.foo.bar) and array methods (.filter/.map/.reduce) work.
            // Wrapping via Context.javaToJS yields NativeJavaObject which exposes only Java
            // methods, not JS properties — production Rhino integrations must do this conversion.
            Object jsInput = javaToRhino(cx, scope, inputCopy);
            org.mozilla.javascript.ScriptableObject.putProperty(scope, "$", jsInput);
            Object result = compiled.exec(cx, scope);
            return org.mozilla.javascript.Context.jsToJava(result, Object.class);
        }

        @SuppressWarnings("unchecked")
        private static Object javaToRhino(
                org.mozilla.javascript.Context cx,
                org.mozilla.javascript.Scriptable scope,
                Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Map) {
                org.mozilla.javascript.Scriptable obj = cx.newObject(scope);
                for (Map.Entry<Object, Object> e : ((Map<Object, Object>) value).entrySet()) {
                    org.mozilla.javascript.ScriptableObject.putProperty(
                            obj, String.valueOf(e.getKey()), javaToRhino(cx, scope, e.getValue()));
                }
                return obj;
            }
            if (value instanceof List) {
                List<Object> list = (List<Object>) value;
                Object[] arr = new Object[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    arr[i] = javaToRhino(cx, scope, list.get(i));
                }
                return cx.newArray(scope, arr);
            }
            // Primitives (Number/Boolean/String) and the like — Rhino handles directly.
            return value;
        }

        @Override
        public void cleanup() {
            org.mozilla.javascript.Context cx = contextHolder.get();
            if (cx != null) {
                org.mozilla.javascript.Context.exit();
                contextHolder.remove();
            }
        }
    }

    // ---------- Javet (V8) ----------
    static final class JavetDriver implements EngineDriver {
        private com.caoccao.javet.interop.V8Runtime v8;
        private com.caoccao.javet.values.reference.V8Script compiled;

        @Override
        public String name() {
            return "Javet";
        }

        @Override
        public void prepare(String script) throws Exception {
            v8 = com.caoccao.javet.interop.V8Host.getV8Instance().createV8Runtime();
            // Use the default JavetObjectConverter (deep Java->V8 native conversion). The
            // ProxyConverter lazy-wraps Java objects and forces a JNI callback for every
            // property access from JS, which is catastrophic for scripts that iterate.
            v8.setConverter(new com.caoccao.javet.interop.converters.JavetObjectConverter());
            compiled =
                    v8.getExecutor(script)
                            .setResourceName("inline.js")
                            .compileV8Script();
        }

        @Override
        public Object eval(Map<String, Object> input) throws Exception {
            // Parity with production: deep-copy input first. Then JavetObjectConverter does a
            // one-time deep conversion to V8-native objects on global.set("$", ...).
            Object inputCopy = com.netflix.conductor.core.events.ScriptEvaluator.deepCopy(input);
            try (com.caoccao.javet.values.reference.V8ValueObject global = v8.getGlobalObject()) {
                global.set("$", inputCopy);
                try (com.caoccao.javet.values.V8Value result = compiled.execute()) {
                    return v8.toObject(result);
                } finally {
                    global.delete("$");
                }
            }
        }

        @Override
        public void cleanup() {}

        @Override
        public void close() throws Exception {
            if (compiled != null) compiled.close();
            if (v8 != null) v8.close();
        }
    }
}
