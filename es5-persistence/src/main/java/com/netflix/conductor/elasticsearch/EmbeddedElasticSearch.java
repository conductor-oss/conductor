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
        try {
            logger.info("Deleting contents of data dir {}", path);
            File f = new File(path);
            if (f.exists()) {
                FileUtils.cleanDirectory(new File(path));
            }
        } catch (IOException e) {
            logger.error("Failed to delete ES data dir");
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
