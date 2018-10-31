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
package com.netflix.conductor.dao.es5.index;

import java.net.InetAddress;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Singleton;

import com.netflix.conductor.dao.IndexDAO;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.conductor.core.config.Configuration;


/**
 * @author Viren
 * Provider for the elasticsearch transport client
 */
public class ElasticSearchModuleV5 extends AbstractModule {

	private static Logger logger = LoggerFactory.getLogger(ElasticSearchModuleV5.class);

	private boolean restTransport;

	public ElasticSearchModuleV5() {
		this(false);
	}

	public ElasticSearchModuleV5(boolean restTransport) {
		this.restTransport = restTransport;
	}
	
	@Provides
	@Singleton
	public Client getClient(Configuration config) throws Exception {

		String clusterAddress = config.getProperty("workflow.elasticsearch.url", "");
		if(clusterAddress.equals("")) {
			logger.warn("workflow.elasticsearch.url is not set.  Indexing will remain DISABLED.");
		}

		Settings settings = Settings.builder()
				.put("client.transport.ignore_cluster_name",true)
				.put("client.transport.sniff", true)
				.build();

		TransportClient tc = new PreBuiltTransportClient(settings);
		String[] hosts = clusterAddress.split(",");
		for (String host : hosts) {
			String[] hostparts = host.split(":");
			String hostname = hostparts[0];
			int hostport = 9200;
			if (hostparts.length == 2) hostport = Integer.parseInt(hostparts[1]);
			tc.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(hostname), hostport));
		}
		return tc;
	}

	@Provides
	@Singleton
	public RestClient getRestClient(Configuration config) throws Exception {

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

	@Override
	protected void configure() {

		if (restTransport) {
			bind(IndexDAO.class).to(ElasticSearchRestDAOV5.class);
		} else {
			bind(IndexDAO.class).to(ElasticSearchDAOV5.class);
		}


	}
}
