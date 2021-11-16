package com.netflix.conductor.annotations.protogen;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ProtoEnum annotates an enum type that will be exposed via the GRPC
 * API as a native Protocol Buffers enum.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ProtoEnum {
}