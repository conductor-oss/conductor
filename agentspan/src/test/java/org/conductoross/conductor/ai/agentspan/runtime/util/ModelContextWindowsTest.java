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
package org.conductoross.conductor.ai.agentspan.runtime.util;

import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/** Tests for ModelContextWindows static defaults (no Spring context needed). */
class ModelContextWindowsTest {

    @Test
    void getContextWindow_exactMatch_gpt4o() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("gpt-4o");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(128_000);
    }

    @Test
    void getContextWindow_prefixMatch_gpt4oWithDate() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("gpt-4o-2024-08-06");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(128_000);
    }

    @Test
    void getContextWindow_specificBeatsGeneral_gpt41Mini() {
        // gpt-4.1-mini should match its own entry (1,047,576), not gpt-4.1
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("gpt-4.1-mini");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(1_047_576);
    }

    @Test
    void getContextWindow_gpt41() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("gpt-4.1");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(1_047_576);
    }

    @Test
    void getContextWindow_gpt54() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("gpt-5.4");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(1_050_000);
    }

    @Test
    void getContextWindow_o3() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("o3");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(200_000);
    }

    @Test
    void getContextWindow_o4Mini() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("o4-mini");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(200_000);
    }

    @Test
    void getContextWindow_claudeSonnet46WithDate() {
        OptionalInt result =
                ModelContextWindows.getContextWindowFromDefaults("claude-sonnet-4-6-20260217");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(1_000_000);
    }

    @Test
    void getContextWindow_claudeOpus46() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("claude-opus-4-6");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(1_000_000);
    }

    @Test
    void getContextWindow_claudeOpus45() {
        OptionalInt result =
                ModelContextWindows.getContextWindowFromDefaults("claude-opus-4-5-20251101");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(200_000);
    }

    @Test
    void getContextWindow_claudeLegacy() {
        OptionalInt result =
                ModelContextWindows.getContextWindowFromDefaults("claude-3-opus-20240229");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(200_000);
    }

    @Test
    void getContextWindow_claudeCatchAll() {
        OptionalInt result =
                ModelContextWindows.getContextWindowFromDefaults("claude-unknown-variant");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(200_000);
    }

    @Test
    void getContextWindow_gemini20Flash() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("gemini-2.0-flash");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(1_048_576);
    }

    @Test
    void getContextWindow_gemini25Pro() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("gemini-2.5-pro");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(1_048_576);
    }

    @Test
    void getContextWindow_gemini15Pro() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("gemini-1.5-pro");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(1_048_576);
    }

    @Test
    void getContextWindow_gpt52() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("gpt-5.2");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(400_000);
    }

    @Test
    void getContextWindow_gemini25FlashLite() {
        OptionalInt result =
                ModelContextWindows.getContextWindowFromDefaults("gemini-2.5-flash-lite");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(1_048_576);
    }

    @Test
    void getContextWindow_gemini3Flash() {
        OptionalInt result =
                ModelContextWindows.getContextWindowFromDefaults("gemini-3-flash-preview");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(1_048_576);
    }

    @Test
    void getContextWindow_gemini20FlashLite() {
        OptionalInt result =
                ModelContextWindows.getContextWindowFromDefaults("gemini-2.0-flash-lite");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(1_048_576);
    }

    @Test
    void getContextWindow_unknownModel_empty() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("llama-3");
        assertThat(result).isEmpty();
    }

    @Test
    void getContextWindow_null_empty() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults(null);
        assertThat(result).isEmpty();
    }

    @Test
    void getContextWindow_blank_empty() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("");
        assertThat(result).isEmpty();
    }

    // ── Regression: gpt-5.3-codex must be known so proactive condensation
    // fires before the conversation blows past the model's context window
    // (execution cfca8846 failed at coder iteration 19 with 400
    // context_length_exceeded because this lookup returned empty).
    @Test
    void getContextWindow_exactMatch_gpt53Codex() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("gpt-5.3-codex");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(400_000);
    }

    @Test
    void getContextWindow_prefixMatch_gpt53WithSuffix() {
        OptionalInt result =
                ModelContextWindows.getContextWindowFromDefaults("gpt-5.3-codex-2026-04");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(400_000);
    }

    @Test
    void getContextWindow_prefixMatch_gpt53Plain() {
        OptionalInt result = ModelContextWindows.getContextWindowFromDefaults("gpt-5.3");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(400_000);
    }

    @Test
    void getContextWindow_catchAll_unknownGpt5Variant() {
        // Catch-all "gpt-5" entry — better a conservative 400k than empty,
        // which would silently disable proactive condensation.
        OptionalInt result =
                ModelContextWindows.getContextWindowFromDefaults("gpt-5.9-future-variant");
        assertThat(result).isPresent();
        assertThat(result.getAsInt()).isEqualTo(400_000);
    }
}
