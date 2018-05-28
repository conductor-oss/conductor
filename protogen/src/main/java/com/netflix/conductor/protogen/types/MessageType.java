package com.netflix.conductor.protogen.types;

import com.netflix.conductor.protogen.File;
import com.netflix.conductor.protogen.types.AbstractType;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

public class MessageType extends AbstractType {
    private File protoFile;

    public MessageType(Type javaType, ClassName javaProtoType, File protoFile) {
        super(javaType, javaProtoType);
        this.protoFile = protoFile;
    }

    @Override
    public String getProtoType() {
        List<String> classes = ((ClassName)getJavaProtoType()).simpleNames();
        return String.join(".", classes.subList(1, classes.size()));
    }

    @Override
    public TypeName getRawJavaType() {
        return getJavaProtoType();
    }

    public File getProtoFile() {
        return protoFile;
    }

    @Override
    public void mapToProto(String field, MethodSpec.Builder method) {
        method.addStatement("to.$L( toProto( from.$L() ) )",
                fieldMethod("set", field), fieldMethod("get", field));
    }

    @Override
    public void mapFromProto(String field, MethodSpec.Builder method) {
        method.addStatement("to.$L( fromProto( from.$L() ) )",
                fieldMethod("set", field), fieldMethod("get", field));
    }

    @Override
    public void getDependencies(Set<String> deps) {
        deps.add(getProtoFile().getFilePath());
    }
}
