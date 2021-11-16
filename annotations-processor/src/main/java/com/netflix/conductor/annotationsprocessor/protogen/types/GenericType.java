package com.netflix.conductor.annotationsprocessor.protogen.types;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;

abstract class GenericType extends AbstractType {
    public GenericType(Type type) {
        super(type, null);
    }

    protected Class getRawType() {
        ParameterizedType tt = (ParameterizedType)this.getJavaType();
        return (Class)tt.getRawType();
    }

    protected AbstractType resolveGenericParam(int idx) {
        ParameterizedType tt = (ParameterizedType)this.getJavaType();
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
