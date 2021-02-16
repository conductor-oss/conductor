/*
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.conductor.elasticsearch;

import org.elasticsearch.client.RestClientBuilder;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

public class ElasticSearchRestClientBuilderProviderTest {

    @Test
    public void testGetNoAuth() throws URISyntaxException {
        ElasticSearchConfiguration configuration = Mockito.mock(ElasticSearchConfiguration.class);
        Mockito.when(configuration.getURIs()).thenReturn(Arrays.asList(new URI("https://localhost:9201")));

        ElasticSearchRestClientBuilderProvider provider = new ElasticSearchRestClientBuilderProvider(configuration);
        RestClientBuilder restClientBuilder = provider.get();

        Mockito.verify(configuration, Mockito.atMost(1)).getElasticSearchBasicAuthUsername();
        Mockito.verify(configuration, Mockito.atMost(1)).getElasticSearchBasicAuthPassword();
    }

    @Test
    public void testGetWithAuth() throws URISyntaxException {
        ElasticSearchConfiguration configuration = Mockito.mock(ElasticSearchConfiguration.class);
        Mockito.when(configuration.getElasticSearchBasicAuthUsername()).thenReturn("testuser");
        Mockito.when(configuration.getElasticSearchBasicAuthPassword()).thenReturn("testpassword");
        Mockito.when(configuration.getURIs()).thenReturn(Arrays.asList(new URI("https://localhost:9201")));

        ElasticSearchRestClientBuilderProvider provider = new ElasticSearchRestClientBuilderProvider(configuration);
        RestClientBuilder restClientBuilder = provider.get();

        Mockito.verify(configuration, Mockito.atLeast(2)).getElasticSearchBasicAuthUsername();
        Mockito.verify(configuration, Mockito.atLeast(2)).getElasticSearchBasicAuthPassword();
    }
}
