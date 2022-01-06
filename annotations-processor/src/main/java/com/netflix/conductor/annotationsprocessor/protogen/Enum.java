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

import javax.lang.model.element.Modifier;

import com.netflix.conductor.annotationsprocessor.protogen.types.MessageType;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

public class Enum extends AbstractMessage {
    public enum MapType {
        FROM_PROTO("fromProto"),
        TO_PROTO("toProto");

        private final String methodName;

        MapType(String m) {
            methodName = m;
        }

        public String getMethodName() {
            return methodName;
        }
    }

    public Enum(Class cls, MessageType parent) {
        super(cls, parent);

        int protoIndex = 0;
        for (java.lang.reflect.Field field : cls.getDeclaredFields()) {
            if (field.isEnumConstant()) fields.add(new EnumField(protoIndex++, field));
        }
    }

    @Override
    public String getProtoClass() {
        return "enum";
    }

    private MethodSpec javaMap(MapType mt, TypeName from, TypeName to) {
        MethodSpec.Builder method = MethodSpec.methodBuilder(mt.getMethodName());
        method.addModifiers(Modifier.PUBLIC);
        method.returns(to);
        method.addParameter(from, "from");

        method.addStatement("$T to", to);
        method.beginControlFlow("switch (from)");

        for (Field field : fields) {
            String fromName = (mt == MapType.TO_PROTO) ? field.getName() : field.getProtoName();
            String toName = (mt == MapType.TO_PROTO) ? field.getProtoName() : field.getName();
            method.addStatement("case $L: to = $T.$L; break", fromName, to, toName);
        }

        method.addStatement(
                "default: throw new $T(\"Unexpected enum constant: \" + from)",
                IllegalArgumentException.class);
        method.endControlFlow();
        method.addStatement("return to");
        return method.build();
    }

    @Override
    protected void javaMapFromProto(TypeSpec.Builder type) {
        type.addMethod(
                javaMap(
                        MapType.FROM_PROTO,
                        this.type.getJavaProtoType(),
                        TypeName.get(this.clazz)));
    }

    @Override
    protected void javaMapToProto(TypeSpec.Builder type) {
        type.addMethod(
                javaMap(MapType.TO_PROTO, TypeName.get(this.clazz), this.type.getJavaProtoType()));
    }

    public class EnumField extends Field {
        protected EnumField(int index, java.lang.reflect.Field field) {
            super(index, field);
        }

        @Override
        public String getProtoTypeDeclaration() {
            return String.format("%s = %d", getProtoName(), getProtoIndex());
        }
    }
}
