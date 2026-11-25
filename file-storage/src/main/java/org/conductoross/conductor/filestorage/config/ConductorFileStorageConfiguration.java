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
package org.conductoross.conductor.filestorage.config;

import org.conductoross.conductor.core.storage.ConductorFileStorageProperties;
import org.conductoross.conductor.core.storage.FileStorage;
import org.conductoross.conductor.filestorage.storage.ConductorFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConductorFileStorageProperties.class)
@ConditionalOnProperty(name = "conductor.file-storage.enabled", havingValue = "true")
public class ConductorFileStorageConfiguration {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ConductorFileStorageConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "conductor.file-storage.type", havingValue = "conductor")
    public FileStorage conductorFileStorage(ConductorFileStorageProperties properties) {
        LOGGER.warn(
                "Conductor file storage uses {}. Multi-node deployments must mount this directory "
                        + "on a filesystem shared by every Conductor server node.",
                properties.getDirectory());
        return new ConductorFileStorage(properties);
    }

    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}
