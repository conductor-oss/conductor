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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * Reads the credential values Conductor substituted into a task's input.
 *
 * <p>Shared by every hosted-agent client so one credential reads the same way whichever platform it
 * is for, and so the guard against an unsubstituted reference exists in exactly one place.
 */
public final class AgentCredentials {

    private AgentCredentials() {}

    /**
     * One credential value, rejecting anything the engine did not substitute.
     *
     * <p>Conductor resolves {@code ${workflow.secrets.X}} in task input before the task runs, but
     * not when the input was offloaded to external payload storage. Passing such a value on would
     * mean sending the literal reference as a credential — and since every lookup would then miss,
     * auth would fall through to the host's own identity and the agent would silently run as
     * someone else. Fail instead.
     */
    public static String value(Map<String, String> credentials, String key) {
        if (credentials == null) {
            return null;
        }
        String value = credentials.get(key);
        if (value != null && value.contains("${workflow.secrets.")) {
            throw new IllegalArgumentException(
                    "Credential '"
                            + key
                            + "' still holds an unresolved secret reference. Conductor does not"
                            + " substitute secrets for task input held in external payload storage;"
                            + " pass the value another way rather than running as the host identity.");
        }
        return value;
    }

    /**
     * The API key, under either spelling.
     *
     * <p>Providers name this differently in their own docs — Azure writes {@code apiKey}, OpenAI
     * {@code api_key} — and a workflow author configuring a second provider should not have to
     * discover that. Both are accepted everywhere an API key is.
     */
    public static String apiKey(Map<String, String> credentials) {
        return StringUtils.defaultIfBlank(
                value(credentials, "apiKey"), value(credentials, "api_key"));
    }
}
