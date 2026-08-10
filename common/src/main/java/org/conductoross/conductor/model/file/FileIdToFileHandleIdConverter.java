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
package org.conductoross.conductor.model.file;

/**
 * Converts between the bare {@code fileId} (URL path variables, {@code FileModel}, DAO/service
 * params) and the prefixed {@code fileHandleId} ({@code conductor://file/<fileId>}) used in JSON
 * DTOs. Idempotent in both directions.
 */
public final class FileIdToFileHandleIdConverter {

    public static final String PREFIX = "conductor://file/";

    private FileIdToFileHandleIdConverter() {}

    public static String toFileHandleId(String fileId) {
        return fileId.startsWith(PREFIX) ? fileId : PREFIX + fileId;
    }

    public static String toFileId(String value) {
        return value.startsWith(PREFIX) ? value.substring(PREFIX.length()) : value;
    }

    public static boolean isFileHandleId(Object value) {
        return value instanceof String s && s.startsWith(PREFIX);
    }
}
