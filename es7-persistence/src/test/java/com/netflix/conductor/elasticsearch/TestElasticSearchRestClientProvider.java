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

import org.elasticsearch.client.RestClient;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class TestElasticSearchRestClientProvider {
    @Test
    public void testGetRestClient() throws URISyntaxException {
        ElasticSearchConfiguration configuration = Mockito.mock(ElasticSearchConfiguration.class);
        Mockito.when(configuration.getElasticsearchRestClientConnectionRequestTimeout()).thenReturn(30000);
        Mockito.when(configuration.getURIs()).thenReturn(Arrays.asList(new URI("https://localhost:9201")));

        ElasticSearchRestClientProvider elasticSearchRestClientProvider = new ElasticSearchRestClientProvider(configuration);
        RestClient restClient = elasticSearchRestClientProvider.get();

        assertEquals(restClient.getNodes().get(0).getHost().getHostName(), "localhost");
        assertEquals(restClient.getNodes().get(0).getHost().getSchemeName(),"https");
        assertEquals(restClient.getNodes().get(0).getHost().getPort(), 9201);
    }
}
