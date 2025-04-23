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
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
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

}
