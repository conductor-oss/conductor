package com.netflix.conductor.annotationsprocessor.protogen;

import com.netflix.conductor.annotations.protogen.ProtoEnum;
import com.netflix.conductor.annotations.protogen.ProtoMessage;
import com.netflix.conductor.annotationsprocessor.protogen.types.MessageType;
import com.netflix.conductor.annotationsprocessor.protogen.types.TypeMapper;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class AbstractMessage {
    protected Class<?> clazz;
    protected MessageType type;
    protected List<Field> fields = new ArrayList<Field>();
    protected List<AbstractMessage> nested = new ArrayList<>();

    public AbstractMessage(Class<?> cls, MessageType parentType) {
        assert cls.isAnnotationPresent(ProtoMessage.class) ||
                cls.isAnnotationPresent(ProtoEnum.class);

        this.clazz = cls;
        this.type = TypeMapper.INSTANCE.declare(cls, parentType);

        for (Class<?> nested : clazz.getDeclaredClasses()) {
            if (nested.isEnum())
                addNestedEnum(nested);
            else
                addNestedClass(nested);
        }
    }

    private void addNestedEnum(Class<?> cls) {
        ProtoEnum ann = (ProtoEnum)cls.getAnnotation(ProtoEnum.class);
        if (ann != null) {
            nested.add(new Enum(cls, this.type));
        }
    }

    private void addNestedClass(Class<?> cls) {
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

        for (AbstractMessage abstractMessage : this.nested) {
            abstractMessage.generateJavaMapper(builder);
        }
    }

    public void generateAbstractMethods(Set<MethodSpec> specs) {
        for (Field field : fields) {
            field.generateAbstractMethods(specs);
        }

        for (AbstractMessage elem : nested) {
            elem.generateAbstractMethods(specs);
        }
    }

    public void findDependencies(Set<String> dependencies) {
        for (Field field : fields) {
            field.getDependencies(dependencies);
        }

        for (AbstractMessage elem : nested) {
            elem.findDependencies(dependencies);
        }
    }

    public List<AbstractMessage> getNested() {
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
        public String getProtoName() { return field.getName().toUpperCase(); }

        public void getDependencies(Set<String> deps) {}
        public void generateAbstractMethods(Set<MethodSpec> specs) {}
    }
}
