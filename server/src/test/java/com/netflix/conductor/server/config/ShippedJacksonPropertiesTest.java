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
package com.netflix.conductor.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Settings the shipped server must carry, read from the {@code application.properties} that goes
 * into the image.
 *
 * <p>{@code accept-single-value-as-array} is what lets three of the shipped SDK schema clients post
 * a bare object to {@code POST /api/schema}, which declares a list. The controller test that
 * exercises that request lives in another module and cannot see this file, so it restates the
 * setting and would stay green if the line were deleted here. This is what notices.
 */
public class ShippedJacksonPropertiesTest {

    private static Properties shipped() throws IOException {
        try (InputStream in =
                ShippedJacksonPropertiesTest.class.getResourceAsStream("/application.properties")) {
            assertNotNull("server application.properties is not on the classpath", in);
            Properties properties = new Properties();
            properties.load(in);
            return properties;
        }
    }

    @Test
    public void theShippedServerAcceptsASingleValueWhereAListIsDeclared() throws IOException {
        assertEquals(
                "true",
                shipped()
                        .getProperty(
                                "spring.jackson.deserialization.accept-single-value-as-array"));
    }
}
