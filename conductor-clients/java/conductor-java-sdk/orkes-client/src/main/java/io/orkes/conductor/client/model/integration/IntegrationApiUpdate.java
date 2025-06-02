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
package io.orkes.conductor.client.model.integration;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntegrationApiUpdate {

    private Map<String, Object> configuration;
    private String description;
    private Boolean enabled;
    private Long maxTokens;
    private Frequency frequency;

    @Getter
    public enum Frequency {
        DAILY("daily"), WEEKLY("weekly"), MONTHLY("monthly");

        @JsonProperty
        private final String value;

        Frequency(String value) {
            this.value = value;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Frequency fromValue(String value) {
            for (Frequency frequency : values()) {
                if (frequency.getValue().equalsIgnoreCase(value)) {
                    return frequency;
                }
            }
            throw new IllegalArgumentException("Unknown frequency: " + value);
        }
    }

}
