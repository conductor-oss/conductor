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
package org.conductoross.conductor.ai.providers.anthropic;

import java.util.Base64;
import java.util.List;

import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.ContentBlock;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.Message;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.MessagesRequest;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.MessagesResponse;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.ResponseContentBlock;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.ResponseUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.UserMessage;
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
 * Verifies that image media on a {@link UserMessage} is forwarded to the Anthropic Messages API as
 * an {@code image} content block. Regression test for a bug where {@code convertMessage} took only
 * {@code UserMessage.getText()} and silently dropped {@code getMedia()}, so vision-capable Claude
 * models never received the image.
 *
 * <p>The HTTP layer is stubbed via Mockito; the request built by {@link AnthropicChatModel} is
 * captured and inspected.
 */
class AnthropicChatModelMediaTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};

    private static ResponseContentBlock textBlock(String text) {
        return new ResponseContentBlock("text", text, null, null, null, null, null, null);
    }

    @Test
    void userMediaIsForwardedAsImageContentBlock() throws Exception {
        AnthropicMessagesApi api = mock(AnthropicMessagesApi.class);
        MessagesResponse canned =
                new MessagesResponse(
                        "msg_img",
                        "message",
                        "assistant",
                        List.of(textBlock("MELON7391")),
                        "claude-sonnet-4-5",
                        "end_turn",
                        null,
                        new ResponseUsage(10, 5, null, null));
        when(api.createMessage(any(MessagesRequest.class))).thenReturn(canned);

        AnthropicChatModel chatModel = new AnthropicChatModel(api);

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

        AnthropicChatOptions options =
                AnthropicChatOptions.builder().model("claude-sonnet-4-5").maxTokens(1024).build();
        chatModel.call(new Prompt(List.of(userMsg), options));

        ArgumentCaptor<MessagesRequest> captor = ArgumentCaptor.forClass(MessagesRequest.class);
        verify(api).createMessage(captor.capture());
        MessagesRequest sent = captor.getValue();

        Message user = sent.messages().get(sent.messages().size() - 1);
        assertEquals("user", user.role());
        assertInstanceOf(
                List.class,
                user.content(),
                "user message with media must serialize to a content-block list, not a bare string");

        @SuppressWarnings("unchecked")
        List<ContentBlock> blocks = (List<ContentBlock>) user.content();

        List<ContentBlock> imageBlocks =
                blocks.stream().filter(b -> "image".equals(b.type())).toList();
        assertFalse(
                imageBlocks.isEmpty(),
                "image content block missing — media was dropped (the original bug)");

        ContentBlock img = imageBlocks.get(0);
        assertEquals("base64", img.source().type());
        assertEquals("image/png", img.source().mediaType());
        assertEquals(
                Base64.getEncoder().encodeToString(PNG_BYTES),
                img.source().data(),
                "image bytes must be base64-encoded verbatim");

        // The text prompt must still be present alongside the image.
        assertTrue(
                blocks.stream()
                        .anyMatch(
                                b ->
                                        "text".equals(b.type())
                                                && "Transcribe the text in the image."
                                                        .equals(b.text())),
                "text content block must accompany the image");
    }
}
