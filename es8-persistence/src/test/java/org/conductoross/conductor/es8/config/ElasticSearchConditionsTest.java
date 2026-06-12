/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.es8.config;

import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ElasticSearchConditionsTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(ConditionalTestConfiguration.class);

    @Test
    public void shouldActivateForElasticsearch8IndexingType() {
        contextRunner
                .withPropertyValues(
                        "conductor.indexing.enabled=true", "conductor.indexing.type=elasticsearch8")
                .run(context -> assertTrue(context.containsBean("es8Marker")));
    }

    @Test
    public void shouldNotActivateWhenIndexingIsDisabled() {
        contextRunner
                .withPropertyValues(
                        "conductor.indexing.enabled=false",
                        "conductor.indexing.type=elasticsearch8")
                .run(context -> assertFalse(context.containsBean("es8Marker")));
    }

    @Test
    public void shouldNotActivateForLegacyVersionPropertyOnly() {
        contextRunner
                .withPropertyValues(
                        "conductor.indexing.enabled=true", "conductor.elasticsearch.version=8")
                .run(context -> assertFalse(context.containsBean("es8Marker")));
    }

    @Test
    public void shouldNotActivateForElasticsearchV7Selector() {
        contextRunner
                .withPropertyValues(
                        "conductor.indexing.enabled=true", "conductor.indexing.type=elasticsearch")
                .run(context -> assertFalse(context.containsBean("es8Marker")));
    }

    @Configuration(proxyBeanMethods = false)
    @Conditional(ElasticSearchConditions.ElasticSearchV8Enabled.class)
    static class ConditionalTestConfiguration {

        @Bean
        Marker es8Marker() {
            return new Marker();
        }
    }

    static class Marker {}
}
