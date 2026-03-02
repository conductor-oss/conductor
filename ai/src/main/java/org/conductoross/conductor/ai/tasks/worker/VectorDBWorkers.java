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
package org.conductoross.conductor.ai.tasks.worker;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.conductoross.conductor.ai.LLMs;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.models.IndexDocInput;
import org.conductoross.conductor.ai.models.IndexedDoc;
import org.conductoross.conductor.ai.models.StoreEmbeddingsInput;
import org.conductoross.conductor.ai.models.VectorDBInput;
import org.conductoross.conductor.ai.vectordb.VectorDBs;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.conductoross.conductor.core.execution.tasks.AnnotatedSystemTaskWorker;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;
import com.netflix.conductor.sdk.workflow.task.OutputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
@Component
@Conditional(AIIntegrationEnabledCondition.class)
public class VectorDBWorkers implements AnnotatedSystemTaskWorker {

    private static final TypeReference<Map<String, Object>> MAP_OF_STRING_TO_OBJ =
            new TypeReference<Map<String, Object>>() {};

    private final VectorDBs vectorDBs;
    private final LLMs llm;

    public VectorDBWorkers(VectorDBs vectorDBs, LLMs llm) {
        this.vectorDBs = vectorDBs;
        this.llm = llm;
        log.info("VectorDBWorkers initialized with LLMs: {} and vectorDBs: {}", llm, vectorDBs);
    }

    @WorkerTask("LLM_INDEX_TEXT")
    public void indexText(IndexDocInput input) {
        if (isBlank(input.getDocId())) {
            throw new NonRetryableException("docId is empty");
        }

        try {
            String chunk = input.getText();
            EmbeddingGenRequest request =
                    EmbeddingGenRequest.builder()
                            .model(input.getEmbeddingModel())
                            .dimensions(input.getDimensions())
                            .text(chunk)
                            .build();
            request.setLlmProvider(input.getEmbeddingModelProvider());
            List<Float> embeddings = llm.generateEmbeddings(TaskContext.get().getTask(), request);

            vectorDBs.storeEmbeddings(
                    input.getVectorDB(),
                    TaskContext.get(),
                    input.getIndex(),
                    input.getNamespace(),
                    chunk,
                    input.getDocId(),
                    input.getDocId(),
                    embeddings,
                    input.getMetadata());
        } catch (Exception e) {
            log.error("Error while indexing text: {}", e.getMessage(), e);
            throw e;
        }
    }

    @WorkerTask("LLM_STORE_EMBEDDINGS")
    public @OutputParam("result") int storeEmbeddings(StoreEmbeddingsInput input) {
        String id = Optional.ofNullable(input.getId()).orElse(UUID.randomUUID().toString());
        try {
            return vectorDBs.storeEmbeddings(
                    input.getVectorDB(),
                    TaskContext.get(),
                    input.getIndex(),
                    input.getNamespace(),
                    "",
                    "",
                    id,
                    input.getEmbeddings(),
                    input.getMetadata());
        } catch (Exception e) {
            log.error("Error while storing LLM embeddings: {}", e.getMessage(), e);
            throw e;
        }
    }

    @WorkerTask("LLM_SEARCH_EMBEDDINGS")
    public @OutputParam("result") List<IndexedDoc> searchUsingEmbeddings(
            VectorDBInput embeddingsInput) {
        try {
            return vectorDBs.searchEmbeddings(
                    embeddingsInput.getVectorDB(),
                    TaskContext.get(),
                    embeddingsInput.getIndex(),
                    embeddingsInput.getNamespace(),
                    embeddingsInput.getEmbeddings(),
                    embeddingsInput.getMaxResults());
        } catch (Exception e) {
            log.error("Error while getting LLM embeddings: {}", e.getMessage(), e);
            throw e;
        }
    }

    // Legacy
    @Deprecated
    @WorkerTask("LLM_GET_EMBEDDINGS")
    public @OutputParam("result") List<IndexedDoc> searchUsingEmbeddingsDeprecated(
            VectorDBInput embeddingsInput) {
        return searchUsingEmbeddings(embeddingsInput);
    }

    @WorkerTask(value = "LLM_SEARCH_INDEX")
    public List<IndexedDoc> searchIndex(VectorDBInput input) {
        try {
            List<Float> embeds =
                    generateEmbeddings(
                            TaskContext.get().getTask(),
                            input.getEmbeddingModelProvider(),
                            input.getEmbeddingModel(),
                            input.getQuery(),
                            input.getDimensions());
            return vectorDBs.searchEmbeddings(
                    input.getVectorDB(),
                    TaskContext.get(),
                    input.getIndex(),
                    input.getNamespace(),
                    embeds,
                    input.getMaxResults());

        } catch (Exception e) {
            log.error("Error while doing VectorDB index search: {}", e.getMessage(), e);
            throw e;
        }
    }

    private List<Float> generateEmbeddings(
            Task task,
            String embeddingModelProvider,
            String embeddingModel,
            String text,
            Integer dimensions) {
        EmbeddingGenRequest request =
                EmbeddingGenRequest.builder()
                        .model(embeddingModel)
                        .dimensions(dimensions)
                        .text(text)
                        .build();
        request.setLlmProvider(embeddingModelProvider);
        return llm.generateEmbeddings(task, request);
    }
}
