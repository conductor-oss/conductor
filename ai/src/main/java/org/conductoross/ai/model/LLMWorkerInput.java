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
package org.conductoross.ai.model;

import java.util.List;

import lombok.Data;

@Data
public class LLMWorkerInput {

    private String llmProvider;
    private String model;
    private String prompt;
    private Double temperature;
    private Double frequencyPenalty;
    private Double topP;
    private Integer topK;
    private Double presencePenalty;
    private List<String> stopWords;
    private Integer maxTokens;
    private int maxResults = 1;
}
