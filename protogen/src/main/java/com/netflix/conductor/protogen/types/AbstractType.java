package com.netflix.conductor.protogen.types;

import com.google.common.base.CaseFormat;
import com.netflix.conductor.protogen.*;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

public abstract class AbstractType {
    private static Map<Type, AbstractType> TYPES = new HashMap<>();
    private static void addScalar(Type t, String protoType) {
        TYPES.put(t, new ScalarType(t, TypeName.get(t), protoType));
    }
    static {
        addScalar(int.class, "int32");
        addScalar(Integer.class, "int32");
        addScalar(long.class, "int64");
        addScalar(Long.class, "int64");
        addScalar(String.class, "string");
        addScalar(boolean.class, "bool");
        addScalar(Boolean.class, "bool");

        TYPES.put(Object.class, new AnyType());
    }

    static Map<Type, Class> PROTO_LIST_TYPES = new HashMap<>();
    static {
        PROTO_LIST_TYPES.put(List.class, ArrayList.class);
        PROTO_LIST_TYPES.put(Set.class, HashSet.class);
        PROTO_LIST_TYPES.put(LinkedList.class, LinkedList.class);
    }

    public static AbstractType get(Type t) {
        if (!TYPES.containsKey(t)) {
            if (t instanceof ParameterizedType) {
                Type raw = ((ParameterizedType) t).getRawType();
                if (PROTO_LIST_TYPES.containsKey(raw)) {
                    TYPES.put(t, new ListType(t));
                } else if (raw.equals(Map.class)) {
                    TYPES.put(t, new MapType(t));
                }
            }
        }
        if (!TYPES.containsKey(t)) {
            throw new IllegalArgumentException("Cannot map type: " + t);
        }
        return TYPES.get(t);
    }

    public static MessageType get(String className) {
        for (Map.Entry<Type, AbstractType> pair : TYPES.entrySet()) {
            AbstractType t = pair.getValue();
            if (t instanceof MessageType) {
                if (((Class) t.getJavaType()).getSimpleName().equals(className))
                    return (MessageType)t;
            }
        }
        return null;
    }

    public static MessageType declare(Class type, MessageType parent) {
        return declare(type, (ClassName)parent.getJavaProtoType(), parent.getProtoFile());
    }

    public static MessageType declare(Class type, ClassName parentType, File protoFile) {
        String simpleName = type.getSimpleName();
        MessageType t = new MessageType(type, parentType.nestedClass(simpleName), protoFile);
        if (TYPES.containsKey(type)) {
            throw new IllegalArgumentException("duplicate type declaration: "+type);
        }
        TYPES.put(type, t);
        return t;
    }

    public static MessageType baseClass(ClassName className, File protoFile) {
        return new MessageType(Object.class, className, protoFile);
    }

    Type javaType;
    TypeName javaProtoType;

    AbstractType(Type javaType, TypeName javaProtoType) {
        this.javaType = javaType;
        this.javaProtoType = javaProtoType;
    }

    public Type getJavaType() {
        return javaType;
    }

    public TypeName getJavaProtoType() {
        return javaProtoType;
    }

    public abstract String getProtoType();
    public abstract TypeName getRawJavaType();
    public abstract void mapToProto(String field, MethodSpec.Builder method);
    public abstract void mapFromProto(String field, MethodSpec.Builder method);

    public abstract void getDependencies(Set<String> deps);
    public abstract void generateAbstractMethods(Set<MethodSpec> specs);

    protected String fieldMethod(String m, String field) {
        return m + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, field);
    }

}
