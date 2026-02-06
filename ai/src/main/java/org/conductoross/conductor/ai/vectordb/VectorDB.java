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
package org.conductoross.conductor.ai.vectordb;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.models.IndexedDoc;

public abstract class VectorDB {

    protected String type;

    public VectorDB(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public abstract int updateEmbeddings(
            String indexName,
            String namespace,
            String doc,
            String parentDocId,
            String id,
            List<Float> embeddings,
            Map<String, Object> metadata);

    public abstract List<IndexedDoc> search(
            String indexName, String namespace, List<Float> embeddings, int maxResults);
}
