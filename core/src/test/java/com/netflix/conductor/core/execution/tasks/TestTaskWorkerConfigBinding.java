/*
 * Copyright 2026 Conductor Authors.
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
package com.netflix.conductor.core.execution.tasks;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * End-to-end check that {@code conductor.app.taskWorkerConfigs.<TYPE>.*} keys written in a real
 * {@code application.properties} file survive Spring Boot's relaxed binding (which may fold case or
 * mangle separators in map keys) and resolve through {@link SystemTaskWorker}'s lookup.
 */
public class TestTaskWorkerConfigBinding {

    private ConductorProperties bindFromPropertiesFile(String propertiesFileContent)
            throws Exception {
        List<PropertySource<?>> sources =
                new PropertiesPropertySourceLoader()
                        .load(
                                "test",
                                new ByteArrayResource(
                                        propertiesFileContent.getBytes(StandardCharsets.UTF_8)));
        Iterable<ConfigurationPropertySource> configSources =
                ConfigurationPropertySources.from(sources.get(0));
        return new Binder(configSources)
                .bind("conductor.app", ConductorProperties.class)
                .orElseThrow(() -> new AssertionError("binding produced no result"));
    }

    @Test
    public void propertiesFileKeysBindAndResolveThroughWorkerLookup() throws Exception {
        ConductorProperties properties =
                bindFromPropertiesFile(
                        """
                        conductor.app.taskWorkerConfigs.HTTP.threadCount=20
                        conductor.app.taskWorkerConfigs.LLM_TEXT_COMPLETE.permitCount=3
                        """);

        SystemTaskWorker worker =
                new SystemTaskWorker(
                        mock(QueueDAO.class),
                        mock(AsyncSystemTaskExecutor.class),
                        properties,
                        mock(ExecutionService.class));

        // Uppercase key: HTTP gets a dedicated pool with permits == threadCount.
        assertEquals(
                "HTTP override from .properties must resolve",
                20,
                worker.getExecutionConfig("HTTP").getSemaphoreUtil().availableSlots());
        // Uppercase + underscores: permitCount override on the shared pool.
        assertEquals(
                "LLM_TEXT_COMPLETE override from .properties must resolve",
                3,
                worker.getExecutionConfig("LLM_TEXT_COMPLETE").getSemaphoreUtil().availableSlots());
    }
}
