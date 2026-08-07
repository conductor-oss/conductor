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
package org.conductoross.conductor.ai.vectordb.valkey;

import java.util.Locale;

import org.conductoross.conductor.ai.vectordb.VectorDBConfig;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Configuration for a Valkey-backed vector database instance. Requires a Valkey server with the
 * {@code valkey-search} module loaded.
 *
 * <p>Configuration example:
 *
 * <pre>
 * conductor.vectordb.instances:
 *   - name: "valkey-local"
 *     type: "valkey"
 *     valkey:
 *       host: "localhost"
 *       port: 6379
 *       useTls: true
 *       password: "${VALKEY_PASSWORD}"
 *       dimensions: 1536
 *       distanceMetric: "cosine"
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValkeyConfig implements VectorDBConfig<ValkeyVectorDB> {

    private String host = "localhost";

    private Integer port = 6379;

    private String username;

    @ToString.Exclude private String password;

    private Integer database = 0;

    private Boolean useTls = false;

    private Integer dimensions = 256;

    /** Distance metric: "cosine", "l2", or "ip". Unknown values are rejected at construction. */
    private String distanceMetric = "cosine";

    /** Indexing algorithm: "hnsw" or "flat". Unknown values are rejected at construction. */
    private String indexingMethod = "hnsw";

    /**
     * Key prefix for hash keys and FT.CREATE PREFIX filter. Keys follow the schema: {@code
     * <keyPrefix>:<indexName>:<namespace>:<docId>}.
     */
    private String keyPrefix = "conductor";

    /** Timeout in milliseconds for each GLIDE future resolution. */
    private Integer requestTimeoutMs = 2000;

    @Override
    public ValkeyVectorDB get() {
        throw new UnsupportedOperationException("Use get(String name) instead");
    }

    public ValkeyVectorDB get(String name) {
        // Validate name, metric, and algorithm eagerly so mis-configuration fails at startup with a
        // clear message instead of surfacing later as an opaque NullPointerException.
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Valkey vector DB instance name must not be blank");
        }
        resolveDistanceMetric(distanceMetric);
        resolveIndexingMethod(indexingMethod);
        return new ValkeyVectorDB(name, this);
    }

    /**
     * Maps a user-facing distance metric string to the corresponding GLIDE DistanceMetric enum.
     * Rejects unknown values with an informative error. Uses Locale.ROOT for case folding.
     */
    static glide.api.models.commands.FT.FTCreateOptions.DistanceMetric resolveDistanceMetric(
            String metric) {
        if (metric == null) {
            return glide.api.models.commands.FT.FTCreateOptions.DistanceMetric.COSINE;
        }
        switch (metric.toLowerCase(Locale.ROOT)) {
            case "cosine":
                return glide.api.models.commands.FT.FTCreateOptions.DistanceMetric.COSINE;
            case "l2":
                return glide.api.models.commands.FT.FTCreateOptions.DistanceMetric.L2;
            case "ip":
                return glide.api.models.commands.FT.FTCreateOptions.DistanceMetric.IP;
            default:
                throw new IllegalArgumentException(
                        "Unknown distance metric: '"
                                + metric
                                + "'. Supported values: cosine, l2, ip");
        }
    }

    /**
     * Validates the indexing method string. Returns the normalized value or throws on unknown
     * input. Uses Locale.ROOT for case folding.
     */
    static String resolveIndexingMethod(String method) {
        if (method == null) {
            return "hnsw";
        }
        switch (method.toLowerCase(Locale.ROOT)) {
            case "hnsw":
                return "hnsw";
            case "flat":
                return "flat";
            default:
                throw new IllegalArgumentException(
                        "Unknown indexing method: '" + method + "'. Supported values: hnsw, flat");
        }
    }
}
