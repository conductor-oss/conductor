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
package conductor.test.scheduler;

import org.conductoross.conductor.RestConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/** Minimal Spring Boot application for scheduler integration tests. Uses SQLite persistence. */
@SpringBootApplication(
        exclude = {DataSourceAutoConfiguration.class, MongoAutoConfiguration.class},
        scanBasePackages = {})
@ComponentScan(
        basePackages = {
            "com.netflix.conductor.core",
            "com.netflix.conductor.rest",
            "com.netflix.conductor.service",
            "com.netflix.conductor.model",
            "com.netflix.conductor.common",
            "com.netflix.conductor.contribs",
            "com.netflix.conductor.sqlite",
            "com.netflix.conductor.dao",
            "com.netflix.conductor.tasks",
            "io.orkes.conductor",
            "org.conductoross.conductor"
        },
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = RestConfiguration.class))
public class SchedulerTestApp {}
