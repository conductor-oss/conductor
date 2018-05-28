package com.netflix.conductor.protogen;

import com.netflix.conductor.protogen.types.MessageType;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;

public class Enum extends Element {
    public Enum(Class cls, MessageType parent) {
        super(cls, parent);

        int protoIndex = 0;
        for (java.lang.reflect.Field field : cls.getDeclaredFields()) {
            if (field.isEnumConstant())
                fields.add(new EnumField(protoIndex++, field));
        }
    }

    @Override
    public String getProtoClass() {
        return "enum";
    }

    private MethodSpec javaMap(String methodName, TypeName from, TypeName to) {
        MethodSpec.Builder method = MethodSpec.methodBuilder(methodName);
        method.addModifiers(Modifier.STATIC, Modifier.PUBLIC);
        method.returns(to);
        method.addParameter(from, "from");

        method.addStatement("$T to", to);
        method.beginControlFlow("switch (from)");

        for (Field field : fields) {
            String name = field.getName();
            method.addStatement("case $L: to = $T.$L; break", name, to, name);
        }

        method.addStatement("default: throw new $T(\"Unexpected enum constant: \" + from)",
                IllegalArgumentException.class);
        method.endControlFlow();
        method.addStatement("return to");
        return method.build();
    }

    @Override
    protected void javaMapFromProto(TypeSpec.Builder type) {
        type.addMethod(javaMap("fromProto", this.type.getJavaProtoType(), TypeName.get(this.clazz)));
    }

    @Override
    protected void javaMapToProto(TypeSpec.Builder type) {
        type.addMethod(javaMap("toProto", TypeName.get(this.clazz), this.type.getJavaProtoType()));
    }

    public class EnumField extends Field {
        protected EnumField(int index, java.lang.reflect.Field field) {
            super(index, field);
        }

        @Override
        public String getProtoTypeDeclaration() {
            return String.format("%s = %d", getName(), getProtoIndex());
        }
    }
}
