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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import com.google.protobuf.Any;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

public class TypeMapper {
    static Map<Type, Class> PROTO_LIST_TYPES = new HashMap<>();

    static {
        PROTO_LIST_TYPES.put(List.class, ArrayList.class);
        PROTO_LIST_TYPES.put(Set.class, HashSet.class);
        PROTO_LIST_TYPES.put(LinkedList.class, LinkedList.class);
    }

    public static TypeMapper INSTANCE = new TypeMapper();

    private Map<Type, AbstractType> types = new HashMap<>();

    public void addScalarType(Type t, String protoType) {
        types.put(t, new ScalarType(t, TypeName.get(t), protoType));
    }

    public void addMessageType(Class<?> t, MessageType message) {
        types.put(t, message);
    }

    public TypeMapper() {
        addScalarType(int.class, "int32");
        addScalarType(Integer.class, "int32");
        addScalarType(long.class, "int64");
        addScalarType(Long.class, "int64");
        addScalarType(String.class, "string");
        addScalarType(boolean.class, "bool");
        addScalarType(Boolean.class, "bool");

        addMessageType(
                Object.class,
                new ExternMessageType(
                        Object.class,
                        ClassName.get("com.google.protobuf", "Value"),
                        "google.protobuf.Value",
                        "google/protobuf/struct.proto"));

        addMessageType(
                Any.class,
                new ExternMessageType(
                        Any.class,
                        ClassName.get(Any.class),
                        "google.protobuf.Any",
                        "google/protobuf/any.proto"));
    }

    public AbstractType get(Type t) {
        if (!types.containsKey(t)) {
            if (t instanceof ParameterizedType) {
                Type raw = ((ParameterizedType) t).getRawType();
                if (PROTO_LIST_TYPES.containsKey(raw)) {
                    types.put(t, new ListType(t));
                } else if (raw.equals(Map.class)) {
                    types.put(t, new MapType(t));
                }
            }
        }
        if (!types.containsKey(t)) {
            throw new IllegalArgumentException("Cannot map type: " + t);
        }
        return types.get(t);
    }

    public MessageType get(String className) {
        for (Map.Entry<Type, AbstractType> pair : types.entrySet()) {
            AbstractType t = pair.getValue();
            if (t instanceof MessageType) {
                if (((Class) t.getJavaType()).getSimpleName().equals(className))
                    return (MessageType) t;
            }
        }
        return null;
    }

    public MessageType declare(Class type, MessageType parent) {
        return declare(type, (ClassName) parent.getJavaProtoType(), parent.getProtoFilePath());
    }

    public MessageType declare(Class type, ClassName parentType, String protoFilePath) {
        String simpleName = type.getSimpleName();
        MessageType t = new MessageType(type, parentType.nestedClass(simpleName), protoFilePath);
        if (types.containsKey(type)) {
            throw new IllegalArgumentException("duplicate type declaration: " + type);
        }
        types.put(type, t);
        return t;
    }

    public MessageType baseClass(ClassName className, String protoFilePath) {
        return new MessageType(Object.class, className, protoFilePath);
    }
}
