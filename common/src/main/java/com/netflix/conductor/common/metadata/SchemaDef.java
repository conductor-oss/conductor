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

import com.netflix.conductor.annotations.protogen.ProtoEnum;
import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@ProtoMessage
public class SchemaDef extends Auditable {

    @ProtoEnum
    public enum Type {
        JSON,
        AVRO,
        PROTOBUF
    }

    @ProtoField(id = 1)
    @NotNull
    private String name;

    /**
     * The registry stores every schema at a version of 1 or more; a save that names none is stored
     * at 1.
     *
     * <p>Zero — the default, and what an omitted version deserialises to — means "whichever is
     * newest" when this object is a reference from a workflow or task definition. Such a reference
     * follows the registry forward as new versions are registered; name a version explicitly to pin
     * one.
     */
    @ProtoField(id = 2)
    @NotNull
    private int version;

    @ProtoField(id = 3)
    @NotNull
    private Type type;

    // Schema definition stored here
    private Map<String, Object> data;

    // Externalized schema definition (eg. via AVRO, Protobuf registry). Where a schema registry
    // resolves this, it points to the name of the schema in that registry. Nothing in this server
    // dereferences it: see SchemaService#validate, which refuses a schema carrying one.
    private String externalRef;
}
