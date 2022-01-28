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

import java.util.HashSet;
import java.util.Set;

import com.netflix.conductor.annotationsprocessor.protogen.types.TypeMapper;

import com.squareup.javapoet.ClassName;

public class ProtoFile {
    public static String PROTO_SUFFIX = "Pb";

    private ClassName baseClass;
    private AbstractMessage message;
    private String filePath;

    private String protoPackageName;
    private String javaPackageName;
    private String goPackageName;

    public ProtoFile(
            Class<?> object,
            String protoPackageName,
            String javaPackageName,
            String goPackageName) {
        this.protoPackageName = protoPackageName;
        this.javaPackageName = javaPackageName;
        this.goPackageName = goPackageName;

        String className = object.getSimpleName() + PROTO_SUFFIX;
        this.filePath = "model/" + object.getSimpleName().toLowerCase() + ".proto";
        this.baseClass = ClassName.get(this.javaPackageName, className);
        this.message = new Message(object, TypeMapper.INSTANCE.baseClass(baseClass, filePath));
    }

    public String getJavaClassName() {
        return baseClass.simpleName();
    }

    public String getFilePath() {
        return filePath;
    }

    public String getProtoPackageName() {
        return protoPackageName;
    }

    public String getJavaPackageName() {
        return javaPackageName;
    }

    public String getGoPackageName() {
        return goPackageName;
    }

    public AbstractMessage getMessage() {
        return message;
    }

    public Set<String> getIncludes() {
        Set<String> includes = new HashSet<>();
        message.findDependencies(includes);
        includes.remove(this.getFilePath());
        return includes;
    }
}
