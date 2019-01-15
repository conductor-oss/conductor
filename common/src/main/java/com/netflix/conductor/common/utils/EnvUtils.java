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
        String value = Optional.ofNullable(System.getProperty(test))
                .orElseGet(() -> Optional.ofNullable(System.getenv(test))
                        .orElse(null));
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
