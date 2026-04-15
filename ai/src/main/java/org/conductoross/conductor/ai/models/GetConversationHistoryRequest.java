/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.models;

import org.conductoross.conductor.common.Documented;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GetConversationHistoryRequest extends LLMWorkerInput {

    @Documented(usage = "Optional query by which to search past conversations")
    private String searchQuery;

    @Documented(usage = "Name of the agentic workflow", required = true)
    private String agent;

    @Documented(
            usage =
                    "Task inside the agentic workflow which contains conversation.  If not specified, the first chat complete task is used")
    private String agenticTask;

    @Documented(
            usage =
                    "Name of the user for which to fetch messages. When not given, defaults to the current user")
    private String user;

    @Documented(usage = "Number of past conversations to fetch")
    private int fetchCount = 128;

    @Documented(usage = "How many conversations to keep as is without summarizing")
    private int keepLastN = 32;

    @Documented(usage = "How many days in the past to look into for history")
    private int daysUpTo = 31;

    @Documented(usage = "If set, summarizes the conversations beyond the keepLastN value")
    private boolean summarize;

    @Documented(usage = "Prompt used to summarize the messages")
    private String summaryPrompt;
}
