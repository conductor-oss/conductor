/*
 * Copyright 2022 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.annotationsprocessor.protogen.types;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

public class MessageType extends AbstractType {
    private String protoFilePath;

    public MessageType(Type javaType, ClassName javaProtoType, String protoFilePath) {
        super(javaType, javaProtoType);
        this.protoFilePath = protoFilePath;
    }

    @Override
    public String getProtoType() {
        List<String> classes = ((ClassName) getJavaProtoType()).simpleNames();
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
        if (!isEnum()) method.beginControlFlow("if (from.$L())", protoMethodName("has", field));

        method.addStatement(
                "to.$L( fromProto( from.$L() ) )",
                javaMethodName("set", field),
                protoMethodName("get", field));

        if (!isEnum()) method.endControlFlow();
    }

    @Override
    public void getDependencies(Set<String> deps) {
        deps.add(protoFilePath);
    }

    @Override
    public void generateAbstractMethods(Set<MethodSpec> specs) {}
}
