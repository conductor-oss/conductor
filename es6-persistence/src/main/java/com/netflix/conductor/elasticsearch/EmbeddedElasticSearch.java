/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.elasticsearch;

import com.netflix.conductor.service.Lifecycle;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface EmbeddedElasticSearch extends Lifecycle {
    Logger logger = LoggerFactory.getLogger(EmbeddedElasticSearch.class);

    default void cleanDataDir(String path) {
        File dataDir = new File(path);

        try {
            logger.info("Deleting contents of data dir {}", path);
            if (dataDir.exists()) {
                FileUtils.cleanDirectory(dataDir);
            }
        } catch (IOException e) {
            logger.error(String.format("Failed to delete ES data dir: %s", dataDir.getAbsolutePath()), e);
        }
    }

    default File createDataDir(String dataDirLoc) throws IOException {
        Path dataDirPath = FileSystems.getDefault().getPath(dataDirLoc);
        Files.createDirectories(dataDirPath);
        return dataDirPath.toFile();
    }

    default File setupDataDir(String path) throws IOException {
        cleanDataDir(path);
        return createDataDir(path);
    }
}
