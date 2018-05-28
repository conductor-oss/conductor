package com.netflix.conductor.protogen;

import com.google.common.base.CaseFormat;
import com.netflix.conductor.common.annotations.ProtoField;
import com.netflix.conductor.common.annotations.ProtoMessage;
import com.netflix.conductor.protogen.types.AbstractType;
import com.netflix.conductor.protogen.types.MessageType;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.Set;

public class Message extends Element {
    public Message(Class cls, MessageType parent) {
        super(cls, parent);

        for (java.lang.reflect.Field field: clazz.getDeclaredFields()) {
            ProtoField ann = field.getAnnotation(ProtoField.class);
            if (ann == null)
                continue;

            fields.add(new MessageField(ann.id(), field));
        }
    }

    protected ProtoMessage getAnnotation() {
        return (ProtoMessage)this.clazz.getAnnotation(ProtoMessage.class);
    }

    @Override
    public String getProtoClass() {
        return "message";
    }

    @Override
    protected void javaMapToProto(TypeSpec.Builder type) {
        if (!getAnnotation().toProto() || getAnnotation().wrapper())
            return;

        ClassName javaProtoType = (ClassName)this.type.getJavaProtoType();
        MethodSpec.Builder method = MethodSpec.methodBuilder("toProto");
        method.addModifiers(Modifier.STATIC, Modifier.PUBLIC);
        method.returns(javaProtoType);
        method.addParameter(this.clazz, "from");

        method.addStatement("$T to = $T.newBuilder()",
                javaProtoType.nestedClass("Builder"), javaProtoType);

        for (Field field : this.fields) {
            if (field instanceof MessageField) {
                AbstractType fieldType = ((MessageField) field).getAbstractType();
                fieldType.mapToProto(field.getName(), method);
            }
        }

        method.addStatement("return to.build()");
        type.addMethod(method.build());
    }

    @Override
    protected void javaMapFromProto(TypeSpec.Builder type) {
        if (!getAnnotation().fromProto() || getAnnotation().wrapper())
            return;

        MethodSpec.Builder method = MethodSpec.methodBuilder("fromProto");
        method.addModifiers(Modifier.STATIC, Modifier.PUBLIC);
        method.returns(this.clazz);
        method.addParameter(this.type.getJavaProtoType(), "from");

        method.addStatement("$T to = new $T()", this.clazz, this.clazz);

        for (Field field : this.fields) {
            if (field instanceof MessageField) {
                AbstractType fieldType = ((MessageField) field).getAbstractType();
                fieldType.mapFromProto(field.getName(), method);
            }
        }

        method.addStatement("return to");
        type.addMethod(method.build());
    }

    public static class MessageField extends Field {
        protected AbstractType type;

        protected MessageField(int index, java.lang.reflect.Field field) {
            super(index, field);
        }

        public AbstractType getAbstractType() {
            if (type == null) {
                type = AbstractType.get(field.getGenericType());
            }
            return type;
        }

        @Override
        public String getProtoTypeDeclaration() {
            return String.format("%s %s = %d",
                    getAbstractType().getProtoType(),
                    CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, getName()),
                    getProtoIndex());
        }

        @Override
        public void getDependencies(Set<String> deps) {
            getAbstractType().getDependencies(deps);
        }
    }
}
