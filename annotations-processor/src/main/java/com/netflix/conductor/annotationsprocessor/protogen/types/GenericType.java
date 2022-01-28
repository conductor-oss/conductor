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
import java.util.Set;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

abstract class GenericType extends AbstractType {
    public GenericType(Type type) {
        super(type, null);
    }

    protected Class getRawType() {
        ParameterizedType tt = (ParameterizedType) this.getJavaType();
        return (Class) tt.getRawType();
    }

    protected AbstractType resolveGenericParam(int idx) {
        ParameterizedType tt = (ParameterizedType) this.getJavaType();
        Type[] types = tt.getActualTypeArguments();

        AbstractType abstractType = TypeMapper.INSTANCE.get(types[idx]);
        if (abstractType instanceof GenericType) {
            return WrappedType.wrap((GenericType) abstractType);
        }
        return abstractType;
    }

    public abstract String getWrapperSuffix();

    public abstract AbstractType getValueType();

    public abstract TypeName resolveJavaProtoType();

    @Override
    public TypeName getRawJavaType() {
        return ClassName.get(getRawType());
    }

    @Override
    public void getDependencies(Set<String> deps) {
        getValueType().getDependencies(deps);
    }

    @Override
    public void generateAbstractMethods(Set<MethodSpec> specs) {
        getValueType().generateAbstractMethods(specs);
    }

    @Override
    public TypeName getJavaProtoType() {
        if (javaProtoType == null) {
            javaProtoType = resolveJavaProtoType();
        }
        return javaProtoType;
    }
}
