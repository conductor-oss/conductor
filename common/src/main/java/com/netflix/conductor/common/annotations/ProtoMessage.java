package com.netflix.conductor.common.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ProtoMessage {
    boolean toProto() default true;
    boolean fromProto() default true;
    boolean wrapper() default false;
}
