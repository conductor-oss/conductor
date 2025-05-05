/* 
 * Copyright 2024 Conductor Authors.
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
package io.orkes.conductor.client.model.integration.ai;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class IndexDocInput {

    private String llmProvider;
    private String model;
    private String embeddingModelProvider;
    private String embeddingModel;
    private String vectorDB;
    private String text;
    private String docId;
    private String url;
    private String mediaType;
    private String namespace;
    private String index;
    private int chunkSize;
    private int chunkOverlap;
    private Map<String, Object> metadata;
    private Integer dimensions;

    public String getNamespace() {
        if(namespace == null) {
            return docId;
        }
        return namespace;
    }

    public int getChunkSize() {
        return chunkSize > 0 ? chunkSize : 12000;
    }

    public int getChunkOverlap() {
        return chunkOverlap > 0 ? chunkOverlap : 400;
    }
}