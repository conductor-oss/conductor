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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.LLMs;
import org.conductoross.conductor.ai.model.AudioGenRequest;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.EmbeddingGenRequest;
import org.conductoross.conductor.ai.model.ImageGenRequest;
import org.conductoross.conductor.ai.model.LLMResponse;
import org.conductoross.conductor.ai.model.TextCompletion;
import org.conductoross.conductor.ai.model.VideoGenRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives every {@code @WorkerTask} method on {@link LLMWorkers} with a stubbed {@link LLMs} so the
 * pure plumbing (input mapping, task-context wiring, video state machine, embeddings validation) is
 * covered without a live provider. Production already exercises this code via {@code
 * AIReasoningEndToEndTest} end-to-end; this file pins the fast-path unit behavior.
 */
public class LLMWorkersTest {

    private RecordingLLMs llms;
    private LLMWorkers workers;
    private Task task;

    @BeforeEach
    void setUp() {
        llms = new RecordingLLMs();
        workers = new LLMWorkers(llms);
        task = new Task();
        task.setTaskId("task-" + System.nanoTime());
        task.setWorkflowInstanceId("wf-" + System.nanoTime());
        // TaskContext.set() builds a TaskResult from the task — the TaskResult ctor reads
        // task.getStatus().ordinal(), so the status must be set before we publish.
        task.setStatus(Task.Status.IN_PROGRESS);
        // LLMWorkers reads ``TaskContext.get().getTask()`` inside each handler — the
        // SystemTaskWorker normally seeds this before invoking @WorkerTask methods. In a
        // unit test we have to do that ourselves.
        TaskContext.set(task);
    }

    @AfterEach
    void tearDown() {
        TaskContext.clear();
    }

    // =================================================================
    // LLM_CHAT_COMPLETE
    // =================================================================

    @Test
    void chatCompletion_passesInputToLLMsAndReturnsResponse() {
        // The worker is a thin shell — its job is to forward the ChatCompletion (and the
        // ThreadLocal-bound Task) into LLMs.chatComplete. Verify both ends of that contract.
        LLMResponse canned = LLMResponse.builder().result("hello").completionTokens(7).build();
        llms.stagedChatResponse = canned;

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");

        LLMResponse out = workers.chatCompletion(in);

        assertSame(canned, out, "worker must return whatever LLMs.chatComplete produced");
        assertSame(task, llms.lastChatTask, "worker must forward the TaskContext-bound task");
        assertSame(in, llms.lastChatCompletion, "worker must pass through the ChatCompletion");
    }

    // =================================================================
    // LLM_TEXT_COMPLETE — maps TextCompletion → ChatCompletion
    // =================================================================

    @Test
    void textCompletion_mapsAllFieldsOntoChatCompletionAndAddsBootstrapUserMessage() {
        // textCompletion is the only worker that synthesizes its own messages array — and
        // it has a non-trivial promptName-vs-prompt selector. The mapping has historically
        // been a source of bugs (field added on TextCompletion but missed here), so pin
        // each translation point explicitly.
        TextCompletion in = new TextCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.setTemperature(0.4);
        in.setMaxResults(2);
        in.setMaxTokens(123);
        in.setTopP(0.8);
        in.setStopWords(List.of("STOP"));
        in.setJsonOutput(true);
        in.setPrompt("ignored when promptName is set");
        in.setPromptName("greeting_v1");
        in.setPromptVersion(5);
        in.setPromptVariables(Map.of("name", "Conductor"));

        llms.stagedChatResponse = LLMResponse.builder().result("hi").build();
        LLMResponse out = workers.textCompletion(in);
        assertNotNull(out);

        ChatCompletion mapped = llms.lastChatCompletion;
        assertNotNull(mapped, "textCompletion must invoke LLMs.chatComplete");

        assertEquals("fake", mapped.getLlmProvider());
        assertEquals("fake-1", mapped.getModel());
        assertEquals(0.4, mapped.getTemperature());
        assertEquals(2, mapped.getMaxResults());
        assertEquals(123, mapped.getMaxTokens());
        assertEquals(0.8, mapped.getTopP());
        assertEquals(List.of("STOP"), mapped.getStopWords());
        assertTrue(mapped.isJsonOutput());
        assertEquals(
                "greeting_v1",
                mapped.getInstructions(),
                "promptName must win over prompt when both are set");
        assertEquals(5, mapped.getPromptVersion());
        assertEquals(Map.of("name", "Conductor"), mapped.getPromptVariables());

        // The bootstrap user turn is non-obvious — without it, providers see an
        // instructions-only payload and refuse to respond. Lock it in.
        assertEquals(
                1,
                mapped.getMessages().size(),
                "textCompletion must add a single bootstrap user message");
        assertEquals(
                "user",
                mapped.getMessages().getFirst().getRole().name(),
                "bootstrap message role must be ``user``");
        assertTrue(
                mapped.getMessages().getFirst().getMessage().toLowerCase().contains("instructions"),
                "bootstrap message must reference the instructions: "
                        + mapped.getMessages().getFirst().getMessage());
    }

    @Test
    void textCompletion_promptNameNull_fallsBackToPrompt() {
        // The ?: in textCompletion uses promptName when non-null, else prompt. Pin the
        // fallback branch directly.
        TextCompletion in = new TextCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.setPrompt("write me a haiku");
        in.setPromptName(null);

        llms.stagedChatResponse = LLMResponse.builder().result("done").build();
        workers.textCompletion(in);

        assertEquals(
                "write me a haiku",
                llms.lastChatCompletion.getInstructions(),
                "with promptName=null, the prompt field must populate ChatCompletion.instructions");
    }

    // =================================================================
    // GENERATE_IMAGE / GENERATE_AUDIO — pure delegations
    // =================================================================

    @Test
    void generateImage_forwardsToLLMs() {
        ImageGenRequest req = new ImageGenRequest();
        req.setLlmProvider("fake");
        req.setModel("fake-image");
        llms.stagedImageResponse = LLMResponse.builder().result("img").build();

        LLMResponse out = workers.generateImage(req);
        assertSame(llms.stagedImageResponse, out);
        assertSame(req, llms.lastImageReq);
        assertSame(task, llms.lastImageTask);
    }

    @Test
    void generateAudio_forwardsToLLMs() {
        AudioGenRequest req = AudioGenRequest.builder().text("hello").voice("alloy").build();
        req.setLlmProvider("fake");
        req.setModel("tts-1");
        llms.stagedAudioResponse = LLMResponse.builder().result("audio").build();

        LLMResponse out = workers.generateAudio(req);
        assertSame(llms.stagedAudioResponse, out);
        assertSame(req, llms.lastAudioReq);
        assertSame(task, llms.lastAudioTask);
    }

    // =================================================================
    // GENERATE_VIDEO — state machine
    // =================================================================

    @Test
    void generateVideo_initialCall_startsJobAndReturnsInProgress() {
        // First invocation has no jobId on the task output, so LLMWorkers must call
        // LLMs.generateVideo(...) (the "start" half) and emit IN_PROGRESS with a 5s
        // callback delay.
        llms.stagedVideoResponse =
                LLMResponse.builder().jobId("job-1").finishReason("RUNNING").build();

        VideoGenRequest req = new VideoGenRequest();
        req.setLlmProvider("fake");
        req.setModel("fake-video");

        TaskResult result = workers.generateVideo(req);

        assertEquals(TaskResult.Status.IN_PROGRESS, result.getStatus());
        assertEquals(5L, result.getCallbackAfterSeconds());
        assertEquals("job-1", result.getOutputData().get("jobId"));
        assertNotNull(llms.lastVideoStartTask, "first invocation must hit generateVideo");
        // The "checkVideoStatus" path must NOT have run yet — that's what differentiates
        // the start half from the poll half.
        assertSame(null, llms.lastVideoStatusReq);
    }

    @Test
    void generateVideo_pollWithJobIdAndStatusRunning_keepsInProgress() {
        // After the start call the worker writes ``jobId`` onto Task.outputData. On the
        // poll iteration the worker reads that back and invokes checkVideoStatus instead.
        task.getOutputData().put("jobId", "job-1");
        llms.stagedVideoStatusResponse =
                LLMResponse.builder().jobId("job-1").finishReason("RUNNING").build();

        VideoGenRequest req = new VideoGenRequest();
        req.setLlmProvider("fake");
        req.setModel("fake-video");

        TaskResult result = workers.generateVideo(req);

        // ``RUNNING`` is neither COMPLETED nor FAILED — worker must leave the status as
        // the default IN_PROGRESS so the next poll fires.
        assertEquals(TaskResult.Status.IN_PROGRESS, result.getStatus());
        assertEquals(5L, result.getCallbackAfterSeconds());
        assertNotNull(llms.lastVideoStatusReq, "poll iteration must hit checkVideoStatus");
        assertEquals(
                "job-1",
                llms.lastVideoStatusReq.getJobId(),
                "worker must thread the persisted jobId back into the status request");
    }

    @Test
    void generateVideo_pollWithStatusCompleted_marksTaskCompleted() {
        task.getOutputData().put("jobId", "job-1");
        llms.stagedVideoStatusResponse =
                LLMResponse.builder().jobId("job-1").finishReason("COMPLETED").build();

        VideoGenRequest req = new VideoGenRequest();
        TaskResult result = workers.generateVideo(req);

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
    }

    @Test
    void generateVideo_pollWithStatusFailed_marksTaskFailed() {
        task.getOutputData().put("jobId", "job-1");
        llms.stagedVideoStatusResponse =
                LLMResponse.builder().jobId("job-1").finishReason("FAILED").build();

        VideoGenRequest req = new VideoGenRequest();
        TaskResult result = workers.generateVideo(req);

        assertEquals(TaskResult.Status.FAILED, result.getStatus());
    }

    // =================================================================
    // LLM_GENERATE_EMBEDDINGS — pre-flight validation + delegation
    // =================================================================

    @Test
    void generateEmbeddings_blankText_throwsNonRetryable() {
        EmbeddingGenRequest req = new EmbeddingGenRequest();
        req.setLlmProvider("fake");
        req.setModel("fake-embed");
        req.setText("   "); // blank should be treated the same as empty/null

        NonRetryableException thrown =
                assertThrows(NonRetryableException.class, () -> workers.generateEmbeddings(req));
        assertTrue(
                thrown.getMessage().toLowerCase().contains("no input text"),
                "expected the 'No input text' guard; got: " + thrown.getMessage());
        // Validation must run BEFORE any provider call; nothing should land on the stub.
        assertFalse(llms.embeddingsCalled, "blank text must not reach LLMs.generateEmbeddings");
    }

    @Test
    void generateEmbeddings_validInput_delegatesAndReturnsList() {
        List<Float> staged = List.of(0.1f, 0.2f, 0.3f, 0.4f);
        llms.stagedEmbeddings = staged;

        EmbeddingGenRequest req = new EmbeddingGenRequest();
        req.setLlmProvider("fake");
        req.setModel("fake-embed");
        req.setText("hello");
        req.setDimensions(4);

        List<Float> got = workers.generateEmbeddings(req);
        assertSame(staged, got);
        assertTrue(llms.embeddingsCalled);
        // Worker rebuilds an EmbeddingGenRequest internally; verify the rebuild
        // preserved the model, text, dimensions, and provider.
        assertNotNull(llms.lastEmbeddingsReq);
        assertEquals("fake-embed", llms.lastEmbeddingsReq.getModel());
        assertEquals("hello", llms.lastEmbeddingsReq.getText());
        assertEquals(4, llms.lastEmbeddingsReq.getDimensions());
        assertEquals("fake", llms.lastEmbeddingsReq.getLlmProvider());
    }

    // =================================================================
    // Test double — records calls into an LLMs-shaped surface
    // =================================================================

    /**
     * Stub {@link LLMs} that lets each test stage canned return values and inspect the inputs the
     * worker forwarded. We construct {@link LLMs} with null collaborators because every public
     * method that LLMWorkers calls is overridden here — the parent constructor never dereferences
     * the dependencies.
     */
    static class RecordingLLMs extends LLMs {

        LLMResponse stagedChatResponse;
        LLMResponse stagedImageResponse;
        LLMResponse stagedAudioResponse;
        LLMResponse stagedVideoResponse;
        LLMResponse stagedVideoStatusResponse;
        List<Float> stagedEmbeddings = new ArrayList<>();

        Task lastChatTask;
        ChatCompletion lastChatCompletion;
        Task lastImageTask;
        ImageGenRequest lastImageReq;
        Task lastAudioTask;
        AudioGenRequest lastAudioReq;
        Task lastVideoStartTask;
        VideoGenRequest lastVideoStartReq;
        VideoGenRequest lastVideoStatusReq;
        EmbeddingGenRequest lastEmbeddingsReq;
        boolean embeddingsCalled;

        RecordingLLMs() {
            // The parent constructor walks the modelConfigurations list — passing an empty
            // list makes that loop a no-op, so ``Environment`` is never dereferenced and
            // ``payloadStoreLocation`` stays null. Every method LLMWorkers actually calls on
            // ``LLMs`` is overridden below, so none of those dependencies are touched at
            // runtime.
            super(new ArrayList<>(), null, emptyProvider(), null);
        }

        private static org.conductoross.conductor.ai.AIModelProvider emptyProvider() {
            return new org.conductoross.conductor.ai.AIModelProvider(new ArrayList<>(), null);
        }

        @Override
        public LLMResponse chatComplete(Task task, ChatCompletion chatCompletion) {
            lastChatTask = task;
            lastChatCompletion = chatCompletion;
            return stagedChatResponse;
        }

        @Override
        public LLMResponse generateImage(Task task, ImageGenRequest req) {
            lastImageTask = task;
            lastImageReq = req;
            return stagedImageResponse;
        }

        @Override
        public LLMResponse generateAudio(Task task, AudioGenRequest req) {
            lastAudioTask = task;
            lastAudioReq = req;
            return stagedAudioResponse;
        }

        @Override
        public LLMResponse generateVideo(Task task, VideoGenRequest req) {
            lastVideoStartTask = task;
            lastVideoStartReq = req;
            return stagedVideoResponse;
        }

        @Override
        public LLMResponse checkVideoStatus(Task task, VideoGenRequest req) {
            lastVideoStatusReq = req;
            return stagedVideoStatusResponse;
        }

        @Override
        public List<Float> generateEmbeddings(Task task, EmbeddingGenRequest req) {
            embeddingsCalled = true;
            lastEmbeddingsReq = req;
            return stagedEmbeddings;
        }
    }
}
