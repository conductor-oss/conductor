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

import com.netflix.conductor.common.jackson.JsonProtoModule;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.kotlin.KotlinModule;

/**
 * A Factory class for creating a customized {@link ObjectMapper}. This is only used by the
 * conductor-client module and tests that rely on {@link ObjectMapper}. See
 * TestObjectMapperConfiguration.
 */
public class ObjectMapperProvider {

    private static final ObjectMapper objectMapper = _getObjectMapper();

    /**
     * The customizations in this method are configured using {@link
     * org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration}
     *
     * <p>Customizations are spread across, 1. {@link ObjectMapperBuilderConfiguration} 2. {@link
     * JsonProtoModule}
     *
     * <p>IMPORTANT: Changes in this method need to be also performed in the default {@link
     * ObjectMapper} that Spring Boot creates.
     *
     * @see org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Jackson 3 mappers are immutable, so every setting has to be applied to the builder before the
     * mapper is built. The jdk8 and java.time datatypes and the property-access optimisations that
     * used to need separate modules are part of the core now, which is why only the proto and
     * Kotlin modules are registered here.
     *
     * <p>WRITE_DATES_AS_TIMESTAMPS is enabled to keep the numeric date encoding that Jackson 2
     * produced with JavaTimeModule. Jackson 3 defaults to ISO-8601 strings, which would change the
     * payload format seen by existing clients and stored task/workflow documents.
     */
    private static ObjectMapper _getObjectMapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .changeDefaultPropertyInclusion(
                        value ->
                                JsonInclude.Value.construct(
                                        JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS))
                .addModule(new JsonProtoModule())
                .addModule(new KotlinModule.Builder().build())
                .build();
    }
}
