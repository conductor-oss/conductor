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
package org.conductoross.conductor.ai.providers.gemini;

import java.util.Base64;
import java.util.List;

import org.conductoross.conductor.ai.providers.gemini.api.GeminiApi;
import org.conductoross.conductor.ai.providers.gemini.api.GeminiApi.Candidate;
import org.conductoross.conductor.ai.providers.gemini.api.GeminiApi.Content;
import org.conductoross.conductor.ai.providers.gemini.api.GeminiApi.GenerateContentResponse;
import org.conductoross.conductor.ai.providers.gemini.api.GeminiApi.Part;
import org.conductoross.conductor.ai.providers.gemini.api.GeminiApi.UsageMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that image media on a {@link UserMessage} is forwarded to the Gemini API as an inline
 * data {@link Part}. Regression test for a bug where {@code convertMessage} took only {@code
 * UserMessage.getText()} and silently dropped {@code getMedia()}, so vision-capable Gemini models
 * never received the image.
 *
 * <p>The HTTP layer is stubbed via Mockito; the contents built by {@link GeminiChatModel} are
 * captured and inspected.
 */
class GeminiChatModelMediaTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};

    @Test
    @SuppressWarnings("unchecked")
    void userMediaIsForwardedAsInlineDataPart() throws Exception {
        GeminiApi api = mock(GeminiApi.class);
        GenerateContentResponse canned =
                new GenerateContentResponse(
                        List.of(
                                new Candidate(
                                        new Content("model", List.of(Part.text("MELON7391"))),
                                        "STOP")),
                        new UsageMetadata(10, 5, null),
                        "resp_img");
        when(api.generateContent(any(), any(), any(), any(), any())).thenReturn(canned);

        GeminiChatModel chatModel = new GeminiChatModel(api);

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

        GeminiChatOptions options = GeminiChatOptions.builder().model("gemini-2.5-flash").build();
        chatModel.call(new Prompt(List.of(userMsg), options));

        ArgumentCaptor<List<Content>> captor = ArgumentCaptor.forClass(List.class);
        verify(api)
                .generateContent(eq("gemini-2.5-flash"), captor.capture(), isNull(), any(), any());
        List<Content> sentContents = captor.getValue();

        Content user =
                sentContents.stream()
                        .filter(c -> "user".equals(c.role()))
                        .reduce((a, b) -> b)
                        .orElseThrow();

        List<Part> imageParts = user.parts().stream().filter(p -> p.inlineData() != null).toList();
        assertFalse(
                imageParts.isEmpty(),
                "inline image part missing — media was dropped (the original bug)");

        Part img = imageParts.get(0);
        assertEquals("image/png", img.inlineData().mimeType());
        assertEquals(
                Base64.getEncoder().encodeToString(PNG_BYTES),
                img.inlineData().data(),
                "image bytes must be base64-encoded verbatim");

        // The text prompt must still be present alongside the image.
        assertTrue(
                user.parts().stream()
                        .anyMatch(p -> "Transcribe the text in the image.".equals(p.text())),
                "text part must accompany the image");
    }
}
