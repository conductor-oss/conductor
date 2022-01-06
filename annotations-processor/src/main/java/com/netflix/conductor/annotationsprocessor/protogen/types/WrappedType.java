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

import javax.lang.model.element.Modifier;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

public class WrappedType extends AbstractType {
    private AbstractType realType;
    private MessageType wrappedType;

    public static WrappedType wrap(GenericType realType) {
        Type valueType = realType.getValueType().getJavaType();
        if (!(valueType instanceof Class))
            throw new IllegalArgumentException("cannot wrap primitive type: " + valueType);

        String className = ((Class) valueType).getSimpleName() + realType.getWrapperSuffix();
        MessageType wrappedType = TypeMapper.INSTANCE.get(className);
        if (wrappedType == null)
            throw new IllegalArgumentException("missing wrapper class: " + className);
        return new WrappedType(realType, wrappedType);
    }

    public WrappedType(AbstractType realType, MessageType wrappedType) {
        super(realType.getJavaType(), wrappedType.getJavaProtoType());
        this.realType = realType;
        this.wrappedType = wrappedType;
    }

    @Override
    public String getProtoType() {
        return wrappedType.getProtoType();
    }

    @Override
    public TypeName getRawJavaType() {
        return realType.getRawJavaType();
    }

    @Override
    public void mapToProto(String field, MethodSpec.Builder method) {
        wrappedType.mapToProto(field, method);
    }

    @Override
    public void mapFromProto(String field, MethodSpec.Builder method) {
        wrappedType.mapFromProto(field, method);
    }

    @Override
    public void getDependencies(Set<String> deps) {
        this.realType.getDependencies(deps);
        this.wrappedType.getDependencies(deps);
    }

    @Override
    public void generateAbstractMethods(Set<MethodSpec> specs) {
        MethodSpec fromProto =
                MethodSpec.methodBuilder("fromProto")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(this.realType.getJavaType())
                        .addParameter(this.wrappedType.getJavaProtoType(), "in")
                        .build();

        MethodSpec toProto =
                MethodSpec.methodBuilder("toProto")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(this.wrappedType.getJavaProtoType())
                        .addParameter(this.realType.getJavaType(), "in")
                        .build();

        specs.add(fromProto);
        specs.add(toProto);
    }
}
