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
package com.netflix.conductor.annotationsprocessor.protogen;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.Modifier;

import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;
import com.netflix.conductor.annotationsprocessor.protogen.types.AbstractType;
import com.netflix.conductor.annotationsprocessor.protogen.types.MessageType;
import com.netflix.conductor.annotationsprocessor.protogen.types.TypeMapper;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

public class Message extends AbstractMessage {
    public Message(Class<?> cls, MessageType parent) {
        super(cls, parent);

        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            ProtoField ann = field.getAnnotation(ProtoField.class);
            if (ann == null) continue;

            fields.add(new MessageField(ann.id(), field));
        }
    }

    protected ProtoMessage getAnnotation() {
        return (ProtoMessage) this.clazz.getAnnotation(ProtoMessage.class);
    }

    @Override
    public String getProtoClass() {
        return "message";
    }

    @Override
    protected void javaMapToProto(TypeSpec.Builder type) {
        if (!getAnnotation().toProto() || getAnnotation().wrapper()) return;

        ClassName javaProtoType = (ClassName) this.type.getJavaProtoType();
        MethodSpec.Builder method = MethodSpec.methodBuilder("toProto");
        method.addModifiers(Modifier.PUBLIC);
        method.returns(javaProtoType);
        method.addParameter(this.clazz, "from");

        method.addStatement(
                "$T to = $T.newBuilder()", javaProtoType.nestedClass("Builder"), javaProtoType);

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
        if (!getAnnotation().fromProto() || getAnnotation().wrapper()) return;

        MethodSpec.Builder method = MethodSpec.methodBuilder("fromProto");
        method.addModifiers(Modifier.PUBLIC);
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
                type = TypeMapper.INSTANCE.get(field.getGenericType());
            }
            return type;
        }

        private static Pattern CAMEL_CASE_RE = Pattern.compile("(?<=[a-z])[A-Z]");

        private static String toUnderscoreCase(String input) {
            Matcher m = CAMEL_CASE_RE.matcher(input);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(sb, "_" + m.group());
            }
            m.appendTail(sb);
            return sb.toString().toLowerCase();
        }

        @Override
        public String getProtoTypeDeclaration() {
            return String.format(
                    "%s %s = %d",
                    getAbstractType().getProtoType(), toUnderscoreCase(getName()), getProtoIndex());
        }

        @Override
        public void getDependencies(Set<String> deps) {
            getAbstractType().getDependencies(deps);
        }

        @Override
        public void generateAbstractMethods(Set<MethodSpec> specs) {
            getAbstractType().generateAbstractMethods(specs);
        }
    }
}
