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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(
        value = "conductor.worker.document-loader.file-based.enabled",
        havingValue = "true",
        matchIfMissing = true)
@Slf4j
public class FileSystemDocumentLoader implements DocumentLoader {

    @Override
    public byte[] download(String location) {
        try {

            return Files.readAllBytes(Path.of(location.replace("file://", "")));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String upload(
            Map<String, String> headers, String contentType, byte[] data, String fileURI) {
        try {
            if (data == null) {
                return null;
            }
            Path path = Path.of(fileURI.replace("file://", ""));
            var result = path.toFile().getParentFile().mkdirs();
            Files.write(path, data);
            return "file://" + path.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Streaming upload that writes directly from an InputStream to the filesystem without buffering
     * the entire content in memory. Suitable for large files such as video.
     */
    @Override
    public String upload(
            Map<String, String> headers, String contentType, InputStream data, String fileURI) {
        try {
            if (data == null) {
                return null;
            }
            Path path = Path.of(fileURI.replace("file://", ""));
            path.toFile().getParentFile().mkdirs();
            Files.copy(data, path, StandardCopyOption.REPLACE_EXISTING);
            return "file://" + path.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> listFiles(String location) {
        try (Stream<Path> paths = Files.list(Path.of(new URI(location)))) {
            return paths.map(path -> path.toUri().toString()).toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean supports(String location) {
        return location.startsWith("file://");
    }
}
