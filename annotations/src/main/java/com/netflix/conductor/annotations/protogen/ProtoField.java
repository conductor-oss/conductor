package com.netflix.conductor.annotations.protogen;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ProtoField annotates a field inside an struct with metadata on how to
 * expose it on its corresponding Protocol Buffers struct.
 * For a field to be exposed in a ProtoBuf struct, the containing struct
 * must also be annotated with a {@link ProtoMessage} or {@link ProtoEnum}
 * tag.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ProtoField {
    /**
     * Mandatory. Sets the Protocol Buffer ID for this specific field. Once a field
     * has been annotated with a given ID, the ID can never change to a different value
     * or the resulting Protocol Buffer struct will not be backwards compatible.
     * @return the numeric ID for the field
     */
    int id();
}

