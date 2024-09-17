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
package com.netflix.conductor.client.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ConductorClientRequest {

    public enum Method {
        GET, POST, PUT, DELETE, PATCH
    }

    private final Method method;
    private final String path;
    private final List<Param> pathParams;
    private final List<Param> queryParams;
    private final Map<String, String> headerParams;
    private final Object body;

    private ConductorClientRequest(Builder builder) {
        this.method = builder.method;
        this.path = builder.path;
        this.pathParams = builder.pathParams;
        this.queryParams = builder.queryParams;
        this.headerParams = builder.headerParams;
        this.body = builder.body;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Method method;
        private String path;
        private final List<Param> pathParams = new ArrayList<>();
        private final List<Param> queryParams = new ArrayList<>();
        private final Map<String, String> headerParams = new HashMap<>();
        private Object body;

        public Builder method(Method method) {
            if (method == null) {
                throw new IllegalArgumentException("Method cannot be null");
            }
            this.method = method;
            return this;
        }

        public Builder path(String path) {
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("Path cannot be null or empty");
            }
            this.path = path;
            return this;
        }

        public Builder addPathParam(String name, Integer value) {
            return addPathParam(name, Integer.toString(value));
        }

        public Builder addPathParam(String name, String value) {
            if (name == null || name.isEmpty() || value == null) {
                throw new IllegalArgumentException("Path parameter name and value cannot be null or empty");
            }
            this.pathParams.add(new Param(name, value));
            return this;
        }

        public Builder addQueryParam(String name, Long value) {
            if (value == null) {
                return this;
            }

            addQueryParam(name, Long.toString(value));
            return this;
        }

        public Builder addQueryParam(String name, Integer value) {
            if (value == null) {
                return this;
            }

            addQueryParam(name, Integer.toString(value));
            return this;
        }

        public Builder addQueryParam(String name, Boolean value) {
            if (value == null) {
                return this;
            }

            addQueryParam(name, Boolean.toString(value));
            return this;
        }

        public Builder addQueryParams(String name, List<String> values) {
            values.forEach(it -> addQueryParam(name, it));
            return this;
        }

        public Builder addQueryParam(String name, String value) {
            if (value == null) {
                return this;
            }

            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Query parameter name cannot be null or empty");
            }

            this.queryParams.add(new Param(name, value));
            return this;
        }

        public Builder addHeaderParam(String name, String value) {
            if (name == null || name.isEmpty() || value == null) {
                throw new IllegalArgumentException("Header parameter name and value cannot be null or empty");
            }

            this.headerParams.put(name, value);
            return this;
        }

        public Builder body(Object body) {
            this.body = body;
            return this;
        }

        public ConductorClientRequest build() {
            return new ConductorClientRequest(this);
        }
    }
}
