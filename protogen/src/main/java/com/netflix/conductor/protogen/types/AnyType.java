package com.netflix.conductor.protogen.types;

import com.squareup.javapoet.ClassName;

import java.util.Set;

public class AnyType extends MessageType {
    public AnyType() {
        super(Object.class, ClassName.get("com.google.protobuf", "Value"), null);
    }

    @Override
    public void getDependencies(Set<String> deps) {
        deps.add("google/protobuf/struct.proto");
    }

    @Override
    public String getProtoType() {
        return "google.protobuf.Value";
    }
}
