package com.netflix.conductor.contribs.metrics;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Spectator;

@RunWith(SpringRunner.class)
@TestPropertySource(properties = {"conductor.metrics-prometheus.enabled=true"})
@Ignore
public class PrometheusMetricsConfigurationTest {
  @Test
  public void testCollector() {
    new ApplicationContextRunner()
        .withPropertyValues("conductor.metrics-prometheus.enabled:true")
        .withUserConfiguration(PrometheusMetricsConfiguration.class)
        .run(context -> {
          final Optional<Field> registries = Arrays
              .stream(Spectator.globalRegistry().getClass().getDeclaredFields())
              .filter(f -> f.getName().equals("registries")).findFirst();
          Assert.assertTrue(registries.isPresent());
          registries.get().setAccessible(true);
          Assert.assertEquals(1, ((List<Meter>)registries.get().get(Spectator.globalRegistry())).size());
        });
  }
}
