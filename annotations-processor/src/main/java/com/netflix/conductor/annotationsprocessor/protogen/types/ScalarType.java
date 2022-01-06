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
import java.util.Set;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

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
        method.addStatement(
                "to.$L( from.$L() )", javaMethodName("set", field), protoMethodName("get", field));
    }

    private boolean isNullableType() {
        final Type jt = getJavaType();
        return jt.equals(Boolean.class)
                || jt.equals(Byte.class)
                || jt.equals(Character.class)
                || jt.equals(Short.class)
                || jt.equals(Integer.class)
                || jt.equals(Long.class)
                || jt.equals(Double.class)
                || jt.equals(Float.class)
                || jt.equals(String.class);
    }

    @Override
    public void mapToProto(String field, MethodSpec.Builder method) {
        final boolean nullable = isNullableType();
        String getter =
                (getJavaType().equals(boolean.class) || getJavaType().equals(Boolean.class))
                        ? javaMethodName("is", field)
                        : javaMethodName("get", field);

        if (nullable) method.beginControlFlow("if (from.$L() != null)", getter);

        method.addStatement("to.$L( from.$L() )", protoMethodName("set", field), getter);

        if (nullable) method.endControlFlow();
    }

    @Override
    public void getDependencies(Set<String> deps) {}

    @Override
    public void generateAbstractMethods(Set<MethodSpec> specs) {}
}
