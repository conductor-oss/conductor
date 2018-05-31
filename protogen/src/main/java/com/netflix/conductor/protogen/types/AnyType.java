package com.netflix.conductor.protogen.types;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;

import javax.lang.model.element.Modifier;
import java.util.Set;

public class AnyType extends MessageType {
    public AnyType() {
        super(Object.class, ClassName.get("com.google.protobuf", "Value"), null);
    }

    @Override
    public String getProtoType() {
        return "google.protobuf.Value";
    }

    @Override
    public void getDependencies(Set<String> deps) {
        deps.add("google/protobuf/struct.proto");
    }

    @Override
    public void generateAbstractMethods(Set<MethodSpec> specs) {
        MethodSpec fromProto = MethodSpec.methodBuilder("fromProto")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(this.getJavaType())
                .addParameter(this.getJavaProtoType(), "in")
                .build();

        MethodSpec toProto = MethodSpec.methodBuilder("toProto")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(this.getJavaProtoType())
                .addParameter(this.getJavaType(), "in")
                .build();

        specs.add(fromProto);
        specs.add(toProto);
    }
}
