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
package com.netflix.conductor.common.utils;

import java.util.Optional;

public class EnvUtils {

    public enum SystemParameters {
        CPEWF_TASK_ID,
        NETFLIX_ENV,
        NETFLIX_STACK
    }

    public static boolean isEnvironmentVariable(String test) {
        for (SystemParameters c : SystemParameters.values()) {
            if (c.name().equals(test)) {
                return true;
            }
        }
        String value =
                Optional.ofNullable(System.getProperty(test))
                        .orElseGet(() -> Optional.ofNullable(System.getenv(test)).orElse(null));
        return value != null;
    }

    public static String getSystemParametersValue(String sysParam, String taskId) {
        if ("CPEWF_TASK_ID".equals(sysParam)) {
            return taskId;
        }

        String value = System.getenv(sysParam);
        if (value == null) {
            value = System.getProperty(sysParam);
        }
        return value;
    }
}
