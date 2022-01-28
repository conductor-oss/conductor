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
import java.util.HashMap;
import java.util.Map;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

public class MapType extends GenericType {
    private AbstractType keyType;
    private AbstractType valueType;

    public MapType(Type type) {
        super(type);
    }

    @Override
    public String getWrapperSuffix() {
        return "Map";
    }

    @Override
    public AbstractType getValueType() {
        if (valueType == null) {
            valueType = resolveGenericParam(1);
        }
        return valueType;
    }

    public AbstractType getKeyType() {
        if (keyType == null) {
            keyType = resolveGenericParam(0);
        }
        return keyType;
    }

    @Override
    public void mapToProto(String field, MethodSpec.Builder method) {
        AbstractType valueType = getValueType();
        if (valueType instanceof ScalarType) {
            method.addStatement(
                    "to.$L( from.$L() )",
                    protoMethodName("putAll", field),
                    javaMethodName("get", field));
        } else {
            TypeName typeName =
                    ParameterizedTypeName.get(
                            Map.Entry.class,
                            getKeyType().getJavaType(),
                            getValueType().getJavaType());
            method.beginControlFlow(
                    "for ($T pair : from.$L().entrySet())", typeName, javaMethodName("get", field));
            method.addStatement(
                    "to.$L( pair.getKey(), toProto( pair.getValue() ) )",
                    protoMethodName("put", field));
            method.endControlFlow();
        }
    }

    @Override
    public void mapFromProto(String field, MethodSpec.Builder method) {
        AbstractType valueType = getValueType();
        if (valueType instanceof ScalarType) {
            method.addStatement(
                    "to.$L( from.$L() )",
                    javaMethodName("set", field),
                    protoMethodName("get", field) + "Map");
        } else {
            Type keyType = getKeyType().getJavaType();
            Type valueTypeJava = getValueType().getJavaType();
            TypeName valueTypePb = getValueType().getJavaProtoType();

            ParameterizedTypeName entryType =
                    ParameterizedTypeName.get(
                            ClassName.get(Map.Entry.class), TypeName.get(keyType), valueTypePb);
            ParameterizedTypeName mapType =
                    ParameterizedTypeName.get(Map.class, keyType, valueTypeJava);
            ParameterizedTypeName hashMapType =
                    ParameterizedTypeName.get(HashMap.class, keyType, valueTypeJava);
            String mapName = field + "Map";

            method.addStatement("$T $L = new $T()", mapType, mapName, hashMapType);
            method.beginControlFlow(
                    "for ($T pair : from.$L().entrySet())",
                    entryType,
                    protoMethodName("get", field) + "Map");
            method.addStatement("$L.put( pair.getKey(), fromProto( pair.getValue() ) )", mapName);
            method.endControlFlow();
            method.addStatement("to.$L($L)", javaMethodName("set", field), mapName);
        }
    }

    @Override
    public TypeName resolveJavaProtoType() {
        return ParameterizedTypeName.get(
                (ClassName) getRawJavaType(),
                getKeyType().getJavaProtoType(),
                getValueType().getJavaProtoType());
    }

    @Override
    public String getProtoType() {
        AbstractType keyType = getKeyType();
        AbstractType valueType = getValueType();
        if (!(keyType instanceof ScalarType)) {
            throw new IllegalArgumentException(
                    "cannot map non-scalar map key: " + this.getJavaType());
        }
        return String.format("map<%s, %s>", keyType.getProtoType(), valueType.getProtoType());
    }
}
