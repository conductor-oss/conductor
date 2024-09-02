/*
 * Copyright 2020 Orkes, Inc.
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
package com.netflix.conductor.client.config;

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * Used to configure the Conductor workers using conductor-workers.properties.
 */
public class PropertyFactory {

    private static final Properties PROPERTIES = loadProperties("conductor-workers.properties");

    private static final String PROPERTY_PREFIX = "conductor.worker";

    // Handle potential parsing exceptions ?

    @RequiredArgsConstructor
    private static class WorkerProperty {

        final String key;

        String getString() {
            if (PROPERTIES == null) {
                return null;
            }

            return PROPERTIES.getProperty(key);
        }

        Integer getInteger() {
            String value = getString();
            return value == null ? null : Integer.parseInt(value);
        }

        Boolean getBoolean() {
            String value = getString();
            return value == null ? null : Boolean.parseBoolean(value);
        }

        String getString(String defaultValue) {
            String value = getString();
            return value == null ? defaultValue : value;
        }

        Integer getInteger(Integer defaultValue) {
            String value = getString();
            return value == null ? defaultValue : Integer.parseInt(value);
        }

        Boolean getBoolean(Boolean defaultValue) {
            String value = getString();
            return value == null ? defaultValue : Boolean.parseBoolean(value);
        }
    }

    private final WorkerProperty global;

    private final WorkerProperty local;

    private static final ConcurrentHashMap<String, PropertyFactory> PROPERTY_FACTORY_MAP =
            new ConcurrentHashMap<>();

    private PropertyFactory(String prefix, String propName, String workerName) {
        this.global = new WorkerProperty(prefix + "." + propName);
        this.local = new WorkerProperty(prefix + "." + workerName + "." + propName);
    }

    /**
     * @param defaultValue Default Value
     * @return Returns the value as integer. If not value is set (either global or worker specific),
     * then returns the default value.
     */
    public Integer getInteger(int defaultValue) {
        Integer value = local.getInteger();
        if (value == null) {
            value = global.getInteger(defaultValue);
        }
        return value;
    }

    /**
     * @param defaultValue Default Value
     * @return Returns the value as String. If not value is set (either global or worker specific),
     * then returns the default value.
     */
    public String getString(String defaultValue) {
        String value = local.getString();
        if (value == null) {
            value = global.getString(defaultValue);
        }
        return value;
    }

    /**
     * @param defaultValue Default Value
     * @return Returns the value as Boolean. If not value is set (either global or worker specific),
     * then returns the default value.
     */
    public Boolean getBoolean(Boolean defaultValue) {
        Boolean value = local.getBoolean();
        if (value == null) {
            value = global.getBoolean(defaultValue);
        }
        return value;
    }

    public static Integer getInteger(String workerName, String property, Integer defaultValue) {
        return getPropertyFactory(workerName, property).getInteger(defaultValue);
    }

    public static Boolean getBoolean(String workerName, String property, Boolean defaultValue) {
        return getPropertyFactory(workerName, property).getBoolean(defaultValue);
    }

    public static String getString(String workerName, String property, String defaultValue) {
        return getPropertyFactory(workerName, property).getString(defaultValue);
    }

    private static PropertyFactory getPropertyFactory(String workerName, String property) {
        String key = property + "." + workerName;
        return PROPERTY_FACTORY_MAP.computeIfAbsent(
                key, t -> new PropertyFactory(PROPERTY_PREFIX, property, workerName));
    }

    @SneakyThrows
    private static Properties loadProperties(String file) {
        Properties properties = new Properties();
        try (InputStream input = PropertyFactory.class.getClassLoader().getResourceAsStream(file)) {
            if (input == null) {
                return null;
            }
            properties.load(input);
        }

        return properties;
    }
}
