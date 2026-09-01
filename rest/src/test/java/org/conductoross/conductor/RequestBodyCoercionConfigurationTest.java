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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import com.netflix.conductor.common.config.ObjectMapperBuilderConfiguration;
import com.netflix.conductor.common.config.ObjectMapperConfiguration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Where the single-value-as-array coercion is allowed to reach.
 *
 * <p>The application's {@link ObjectMapper} bean is injected into every persistence DAO and is what
 * stored rows are read back with, so it stays strict; only the HTTP message converter is lenient.
 * Enabling the feature on the shared bean is a one-line change that would pass every other test in
 * this repository, which is why the boundary is asserted rather than left to review.
 *
 * <p>That the converter's mapper is a faithful copy — that copying it late did not lose the
 * inclusion settings applied to the application mapper after construction — is covered where it can
 * be seen from outside, by the response-shape assertions in {@code SchemaResourceTest}.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = RequestBodyCoercionConfigurationTest.TestConfig.class)
public class RequestBodyCoercionConfigurationTest {

    @Autowired private ObjectMapper applicationObjectMapper;

    @Autowired private RequestMappingHandlerAdapter handlerAdapter;

    @Test
    public void theApplicationObjectMapperStaysStrict() {
        assertFalse(
                "the mapper injected into the DAOs must not accept a single value as an array",
                applicationObjectMapper.isEnabled(
                        DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY));
    }

    @Test
    public void theHttpMessageConverterIsLenient() {
        boolean found = false;
        for (HttpMessageConverter<?> converter : handlerAdapter.getMessageConverters()) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                found = true;
                assertTrue(
                        "a request body declared as a list must accept a bare object",
                        jackson.getObjectMapper()
                                .isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY));
            }
        }
        assertTrue("no Jackson message converter was registered", found);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
        ObjectMapperBuilderConfiguration.class,
        ObjectMapperConfiguration.class,
        RequestBodyCoercionConfiguration.class
    })
    static class TestConfig {}
}
