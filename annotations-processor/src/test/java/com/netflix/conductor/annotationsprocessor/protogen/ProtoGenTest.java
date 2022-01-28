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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.io.Resources;

import static org.junit.Assert.*;

public class ProtoGenTest {
    private static final Charset charset = StandardCharsets.UTF_8;

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void happyPath() throws Exception {
        File rootDir = folder.getRoot();
        String protoPackage = "protoPackage";
        String javaPackage = "abc.protogen.example";
        String goPackage = "goPackage";
        String sourcePackage = "com.example";
        String mapperPackage = "mapperPackage";

        File jarFile = new File("./build/libs/example.jar");
        assertTrue(jarFile.exists());

        File mapperDir = new File(rootDir, "mapperDir");
        mapperDir.mkdirs();

        File protosDir = new File(rootDir, "protosDir");
        protosDir.mkdirs();

        File modelDir = new File(protosDir, "model");
        modelDir.mkdirs();

        ProtoGen generator = new ProtoGen(protoPackage, javaPackage, goPackage);
        generator.processPackage(jarFile, sourcePackage);
        generator.writeMapper(mapperDir, mapperPackage);
        generator.writeProtos(protosDir);

        List<File> models = Lists.newArrayList(modelDir.listFiles());
        assertEquals(1, models.size());
        File exampleProtoFile =
                models.stream().filter(f -> f.getName().equals("example.proto")).findFirst().get();
        assertTrue(exampleProtoFile.length() > 0);
        assertEquals(
                Resources.asCharSource(Resources.getResource("example.proto.txt"), charset).read(),
                Files.asCharSource(exampleProtoFile, charset).read());
    }
}
