/*
 * Copyright 2022 Orkes, Inc.
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
package com.netflix.conductor.sdk.workflow.utils;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class InputOutputGetter {

    public enum Field {
        input,
        output
    }

    public static final class Map {
        private final String parent;

        public Map(String parent) {
            this.parent = parent;
        }

        public String get(String key) {
            return parent + "." + key + "}";
        }

        public Map map(String key) {
            return new Map(parent + "." + key);
        }

        public List list(String key) {
            return new List(parent + "." + key);
        }

        @Override
        public String toString() {
            return parent + "}";
        }
    }

    public static final class List {

        private final String parent;

        public List(String parent) {
            this.parent = parent;
        }

        public List list(String key) {
            return new List(parent + "." + key);
        }

        public Map map(String key) {
            return new Map(parent + "." + key);
        }

        public String get(String key, int index) {
            return parent + "." + key + "[" + index + "]}";
        }

        public String get(int index) {
            return parent + "[" + index + "]}";
        }

        @Override
        public String toString() {
            return parent + "}";
        }
    }

    private final String name;

    private final Field field;

    public InputOutputGetter(String name, Field field) {
        this.name = name;
        this.field = field;
    }

    public String get(String key) {
        return "${" + name + "." + field + "." + key + "}";
    }

    public String getParent() {
        return "${" + name + "." + field + "}";
    }

    @JsonIgnore
    public Map map(String key) {
        return new Map("${" + name + "." + field + "." + key);
    }

    @JsonIgnore
    public List list(String key) {
        return new List("${" + name + "." + field + "." + key);
    }
}
