package org.conductoross.conductor.ai.http;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "conductor.ai.http")
public class AIHttpClientProperties {

    private Duration connectTimeout = Duration.ofSeconds(60);
    private Duration readTimeout = Duration.ofSeconds(120);
    private Duration writeTimeout = Duration.ofSeconds(60);
    private int maxIdleConnections = 50;
    private Duration keepAlive = Duration.ofMinutes(5);
    private int maxRetries = 3;
}
