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
package org.conductoross.conductor.ai;

import java.util.List;

import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LLMsTest {

    private AIModelProvider mockModelProvider;
    private LLMs llms;
    private AIModel mockModel;

    @BeforeEach
    void setUp() {
        mockModelProvider = mock(AIModelProvider.class);
        mockModel = mock(AIModel.class);
        when(mockModelProvider.getPayloadStoreLocation()).thenReturn("/tmp/test-payload");
        when(mockModelProvider.getModel(any())).thenReturn(mockModel);

        llms = new LLMs(List.of(), null, mockModelProvider);
    }

    @Test
    void testConstructor_setsPayloadStoreLocation() {
        assertEquals("/tmp/test-payload", llms.payloadStoreLocation);
    }

    @Test
    void testGenerateEmbeddings_delegatesToModel() {
        Task mockTask = mock(Task.class);
        when(mockTask.getWorkflowInstanceId()).thenReturn("wf-1");
        when(mockTask.getTaskId()).thenReturn("task-1");

        List<Float> expectedEmbeddings = List.of(0.1f, 0.2f, 0.3f);
        when(mockModel.generateEmbeddings(any())).thenReturn(expectedEmbeddings);

        EmbeddingGenRequest request = new EmbeddingGenRequest();
        request.setLlmProvider("openai");

        List<Float> result = llms.generateEmbeddings(mockTask, request);

        assertEquals(expectedEmbeddings, result);
        verify(mockModel).generateEmbeddings(request);
    }

    @Test
    void testGenerateEmbeddings_usesCorrectProvider() {
        Task mockTask = mock(Task.class);
        when(mockTask.getWorkflowInstanceId()).thenReturn("wf-1");
        when(mockTask.getTaskId()).thenReturn("task-1");

        when(mockModel.generateEmbeddings(any())).thenReturn(List.of(0.5f));

        EmbeddingGenRequest request = new EmbeddingGenRequest();
        request.setLlmProvider("anthropic");
        request.setModel("text-embedding-model");
        request.setText("Sample text for embedding");

        llms.generateEmbeddings(mockTask, request);

        verify(mockModelProvider).getModel(request);
    }
}
