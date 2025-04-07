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

import lombok.Data;

@Data
public class LLMWorkerInput {

    private String llmProvider;
    private String model;
    private String embeddingModel;
    private String embeddingModelProvider;
    private String prompt;
    private double temperature = 0.1;
    private double topP = 0.9;
    private List<String> stopWords;
    private int maxTokens;
    private int maxResults = 1;

}