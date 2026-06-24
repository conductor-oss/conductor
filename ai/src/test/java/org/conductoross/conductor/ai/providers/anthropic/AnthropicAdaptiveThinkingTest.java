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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link AnthropicChatModel#requiresAdaptiveThinking(String)}. Matching is by line name (no
 * version digits) so new releases work without a code change: the Opus / Fable / Mythos lines use
 * adaptive thinking, while Sonnet and Haiku still use the legacy enabled shape. Grounded in the
 * adaptive-thinking docs (checked 2026-06): Opus is adaptive from 4.6 on (4.7+ reject enabled),
 * Fable / Mythos are adaptive-only, Sonnet 4.6 and all Haiku accept enabled.
 */
class AnthropicAdaptiveThinkingTest {

    /** The adaptive lines -- current and future versions all match. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "claude-opus-4-6",
                "claude-opus-4-7",
                "claude-opus-4-8",
                "claude-opus-4-9", // future Opus minor -- no code change needed
                "claude-opus-5-0", // future Opus major
                "claude-fable-5",
                "claude-mythos-5",
                "claude-mythos-preview"
            })
    void adaptiveLines(String model) {
        assertTrue(
                AnthropicChatModel.requiresAdaptiveThinking(model),
                model + " is on an adaptive line; must use adaptive thinking");
    }

    /** Sonnet and Haiku still use the legacy enabled shape -> must NOT be forced to adaptive. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "claude-sonnet-4-6",
                "claude-sonnet-4-5",
                "claude-sonnet-4-0",
                "claude-sonnet-4-20250514",
                "claude-haiku-4-5",
                "claude-haiku-4-5-20251001",
                "claude-3-7-sonnet-20250219", // only Claude 3.x that thinks; enabled-only
                "claude-3-5-sonnet-20241022"
            })
    void enabledModels(String model) {
        assertFalse(
                AnthropicChatModel.requiresAdaptiveThinking(model),
                model + " uses legacy enabled; must not be forced to adaptive");
    }

    @Test
    void nullModelDefaultsToEnabled() {
        assertFalse(AnthropicChatModel.requiresAdaptiveThinking(null));
    }
}
