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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import javax.annotation.Generated;
import javax.lang.model.element.Modifier;

import com.netflix.conductor.annotations.protogen.ProtoMessage;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.google.common.reflect.ClassPath;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

public class ProtoGen {
    private static final String GENERATOR_NAME =
            "com.netflix.conductor.annotationsprocessor.protogen";

    private String protoPackageName;
    private String javaPackageName;
    private String goPackageName;
    private List<ProtoFile> protoFiles = new ArrayList<>();

    public ProtoGen(String protoPackageName, String javaPackageName, String goPackageName) {
        this.protoPackageName = protoPackageName;
        this.javaPackageName = javaPackageName;
        this.goPackageName = goPackageName;
    }

    public void writeMapper(File root, String mapperPackageName) throws IOException {
        TypeSpec.Builder protoMapper =
                TypeSpec.classBuilder("AbstractProtoMapper")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .addAnnotation(
                                AnnotationSpec.builder(Generated.class)
                                        .addMember("value", "$S", GENERATOR_NAME)
                                        .build());

        Set<MethodSpec> abstractMethods = new HashSet<>();

        protoFiles.sort(
                new Comparator<ProtoFile>() {
                    public int compare(ProtoFile p1, ProtoFile p2) {
                        String n1 = p1.getMessage().getName();
                        String n2 = p2.getMessage().getName();
                        return n1.compareTo(n2);
                    }
                });

        for (ProtoFile protoFile : protoFiles) {
            AbstractMessage elem = protoFile.getMessage();
            elem.generateJavaMapper(protoMapper);
            elem.generateAbstractMethods(abstractMethods);
        }

        protoMapper.addMethods(abstractMethods);

        JavaFile javaFile =
                JavaFile.builder(mapperPackageName, protoMapper.build()).indent("    ").build();
        File filename = new File(root, "AbstractProtoMapper.java");
        try (Writer writer = new FileWriter(filename.toString())) {
            System.out.printf("protogen: writing '%s'...\n", filename);
            javaFile.writeTo(writer);
        }
    }

    public void writeProtos(File root) throws IOException {
        TemplateLoader loader = new ClassPathTemplateLoader("/templates", ".proto");
        Handlebars handlebars =
                new Handlebars(loader)
                        .infiniteLoops(true)
                        .prettyPrint(true)
                        .with(EscapingStrategy.NOOP);

        Template protoFile = handlebars.compile("file");

        for (ProtoFile file : protoFiles) {
            File filename = new File(root, file.getFilePath());
            try (Writer writer = new FileWriter(filename)) {
                System.out.printf("protogen: writing '%s'...\n", filename);
                protoFile.apply(file, writer);
            }
        }
    }

    public void processPackage(File jarFile, String packageName) throws IOException {
        if (!jarFile.isFile()) throw new IOException("missing Jar file " + jarFile);

        URL[] urls = new URL[] {jarFile.toURI().toURL()};
        ClassLoader loader =
                new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
        ClassPath cp = ClassPath.from(loader);

        System.out.printf("protogen: processing Jar '%s'\n", jarFile);
        for (ClassPath.ClassInfo info : cp.getTopLevelClassesRecursive(packageName)) {
            try {
                processClass(info.load());
            } catch (NoClassDefFoundError ignored) {
            }
        }
    }

    public void processClass(Class<?> obj) {
        if (obj.isAnnotationPresent(ProtoMessage.class)) {
            System.out.printf("protogen: found %s\n", obj.getCanonicalName());
            protoFiles.add(new ProtoFile(obj, protoPackageName, javaPackageName, goPackageName));
        }
    }
}
