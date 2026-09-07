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
package org.conductoross.conductor.core.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** A closeable file download stream with its persisted content metadata. */
public final class FileContent implements AutoCloseable {

    private final InputStream inputStream;
    private final String contentType;
    private final long contentLength;

    public FileContent(InputStream inputStream, String contentType, long contentLength) {
        this.inputStream = Objects.requireNonNull(inputStream, "inputStream");
        this.contentType = contentType;
        this.contentLength = contentLength;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public String getContentType() {
        return contentType;
    }

    public long getContentLength() {
        return contentLength;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
