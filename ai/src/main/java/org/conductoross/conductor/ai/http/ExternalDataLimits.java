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
package org.conductoross.conductor.ai.http;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

/** Bounds untrusted tool data before it is copied into durable workflow state. */
public final class ExternalDataLimits {

    public static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    public static final int MAX_NESTING_DEPTH = 32;

    private ExternalDataLimits() {}

    /** Rejects object graphs that would make workflow state expensive or unsafe to process. */
    public static void validateStructure(Object value) {
        validateStructure(value, 0, new IdentityHashMap<>());
    }

    private static void validateStructure(
            Object value, int depth, IdentityHashMap<Object, Boolean> seen) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return;
        }
        if (depth > MAX_NESTING_DEPTH) {
            throw new IllegalArgumentException(
                    "External tool data exceeds the maximum nesting depth of " + MAX_NESTING_DEPTH);
        }
        if (seen.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("External tool data contains a cyclic structure");
        }
        try {
            if (value instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    validateStructure(entry.getKey(), depth + 1, seen);
                    validateStructure(entry.getValue(), depth + 1, seen);
                }
            } else if (value instanceof Collection<?> collection) {
                for (Object item : collection) {
                    validateStructure(item, depth + 1, seen);
                }
            } else if (value.getClass().isArray()) {
                for (int i = 0; i < Array.getLength(value); i++) {
                    validateStructure(Array.get(value, i), depth + 1, seen);
                }
            }
        } finally {
            seen.remove(value);
        }
    }
}
