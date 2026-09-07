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
package org.conductoross.conductor.ai.agentspan.runtime.ai;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.conductoross.conductor.ai.model.LLMWorkerInput;
import org.conductoross.conductor.ai.providers.ollama.Ollama;
import org.conductoross.conductor.ai.providers.ollama.OllamaConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.core.secrets.NoopSecretsDAO;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import okhttp3.OkHttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the real TaskContext override path without stubbing credentials or environment. */
class AgentspanAIModelProviderOllamaTest {

    @AfterEach
    void clearTaskContext() {
        TaskContext.clear();
    }

    @Test
    void getModelUsesPerAgentBaseUrlFromTheRealTaskContext() {
        AgentspanAIModelProvider provider =
                new AgentspanAIModelProvider(
                        List.of(),
                        new StandardEnvironment(),
                        new OkHttpClient(),
                        new CredentialResolutionService(new NoopSecretsDAO()));
        Task task = new Task();
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setInputData(Map.of("baseUrl", "http://per-agent-host:11434"));
        TaskContext.set(task);

        LLMWorkerInput input = new LLMWorkerInput();
        input.setLlmProvider("ollama");
        input.setModel("llama3");
        AIModel model = provider.getModel(input);

        assertThat(model).isInstanceOf(Ollama.class);
        OllamaConfiguration config =
                (OllamaConfiguration) ReflectionTestUtils.getField(model, "config");
        assertThat(config.getBaseURL()).isEqualTo("http://per-agent-host:11434");
    }
}
