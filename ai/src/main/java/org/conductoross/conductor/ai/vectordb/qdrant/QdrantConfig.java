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
package org.conductoross.conductor.ai.vectordb.qdrant;

import java.time.Duration;

import org.conductoross.conductor.ai.vectordb.VectorDBConfig;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Connection and collection settings for a Qdrant instance. The client talks to Qdrant over gRPC,
 * so {@code port} is the gRPC port (6334 by default), not the REST port.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QdrantConfig implements VectorDBConfig<QdrantVectorDB> {

    private String host = "localhost";

    private Integer port = 6334;

    /** API key for Qdrant Cloud or a secured instance. Leave empty for an open instance. */
    private String apiKey;

    /** Use TLS for the gRPC connection. Qdrant Cloud requires it. */
    private Boolean useTls = false;

    /**
     * Distance function for collections that Conductor creates: cosine, euclid, dot, or manhattan.
     * Existing collections keep the distance they were created with.
     */
    private String distance = "cosine";

    /**
     * Deadline for each call to Qdrant. A call that takes longer fails, and Conductor retries the
     * task. Zero or a negative value means no deadline.
     */
    private Duration timeout = Duration.ofSeconds(30);

    @Override
    public QdrantVectorDB get() {
        throw new UnsupportedOperationException("Use get(String name) instead");
    }

    public QdrantVectorDB get(String name) {
        return new QdrantVectorDB(name, this);
    }
}
