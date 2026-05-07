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

import java.util.List;
import java.util.Objects;

/**
 * Payload for {@code POST /api/files/{fileId}/multipart/{uploadId}/complete}. {@code partETags} is
 * the ordered list of ETags (or backend equivalents) from each part upload.
 */
public class MultipartCompleteRequest {

    private List<String> partETags;

    public List<String> getPartETags() {
        return partETags;
    }

    public void setPartETags(List<String> partETags) {
        this.partETags = partETags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MultipartCompleteRequest that)) return false;
        return Objects.equals(partETags, that.partETags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(partETags);
    }

    @Override
    public String toString() {
        return "MultipartCompleteRequest{"
                + "partETags.size="
                + (partETags != null ? partETags.size() : 0)
                + '}';
    }
}
