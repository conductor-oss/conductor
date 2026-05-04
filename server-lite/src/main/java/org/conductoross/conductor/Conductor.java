/*
 * Copyright 2021 Conductor Authors.
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
package org.conductoross.conductor;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

// Prevents from the datasource beans to be loaded, AS they are needed only for specific databases.
// In case that SQL database is selected this class will be imported back in the appropriate
// database persistence module.
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, MongoAutoConfiguration.class})
@ComponentScan(basePackages = {"com.netflix.conductor", "io.orkes.conductor", "org.conductoross"})
public class Conductor {

    private static final Logger log = LoggerFactory.getLogger(Conductor.class);

    public static void main(String[] args) {
        loadExternalConfig();

        SpringApplication.run(Conductor.class, args);
    }

    /**
     * If <code>CONDUCTOR_LITE_CONFIG_FILE</code> is set, registers the file as an additional Spring
     * Boot config location. Spring then loads the file as part of its standard property resolution,
     * placing it above default properties but below environment variables, JVM system properties,
     * and command-line args.
     *
     * <p>Spring Boot property hierarchy is documented here,
     * https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config
     */
    private static void loadExternalConfig() {
        String configFile = System.getProperty("CONDUCTOR_LITE_CONFIG_FILE");
        if (StringUtils.isBlank(configFile)) {
            configFile = System.getenv("CONDUCTOR_LITE_CONFIG_FILE");
        }
        if (StringUtils.isNotBlank(configFile)) {
            log.info("Loading external config from {}", configFile);
            System.setProperty("spring.config.additional-location", "optional:file:" + configFile);
        }
    }
}
