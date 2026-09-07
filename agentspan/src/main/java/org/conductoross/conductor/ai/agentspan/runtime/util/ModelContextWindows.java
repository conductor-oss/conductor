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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Registry of known model context window sizes (in tokens). Used for proactive context condensation
 * — estimating when the conversation is approaching the model's token limit.
 *
 * <p>Lookup order:
 *
 * <ol>
 *   <li>Application property: {@code agentspan.model-context-windows.<model-name>}
 *   <li>Static defaults (prefix matching)
 *   <li>Default override property: {@code agentspan.default-context-window}
 *   <li>Empty — caller falls back to reactive-only condensation
 * </ol>
 *
 * <p>To refresh model data, see:
 *
 * <ul>
 *   <li>OpenAI: https://developers.openai.com/api/docs/models
 *   <li>Anthropic: https://platform.claude.com/docs/en/about-claude/models/overview
 *   <li>Google Gemini: https://ai.google.dev/gemini-api/docs/models
 * </ul>
 */
@Component
public class ModelContextWindows {

    private final Environment environment;

    // Ordered most-specific first so prefix matching works correctly.
    // E.g. "gpt-4.1-mini" must be checked before "gpt-4.1".
    private static final Map<String, Integer> DEFAULTS = new LinkedHashMap<>();

    static {
        // OpenAI  (source: developers.openai.com/api/docs/models — March 2026)
        DEFAULTS.put("gpt-5.4", 1_050_000);
        DEFAULTS.put("gpt-5.3-codex", 400_000);
        DEFAULTS.put("gpt-5.3", 400_000);
        DEFAULTS.put("gpt-5.2", 400_000);
        DEFAULTS.put("gpt-5-mini", 400_000);
        // Catch-all for any other gpt-5.x variant — better to assume a
        // conservative 400k window and let proactive condensation fire than
        // to leave the model unknown and grow the conversation unbounded.
        DEFAULTS.put("gpt-5", 400_000);
        DEFAULTS.put("gpt-4.1-mini", 1_047_576);
        DEFAULTS.put("gpt-4.1-nano", 1_047_576);
        DEFAULTS.put("gpt-4.1", 1_047_576);
        DEFAULTS.put("o4-mini", 200_000);
        DEFAULTS.put("o3-mini", 200_000);
        DEFAULTS.put("o3", 200_000);
        DEFAULTS.put("o1-mini", 128_000);
        DEFAULTS.put("o1", 200_000);
        DEFAULTS.put("gpt-4o-mini", 128_000);
        DEFAULTS.put("gpt-4o", 128_000);
        DEFAULTS.put("gpt-4-turbo", 128_000);
        DEFAULTS.put("gpt-3.5-turbo", 16_385);

        // Anthropic  (source: platform.claude.com/docs/en/about-claude/models/overview — March
        // 2026)
        DEFAULTS.put("claude-opus-4-6", 1_000_000);
        DEFAULTS.put("claude-sonnet-4-6", 1_000_000);
        DEFAULTS.put("claude-sonnet-4-5", 1_000_000);
        DEFAULTS.put("claude-sonnet-4-0", 1_000_000);
        DEFAULTS.put("claude-opus-4-5", 200_000);
        DEFAULTS.put("claude-opus-4-1", 200_000);
        DEFAULTS.put("claude-opus-4-0", 200_000);
        DEFAULTS.put("claude-haiku-4-5", 200_000);
        DEFAULTS.put("claude-3", 200_000);
        DEFAULTS.put("claude", 200_000); // catch-all

        // Google Gemini  (source: ai.google.dev/gemini-api/docs/models — March 2026)
        DEFAULTS.put("gemini-3.1-pro", 1_048_576);
        DEFAULTS.put("gemini-3.1-flash-lite", 1_048_576);
        DEFAULTS.put("gemini-3-flash", 1_048_576);
        DEFAULTS.put("gemini-3", 1_048_576);
        DEFAULTS.put("gemini-2.5-pro", 1_048_576);
        DEFAULTS.put("gemini-2.5-flash-lite", 1_048_576);
        DEFAULTS.put("gemini-2.5-flash", 1_048_576);
        DEFAULTS.put("gemini-2.5", 1_048_576);
        DEFAULTS.put("gemini-2.0-flash-lite", 1_048_576);
        DEFAULTS.put("gemini-2.0-flash", 1_048_576);
        DEFAULTS.put("gemini-2.0", 1_048_576);
        DEFAULTS.put("gemini-1.5-pro", 1_048_576);
        DEFAULTS.put("gemini-1.5-flash", 1_048_576);
        DEFAULTS.put("gemini-1.5", 1_048_576);
        DEFAULTS.put("gemini", 1_048_576); // catch-all
    }

    public ModelContextWindows(Environment environment) {
        this.environment = environment;
    }

    /**
     * Look up the context window size for a model name.
     *
     * @param model The model name without provider prefix (e.g. "gpt-4o", not "openai/gpt-4o").
     * @return Context window in tokens, or empty if the model is unknown.
     */
    public OptionalInt getContextWindow(String model) {
        if (model == null || model.isBlank()) {
            return OptionalInt.empty();
        }

        // 1. Check property override (exact match)
        if (environment != null) {
            String prop = environment.getProperty("agentspan.model-context-windows." + model);
            if (prop != null) {
                try {
                    return OptionalInt.of(Integer.parseInt(prop.trim()));
                } catch (NumberFormatException ignored) {
                    // Fall through to defaults
                }
            }
        }

        // 2. Check static defaults (prefix match)
        OptionalInt fromDefaults = getContextWindowFromDefaults(model);
        if (fromDefaults.isPresent()) {
            return fromDefaults;
        }

        // 3. Check default override property
        if (environment != null) {
            String defaultProp = environment.getProperty("agentspan.default-context-window");
            if (defaultProp != null) {
                try {
                    return OptionalInt.of(Integer.parseInt(defaultProp.trim()));
                } catch (NumberFormatException ignored) {
                    // Fall through
                }
            }
        }

        // 4. Unknown model — no proactive condensation
        return OptionalInt.empty();
    }

    /**
     * Look up context window from static defaults only (no Spring environment). Package-private for
     * testing.
     */
    static OptionalInt getContextWindowFromDefaults(String model) {
        if (model == null || model.isBlank()) {
            return OptionalInt.empty();
        }
        for (Map.Entry<String, Integer> entry : DEFAULTS.entrySet()) {
            if (model.startsWith(entry.getKey())) {
                return OptionalInt.of(entry.getValue());
            }
        }
        return OptionalInt.empty();
    }
}
