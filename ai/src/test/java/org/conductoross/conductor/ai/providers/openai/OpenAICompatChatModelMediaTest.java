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
package org.conductoross.conductor.ai.providers.openai;

import java.util.Base64;
import java.util.List;

import org.conductoross.conductor.ai.providers.openai.api.OpenAIChatCompletionsApi;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIChatCompletionsApi.ChatCompletionRequest;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIChatCompletionsApi.ChatCompletionResult;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIChatCompletionsApi.Choice;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIChatCompletionsApi.ContentPart;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIChatCompletionsApi.MessageItem;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIChatCompletionsApi.Usage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
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
 * Verifies that image media on a {@link UserMessage} is forwarded to OpenAI-compatible providers
 * (Grok, Perplexity — both use {@link OpenAICompatChatModel}) as an {@code image_url} content part.
 * Regression test for the bug tracked in conductor-oss#1242: {@code convertMessage} took only
 * {@code UserMessage.getText()} and silently dropped {@code getMedia()}.
 *
 * <p>The HTTP layer is stubbed via Mockito; the request built by {@link OpenAICompatChatModel} is
 * captured and inspected.
 */
class OpenAICompatChatModelMediaTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};

    @Test
    void userMediaIsForwardedAsImageUrlContentPart() throws Exception {
        OpenAIChatCompletionsApi api = mock(OpenAIChatCompletionsApi.class);
        ChatCompletionResult canned =
                new ChatCompletionResult(
                        "cmpl_img",
                        "chat.completion",
                        "grok-vision",
                        List.of(new Choice(0, MessageItem.assistant("MELON7391"), "stop")),
                        new Usage(10, 5, 15));
        when(api.createChatCompletion(any(ChatCompletionRequest.class))).thenReturn(canned);

        OpenAICompatChatModel chatModel = new OpenAICompatChatModel(api);

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

        ChatOptions options = ChatOptions.builder().model("grok-vision").build();
        chatModel.call(new Prompt(List.of(userMsg), options));

        ArgumentCaptor<ChatCompletionRequest> captor =
                ArgumentCaptor.forClass(ChatCompletionRequest.class);
        verify(api).createChatCompletion(captor.capture());
        ChatCompletionRequest sent = captor.getValue();

        MessageItem user =
                sent.messages().stream()
                        .filter(m -> "user".equals(m.role()))
                        .reduce((a, b) -> b)
                        .orElseThrow();

        assertInstanceOf(
                List.class,
                user.content(),
                "user message with media must serialize to a content-part list, not a bare string");

        @SuppressWarnings("unchecked")
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
