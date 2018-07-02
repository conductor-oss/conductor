package com.netflix.conductor.elasticsearch;

import com.netflix.conductor.service.Lifecycle;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

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
