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
package com.netflix.conductor.common.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.cfg.DateTimeFeature;

import static tools.jackson.databind.DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES;
import static tools.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES;
import static tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static tools.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;

@Configuration
public class ObjectMapperBuilderConfiguration {

    /**
     * Keeps the Spring-managed mapper in step with {@link ObjectMapperProvider#getObjectMapper()}.
     * Jackson 3 mappers cannot be reconfigured after construction, so everything the old
     * ObjectMapperConfiguration applied afterwards is set on the builder here instead.
     */
    @Bean
    public JsonMapperBuilderCustomizer conductorJsonMapperBuilderCustomizer() {
        return builder ->
                builder.disable(
                                FAIL_ON_UNKNOWN_PROPERTIES,
                                FAIL_ON_IGNORED_PROPERTIES,
                                FAIL_ON_NULL_FOR_PRIMITIVES)
                        .disable(FAIL_ON_EMPTY_BEANS)
                        .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .changeDefaultPropertyInclusion(
                                value ->
                                        JsonInclude.Value.construct(
                                                JsonInclude.Include.NON_NULL,
                                                JsonInclude.Include.ALWAYS));
    }
}
