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
package org.conductoross.conductor;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lets a request body declared as a list arrive as a bare object.
 *
 * <p>Several shipped SDK clients post one — the schema clients for Python, Ruby and Rust among them
 * — so without this they fail against a server whose contract looks identical to the one they were
 * written for. It only ever makes a request that would have been rejected succeed, so no working
 * caller changes behaviour.
 *
 * <p>Applied to the HTTP message converter rather than to the application's {@link ObjectMapper}
 * bean. That bean is injected into every persistence DAO and read back stored rows with, and
 * loosening it there would accept a malformed row as readily as a lenient client. Inbound HTTP is
 * where clients vary; a row this server wrote itself does not.
 *
 * <p>The mapper is copied inside {@code extendMessageConverters}, which runs after the application
 * mapper has been fully initialised, so the copy carries the modules and inclusion settings applied
 * to it in {@code @PostConstruct}. {@code ACCEPT_SINGLE_VALUE_AS_ARRAY} is a deserialization
 * feature, so responses are unaffected.
 */
@Configuration
public class RequestBodyCoercionConfiguration implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                ObjectMapper lenient =
                        jackson.getObjectMapper()
                                .copy()
                                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
                jackson.setObjectMapper(lenient);
            }
        }
    }
}
