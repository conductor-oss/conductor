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

import java.util.List;

import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.MessagesRequest;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.MessagesResponse;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.ResponseContentBlock;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.ResponseUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Anthropic API returns HTTP 400 unless {@code thinking.budget_tokens < max_tokens}. These
 * tests pin that the legacy {@code enabled} thinking request always leaves room for the answer.
 */
class AnthropicThinkingMaxTokensTest {

    private static MessagesRequest send(Integer maxTokens, int budget) throws Exception {
        AnthropicMessagesApi api = mock(AnthropicMessagesApi.class);
        when(api.createMessage(any(MessagesRequest.class)))
                .thenReturn(
                        new MessagesResponse(
                                "msg_1",
                                "message",
                                "assistant",
                                List.of(
                                        new ResponseContentBlock(
                                                "text", "ok", null, null, null, null, null, null)),
                                "claude-sonnet-4-5",
                                "end_turn",
                                null,
                                new ResponseUsage(1, 1, null, null)));
        var options =
                AnthropicChatOptions.builder()
                        .model("claude-sonnet-4-5")
                        .thinkingBudgetTokens(budget);
        if (maxTokens != null) {
            options.maxTokens(maxTokens);
        }
        new AnthropicChatModel(api).call(new Prompt("hi", options.build()));
        ArgumentCaptor<MessagesRequest> captor = ArgumentCaptor.forClass(MessagesRequest.class);
        verify(api).createMessage(captor.capture());
        return captor.getValue();
    }

    @Test
    void defaultMaxTokensIsRaisedAboveLargeBudget() throws Exception {
        MessagesRequest request = send(null, 10000);
        assertEquals(10000, request.thinking().budgetTokens());
        assertEquals(18192, request.maxTokens());
    }

    @Test
    void explicitMaxTokensBelowBudgetIsRaised() throws Exception {
        assertEquals(10192, send(1024, 2000).maxTokens());
    }

    @Test
    void maxTokensJustAboveBudgetIsRaised() throws Exception {
        assertEquals(18192, send(10001, 10000).maxTokens());
    }

    @Test
    void maxTokensAtAnswerFloorIsKept() throws Exception {
        assertEquals(18192, send(18192, 10000).maxTokens());
    }

    @Test
    void maxTokensAboveAnswerFloorIsKept() throws Exception {
        assertEquals(20000, send(20000, 10000).maxTokens());
    }
}
