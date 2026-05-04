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
package com.netflix.conductor;

import java.net.InetAddress;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;

// Prevents from the datasource beans to be loaded, AS they are needed only for specific databases.
// In case that SQL database is selected this class will be imported back in the appropriate
// database persistence module.
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, MongoAutoConfiguration.class})
@ComponentScan(
        basePackages = {
            "com.netflix.conductor",
            "io.orkes.conductor",
            "org.conductoross.conductor"
        })
public class Conductor implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Conductor.class);

    private final Environment environment;

    public Conductor(Environment environment) {
        this.environment = environment;
    }

    public static void main(String[] args) {
        loadExternalConfig();

        SpringApplication.run(Conductor.class, args);
    }

    @Override
    public void run(ApplicationArguments args) {
        String dbType = environment.getProperty("conductor.db.type", "memory");
        String queueType = environment.getProperty("conductor.queue.type", "memory");
        String indexingType = environment.getProperty("conductor.indexing.type", "memory");
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");

        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "localhost";
        }

        String serverUrl = String.format("http://%s:%s%s", hostname, port, contextPath);
        log.info("\n\n\n");
        log.info("┌────────────────────────────────────────────────────────────────────────┐");
        log.info("│                    CONDUCTOR SERVER CONFIGURATION                      │");
        log.info("├────────────────────────────────────────────────────────────────────────┤");
        log.info("│  Database Type    : {}", padRight(dbType, 51) + "│");
        log.info("│  Queue Type       : {}", padRight(queueType, 51) + "│");
        log.info("│  Indexing Type    : {}", padRight(indexingType, 51) + "│");
        log.info("│  Server Port      : {}", padRight(port, 51) + "│");
        log.info("├────────────────────────────────────────────────────────────────────────┤");
        log.info("│  Server URL       : {}", padRight(serverUrl, 51) + "│");
        log.info(
                "│  Swagger UI       : {}",
                padRight(serverUrl + "/swagger-ui/index.html", 51) + "│");
        log.info("└────────────────────────────────────────────────────────────────────────┘");
        log.info("\n\n\n");
    }

    private String padRight(String s, int width) {
        if (s.length() >= width) {
            return s.substring(0, width - 3) + "...";
        }
        return String.format("%-" + width + "s", s);
    }

    /**
     * If <code>CONDUCTOR_CONFIG_FILE</code> is set, registers the file as an additional Spring Boot
     * config location. Spring then loads the file as part of its standard property resolution,
     * placing it above default properties but below environment variables, JVM system properties,
     * and command-line args.
     *
     * <p>Spring Boot property hierarchy is documented here,
     * https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config
     */
    private static void loadExternalConfig() {
        String configFile = System.getProperty("CONDUCTOR_CONFIG_FILE");
        if (StringUtils.isBlank(configFile)) {
            configFile = System.getenv("CONDUCTOR_CONFIG_FILE");
        }
        if (StringUtils.isNotBlank(configFile)) {
            log.info("Loading external config from {}", configFile);
            System.setProperty("spring.config.additional-location", "optional:file:" + configFile);
        }
    }
}
