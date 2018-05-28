package com.netflix.conductor.protogen;

import com.netflix.conductor.common.annotations.ProtoEnum;
import com.netflix.conductor.common.annotations.ProtoMessage;
import com.netflix.conductor.protogen.types.AbstractType;
import com.netflix.conductor.protogen.types.MessageType;
import com.squareup.javapoet.TypeSpec;

import java.util.*;

public abstract class Element {
    protected Class clazz;
    protected MessageType type;
    protected List<Field> fields = new ArrayList<Field>();
    protected List<Element> nested = new ArrayList<>();

    public Element(Class cls, MessageType parentType) {
        this.clazz = cls;
        this.type = AbstractType.declare(cls, parentType);

        for (Class nested : clazz.getDeclaredClasses()) {
            if (nested.isEnum())
                addNestedEnum(nested);
            else
                addNestedClass(nested);
        }
    }

    private void addNestedEnum(Class cls) {
        ProtoEnum ann = (ProtoEnum)cls.getAnnotation(ProtoEnum.class);
        if (ann != null) {
            nested.add(new Enum(cls, this.type));
        }
    }

    private void addNestedClass(Class cls) {
        ProtoMessage ann = (ProtoMessage)cls.getAnnotation(ProtoMessage.class);
        if (ann != null) {
            nested.add(new Message(cls, this.type));
        }
    }

    public abstract String getProtoClass();
    protected abstract void javaMapToProto(TypeSpec.Builder builder);
    protected abstract void javaMapFromProto(TypeSpec.Builder builder);

    public void generateJavaMapper(TypeSpec.Builder builder) {
        javaMapToProto(builder);
        javaMapFromProto(builder);

        for (Element element : this.nested) {
            element.generateJavaMapper(builder);
        }
    }

    public void findDependencies(Set<String> dependencies) {
        for (Field field : fields) {
            field.getDependencies(dependencies);
        }

        for (Element elem : nested) {
            elem.findDependencies(dependencies);
        }
    }

    public List<Element> getNested() {
        return nested;
    }

    public List<Field> getFields() {
        return fields;
    }

    public String getName() {
        return clazz.getSimpleName();
    }

    public static abstract class Field {
        protected int protoIndex;
        protected java.lang.reflect.Field field;

        protected Field(int index, java.lang.reflect.Field field) {
            this.protoIndex = index;
            this.field = field;
        }

        public abstract String getProtoTypeDeclaration();

        public int getProtoIndex() {
            return protoIndex;
        }

        public String getName() {
            return field.getName();
        }

        public void getDependencies(Set<String> deps) {}
    }
}
