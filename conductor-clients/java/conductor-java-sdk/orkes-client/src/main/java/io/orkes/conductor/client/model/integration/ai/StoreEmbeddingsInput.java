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

import java.util.List;
import java.util.Map;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StoreEmbeddingsInput extends LLMWorkerInput {

    private String vectorDB;
    private String index;
    private String namespace;
    private List<Float> embeddings;
    private String id;
    private Map<String, Object> metadata;
}