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

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only verification exposed only by servers with the playback provider enabled. */
@RestController
@RequestMapping("/api/llm/playback")
@ConditionalOnProperty(prefix = "conductor.ai", name = "enable-llm-mocks", havingValue = "true")
public class LLMPlaybackController {
    private final LLMPlaybackVerifier verifier;

    public LLMPlaybackController(LLMPlaybackVerifier verifier) {
        this.verifier = verifier;
    }

    @PostMapping(
            value = "/verify",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(@RequestBody String inventory) {
        Map<String, String> expected = new LinkedHashMap<>();
        // sha256sum's text format lets a shell client send the contract without a JSON runtime.
        for (String line : inventory.lines().toList()) {
            if (line.isBlank()) continue;
            String[] parts = line.split("  ", 2);
            if (parts.length != 2 || !parts[0].matches("[0-9a-f]{64}") || parts[1].isBlank()) {
                return ResponseEntity.badRequest().body("Invalid SHA-256 recording inventory\n");
            }
            String path = parts[1].startsWith("./") ? parts[1].substring(2) : parts[1];
            if (expected.putIfAbsent(path, parts[0]) != null) {
                return ResponseEntity.badRequest().body("Duplicate recording in inventory\n");
            }
        }
        if (expected.isEmpty())
            return ResponseEntity.badRequest().body("Recording inventory is empty\n");
        LLMPlaybackVerifier.Verification result = verifier.verify(Map.copyOf(expected));
        StringBuilder report =
                new StringBuilder()
                        .append(result.complete() ? "PASS" : "FAIL")
                        .append(": ")
                        .append(result.replayedRecordings())
                        .append("/")
                        .append(result.expectedRecordings())
                        .append(" recordings played back; ")
                        .append(result.unmatchedRequests())
                        .append(" unmatched requests\n");
        result.missingRecordings()
                .forEach(path -> report.append("Missing: ").append(path).append('\n'));
        result.differentRecordings()
                .forEach(path -> report.append("Different content: ").append(path).append('\n'));
        result.notReplayedRecordings()
                .forEach(path -> report.append("Not played: ").append(path).append('\n'));
        return ResponseEntity.status(result.complete() ? 200 : 409).body(report.toString());
    }
}
