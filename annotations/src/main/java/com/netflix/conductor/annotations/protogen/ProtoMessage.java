package com.netflix.conductor.annotations.protogen;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ProtoMessage annotates a given Java class so it becomes exposed via the GRPC
 * API as a native Protocol Buffers struct.
 * The annotated class must be a POJO.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ProtoMessage {
    /**
     * Sets whether the generated mapping code will contain a helper to translate
     * the POJO for this class into the equivalent ProtoBuf object.
     * @return whether this class will generate a mapper to ProtoBuf objects
     */
    boolean toProto() default true;

    /**
     * Sets whether the generated mapping code will contain a helper to translate
     * the ProtoBuf object for this class into the equivalent POJO.
     * @return whether this class will generate a mapper from ProtoBuf objects
     */
    boolean fromProto() default true;

    /**
     * Sets whether this is a wrapper class that will be used to encapsulate complex
     * nested type interfaces. Wrapper classes are not directly exposed by the ProtoBuf
     * API and must be mapped manually.
     * @return whether this is a wrapper class
     */
    boolean wrapper() default false;
}
