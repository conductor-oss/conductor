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
package org.conductoross.conductor.ai.agentspan.runtime.ai;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.conductoross.conductor.ai.model.LLMWorkerInput;
import org.conductoross.conductor.ai.providers.ollama.Ollama;
import org.conductoross.conductor.ai.providers.ollama.OllamaConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import okhttp3.OkHttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Ollama base-URL resolution through the per-user provider path.
 *
 * <p>The server is the source of truth for provider configuration. An {@code OLLAMA_BASE_URL}
 * credential in the store (set via UI or {@code agentspan credentials set} — the no-restart prod
 * path) and a per-agent {@code base_url} must reach the model the agent calls, not silently fall
 * back to the startup default of {@code localhost:11434}.
 */
class AgentspanAIModelProviderOllamaTest {

    private static final String REMOTE_URL = "http://gpu-box:11434";

    private CredentialResolutionService credentialService;
    private AgentspanAIModelProvider provider;

    @BeforeEach
    void setUp() {
        credentialService = mock(CredentialResolutionService.class);
        Environment env = mock(Environment.class);
        when(env.getProperty(anyString(), anyString())).thenAnswer(i -> i.getArgument(1));

        provider =
                new AgentspanAIModelProvider(List.of(), env, new OkHttpClient(), credentialService);
    }

    @AfterEach
    void cleanUp() {
        TaskContext.clear();
    }

    private static LLMWorkerInput ollamaInput() {
        LLMWorkerInput input = new LLMWorkerInput();
        input.setLlmProvider("ollama");
        input.setModel("llama3");
        return input;
    }

    private static String baseUrlOf(AIModel model) {
        assertThat(model).isInstanceOf(Ollama.class);
        OllamaConfiguration config =
                (OllamaConfiguration) ReflectionTestUtils.getField(model, "config");
        return config.getBaseURL();
    }

    @Test
    void getModel_ollama_usesCredentialStoreBaseUrl() {
        when(credentialService.resolve("OLLAMA_BASE_URL")).thenReturn(REMOTE_URL);

        AIModel model = provider.getModel(ollamaInput());

        assertThat(baseUrlOf(model)).isEqualTo(REMOTE_URL);
    }

    @Test
    void getModel_ollama_usesPerAgentBaseUrlFromTaskInput() {
        when(credentialService.resolve(anyString())).thenReturn(null);
        Task task = new Task();
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setInputData(Map.of("baseUrl", "http://per-agent-host:11434"));
        TaskContext.set(task);

        AIModel model = provider.getModel(ollamaInput());

        assertThat(baseUrlOf(model)).isEqualTo("http://per-agent-host:11434");
    }
}
