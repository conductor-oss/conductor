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
package org.conductoross.conductor.ai.agentspan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.agentspan.runtime.credentials.KnownProviderEnvVars;
import dev.agentspan.runtime.model.credentials.CredentialMeta;
import dev.agentspan.runtime.spi.CredentialStoreProvider;

/**
 * Read-only, environment-seeded {@link CredentialStoreProvider} for OSS Conductor.
 *
 * <p>On construction it scans the process environment for the well-known provider variables ({@link
 * KnownProviderEnvVars#NAMES}) plus any additional names configured via {@code
 * conductor.integrations.ai.secrets.env-names}, and populates an in-memory map. Agents resolve
 * credentials from this map at runtime.
 *
 * <p>Secrets are intentionally <b>not</b> writable through the API: {@link #set} throws, with a
 * message directing operators to inject secrets as environment variables (e.g. via Kubernetes
 * secrets) and restart. The AgentSpan {@code AgentExceptionHandler} renders that {@link
 * IllegalStateException} as an HTTP 400. {@link #get}, {@link #list} and {@link #delete} operate on
 * the in-memory map; deletions are not persisted and are re-seeded from the environment on the next
 * restart.
 *
 * <p>This store is global (not per-user): the {@code userId} argument is accepted for SPI
 * compatibility but ignored, because environment-injected secrets are shared by the process.
 */
public class EnvBackedCredentialStore implements CredentialStoreProvider {

    private static final Logger log = LoggerFactory.getLogger(EnvBackedCredentialStore.class);

    static final String READ_ONLY_MESSAGE =
            "Secrets are read-only via the Conductor API. Provide them as environment variables "
                    + "(for example OPENAI_API_KEY) so they are injected at runtime, then restart the server.";

    private final Map<String, String> values = new ConcurrentHashMap<>();

    public EnvBackedCredentialStore(List<String> extraNames, UnaryOperator<String> envLookup) {
        Set<String> names = new LinkedHashSet<>(KnownProviderEnvVars.NAMES);
        if (extraNames != null) {
            names.addAll(extraNames);
        }
        int seeded = 0;
        for (String name : names) {
            String value = envLookup.apply(name);
            if (value != null && !value.isBlank()) {
                values.put(name, value);
                seeded++;
            }
        }
        log.info(
                "AgentSpan EnvBackedCredentialStore seeded {} secret(s) from {} known environment variable name(s)",
                seeded,
                names.size());
    }

    @Override
    public String get(String userId, String name) {
        return values.get(name);
    }

    @Override
    public void set(String userId, String name, String value) {
        // IllegalArgumentException maps to HTTP 400 via both Conductor's ApplicationExceptionMapper
        // and AgentSpan's AgentExceptionHandler (a read-only store is a client error, not 500).
        throw new IllegalArgumentException(READ_ONLY_MESSAGE);
    }

    @Override
    public void delete(String userId, String name) {
        values.remove(name);
    }

    @Override
    public List<CredentialMeta> list(String userId) {
        List<CredentialMeta> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            out.add(
                    CredentialMeta.builder()
                            .name(entry.getKey())
                            .partial(toPartial(entry.getValue()))
                            .build());
        }
        return out;
    }

    /** First 4 + "..." + last 4 characters, matching common API-key display conventions. */
    static String toPartial(String value) {
        if (value == null || value.length() < 8) {
            return "****...****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
