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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.agentspan.runtime.model.credentials.CredentialMeta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvBackedCredentialStoreTest {

    private static final String USER = "agentspan-system";

    private EnvBackedCredentialStore store(Map<String, String> env, List<String> extra) {
        return new EnvBackedCredentialStore(extra, env::get);
    }

    @Test
    void seedsKnownProviderVarsFromEnvironment() {
        EnvBackedCredentialStore store =
                store(
                        Map.of("OPENAI_API_KEY", "sk-abcdefgh", "UNRELATED_VAR", "ignored"),
                        List.of());

        assertEquals("sk-abcdefgh", store.get(USER, "OPENAI_API_KEY"));
        // Not a known provider var and not in the extra list -> not seeded.
        assertNull(store.get(USER, "UNRELATED_VAR"));
    }

    @Test
    void seedsConfiguredExtraNames() {
        EnvBackedCredentialStore store =
                store(Map.of("MY_CUSTOM_SECRET", "value-1234"), List.of("MY_CUSTOM_SECRET"));

        assertEquals("value-1234", store.get(USER, "MY_CUSTOM_SECRET"));
    }

    @Test
    void listReturnsMaskedMetadataNeverPlaintext() {
        EnvBackedCredentialStore store =
                store(Map.of("OPENAI_API_KEY", "sk-test-1234567890abcdef"), List.of());

        List<CredentialMeta> list = store.list(USER);
        assertEquals(1, list.size());
        CredentialMeta meta = list.get(0);
        assertEquals("OPENAI_API_KEY", meta.getName());
        assertEquals("sk-t...cdef", meta.getPartial());
        assertTrue(!meta.getPartial().contains("567890"), "partial must not leak the full value");
    }

    @Test
    void setIsRejectedWithGuidance() {
        EnvBackedCredentialStore store = store(Map.of(), List.of());

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class, () -> store.set(USER, "ANYTHING", "v"));
        assertTrue(ex.getMessage().contains("environment variables"));
    }

    @Test
    void deleteRemovesFromInMemoryView() {
        EnvBackedCredentialStore store = store(Map.of("OPENAI_API_KEY", "sk-abcdefgh"), List.of());

        store.delete(USER, "OPENAI_API_KEY");
        assertNull(store.get(USER, "OPENAI_API_KEY"));
        assertTrue(store.list(USER).isEmpty());
    }

    @Test
    void shortValuesAreFullyMasked() {
        assertEquals("****...****", EnvBackedCredentialStore.toPartial("short"));
        assertEquals("****...****", EnvBackedCredentialStore.toPartial(null));
    }
}
