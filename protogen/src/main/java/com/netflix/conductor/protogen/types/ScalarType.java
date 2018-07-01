package com.netflix.conductor.protogen.types;

import com.netflix.conductor.protogen.types.AbstractType;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import org.apache.commons.lang3.ClassUtils;

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

    private void mapCode(String field, MethodSpec.Builder method, String getter) {
        method.addStatement("to.$L( from.$L() )",
                fieldMethod("set", field), fieldMethod(getter, field));
    }

    @Override
    public void mapFromProto(String field, MethodSpec.Builder method) {
        method.addStatement("to.$L( from.$L() )",
                fieldMethod("set", field), fieldMethod("get", field));
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
                fieldMethod("is", field) :
                fieldMethod("get", field);

        if (nullable)
            method.beginControlFlow("if (from.$L() != null)", getter);

        method.addStatement("to.$L( from.$L() )", fieldMethod("set", field), getter);

        if (nullable)
            method.endControlFlow();
    }

    @Override
    public void getDependencies(Set<String> deps) {}

    @Override
    public void generateAbstractMethods(Set<MethodSpec> specs) {}
}
