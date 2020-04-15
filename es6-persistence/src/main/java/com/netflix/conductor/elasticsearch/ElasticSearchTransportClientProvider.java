/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.elasticsearch;

import com.google.inject.ProvisionException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchTransportClientProvider implements Provider<Client> {
    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchTransportClientProvider.class);

    private final ElasticSearchConfiguration configuration;

    @Inject
    public ElasticSearchTransportClientProvider(ElasticSearchConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Client get() {

        Settings settings = Settings.builder()
                .put("client.transport.ignore_cluster_name", true)
                .put("client.transport.sniff", true)
                .build();

        TransportClient tc = new PreBuiltTransportClient(settings);

        List<URI> clusterAddresses = configuration.getURIs();

        if (clusterAddresses.isEmpty()) {
            logger.warn(ElasticSearchConfiguration.ELASTIC_SEARCH_URL_PROPERTY_NAME +
                    " is not set.  Indexing will remain DISABLED.");
        }
        for (URI hostAddress : clusterAddresses) {
            int port = Optional.ofNullable(hostAddress.getPort()).orElse(9200);
            try {
                tc.addTransportAddress(new TransportAddress(InetAddress.getByName(hostAddress.getHost()), port));
            } catch (Exception e) {
                throw new ProvisionException("Invalid host" + hostAddress.getHost(), e);
            }
        }
        return tc;
    }
}
