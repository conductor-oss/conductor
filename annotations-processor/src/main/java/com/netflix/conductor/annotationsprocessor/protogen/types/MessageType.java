package com.netflix.conductor.annotationsprocessor.protogen.types;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

public class MessageType extends AbstractType {
    private String protoFilePath;

    public MessageType(Type javaType, ClassName javaProtoType, String protoFilePath) {
        super(javaType, javaProtoType);
        this.protoFilePath = protoFilePath;
    }

    @Override
    public String getProtoType() {
        List<String> classes = ((ClassName)getJavaProtoType()).simpleNames();
        return String.join(".", classes.subList(1, classes.size()));
    }

    public String getProtoFilePath() {
        return protoFilePath;
    }

    @Override
    public TypeName getRawJavaType() {
        return getJavaProtoType();
    }

    @Override
    public void mapToProto(String field, MethodSpec.Builder method) {
        final String getter = javaMethodName("get", field);
        method.beginControlFlow("if (from.$L() != null)", getter);
        method.addStatement("to.$L( toProto( from.$L() ) )", protoMethodName("set", field), getter);
        method.endControlFlow();
    }

    private boolean isEnum() {
        Type clazz = getJavaType();
        return (clazz instanceof Class<?>) && ((Class) clazz).isEnum();
    }

    @Override
    public void mapFromProto(String field, MethodSpec.Builder method) {
        if (!isEnum())
            method.beginControlFlow("if (from.$L())", protoMethodName("has", field));

        method.addStatement("to.$L( fromProto( from.$L() ) )",
                javaMethodName("set", field), protoMethodName("get", field));

        if (!isEnum())
            method.endControlFlow();
    }

    @Override
    public void getDependencies(Set<String> deps) {
        deps.add(protoFilePath);
    }

    @Override
    public void generateAbstractMethods(Set<MethodSpec> specs) {}
}
