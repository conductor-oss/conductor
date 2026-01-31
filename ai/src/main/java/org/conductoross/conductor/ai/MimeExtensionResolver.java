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
package org.conductoross.conductor.ai;

import java.util.HashMap;
import java.util.Map;

public class MimeExtensionResolver {

    private static final Map<String, String> mimeToExt = new HashMap<>();
    private static final Map<String, String> extToMime = new HashMap<>();

    static {
        // ----- IMAGE -----
        mimeToExt.put("image/jpeg", ".jpg");
        mimeToExt.put("image/jpg", ".jpg");
        mimeToExt.put("image/png", ".png");
        mimeToExt.put("image/gif", ".gif");
        mimeToExt.put("image/bmp", ".bmp");
        mimeToExt.put("image/webp", ".webp");
        mimeToExt.put("image/tiff", ".tiff");
        mimeToExt.put("image/svg+xml", ".svg");
        mimeToExt.put("image/x-icon", ".ico");
        mimeToExt.put("image/heif", ".heif");
        mimeToExt.put("image/heic", ".heic");

        // ----- AUDIO -----
        mimeToExt.put("audio/mpeg", ".mp3");
        mimeToExt.put("audio/wav", ".wav");
        mimeToExt.put("audio/x-wav", ".wav");
        mimeToExt.put("audio/ogg", ".ogg");
        mimeToExt.put("audio/flac", ".flac");
        mimeToExt.put("audio/aac", ".aac");
        mimeToExt.put("audio/mp4", ".m4a");
        mimeToExt.put("audio/opus", ".opus");
        mimeToExt.put("audio/webm", ".weba");
        mimeToExt.put("audio/amr", ".amr");

        // ----- VIDEO -----
        mimeToExt.put("video/mp4", ".mp4");
        mimeToExt.put("video/mpeg", ".mpeg");
        mimeToExt.put("video/x-msvideo", ".avi");
        mimeToExt.put("video/x-ms-wmv", ".wmv");
        mimeToExt.put("video/quicktime", ".mov");
        mimeToExt.put("video/webm", ".webm");
        mimeToExt.put("video/3gpp", ".3gp");
        mimeToExt.put("video/3gpp2", ".3g2");
        mimeToExt.put("video/x-flv", ".flv");
        mimeToExt.put("video/x-matroska", ".mkv");

        // ----- DOCUMENTS -----
        mimeToExt.put("application/pdf", ".pdf");
        mimeToExt.put("application/msword", ".doc");
        mimeToExt.put(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx");
        mimeToExt.put("application/vnd.ms-excel", ".xls");
        mimeToExt.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx");
        mimeToExt.put("application/vnd.ms-powerpoint", ".ppt");
        mimeToExt.put(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                ".pptx");
        mimeToExt.put("application/rtf", ".rtf");
        mimeToExt.put("application/zip", ".zip");
        mimeToExt.put("application/x-7z-compressed", ".7z");
        mimeToExt.put("application/x-rar-compressed", ".rar");
        mimeToExt.put("application/json", ".json");
        mimeToExt.put("application/xml", ".xml");

        // ----- TEXT / CODE -----
        mimeToExt.put("text/plain", ".txt");
        mimeToExt.put("text/html", ".html");
        mimeToExt.put("text/css", ".css");
        mimeToExt.put("text/csv", ".csv");
        mimeToExt.put("text/javascript", ".js");

        // ----- Reverse mapping (ext → mime) -----
        for (Map.Entry<String, String> e : mimeToExt.entrySet()) {
            String ext = e.getValue().replaceFirst("^\\.", "");
            extToMime.put(ext, e.getKey());
        }
        // aliases
        extToMime.put("jpg", "image/jpeg");
        extToMime.put("jpeg", "image/jpeg");
        extToMime.put("htm", "text/html");
    }

    public static String getExtension(String input) {
        if (input == null || input.isEmpty()) return "";

        input = input.trim().toLowerCase();

        // Case 1: Input looks like extension
        if (!input.contains("/") && !input.contains("*")) {
            if (input.startsWith(".")) input = input.substring(1);
            String mime = extToMime.get(input);
            if (mime != null) return mimeToExt.get(mime);
            return "." + input; // fallback
        }

        // Case 2: Wildcard MIME
        if (input.endsWith("/*")) {
            String type = input.substring(0, input.indexOf('/'));
            switch (type) {
                case "image":
                    return ".jpg";
                case "audio":
                    return ".mp3";
                case "video":
                    return ".mp4";
                case "text":
                    return ".txt";
                case "application":
                    return ".bin";
                default:
                    return "";
            }
        }

        // Case 3: Exact MIME
        return mimeToExt.getOrDefault(input, "");
    }

    public static String getMimeType(String ext) {
        if (ext == null || ext.isEmpty()) return "";
        if (ext.startsWith(".")) ext = ext.substring(1);
        return extToMime.getOrDefault(ext.toLowerCase(), "application/octet-stream");
    }

    public static void main(String[] args) {
        System.out.println(getExtension("image/png")); // .png
        System.out.println(getExtension("jpg")); // .jpg
        System.out.println(getExtension("image/*")); // .jpg
        System.out.println(getExtension("application/pdf")); // .pdf
        System.out.println(getExtension(".mp3")); // .mp3
        System.out.println(getExtension("video/*")); // .mp4
        System.out.println(
                getMimeType(
                        "docx")); // application/vnd.openxmlformats-officedocument.wordprocessingml.document
    }
}
