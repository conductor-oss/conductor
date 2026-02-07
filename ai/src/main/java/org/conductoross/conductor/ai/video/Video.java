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
package org.conductoross.conductor.ai.video;

import java.util.Objects;

/**
 * Represents a generated video.
 *
 * <p>Mirrors Spring AI's {@code Image} class with a URL and base64-encoded data representation.
 * Exactly one of {@code url} or {@code b64Json} is typically populated depending on how the
 * provider returns the video.
 */
public class Video {

    private String url;
    private String b64Json;
    private String mimeType;

    public Video(String url, String b64Json) {
        this(url, b64Json, null);
    }

    public Video(String url, String b64Json, String mimeType) {
        this.url = url;
        this.b64Json = b64Json;
        this.mimeType = mimeType;
    }

    /** URL where the video can be accessed (e.g., GCS URI, HTTP URL). */
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    /** Base64-encoded video data. */
    public String getB64Json() {
        return b64Json;
    }

    public void setB64Json(String b64Json) {
        this.b64Json = b64Json;
    }

    /** MIME type of the content (e.g., "video/mp4", "image/webp"). */
    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Video video)) return false;
        return Objects.equals(url, video.url)
                && Objects.equals(b64Json, video.b64Json)
                && Objects.equals(mimeType, video.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, b64Json, mimeType);
    }

    @Override
    public String toString() {
        return "Video{url='%s', mimeType='%s', b64Json=%s}"
                .formatted(
                        url,
                        mimeType,
                        b64Json != null ? "[" + b64Json.length() + " chars]" : "null");
    }
}
