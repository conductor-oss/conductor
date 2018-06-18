package com.netflix.conductor.tests.utils;

public class TestEnvironment {
    private TestEnvironment() {}

    private static void setupSystemProperties() {
        System.setProperty("EC2_REGION", "us-east-1");
        System.setProperty("EC2_AVAILABILITY_ZONE", "us-east-1c");
        System.setProperty("workflow.elasticsearch.url", "localhost:9300");
        System.setProperty("workflow.elasticsearch.index.name", "conductor");
        System.setProperty("workflow.namespace.prefix", "integration-test");
        System.setProperty("db", "memory");
        System.setProperty("workflow.elasticsearch.version", "5");
    }

    public static void setup() {
        setupSystemProperties();
    }

    public static void teardown() {
        System.setProperties(null);
    }
}
