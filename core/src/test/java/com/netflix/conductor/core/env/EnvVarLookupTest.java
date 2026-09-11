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
package com.netflix.conductor.core.env;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnvVarLookupTest {

    private static final String TEST_KEY = "TEST_API_KEY_WITH_NEWLINE";

    @AfterEach
    void tearDown() {
        System.clearProperty(TEST_KEY);
    }

    @Test
    void lookupStripsTrailingNewlineAndWhitespace() {
        System.setProperty(TEST_KEY, "sk-test-12345\n");
        String result = EnvVarLookup.lookup("", TEST_KEY);
        assertEquals("sk-test-12345", result);
    }

    @Test
    void lookupReturnsNullWhenNotSet() {
        assertNull(EnvVarLookup.lookup("", "NON_EXISTENT_KEY_123456789"));
    }
}
