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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        rejectQuoteWrapped(key, value);
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
     * Rejects a credential that arrived wrapped in quote characters.
     *
     * <p>A reference with a sub-key, ${workflow.secrets.NAME.key}, is extracted from JSON and so
     * cannot pick up stray quotes. A flat one, ${workflow.secrets.NAME}, is handed over exactly as
     * stored, and a .env file read verbatim keeps the quotes a shell would have stripped. The
     * credential then reaches the provider with two extra characters and comes back as a plain
     * authentication failure, which says nothing about where to look.
     *
     * <p>Rejected rather than trimmed: no API key, client secret, or access key is quoted on
     * purpose, and silently sending a guess produces the same opaque failure this is meant to
     * prevent.
     */
    private static void rejectQuoteWrapped(String key, String value) {
        if (value == null || value.length() < 2) {
            return;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '\'' || first == '"') && first == last) {
            throw new IllegalArgumentException(
                    "Credential '"
                            + key
                            + "' starts and ends with a "
                            + first
                            + " character. The stored secret includes the quotes; a .env file read"
                            + " verbatim keeps the ones a shell would have removed. Store the value"
                            + " without them.");
        }
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

    /**
     * Guards the point where a client would otherwise fall back to the identity the server itself
     * runs as. That fallback is correct for a task that supplied no credentials — it is how managed
     * identity and instance roles are meant to work — and wrong for a task that supplied some,
     * because it silently authenticates as somebody else.
     *
     * <p>A task reaches this state without any reference surviving in the input: {@code
     * ${workflow.secrets.NAME.key}} resolves to null when the secret is missing, or holds something
     * that is not JSON with that key, so every credential arrives blank and no auth mode matches.
     * Nothing in the task input then shows what went wrong.
     *
     * @param authKeys the credential keys this provider can authenticate with, so unrelated keys
     *     carried in the same map (a scope override, say) do not read as a broken credential
     */
    public static void rejectPartiallyResolved(
            Map<String, String> credentials, Set<String> authKeys, String provider) {
        if (credentials == null) {
            return;
        }
        List<String> empty = new ArrayList<>();
        List<String> resolved = new ArrayList<>();
        credentials.forEach(
                (key, value) -> {
                    if (authKeys.contains(key)) {
                        (StringUtils.isBlank(value) ? empty : resolved).add(key);
                    }
                });
        if (empty.isEmpty() && resolved.isEmpty()) {
            return;
        }
        Collections.sort(empty);
        Collections.sort(resolved);

        // Which keys arrived and which did not is the whole diagnosis: an empty set points at the
        // secret, a mixed one points at the task. Reporting "none resolved" for a partly resolved
        // credential sends the reader to the wrong place, which is how this class of bug stays
        // expensive.
        String detail =
                resolved.isEmpty()
                        ? "the task set the credentials "
                                + empty
                                + " and every one of them is empty. A ${workflow.secrets.NAME.key}"
                                + " reference resolves to nothing when the secret does not exist,"
                                + " or does not hold that key."
                        : "the task set "
                                + resolved
                                + ", which resolved, alongside "
                                + empty
                                + ", which did not. That is not a complete way to authenticate.";

        throw new IllegalArgumentException(
                "No "
                        + provider
                        + " authentication mode could be built: "
                        + detail
                        + " Refusing to fall back to the identity this server runs as, which would"
                        + " authenticate as somebody else.");
    }
}
