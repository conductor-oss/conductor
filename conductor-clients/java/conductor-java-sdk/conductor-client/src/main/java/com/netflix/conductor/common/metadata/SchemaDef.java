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

    private Map<String, Object> data;

    private String externalRef;
}
