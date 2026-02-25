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

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {

    public enum Role {
        user,
        assistant,
        system,
        // When chat completes requests execution of tools
        tool_call,

        // Actual tool execution and its output
        tool
    }

    private Role role;
    private String message;
    private List<String> media = new ArrayList<>();
    private String mimeType;
    private List<ToolCall> toolCalls;

    public ChatMessage(Role role, String message) {
        this.role = role;
        this.message = message;
    }

    public ChatMessage(Role role, ToolCall toolCall) {
        this.role = role;
        this.toolCalls = List.of(toolCall);
    }
}
