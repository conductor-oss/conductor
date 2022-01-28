/*
 * Copyright 2020 Netflix, Inc.
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

import java.util.concurrent.ConcurrentHashMap;

import com.netflix.config.DynamicProperty;

/** Used to configure the Conductor workers using properties. */
public class PropertyFactory {

    private final DynamicProperty global;
    private final DynamicProperty local;

    private static final String PROPERTY_PREFIX = "conductor.worker";

    private static final ConcurrentHashMap<String, PropertyFactory> PROPERTY_FACTORY_MAP =
            new ConcurrentHashMap<>();

    private PropertyFactory(String prefix, String propName, String workerName) {
        this.global = DynamicProperty.getInstance(prefix + "." + propName);
        this.local = DynamicProperty.getInstance(prefix + "." + workerName + "." + propName);
    }

    /**
     * @param defaultValue Default Value
     * @return Returns the value as integer. If not value is set (either global or worker specific),
     *     then returns the default value.
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
     *     then returns the default value.
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
     *     then returns the default value.
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
}
