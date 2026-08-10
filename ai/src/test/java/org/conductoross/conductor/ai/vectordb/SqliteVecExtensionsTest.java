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
package org.conductoross.conductor.ai.vectordb;

import org.conductoross.conductor.ai.vectordb.sqlite.SqliteVecExtensions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link SqliteVecExtensions#bundledResourcePath()} platform detection logic. Each
 * test overrides {@code os.name} and {@code os.arch} system properties and resets them afterwards.
 */
public class SqliteVecExtensionsTest {

    /** Temporarily sets system properties, calls bundledResourcePath(), then restores. */
    private String pathFor(String osName, String osArch) {
        String prevOs = System.getProperty("os.name");
        String prevArch = System.getProperty("os.arch");
        try {
            System.setProperty("os.name", osName);
            System.setProperty("os.arch", osArch);
            return SqliteVecExtensions.bundledResourcePath();
        } finally {
            System.setProperty("os.name", prevOs != null ? prevOs : "");
            System.setProperty("os.arch", prevArch != null ? prevArch : "");
        }
    }

    @Test
    void testLinuxX86_64() {
        assertEquals("/sqlite-vec/linux-x86_64/vec0.so", pathFor("Linux", "amd64"));
    }

    @Test
    void testLinuxAarch64() {
        assertEquals("/sqlite-vec/linux-aarch64/vec0.so", pathFor("Linux", "aarch64"));
    }

    @Test
    void testMacosAarch64() {
        assertEquals("/sqlite-vec/macos-aarch64/vec0.dylib", pathFor("Mac OS X", "aarch64"));
    }

    @Test
    void testMacosX86_64() {
        assertEquals("/sqlite-vec/macos-x86_64/vec0.dylib", pathFor("Mac OS X", "amd64"));
    }

    @Test
    void testMacArm64Alias() {
        // macOS on Apple Silicon sometimes reports "arm64" rather than "aarch64"
        assertEquals("/sqlite-vec/macos-aarch64/vec0.dylib", pathFor("Mac OS X", "arm64"));
    }

    @Test
    void testWindowsX86_64() {
        assertEquals("/sqlite-vec/windows-x86_64/vec0.dll", pathFor("Windows 10", "amd64"));
    }

    @Test
    void testWindowsArm64ReturnsNull() {
        // No arm64 Windows binary published for sqlite-vec yet
        assertNull(pathFor("Windows 10", "aarch64"));
    }

    @Test
    void testUnsupportedOsReturnsNull() {
        assertNull(pathFor("FreeBSD", "amd64"));
    }

    @Test
    void testUnsupportedArchReturnsNull() {
        assertNull(pathFor("Linux", "s390x"));
    }
}
