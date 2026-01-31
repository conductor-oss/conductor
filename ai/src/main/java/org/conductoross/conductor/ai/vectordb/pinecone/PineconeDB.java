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
package org.conductoross.conductor.ai.vectordb.pinecone;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.models.IndexedDoc;
import org.conductoross.conductor.ai.vectordb.VectorDB;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.grpc.StatusRuntimeException;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.pinecone.commons.IndexInterface;
import io.pinecone.proto.UpsertResponse;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
@Component(PineconeDB.TYPE)
public class PineconeDB extends VectorDB {

    public static final String TYPE = "pineconedb";
    private final Cache<String, Index> indexCache;
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private final PineconeConfig config;

    public PineconeDB(PineconeConfig config) {
        super(TYPE);
        this.config = config;
        this.indexCache =
                CacheBuilder.newBuilder()
                        .maximumSize(100)
                        .expireAfterAccess(Duration.ofSeconds(60))
                        .concurrencyLevel(32)
                        .build();
    }

    @SneakyThrows
    private int updateEmbeddingsWithNameSpace(
            String indexName,
            String namespace,
            String doc,
            String parentDocId,
            String id,
            List<Float> embeddings,
            Map<String, Object> additionalMetadata) {
        String metadataJson = objectMapper.writeValueAsString(additionalMetadata);
        Struct.Builder metadataBuilder = Struct.newBuilder();
        Index conn = getConnection(indexName);
        try {

            if (parentDocId != null) {
                metadataBuilder.putFields(
                        "parentDocId", Value.newBuilder().setStringValue(parentDocId).build());
            }
            if (doc != null) {
                metadataBuilder.putFields("text", Value.newBuilder().setStringValue(doc).build());
            }
            metadataBuilder.putFields(
                    "metadata", Value.newBuilder().setStringValue(metadataJson).build());

            Struct metadata = metadataBuilder.build();

            VectorWithUnsignedIndices vectors =
                    IndexInterface.buildUpsertVectorWithUnsignedIndices(
                            id, embeddings, null, null, metadata);
            UpsertResponse upsertResponse = conn.upsert(List.of(vectors), namespace);
            return upsertResponse.getUpsertedCount();

        } catch (StatusRuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int updateEmbeddings(
            String indexName,
            String namespace,
            String doc,
            String parentDocId,
            String id,
            List<Float> embeddings,
            Map<String, Object> additionalMetadata) {
        try {
            return updateEmbeddingsWithNameSpace(
                    indexName, namespace, doc, parentDocId, id, embeddings, additionalMetadata);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("feature 'Namespaces'")) {
                return updateEmbeddingsWithNameSpace(
                        indexName, "", doc, parentDocId, id, embeddings, additionalMetadata);
            } else {
                throw e;
            }
        }
    }

    public List<IndexedDoc> searchWithNameSpace(
            String indexName, String namespace, List<Float> embeddings, int maxResults) {

        Index conn = getConnection(indexName);

        try {
            QueryResponseWithUnsignedIndices response =
                    conn.queryByVector(maxResults, embeddings, namespace, true, true);
            List<ScoredVectorWithUnsignedIndices> scoredVectors = response.getMatchesList();
            List<IndexedDoc> matches = new ArrayList<>(scoredVectors.size());
            for (var scoredVector : scoredVectors) {
                Struct metadata = scoredVector.getMetadata();
                String text = "";
                String parentDocId = null;
                Map metadataMap = null;
                if (metadata != null) {
                    Value value = metadata.getFieldsMap().get("metadata");
                    if (value != null) {
                        String json = value.getStringValue();
                        try {
                            metadataMap = objectMapper.readValue(json, Map.class);
                        } catch (JsonProcessingException jsonProcessingException) {
                            log.error(
                                    jsonProcessingException.getMessage(), jsonProcessingException);
                        }
                    }
                    Value textField = metadata.getFieldsMap().get("text");
                    if (textField != null) {
                        text = textField.getStringValue();
                    }
                    Value parentDocField = metadata.getFieldsMap().get("parentDocId");
                    if (parentDocField != null) {
                        parentDocId = parentDocField.getStringValue();
                    }
                }

                String docId = scoredVector.getId();
                double score = scoredVector.getScore();

                IndexedDoc indexedDoc = new IndexedDoc(docId, parentDocId, text, score);
                indexedDoc.setMetadata(metadataMap);
                matches.add(indexedDoc);
            }
            return matches;

        } catch (StatusRuntimeException e) {
            log.error("Error while searching in pinecone: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> toJavaMap(Struct metadata) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, Value> e : metadata.getFieldsMap().entrySet()) {
            String key = e.getKey();
            Value value = e.getValue();
            if (value.hasNumberValue()) {
                map.put(key, value.getNumberValue());
            } else {
                map.put(key, value.getStringValue());
            }
        }
        return map;
    }

    public List<IndexedDoc> search(
            String indexName, String namespace, List<Float> embeddings, int topK) {
        try {
            return searchWithNameSpace(indexName, namespace, embeddings, topK);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("feature 'Namespaces'")) {
                return searchWithNameSpace(indexName, "", embeddings, topK);
            } else {
                throw e;
            }
        }
    }

    @SneakyThrows
    private Index getConnection(String indexName) {
        return indexCache.get(indexName, () -> getIndex(indexName));
    }

    private Index getIndex(String indexName) {
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException(
                    "Pinecone API key is not configured. Please set conductor.vectordb.pinecone.apiKey");
        }
        Pinecone pinecone =
                new Pinecone.Builder(apiKey)
                        .withOkHttpClient(new OkHttpClient.Builder().build())
                        .build();
        return pinecone.getIndexConnection(indexName);
    }
}
