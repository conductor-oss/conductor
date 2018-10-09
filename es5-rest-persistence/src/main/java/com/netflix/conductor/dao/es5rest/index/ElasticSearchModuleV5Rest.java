/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.dao.es5rest.index;

import java.net.URL;
import java.util.List;
import java.util.LinkedList;

import javax.inject.Singleton;

import com.netflix.conductor.dao.IndexDAO;
import org.apache.http.HttpHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.elasticsearch.client.RestClient;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.conductor.core.config.Configuration;


public class ElasticSearchModuleV5Rest extends AbstractModule {

    private static Logger logger = LoggerFactory.getLogger(ElasticSearchModuleV5Rest.class);

    @Provides
    @Singleton
    public RestClient getClient(Configuration config) throws Exception {

        String clusterAddress = config.getProperty("workflow.elasticsearch.url", "");

        if ("".equals(clusterAddress)) {
            logger.warn("workflow.elasticsearch.url is not set. Indexing will remain DISABLED.");
        }

        List<HttpHost> httpHosts = new LinkedList<>();
        String[] hosts = clusterAddress.split(",");
        for (String host : hosts) {
            URL url = new URL(host);
            httpHosts.add(new HttpHost(url.getHost(), url.getPort(), url.getProtocol()));
        }

        return RestClient.builder(httpHosts.toArray(new HttpHost[httpHosts.size()])).build();
    }

    protected void configure() {
        bind(IndexDAO.class).to(ElasticSearchDAOV5Rest.class);
    }

}
