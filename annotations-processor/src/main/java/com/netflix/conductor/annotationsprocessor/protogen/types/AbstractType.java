package com.netflix.conductor.annotationsprocessor.protogen.types;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import java.lang.reflect.Type;
import java.util.Set;

public abstract class AbstractType {
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

    protected String javaMethodName(String m, String field) {
        String fieldName = field.substring(0, 1).toUpperCase() + field.substring(1);
        return m + fieldName;
    }

    private static class ProtoCase {
        static String convert(String s) {
            StringBuilder out = new StringBuilder(s.length());
            final int len = s.length();
            int i = 0;
            int j = -1;
            while ((j = findWordBoundary(s, ++j)) != -1) {
                out.append(normalizeWord(s.substring(i, j)));
                if (j < len && s.charAt(j) == '_')
                    j++;
                i = j;
            }
            if (i == 0)
                return normalizeWord(s);
            if (i < len)
                out.append(normalizeWord(s.substring(i)));
            return out.toString();
        }

        private static boolean isWordBoundary(char c) {
            return (c >= 'A' && c <= 'Z');
        }

        private static int findWordBoundary(CharSequence sequence, int start) {
            int length = sequence.length();
            if (start >= length)
                return -1;

            if (isWordBoundary(sequence.charAt(start))) {
                int i = start;
                while (i < length && isWordBoundary(sequence.charAt(i)))
                    i++;
                return i;
            } else {
                for (int i = start; i < length; i++) {
                    final char c = sequence.charAt(i);
                    if (c == '_' || isWordBoundary(c))
                        return i;
                }
                return -1;
            }
        }

        private static String normalizeWord(String word) {
            if (word.length() < 2)
                return word.toUpperCase();
            return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
        }
    }

    protected String protoMethodName(String m, String field) {
        return m + ProtoCase.convert(field);
    }
}
