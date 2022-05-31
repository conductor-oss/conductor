package com.netflix.conductor.lambda.config;

public class LambdaProperties {

    private String region = "us-east-1";

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
