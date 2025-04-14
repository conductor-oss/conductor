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
package com.netflix.conductor.common.metadata;

import java.util.Map;

import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class SchemaDef extends Auditable {

    public enum Type {

        JSON, AVRO, PROTOBUF
    }

    private String name;

    private final int version = 1;

    private Type type;

    // Schema definition stored here
    private Map<String, Object> data;

    // Externalized schema definition (eg. via AVRO, Protobuf registry)
    // If using Orkes Schema registry, this points to the name of the schema in the registry
    private String externalRef;

    private String testBuilder;

    public SchemaDef(String name, Type type, Map<String, Object> data, String externalRef) {
        this.name = name;
        this.type = type;
        this.data = data;
        this.externalRef = externalRef;
    }

    public static SchemaDefBuilder builder() {
        return new SchemaDefBuilder();
    }

    // Static inner Builder class
    public static class SchemaDefBuilder {
        private String name;
        private Type type;
        private Map<String, Object> data;
        private String externalRef;

        private SchemaDefBuilder() {
        }

        public SchemaDefBuilder name(String name) {
            this.name = name;
            return this;
        }

        public SchemaDefBuilder type(Type type) {
            this.type = type;
            return this;
        }

        public SchemaDefBuilder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public SchemaDefBuilder externalRef(String externalRef) {
            this.externalRef = externalRef;
            return this;
        }

        public SchemaDef build() {
            return new SchemaDef(this.name, this.type, this.data, this.externalRef);
        }


        public String toString() {
            return "SchemaDef.SchemaDefBuilder(name=" + this.name +
                    ", type=" + this.type +
                    ", data=" + this.data +
                    ", externalRef=" + this.externalRef + ")";
        }
    }
}
