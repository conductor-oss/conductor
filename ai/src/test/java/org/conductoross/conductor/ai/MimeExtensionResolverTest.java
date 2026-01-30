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
package org.conductoross.conductor.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.*;

class MimeExtensionResolverTest {

    // ===== getExtension() tests =====

    @ParameterizedTest
    @CsvSource({
        "image/jpeg, .jpg",
        "image/jpg, .jpg",
        "image/png, .png",
        "image/gif, .gif",
        "image/bmp, .bmp",
        "image/webp, .webp",
        "image/tiff, .tiff",
        "image/svg+xml, .svg",
        "image/x-icon, .ico",
        "image/heif, .heif",
        "image/heic, .heic"
    })
    void testGetExtension_imageMimeTypes(String mimeType, String expectedExt) {
        assertEquals(expectedExt, MimeExtensionResolver.getExtension(mimeType));
    }

    @ParameterizedTest
    @CsvSource({
        "audio/mpeg, .mp3",
        "audio/wav, .wav",
        "audio/x-wav, .wav",
        "audio/ogg, .ogg",
        "audio/flac, .flac",
        "audio/aac, .aac",
        "audio/mp4, .m4a",
        "audio/opus, .opus",
        "audio/webm, .weba",
        "audio/amr, .amr"
    })
    void testGetExtension_audioMimeTypes(String mimeType, String expectedExt) {
        assertEquals(expectedExt, MimeExtensionResolver.getExtension(mimeType));
    }

    @ParameterizedTest
    @CsvSource({
        "video/mp4, .mp4",
        "video/mpeg, .mpeg",
        "video/x-msvideo, .avi",
        "video/x-ms-wmv, .wmv",
        "video/quicktime, .mov",
        "video/webm, .webm",
        "video/3gpp, .3gp",
        "video/3gpp2, .3g2",
        "video/x-flv, .flv",
        "video/x-matroska, .mkv"
    })
    void testGetExtension_videoMimeTypes(String mimeType, String expectedExt) {
        assertEquals(expectedExt, MimeExtensionResolver.getExtension(mimeType));
    }

    @ParameterizedTest
    @CsvSource({
        "application/pdf, .pdf",
        "application/msword, .doc",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document, .docx",
        "application/vnd.ms-excel, .xls",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, .xlsx",
        "application/vnd.ms-powerpoint, .ppt",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation, .pptx",
        "application/rtf, .rtf",
        "application/zip, .zip",
        "application/x-7z-compressed, .7z",
        "application/x-rar-compressed, .rar",
        "application/json, .json",
        "application/xml, .xml"
    })
    void testGetExtension_documentMimeTypes(String mimeType, String expectedExt) {
        assertEquals(expectedExt, MimeExtensionResolver.getExtension(mimeType));
    }

    @ParameterizedTest
    @CsvSource({
        "text/plain, .txt",
        "text/html, .html",
        "text/css, .css",
        "text/csv, .csv",
        "text/javascript, .js"
    })
    void testGetExtension_textMimeTypes(String mimeType, String expectedExt) {
        assertEquals(expectedExt, MimeExtensionResolver.getExtension(mimeType));
    }

    @ParameterizedTest
    @CsvSource({
        "image/*, .jpg",
        "audio/*, .mp3",
        "video/*, .mp4",
        "text/*, .txt",
        "application/*, .bin"
    })
    void testGetExtension_wildcardMimeTypes(String mimeType, String expectedExt) {
        assertEquals(expectedExt, MimeExtensionResolver.getExtension(mimeType));
    }

    @Test
    void testGetExtension_unknownWildcard() {
        assertEquals("", MimeExtensionResolver.getExtension("unknown/*"));
    }

    @ParameterizedTest
    @CsvSource({
        "jpg, .jpg",
        ".jpg, .jpg",
        "jpeg, .jpg",
        "png, .png",
        ".png, .png",
        "mp3, .mp3",
        "pdf, .pdf",
        "docx, .docx"
    })
    void testGetExtension_extensionInput(String input, String expectedExt) {
        assertEquals(expectedExt, MimeExtensionResolver.getExtension(input));
    }

    @Test
    void testGetExtension_unknownExtension() {
        // Unknown extensions return themselves with a dot prefix
        assertEquals(".xyz", MimeExtensionResolver.getExtension("xyz"));
        assertEquals(".unknown", MimeExtensionResolver.getExtension("unknown"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testGetExtension_nullAndEmpty(String input) {
        assertEquals("", MimeExtensionResolver.getExtension(input));
    }

    @Test
    void testGetExtension_unknownMimeType() {
        assertEquals("", MimeExtensionResolver.getExtension("application/unknown"));
    }

    @Test
    void testGetExtension_caseInsensitive() {
        assertEquals(".jpg", MimeExtensionResolver.getExtension("IMAGE/JPEG"));
        assertEquals(".jpg", MimeExtensionResolver.getExtension("Image/Jpeg"));
        assertEquals(".jpg", MimeExtensionResolver.getExtension("JPG"));
    }

    @Test
    void testGetExtension_withWhitespace() {
        assertEquals(".jpg", MimeExtensionResolver.getExtension("  image/jpeg  "));
        assertEquals(".png", MimeExtensionResolver.getExtension(" png "));
    }

    // ===== getMimeType() tests =====

    @ParameterizedTest
    @CsvSource({
        "jpg, image/jpeg",
        "jpeg, image/jpeg",
        "png, image/png",
        "gif, image/gif",
        "bmp, image/bmp",
        "webp, image/webp",
        "svg, image/svg+xml",
        "ico, image/x-icon"
    })
    void testGetMimeType_imageExtensions(String ext, String expectedMime) {
        assertEquals(expectedMime, MimeExtensionResolver.getMimeType(ext));
    }

    @ParameterizedTest
    @CsvSource({
        "mp3, audio/mpeg",
        "wav, audio/x-wav",
        "ogg, audio/ogg",
        "flac, audio/flac",
        "aac, audio/aac",
        "m4a, audio/mp4"
    })
    void testGetMimeType_audioExtensions(String ext, String expectedMime) {
        assertEquals(expectedMime, MimeExtensionResolver.getMimeType(ext));
    }

    @ParameterizedTest
    @CsvSource({
        "mp4, video/mp4",
        "mpeg, video/mpeg",
        "avi, video/x-msvideo",
        "wmv, video/x-ms-wmv",
        "mov, video/quicktime",
        "webm, video/webm",
        "mkv, video/x-matroska"
    })
    void testGetMimeType_videoExtensions(String ext, String expectedMime) {
        assertEquals(expectedMime, MimeExtensionResolver.getMimeType(ext));
    }

    @ParameterizedTest
    @CsvSource({
        "pdf, application/pdf",
        "doc, application/msword",
        "docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls, application/vnd.ms-excel",
        "xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "json, application/json",
        "xml, application/xml",
        "zip, application/zip"
    })
    void testGetMimeType_documentExtensions(String ext, String expectedMime) {
        assertEquals(expectedMime, MimeExtensionResolver.getMimeType(ext));
    }

    @ParameterizedTest
    @CsvSource({
        "txt, text/plain",
        "html, text/html",
        "htm, text/html",
        "css, text/css",
        "csv, text/csv",
        "js, text/javascript"
    })
    void testGetMimeType_textExtensions(String ext, String expectedMime) {
        assertEquals(expectedMime, MimeExtensionResolver.getMimeType(ext));
    }

    @Test
    void testGetMimeType_withDotPrefix() {
        assertEquals("image/jpeg", MimeExtensionResolver.getMimeType(".jpg"));
        assertEquals("application/pdf", MimeExtensionResolver.getMimeType(".pdf"));
    }

    @Test
    void testGetMimeType_caseInsensitive() {
        assertEquals("image/jpeg", MimeExtensionResolver.getMimeType("JPG"));
        assertEquals("image/jpeg", MimeExtensionResolver.getMimeType("Jpg"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testGetMimeType_nullAndEmpty(String input) {
        assertEquals("", MimeExtensionResolver.getMimeType(input));
    }

    @Test
    void testGetMimeType_unknownExtension() {
        assertEquals("application/octet-stream", MimeExtensionResolver.getMimeType("xyz"));
        assertEquals("application/octet-stream", MimeExtensionResolver.getMimeType("unknown"));
    }
}
