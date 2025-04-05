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
package com.netflix.conductor.common.model;

import lombok.Data;

public class RequestParam {
    private String name;
    private String type;  // Query, Header, Path, etc.
    private boolean required;
    private Schema schema;

    @Data
    public static class Schema {
        private String type;
        private String format;
        private Object defaultValue;

        @Override
        public String toString() {
            return "Schema{" +
                    "type='" + type + '\'' +
                    ", format='" + format + '\'' +
                    ", defaultValue=" + defaultValue +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "RequestParam{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", required=" + required +
                '}';
    }
}