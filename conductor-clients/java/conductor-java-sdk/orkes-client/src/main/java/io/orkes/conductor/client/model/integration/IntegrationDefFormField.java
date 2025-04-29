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
package io.orkes.conductor.client.model.integration;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationDefFormField {

    public enum IntegrationDefFormFieldType {
        DROPDOWN, TEXT, PASSWORD, FILE
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Option {
        private String label;
        private String value;
    }

    private IntegrationDefFormFieldType fieldType;
    private List<Option> valueOptions;
    private String label;
    private ConfigKey fieldName;
    private String value;
    private String defaultValue;
    private List<IntegrationDefFormField> dependsOn;
    private String description;
    private boolean optional;

    public static Option option(String label, String value) {
        return new Option(label, value);
    }

}
