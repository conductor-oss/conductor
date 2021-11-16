package com.netflix.conductor.annotationsprocessor.protogen.types;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import java.lang.reflect.Type;
import java.util.Set;

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

    @Override
    public void mapFromProto(String field, MethodSpec.Builder method) {
        method.addStatement("to.$L( from.$L() )",
                javaMethodName("set", field), protoMethodName("get", field));
    }

    private boolean isNullableType() {
        final Type jt = getJavaType();
        return jt.equals(Boolean.class) ||
                jt.equals(Byte.class) ||
                jt.equals(Character.class) ||
                jt.equals(Short.class) ||
                jt.equals(Integer.class) ||
                jt.equals(Long.class) ||
                jt.equals(Double.class) ||
                jt.equals(Float.class) ||
                jt.equals(String.class);
    }

    @Override
    public void mapToProto(String field, MethodSpec.Builder method) {
        final boolean nullable = isNullableType();
        String getter = (
                getJavaType().equals(boolean.class) ||
                getJavaType().equals(Boolean.class)) ?
                javaMethodName("is", field) :
                javaMethodName("get", field);

        if (nullable)
            method.beginControlFlow("if (from.$L() != null)", getter);

        method.addStatement("to.$L( from.$L() )", protoMethodName("set", field), getter);

        if (nullable)
            method.endControlFlow();
    }

    @Override
    public void getDependencies(Set<String> deps) {}

    @Override
    public void generateAbstractMethods(Set<MethodSpec> specs) {}
}
