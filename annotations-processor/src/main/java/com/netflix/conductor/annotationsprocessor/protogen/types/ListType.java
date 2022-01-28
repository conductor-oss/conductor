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
import java.util.stream.Collectors;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

public class ListType extends GenericType {
    private AbstractType valueType;

    public ListType(Type type) {
        super(type);
    }

    @Override
    public String getWrapperSuffix() {
        return "List";
    }

    @Override
    public AbstractType getValueType() {
        if (valueType == null) {
            valueType = resolveGenericParam(0);
        }
        return valueType;
    }

    @Override
    public void mapToProto(String field, MethodSpec.Builder method) {
        AbstractType subtype = getValueType();
        if (subtype instanceof ScalarType) {
            method.addStatement(
                    "to.$L( from.$L() )",
                    protoMethodName("addAll", field),
                    javaMethodName("get", field));
        } else {
            method.beginControlFlow(
                    "for ($T elem : from.$L())",
                    subtype.getJavaType(),
                    javaMethodName("get", field));
            method.addStatement("to.$L( toProto(elem) )", protoMethodName("add", field));
            method.endControlFlow();
        }
    }

    @Override
    public void mapFromProto(String field, MethodSpec.Builder method) {
        AbstractType subtype = getValueType();
        Type entryType = subtype.getJavaType();
        Class collector = TypeMapper.PROTO_LIST_TYPES.get(getRawType());

        if (subtype instanceof ScalarType) {
            if (entryType.equals(String.class)) {
                method.addStatement(
                        "to.$L( from.$L().stream().collect($T.toCollection($T::new)) )",
                        javaMethodName("set", field),
                        protoMethodName("get", field) + "List",
                        Collectors.class,
                        collector);
            } else {
                method.addStatement(
                        "to.$L( from.$L() )",
                        javaMethodName("set", field),
                        protoMethodName("get", field) + "List");
            }
        } else {
            method.addStatement(
                    "to.$L( from.$L().stream().map(this::fromProto).collect($T.toCollection($T::new)) )",
                    javaMethodName("set", field),
                    protoMethodName("get", field) + "List",
                    Collectors.class,
                    collector);
        }
    }

    @Override
    public TypeName resolveJavaProtoType() {
        return ParameterizedTypeName.get(
                (ClassName) getRawJavaType(), getValueType().getJavaProtoType());
    }

    @Override
    public String getProtoType() {
        return "repeated " + getValueType().getProtoType();
    }
}
