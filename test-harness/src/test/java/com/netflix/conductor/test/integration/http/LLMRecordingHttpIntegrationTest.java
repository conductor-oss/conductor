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
package com.netflix.conductor.test.integration.http;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.ModelConfiguration;
import org.conductoross.conductor.ai.model.EmbeddingGenRequest;
import org.conductoross.conductor.ai.providers.mock.MockLLM;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.image.ImageModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.dao.QueueDAO;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import static org.junit.jupiter.api.Assertions.*;

class LLMRecordingHttpIntegrationTest {
    private static final String TEST_PROVIDER = "recording-test-provider";
    private static final String RECORDED_ANSWER = "recorded answer";
    private static final String TASK_NAME = "recording_chat";
    private static final String CHAT_TASK_TYPE = "LLM_CHAT_COMPLETE";

    @TempDir Path directory;
    private static final AtomicInteger PROVIDER_CALLS = new AtomicInteger();

    @Test
    void recordThroughWorkflowApiThenRestartForPlayback() throws Exception {
        PROVIDER_CALLS.set(0);
        try (ServletWebServerApplicationContext server = start(true)) {
            Workflow workflow = run(server, TEST_PROVIDER);
            assertEquals(
                    RECORDED_ANSWER, workflow.getTasks().getFirst().getOutputData().get("result"));
        }
        assertEquals(1, PROVIDER_CALLS.get());
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(1, files.filter(path -> path.toString().endsWith(".json")).count());
        }
        try (ServletWebServerApplicationContext server = start(false)) {
            assertTrue(server.getBeansOfType(TestProviderConfiguration.class).isEmpty());
            Workflow workflow = run(server, MockLLM.NAME);
            assertEquals(
                    RECORDED_ANSWER, workflow.getTasks().getFirst().getOutputData().get("result"));
        }
        assertEquals(1, PROVIDER_CALLS.get(), "Playback must never invoke the test provider");
    }

    private ServletWebServerApplicationContext start(boolean recording) {
        return (ServletWebServerApplicationContext)
                new SpringApplicationBuilder(
                                ConductorTestApp.class,
                                ForkJoinSyncModeIntegrationTest.TestConfig.class,
                                TestProviderConfiguration.class)
                        .run(
                                "--spring.config.name=application,application-integrationtest",
                                "--server.port=0",
                                "--conductor.integrations.ai.enabled=true",
                                "--conductor.ai.record-mode=" + recording,
                                "--conductor.ai.enable-llm-mocks=" + !recording,
                                "--conductor.ai.recordings-directory=" + directory,
                                "--conductor.file-storage.parentDir="
                                        + directory.resolve("payload"),
                                "--test.llm.real-provider=" + recording);
    }

    private Workflow run(ServletWebServerApplicationContext server, String provider) {
        WorkflowTask task = new WorkflowTask();
        task.setName(TASK_NAME);
        task.setTaskReferenceName("chat");
        task.setType(CHAT_TASK_TYPE);
        TaskDef taskDef = new TaskDef(TASK_NAME);
        taskDef.setRetryCount(0);
        task.setTaskDefinition(taskDef);
        task.setInputParameters(
                Map.of(
                        "llmProvider",
                        provider,
                        "model",
                        MockLLM.NAME.equals(provider) ? "mockLLM" : "test-model",
                        "userInput",
                        "Say hello"));
        WorkflowDef definition = new WorkflowDef();
        definition.setName("llm_recording_api_test");
        definition.setVersion(1);
        definition.setSchemaVersion(2);
        definition.setEnforceSchema(false);
        definition.setTasks(List.of(task));
        StartWorkflowRequest request =
                new StartWorkflowRequest()
                        .withName(definition.getName())
                        .withWorkflowDef(definition);
        RestTemplate http = new RestTemplate();
        http.getMessageConverters().removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
        http.getMessageConverters()
                .add(new MappingJackson2HttpMessageConverter(server.getBean(ObjectMapper.class)));
        String baseUrl = "http://localhost:" + server.getWebServer().getPort() + "/api/workflow";
        String workflowId = http.postForObject(baseUrl, request, String.class);
        assertNotNull(workflowId);
        QueueDAO queues = server.getBean(QueueDAO.class);
        AsyncSystemTaskExecutor executor = server.getBean(AsyncSystemTaskExecutor.class);
        WorkflowSystemTask worker = server.getBean(SystemTaskRegistry.class).get(CHAT_TASK_TYPE);
        assertNotNull(worker);
        // The integration-test configuration disables background workers; drain the real task
        // executor.
        Awaitility.await()
                .atMost(20, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            for (String taskId : queues.pop(CHAT_TASK_TYPE, 10, 0))
                                executor.execute(worker, taskId);
                            server.getBean(WorkflowExecutor.class).decide(workflowId);
                            Workflow workflow =
                                    http.getForObject(baseUrl + "/" + workflowId, Workflow.class);
                            assertNotNull(workflow);
                            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
                        });
        return http.getForObject(baseUrl + "/" + workflowId, Workflow.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "test.llm.real-provider", havingValue = "true")
    static class TestProviderConfiguration implements ModelConfiguration<TestProvider> {
        @Bean
        public TestProvider get() {
            return new TestProvider();
        }

        public void setHttpClient(OkHttpClient httpClient) {}
    }

    static class TestProvider implements AIModel {
        public String getModelProvider() {
            return TEST_PROVIDER;
        }

        public ChatModel getChatModel() {
            return prompt -> {
                PROVIDER_CALLS.incrementAndGet();
                return new ChatResponse(
                        List.of(
                                new Generation(
                                        new AssistantMessage(RECORDED_ANSWER),
                                        ChatGenerationMetadata.builder()
                                                .finishReason("STOP")
                                                .build())));
            };
        }

        public ImageModel getImageModel() {
            throw new UnsupportedOperationException();
        }

        public List<Float> generateEmbeddings(EmbeddingGenRequest input) {
            throw new UnsupportedOperationException();
        }
    }
}
