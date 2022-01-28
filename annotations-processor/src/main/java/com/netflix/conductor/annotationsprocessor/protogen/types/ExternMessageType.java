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
package com.netflix.conductor.annotationsprocessor.protogen.types;

import java.lang.reflect.Type;
import java.util.Set;

import javax.lang.model.element.Modifier;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;

public class ExternMessageType extends MessageType {
    private String externProtoType;

    public ExternMessageType(
            Type javaType, ClassName javaProtoType, String externProtoType, String protoFilePath) {
        super(javaType, javaProtoType, protoFilePath);
        this.externProtoType = externProtoType;
    }

    @Override
    public String getProtoType() {
        return externProtoType;
    }

    @Override
    public void generateAbstractMethods(Set<MethodSpec> specs) {
        MethodSpec fromProto =
                MethodSpec.methodBuilder("fromProto")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(this.getJavaType())
                        .addParameter(this.getJavaProtoType(), "in")
                        .build();

        MethodSpec toProto =
                MethodSpec.methodBuilder("toProto")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(this.getJavaProtoType())
                        .addParameter(this.getJavaType(), "in")
                        .build();

        specs.add(fromProto);
        specs.add(toProto);
    }
}
