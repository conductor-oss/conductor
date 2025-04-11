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
