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
package org.conductoross.conductor.ai.pdf;

import java.util.Base64;
import java.util.List;

import org.conductoross.conductor.ai.document.DocumentLoader;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves image references (URLs, file paths, data URIs) to raw byte arrays for embedding in PDF
 * documents. Uses the existing {@link DocumentLoader} infrastructure for downloading remote and
 * local images.
 */
@Component
@Conditional(AIIntegrationEnabledCondition.class)
@Slf4j
public class PdfImageResolver {

    private final List<DocumentLoader> documentLoaders;

    public PdfImageResolver(List<DocumentLoader> documentLoaders) {
        this.documentLoaders = documentLoaders;
    }

    /**
     * Resolves an image source to its raw bytes.
     *
     * @param src the image source (data URI, http/https URL, file:// path, or relative path)
     * @param imageBaseUrl optional base URL for resolving relative paths
     * @return the image bytes, or null if resolution fails
     */
    public byte[] resolve(String src, String imageBaseUrl) {
        if (src == null || src.isBlank()) {
            return null;
        }

        try {
            // data: URIs - decode inline base64
            if (src.startsWith("data:")) {
                return decodeDataUri(src);
            }

            // Resolve relative paths against base URL
            String resolvedSrc = src;
            if (!isAbsoluteUri(src) && imageBaseUrl != null && !imageBaseUrl.isBlank()) {
                resolvedSrc =
                        imageBaseUrl.endsWith("/") ? imageBaseUrl + src : imageBaseUrl + "/" + src;
            }

            // Download via DocumentLoader
            return downloadImage(resolvedSrc);

        } catch (Exception e) {
            log.warn("Failed to resolve image '{}': {}", src, e.getMessage());
            return null;
        }
    }

    private byte[] decodeDataUri(String dataUri) {
        // Format: data:[<mediatype>][;base64],<data>
        int commaIndex = dataUri.indexOf(',');
        if (commaIndex < 0) {
            log.warn("Invalid data URI format: missing comma separator");
            return null;
        }
        String encoded = dataUri.substring(commaIndex + 1);
        return Base64.getDecoder().decode(encoded);
    }

    private boolean isAbsoluteUri(String src) {
        return src.startsWith("http://") || src.startsWith("https://") || src.startsWith("file://");
    }

    private byte[] downloadImage(String location) {
        return documentLoaders.stream()
                .filter(loader -> loader.supports(location))
                .findFirst()
                .map(
                        loader -> {
                            log.debug("Downloading image from: {}", location);
                            return loader.download(location);
                        })
                .orElseGet(
                        () -> {
                            log.warn("No DocumentLoader supports image location: {}", location);
                            return null;
                        });
    }
}
