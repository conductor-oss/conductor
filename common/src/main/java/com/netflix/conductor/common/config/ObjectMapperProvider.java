/*
 * Copyright 2021 Netflix, Inc.
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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A Factory class for creating a customized {@link ObjectMapper}. This is only used by the
 * conductor-client module and tests that rely on {@link ObjectMapper}. See
 * TestObjectMapperConfiguration.
 */
public class ObjectMapperProvider {

    /**
     * The customizations in this method are configured using {@link
     * org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration}
     *
     * <p>Customizations are spread across, 1. {@link ObjectMapperBuilderConfiguration} 2. {@link
     * ObjectMapperConfiguration} 3. {@link JsonProtoModule}
     *
     * <p>IMPORTANT: Changes in this method need to be also performed in the default {@link
     * ObjectMapper} that Spring Boot creates.
     *
     * @see org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
     */
    public ObjectMapper getObjectMapper() {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.setDefaultPropertyInclusion(
                JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS));
        objectMapper.registerModule(new JsonProtoModule());
        return objectMapper;
    }
}
