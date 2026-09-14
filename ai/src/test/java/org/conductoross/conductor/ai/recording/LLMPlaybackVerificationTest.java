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
package org.conductoross.conductor.ai.recording;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.providers.mock.MockLLM;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LLMPlaybackVerificationTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void recursivelyLoadsSharedExamplesAndTracksDistinctRequestsAcrossThreads() throws Exception {
        Map<String, String> inventory = new HashMap<>();
        inventory.put("01_basic/1_first.json", save("01_basic/1_first.json", "hello"));
        inventory.put("02_tools/1_second.json", save("02_tools/1_second.json", "tools"));
        MockLLM playback = new MockLLM(directory, mapper);
        assertEquals(2, playback.verify(inventory).notReplayedRecordings().size());
        playback.getChatModel().call(new Prompt("hello"));
        playback.getChatModel().call(new Prompt("hello"));
        assertFalse(playback.verify(inventory).complete());
        assertEquals(
                List.of("02_tools/1_second.json"),
                playback.verify(inventory).notReplayedRecordings());
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<ChatResponse> call = () -> playback.getChatModel().call(new Prompt("tools"));
            for (var result : executor.invokeAll(List.of(call, call))) result.get();
        }
        assertTrue(playback.verify(inventory).complete());
        assertEquals(2, playback.verify(inventory).replayedRecordings());
        assertTrue(
                new MockLLM(directory, mapper).verify(inventory).replayedRecordings() == 0,
                "A new server process must start with no coverage");
    }

    @Test
    void comparesExpectedContentWithLoadedBytesAndRequiresEveryFixture() throws Exception {
        String name = "01_basic/1_first.json";
        String digest = save(name, "hello");
        MockLLM playback = new MockLLM(directory, mapper);
        playback.getChatModel().call(new Prompt("hello"));
        assertTrue(playback.verify(Map.of(name, digest)).complete());
        String changedDigest = save(name, "changed");
        assertTrue(
                playback.verify(Map.of(name, digest)).complete(),
                "Checks the bytes actually loaded");
        assertEquals(
                List.of(name), playback.verify(Map.of(name, changedDigest)).differentRecordings());
        assertEquals(
                List.of("missing.json"),
                playback.verify(Map.of("missing.json", digest)).missingRecordings());
        assertFalse(playback.verify(Map.of()).complete());
    }

    @Test
    void identicalFixtureAliasesShareCoverageButUnmatchedRequestsFailVerification()
            throws Exception {
        String first = "01_basic/1_first.json";
        String second = "02_alias/1_same.json";
        Map<String, String> inventory =
                Map.of(first, save(first, "hello"), second, save(second, "hello"));
        MockLLM playback = new MockLLM(directory, mapper);
        playback.getChatModel().call(new Prompt("hello"));
        assertTrue(playback.verify(inventory).complete());
        assertThrows(
                NonRetryableException.class,
                () -> playback.getChatModel().call(new Prompt("unrecorded")));
        assertFalse(playback.verify(inventory).complete());
        assertEquals(1, playback.verify(inventory).unmatchedRequests());
    }

    @Test
    void endpointReturnsCiExitContractAndRejectsInvalidInventories() throws Exception {
        String file = "01_basic/1_first.json";
        String digest = save(file, "hello");
        MockLLM playback = new MockLLM(directory, mapper);
        var mvc = MockMvcBuilders.standaloneSetup(new LLMPlaybackController(playback)).build();
        String inventory = digest + "  ./" + file + "\n";
        mvc.perform(
                        post("/api/llm/playback/verify")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(inventory))
                .andExpect(status().isConflict())
                .andExpect(
                        content()
                                .string(
                                        "FAIL: 0/1 recordings played back; 0 unmatched requests\nNot played: "
                                                + file
                                                + "\n"));
        playback.getChatModel().call(new Prompt("hello"));
        mvc.perform(
                        post("/api/llm/playback/verify")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(inventory))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        "PASS: 1/1 recordings played back; 0 unmatched requests\n"));
        for (String invalid : List.of("\n", "not-an-inventory", inventory + inventory)) {
            mvc.perform(
                            post("/api/llm/playback/verify")
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .content(invalid))
                    .andExpect(status().isBadRequest());
        }
    }

    private String save(String file, String prompt) throws Exception {
        Path path = directory.resolve(file);
        Files.createDirectories(path.getParent());
        LLMRecording.Request request =
                new RecordedRequestNormalizer().normalize(new Prompt(prompt), new ChatCompletion());
        LLMRecording recording =
                new LLMRecording(
                        LLMRecording.SCHEMA_VERSION,
                        request,
                        RecordedResponseJson.write(
                                new ChatResponse(
                                        List.of(
                                                new Generation(
                                                        new AssistantMessage("saved answer"))))),
                        null);
        byte[] bytes = mapper.writeValueAsBytes(recording);
        Files.write(path, bytes);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
