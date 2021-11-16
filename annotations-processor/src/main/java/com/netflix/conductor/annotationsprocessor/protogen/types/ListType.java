package com.netflix.conductor.annotationsprocessor.protogen.types;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import java.lang.reflect.Type;
import java.util.stream.Collectors;

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
            method.addStatement("to.$L( from.$L() )",
                    protoMethodName("addAll", field), javaMethodName("get", field));
        } else {
            method.beginControlFlow("for ($T elem : from.$L())",
                    subtype.getJavaType(), javaMethodName("get", field));
            method.addStatement("to.$L( toProto(elem) )",
                protoMethodName("add", field));
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
                method.addStatement("to.$L( from.$L().stream().collect($T.toCollection($T::new)) )",
                        javaMethodName("set", field), protoMethodName("get", field)+"List",
                        Collectors.class, collector);
            } else {
                method.addStatement("to.$L( from.$L() )",
                        javaMethodName("set", field), protoMethodName("get", field) + "List");
            }
        } else {
            method.addStatement("to.$L( from.$L().stream().map(this::fromProto).collect($T.toCollection($T::new)) )",
                    javaMethodName("set", field), protoMethodName("get", field)+"List",
                    Collectors.class, collector);
        }
    }

    @Override
    public TypeName resolveJavaProtoType() {
        return ParameterizedTypeName.get((ClassName)getRawJavaType(),
                getValueType().getJavaProtoType());
    }

    @Override
    public String getProtoType() {
        return "repeated " + getValueType().getProtoType();
    }
}
