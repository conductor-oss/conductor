package com.netflix.conductor.protogen.types;

import com.netflix.conductor.protogen.types.AbstractType;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import java.lang.reflect.Type;

public class ScalarType extends AbstractType {
    private String protoType;

    public ScalarType(Type javaType, TypeName javaProtoType, String protoType) {
        super(javaType, javaProtoType);
        this.protoType = protoType;
    }

    @Override
    public String getProtoType() {
        return protoType;
    }

    @Override
    public TypeName getRawJavaType() {
        return getJavaProtoType();
    }

    private void mapCode(String field, MethodSpec.Builder method, String getter) {
        method.addStatement("to.$L( from.$L() )",
                fieldMethod("set", field), fieldMethod(getter, field));
    }

    @Override
    public void mapFromProto(String field, MethodSpec.Builder method) {
        mapCode(field, method, "get");
    }

    @Override
    public void mapToProto(String field, MethodSpec.Builder method) {
        String getter = (getJavaType().equals(boolean.class) ||
                getJavaType().equals(Boolean.class)) ? "is" : "get";
        mapCode(field, method, getter);
    }
}
