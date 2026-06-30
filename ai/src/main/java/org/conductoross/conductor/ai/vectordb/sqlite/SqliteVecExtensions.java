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
package org.conductoross.conductor.ai.vectordb.sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the sqlite-vec {@code vec0} loadable extension bundled in this jar for the current
 * platform.
 *
 * <p>The {@code vec0.{so,dylib,dll}} binaries are downloaded and verified at build time (see the
 * {@code downloadSqliteVec} task in {@code ai/build.gradle}) and packaged as resources under {@code
 * /sqlite-vec/<platform>/}. At runtime the matching binary is extracted to a temporary file and its
 * path returned, ready to be passed to {@code load_extension}. SQLite derives the extension entry
 * point from the file name {@code vec0} (digits are skipped), which resolves to the exported {@code
 * sqlite3_vec_init} symbol, so no explicit entry point is required.
 *
 * <p>Extraction happens once per JVM; the result is cached. Returns {@code null} when no bundled
 * binary matches the host platform, in which case callers fall back to a configured path or the
 * bare name {@code vec0}.
 */
@Slf4j
public final class SqliteVecExtensions {

    private static volatile String cachedPath;
    private static final Object LOCK = new Object();

    private SqliteVecExtensions() {}

    /**
     * @return absolute path to the extracted {@code vec0} extension for this platform, or {@code
     *     null} if no bundled binary is available for it.
     */
    public static String resolveBundledExtensionPath() {
        if (cachedPath != null) {
            // Guard against the temp file being deleted (e.g. by OS cleanup) between calls.
            if (Files.exists(Paths.get(cachedPath))) {
                return cachedPath;
            }
            log.warn(
                    "Previously extracted sqlite-vec extension no longer exists at {}; re-extracting",
                    cachedPath);
            synchronized (LOCK) {
                cachedPath = null;
            }
        }
        synchronized (LOCK) {
            if (cachedPath != null) {
                return cachedPath;
            }
            String resource = bundledResourcePath();
            if (resource == null) {
                log.warn(
                        "No bundled sqlite-vec extension for os.name='{}' os.arch='{}'; "
                                + "set extensionPath or install vec0 manually",
                        System.getProperty("os.name"),
                        System.getProperty("os.arch"));
                return null;
            }
            try (InputStream in = SqliteVecExtensions.class.getResourceAsStream(resource)) {
                if (in == null) {
                    log.warn(
                            "Bundled sqlite-vec extension resource {} not found on the classpath",
                            resource);
                    return null;
                }
                String binaryName = resource.substring(resource.lastIndexOf('/') + 1);
                Path tempDir = Files.createTempDirectory("sqlite-vec");
                Path target = tempDir.resolve(binaryName);
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                target.toFile().deleteOnExit();
                tempDir.toFile().deleteOnExit();
                cachedPath = target.toAbsolutePath().toString();
                log.info("Extracted bundled sqlite-vec extension to {}", cachedPath);
                return cachedPath;
            } catch (IOException e) {
                log.warn("Failed to extract bundled sqlite-vec extension: {}", e.getMessage(), e);
                return null;
            }
        }
    }

    /**
     * Maps the current OS/architecture to the bundled resource path, or {@code null} if
     * unsupported. Package-private for testing.
     */
    public static String bundledResourcePath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        String normArch;
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            normArch = "aarch64";
        } else if (arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64")) {
            normArch = "x86_64";
        } else {
            return null;
        }

        String platform;
        String binary;
        if (os.contains("linux")) {
            platform = "linux-" + normArch;
            binary = "vec0.so";
        } else if (os.contains("mac") || os.contains("darwin")) {
            platform = "macos-" + normArch;
            binary = "vec0.dylib";
        } else if (os.contains("windows")) {
            if (!"x86_64".equals(normArch)) {
                return null; // only windows-x86_64 is published
            }
            platform = "windows-x86_64";
            binary = "vec0.dll";
        } else {
            return null;
        }
        return "/sqlite-vec/" + platform + "/" + binary;
    }
}
