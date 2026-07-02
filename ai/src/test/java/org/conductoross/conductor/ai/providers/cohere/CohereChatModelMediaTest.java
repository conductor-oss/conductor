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
package org.conductoross.conductor.ai.providers.cohere;

import java.util.Base64;
import java.util.List;

import org.conductoross.conductor.ai.providers.cohere.api.CohereApi;
import org.conductoross.conductor.ai.providers.cohere.api.CohereApi.ChatCompletionRequest;
import org.conductoross.conductor.ai.providers.cohere.api.CohereApi.ChatCompletionResponse;
import org.conductoross.conductor.ai.providers.cohere.api.CohereApi.ChatMessage;
import org.conductoross.conductor.ai.providers.cohere.api.CohereApi.ContentPart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that image media on a {@link UserMessage} is forwarded to Cohere's v2 chat API as an
 * {@code image_url} content part. Cohere is vision-capable (e.g. {@code command-a-vision-07-2025});
 * this is the regression guard for the fix where {@code toCohereMessage} took only
 * {@code getText()} (and the request DTO's content was a bare String).
 *
 * <p>The HTTP layer is stubbed via Mockito; the request built by {@link CohereChatModel} is
 * captured and inspected.
 */
class CohereChatModelMediaTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};

    @Test
    @SuppressWarnings("unchecked")
    void userMediaIsForwardedAsImageUrlContentPart() {
        CohereApi api = mock(CohereApi.class);
        ChatCompletionResponse canned =
                new ChatCompletionResponse(
                        "id_img",
                        new ChatCompletionResponse.ResponseMessage(
                                "assistant",
                                List.of(new ChatCompletionResponse.ContentBlock("text", "MELON7391"))),
                        "COMPLETE",
                        new ChatCompletionResponse.Usage(10, 5));
        when(api.chat(any(ChatCompletionRequest.class))).thenReturn(ResponseEntity.ok(canned));

        CohereChatModel chatModel = new CohereChatModel(api);

        UserMessage userMsg =
                UserMessage.builder()
                        .text("Transcribe the text in the image.")
                        .media(
                                List.of(
                                        Media.builder()
                                                .data(PNG_BYTES)
                                                .mimeType(MimeTypeUtils.IMAGE_PNG)
                                                .build()))
                        .build();

        CohereChatOptions options =
                CohereChatOptions.builder().model("command-a-vision-07-2025").build();
        chatModel.call(new Prompt(List.of(userMsg), options));

        ArgumentCaptor<ChatCompletionRequest> captor =
                ArgumentCaptor.forClass(ChatCompletionRequest.class);
        verify(api).chat(captor.capture());
        ChatCompletionRequest sent = captor.getValue();

        ChatMessage user =
                sent.messages().stream()
                        .filter(m -> "user".equals(m.role()))
                        .reduce((a, b) -> b)
                        .orElseThrow();

        assertInstanceOf(
                List.class,
                user.content(),
                "user message with media must serialize to a content-part list, not a bare string");

        List<ContentPart> parts = (List<ContentPart>) user.content();

        List<ContentPart> imageParts =
                parts.stream().filter(p -> "image_url".equals(p.type())).toList();
        assertFalse(
                imageParts.isEmpty(),
                "image_url content part missing — media was dropped (the original bug)");

        String expected = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG_BYTES);
        assertEquals(
                expected,
                imageParts.get(0).imageUrl().url(),
                "image bytes must be sent as a base64 data URI");

        // The text prompt must still accompany the image.
        assertTrue(
                parts.stream()
                        .anyMatch(
                                p ->
                                        "text".equals(p.type())
                                                && "Transcribe the text in the image."
                                                        .equals(p.text())),
                "text content part must accompany the image");
    }
}
