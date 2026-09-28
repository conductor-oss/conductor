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
package org.conductoross.conductor.ai.agentspan.runtime.util;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compile-time check that a JSON Schema only uses keywords the runtime INLINE validator ({@link
 * JavaScriptBuilder#schemaValidatorScript()}) actually handles. Closes /dg #1: the runtime
 * validator is a hand-rolled Draft-07 subset; without this check, a tool author who declares a
 * schema using {@code $ref}, {@code allOf}, {@code anyOf}, {@code oneOf}, {@code format}, {@code
 * if}/{@code then}/{@code else}, or other unsupported features gets <em>silent permissive
 * validation</em> — the runtime walks the schema, ignores the unsupported keywords, and lets the
 * instance through as if it matched.
 *
 * <p>Silent permissive validation is worse than no validation. This validator turns it into a loud
 * compile-time rejection so the tool author fixes the schema (or removes a misleading constraint)
 * instead of shipping a schema whose declared rules don't fire at runtime.
 *
 * <p>The supported keyword set is intentionally a strict superset of what {@code
 * schemaValidatorScript} implements — keep them in lockstep. When the runtime validator gains a
 * keyword, add it here. When this validator rejects a keyword, the runtime can be relied on to
 * never see it.
 */
public final class SchemaSubsetValidator {

    private SchemaSubsetValidator() {}

    /**
     * Keywords {@link JavaScriptBuilder#schemaValidatorScript()} understands. Everything else is
     * rejected by {@link #validate(Map, String)}.
     */
    private static final Set<String> SUPPORTED =
            new LinkedHashSet<>(
                    Arrays.asList(
                            // Type / shape
                            "type",
                            "properties",
                            "required",
                            "additionalProperties",
                            "items",
                            // Validation
                            "enum",
                            "minLength",
                            "maxLength",
                            "pattern",
                            "minimum",
                            "maximum",
                            "minItems",
                            "maxItems",
                            // Documentation — ignored by the runtime, harmless at compile time.
                            "title",
                            "description",
                            "examples",
                            "default",
                            "$schema",
                            "$id"));

    /**
     * Keywords known to exist in Draft-07 but explicitly NOT supported by the runtime validator.
     * Listed separately so the rejection message can distinguish "you used a real keyword we don't
     * implement" from "you made up a keyword that doesn't exist." Both paths reject; the error text
     * is different.
     */
    private static final Set<String> KNOWN_UNSUPPORTED =
            new LinkedHashSet<>(
                    Arrays.asList(
                            "$ref",
                            "$defs",
                            "definitions",
                            "allOf",
                            "anyOf",
                            "oneOf",
                            "not",
                            "if",
                            "then",
                            "else",
                            "dependencies",
                            "dependentRequired",
                            "dependentSchemas",
                            "format",
                            "const",
                            "multipleOf",
                            "exclusiveMinimum",
                            "exclusiveMaximum",
                            "uniqueItems",
                            "contains",
                            "minContains",
                            "maxContains",
                            "propertyNames",
                            "patternProperties",
                            "contentEncoding",
                            "contentMediaType",
                            "contentSchema",
                            "readOnly",
                            "writeOnly"));

    /**
     * Walk {@code schema} recursively, throwing {@link UnsupportedSchemaException} on the first
     * unsupported keyword encountered. {@code location} is a human-readable label (e.g. "tool
     * 'write_file' inputSchema") prepended to the error message.
     *
     * <p>A {@code null} or non-Map schema is treated as "no schema" — the runtime path handles it
     * the same way (early-return without validation), so accept it here too.
     */
    public static void validate(Map<String, Object> schema, String location) {
        if (schema == null || schema.isEmpty()) return;
        validateInternal(schema, location, "");
    }

    @SuppressWarnings("unchecked")
    private static void validateInternal(Map<String, Object> schema, String location, String path) {
        for (Map.Entry<String, Object> e : schema.entrySet()) {
            String key = e.getKey();
            if (SUPPORTED.contains(key)) continue;
            if (KNOWN_UNSUPPORTED.contains(key)) {
                throw new UnsupportedSchemaException(
                        location
                                + ": uses unsupported JSON Schema keyword '"
                                + key
                                + "' at "
                                + (path.isEmpty() ? "<root>" : path)
                                + ". The PAC runtime validator implements a Draft-07 subset; "
                                + "keywords like $ref/allOf/oneOf/format would silently pass at "
                                + "runtime, producing permissive validation. Restrict the schema "
                                + "to: "
                                + String.join(", ", SUPPORTED)
                                + ".");
            }
            // Unknown keyword — not in either set. Could be a typo, could be
            // a custom extension. Either way, ambiguous behaviour at runtime:
            // reject loudly.
            throw new UnsupportedSchemaException(
                    location
                            + ": unknown JSON Schema keyword '"
                            + key
                            + "' at "
                            + (path.isEmpty() ? "<root>" : path)
                            + ". Allowed keywords: "
                            + String.join(", ", SUPPORTED)
                            + ".");
        }

        // Recurse into nested schemas via ``properties`` and ``items``.
        Object props = schema.get("properties");
        if (props instanceof Map<?, ?> propsMap) {
            for (Map.Entry<?, ?> entry : propsMap.entrySet()) {
                Object subSchema = entry.getValue();
                if (subSchema instanceof Map<?, ?> subMap) {
                    validateInternal(
                            (Map<String, Object>) subMap,
                            location,
                            path + "/properties/" + entry.getKey());
                }
            }
        }
        Object items = schema.get("items");
        if (items instanceof Map<?, ?> itemsMap) {
            validateInternal((Map<String, Object>) itemsMap, location, path + "/items");
        } else if (items instanceof List<?> itemsList) {
            // Tuple-form items array (Draft-04/06). The runtime validator
            // doesn't handle it either, but the array itself isn't a
            // keyword — recurse into each tuple-position schema.
            for (int i = 0; i < itemsList.size(); i++) {
                Object sub = itemsList.get(i);
                if (sub instanceof Map<?, ?> subMap) {
                    validateInternal(
                            (Map<String, Object>) subMap, location, path + "/items[" + i + "]");
                }
            }
        }
    }

    /** Thrown by {@link #validate(Map, String)} on an unsupported keyword. */
    public static final class UnsupportedSchemaException extends RuntimeException {
        public UnsupportedSchemaException(String message) {
            super(message);
        }
    }
}
