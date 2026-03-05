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
package org.conductoross.conductor.ai.document;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface DocumentLoader {

    byte[] download(String location);

    String upload(Map<String, String> headers, String contentType, byte[] data, String fileURI);

    /**
     * Upload data from an InputStream, allowing streaming of large files (e.g., video) without
     * buffering the entire content in memory.
     *
     * <p>Default implementation reads all bytes into memory and delegates to the byte[]-based
     * upload. Implementations should override this for true streaming behavior.
     */
    default String upload(
            Map<String, String> headers, String contentType, InputStream data, String fileURI) {
        try {
            return upload(headers, contentType, data.readAllBytes(), fileURI);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read InputStream for upload", e);
        }
    }

    List<String> listFiles(String location);

    boolean supports(String location);
}
