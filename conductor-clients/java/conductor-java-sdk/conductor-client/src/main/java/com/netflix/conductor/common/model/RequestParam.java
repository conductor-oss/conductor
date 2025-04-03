package com.netflix.conductor.common.model;

import lombok.Data;

public class RequestParam {
    private String name;
    private String type;  // Query, Header, Path, etc.
    private boolean required;
    private Schema schema;

    @Data
    public static class Schema {
        private String type;
        private String format;
        private Object defaultValue;

        @Override
        public String toString() {
            return "Schema{" +
                    "type='" + type + '\'' +
                    ", format='" + format + '\'' +
                    ", defaultValue=" + defaultValue +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "RequestParam{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", required=" + required +
                '}';
    }
}