package com.netflix.conductor.annotationsprocessor.protogen.types;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;

import javax.lang.model.element.Modifier;
import java.lang.reflect.Type;
import java.util.Set;

public class ExternMessageType extends MessageType {
    private String externProtoType;

    public ExternMessageType(Type javaType, ClassName javaProtoType, String externProtoType, String protoFilePath) {
        super(javaType, javaProtoType, protoFilePath);
        this.externProtoType = externProtoType;
    }

    @Override
    public String getProtoType() {
        return externProtoType;
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
