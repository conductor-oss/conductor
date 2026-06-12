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
import org.springframework.stereotype.Component;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class VectorDBs {

    private final VectorDBProvider vectorDBProvider;

    public VectorDBs(VectorDBProvider vectorDBProvider) {
        this.vectorDBProvider = vectorDBProvider;
        log.info("vectorDBProvider: {}", vectorDBProvider);
    }

    public int storeEmbeddings(
            String vectorDBName,
            TaskContext context,
            String indexName,
            String namespace,
            String text,
            String parentDocId,
            String id,
            List<Float> embeddings,
            Map<String, Object> metadata) {
        VectorDB db = vectorDBProvider.get(vectorDBName, context);
        if (db == null) {
            throw new NonRetryableException("VectorDB not found: " + vectorDBName);
        }
        return db.updateEmbeddings(
                indexName, namespace, text, parentDocId, id, embeddings, metadata);
    }

    public List<IndexedDoc> searchEmbeddings(
            String vectorDBName,
            TaskContext context,
            String indexName,
            String namespace,
            List<Float> embeddings,
            int maxResults) {
        VectorDB db = vectorDBProvider.get(vectorDBName, context);
        if (db == null) {
            throw new NonRetryableException("VectorDB not found: " + vectorDBName);
        }
        return db.search(indexName, namespace, embeddings, maxResults);
    }
}
