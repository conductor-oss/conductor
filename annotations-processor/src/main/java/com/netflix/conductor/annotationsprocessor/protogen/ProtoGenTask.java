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
import java.io.IOException;

public class ProtoGenTask {
    private String protoPackage;
    private String javaPackage;
    private String goPackage;

    private File protosDir;
    private File mapperDir;
    private String mapperPackage;

    private File sourceJar;
    private String sourcePackage;

    public String getProtoPackage() {
        return protoPackage;
    }

    public void setProtoPackage(String protoPackage) {
        this.protoPackage = protoPackage;
    }

    public String getJavaPackage() {
        return javaPackage;
    }

    public void setJavaPackage(String javaPackage) {
        this.javaPackage = javaPackage;
    }

    public String getGoPackage() {
        return goPackage;
    }

    public void setGoPackage(String goPackage) {
        this.goPackage = goPackage;
    }

    public File getProtosDir() {
        return protosDir;
    }

    public void setProtosDir(File protosDir) {
        this.protosDir = protosDir;
    }

    public File getMapperDir() {
        return mapperDir;
    }

    public void setMapperDir(File mapperDir) {
        this.mapperDir = mapperDir;
    }

    public String getMapperPackage() {
        return mapperPackage;
    }

    public void setMapperPackage(String mapperPackage) {
        this.mapperPackage = mapperPackage;
    }

    public File getSourceJar() {
        return sourceJar;
    }

    public void setSourceJar(File sourceJar) {
        this.sourceJar = sourceJar;
    }

    public String getSourcePackage() {
        return sourcePackage;
    }

    public void setSourcePackage(String sourcePackage) {
        this.sourcePackage = sourcePackage;
    }

    public void generate() {
        ProtoGen generator = new ProtoGen(protoPackage, javaPackage, goPackage);
        try {
            generator.processPackage(sourceJar, sourcePackage);
            generator.writeMapper(mapperDir, mapperPackage);
            generator.writeProtos(protosDir);
        } catch (IOException e) {
            System.err.printf("protogen: failed with %s\n", e);
        }
    }

    public static void main(String[] args) {
        if (args == null || args.length < 8) {
            throw new RuntimeException(
                    "protogen configuration incomplete, please provide all required (8) inputs");
        }
        ProtoGenTask task = new ProtoGenTask();
        int argsId = 0;
        task.setProtoPackage(args[argsId++]);
        task.setJavaPackage(args[argsId++]);
        task.setGoPackage(args[argsId++]);
        task.setProtosDir(new File(args[argsId++]));
        task.setMapperDir(new File(args[argsId++]));
        task.setMapperPackage(args[argsId++]);
        task.setSourceJar(new File(args[argsId++]));
        task.setSourcePackage(args[argsId]);
        System.out.println("Running protogen with arguments: " + task);
        task.generate();
        System.out.println("protogen completed.");
    }

    @Override
    public String toString() {
        return "ProtoGenTask{"
                + "protoPackage='"
                + protoPackage
                + '\''
                + ", javaPackage='"
                + javaPackage
                + '\''
                + ", goPackage='"
                + goPackage
                + '\''
                + ", protosDir="
                + protosDir
                + ", mapperDir="
                + mapperDir
                + ", mapperPackage='"
                + mapperPackage
                + '\''
                + ", sourceJar="
                + sourceJar
                + ", sourcePackage='"
                + sourcePackage
                + '\''
                + '}';
    }
}
