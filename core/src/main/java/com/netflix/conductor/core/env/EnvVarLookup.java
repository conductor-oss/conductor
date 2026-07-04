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
package com.netflix.conductor.core.env;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EnvVarLookup {

    private EnvVarLookup() {}

    /** Reads {@code prefix + key} from the environment, falling back to system properties. */
    public static String lookup(String prefix, String key) {
        String full = prefix + key;
        String value = System.getenv(full);
        if (value == null) {
            value = System.getProperty(full);
        }
        return value;
    }

    /** Returns all env vars / system properties whose key starts with {@code prefix}, prefix stripped. */
    public static Map<String, String> allWithPrefix(String prefix) {
        Map<String, String> out = new LinkedHashMap<>();
        System.getenv()
                .forEach(
                        (k, v) -> {
                            if (k.startsWith(prefix)) {
                                out.put(k.substring(prefix.length()), v);
                            }
                        });
        System.getProperties()
                .forEach(
                        (k, v) -> {
                            String ks = String.valueOf(k);
                            if (ks.startsWith(prefix)) {
                                out.put(ks.substring(prefix.length()), String.valueOf(v));
                            }
                        });
        return out;
    }
}
