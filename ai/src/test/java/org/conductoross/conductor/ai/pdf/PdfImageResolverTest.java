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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PdfImageResolverTest {

    private DocumentLoader httpLoader;
    private DocumentLoader fileLoader;
    private PdfImageResolver resolver;

    @BeforeEach
    void setUp() {
        httpLoader = mock(DocumentLoader.class);
        when(httpLoader.supports("http://example.com/image.png")).thenReturn(true);
        when(httpLoader.supports("https://example.com/image.jpg")).thenReturn(true);
        when(httpLoader.supports(
                        argThat(
                                s ->
                                        s != null
                                                && (s.startsWith("http://")
                                                        || s.startsWith("https://")))))
                .thenReturn(true);

        fileLoader = mock(DocumentLoader.class);
        when(fileLoader.supports(argThat(s -> s != null && s.startsWith("file://"))))
                .thenReturn(true);

        resolver = new PdfImageResolver(List.of(httpLoader, fileLoader));
    }

    // ========== Data URI Tests ==========

    @Test
    void testResolveBase64DataUri() {
        byte[] original = {1, 2, 3, 4, 5};
        String encoded = Base64.getEncoder().encodeToString(original);
        String dataUri = "data:image/png;base64," + encoded;

        byte[] result = resolver.resolve(dataUri, null);

        assertNotNull(result);
        assertArrayEquals(original, result);
    }

    @Test
    void testResolveDataUriWithDifferentMimeType() {
        byte[] original = "SVG content".getBytes();
        String encoded = Base64.getEncoder().encodeToString(original);
        String dataUri = "data:image/svg+xml;base64," + encoded;

        byte[] result = resolver.resolve(dataUri, null);

        assertNotNull(result);
        assertArrayEquals(original, result);
    }

    @Test
    void testResolveInvalidDataUriReturnsNull() {
        // data URI without comma separator
        byte[] result = resolver.resolve("data:image/png;base64", null);
        assertNull(result);
    }

    // ========== HTTP/HTTPS URL Tests ==========

    @Test
    void testResolveHttpUrl() {
        byte[] imageBytes = {10, 20, 30};
        when(httpLoader.download("http://example.com/image.png")).thenReturn(imageBytes);

        byte[] result = resolver.resolve("http://example.com/image.png", null);

        assertNotNull(result);
        assertArrayEquals(imageBytes, result);
        verify(httpLoader).download("http://example.com/image.png");
    }

    @Test
    void testResolveHttpsUrl() {
        byte[] imageBytes = {40, 50, 60};
        when(httpLoader.download("https://example.com/image.jpg")).thenReturn(imageBytes);

        byte[] result = resolver.resolve("https://example.com/image.jpg", null);

        assertNotNull(result);
        assertArrayEquals(imageBytes, result);
    }

    @Test
    void testResolveHttpUrlFailureReturnsNull() {
        when(httpLoader.download("http://example.com/missing.png"))
                .thenThrow(new RuntimeException("404"));

        byte[] result = resolver.resolve("http://example.com/missing.png", null);

        assertNull(result);
    }

    // ========== File URL Tests ==========

    @Test
    void testResolveFileUrl() {
        byte[] imageBytes = {70, 80, 90};
        when(fileLoader.download("file:///path/to/image.png")).thenReturn(imageBytes);

        byte[] result = resolver.resolve("file:///path/to/image.png", null);

        assertNotNull(result);
        assertArrayEquals(imageBytes, result);
    }

    // ========== Relative Path Tests ==========

    @Test
    void testResolveRelativePathWithBaseUrl() {
        byte[] imageBytes = {11, 22, 33};
        when(httpLoader.download("https://cdn.example.com/assets/logo.png")).thenReturn(imageBytes);

        byte[] result = resolver.resolve("logo.png", "https://cdn.example.com/assets/");

        assertNotNull(result);
        assertArrayEquals(imageBytes, result);
        verify(httpLoader).download("https://cdn.example.com/assets/logo.png");
    }

    @Test
    void testResolveRelativePathWithBaseUrlNoTrailingSlash() {
        byte[] imageBytes = {44, 55, 66};
        when(httpLoader.download("https://cdn.example.com/assets/logo.png")).thenReturn(imageBytes);

        byte[] result = resolver.resolve("logo.png", "https://cdn.example.com/assets");

        assertNotNull(result);
        verify(httpLoader).download("https://cdn.example.com/assets/logo.png");
    }

    @Test
    void testResolveRelativePathWithoutBaseUrlFails() {
        // No base URL, no loader supports bare relative path
        byte[] result = resolver.resolve("images/logo.png", null);

        assertNull(result);
    }

    @Test
    void testResolveAbsoluteUrlIgnoresBaseUrl() {
        byte[] imageBytes = {77, 88, 99};
        when(httpLoader.download("http://other.com/pic.png")).thenReturn(imageBytes);

        // Absolute URL should ignore the base URL
        byte[] result = resolver.resolve("http://other.com/pic.png", "https://cdn.example.com/");

        assertNotNull(result);
        verify(httpLoader).download("http://other.com/pic.png");
    }

    // ========== Null and Edge Case Tests ==========

    @Test
    void testResolveNullSrcReturnsNull() {
        assertNull(resolver.resolve(null, null));
    }

    @Test
    void testResolveEmptySrcReturnsNull() {
        assertNull(resolver.resolve("", null));
    }

    @Test
    void testResolveBlankSrcReturnsNull() {
        assertNull(resolver.resolve("   ", null));
    }

    @Test
    void testResolveWithNoSupportingLoaderReturnsNull() {
        PdfImageResolver emptyResolver = new PdfImageResolver(List.of());
        byte[] result = emptyResolver.resolve("http://example.com/img.png", null);
        assertNull(result);
    }
}
