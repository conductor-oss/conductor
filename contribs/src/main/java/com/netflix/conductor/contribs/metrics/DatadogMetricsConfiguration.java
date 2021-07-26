package com.netflix.conductor.contribs.metrics;

import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.micrometer.MicrometerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Metrics Datadog module, sending all metrics to a Datadog server.
 * <p>
 * Enable in config: conductor.metrics-datadog.enabled=true
 * <p>
 * Make sure your dependencies include both micrometer-registry-datadog & spring-boot-starter-actuator
 */
@ConditionalOnProperty(value = "conductor.metrics-datadog.enabled", havingValue = "true")
@Configuration
public class DatadogMetricsConfiguration {

    public DatadogMetricsConfiguration(MeterRegistry meterRegistry) {
        final MicrometerRegistry metricsRegistry = new MicrometerRegistry(meterRegistry);
        Spectator.globalRegistry().add(metricsRegistry);
    }
}
