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
package com.netflix.conductor.dao.index;

import java.net.InetAddress;

import javax.inject.Singleton;

import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.conductor.core.config.Configuration;


/**
 * @author Viren
 * Provider for the elasticsearch transport client
 */
public class ElasticsearchModule extends AbstractModule {

	private static Logger log = LoggerFactory.getLogger(ElasticSearchDAO.class);
	
	@Provides
	@Singleton
	public Client getClient(Configuration config) throws Exception {

		String clusterAddress = config.getProperty("workflow.elasticsearch.url", "");
		if(clusterAddress.equals("")) {
			log.warn("workflow.elasticsearch.url is not set.  Indexing will remain DISABLED.");
		}
		
    	Settings.Builder settings = Settings.settingsBuilder();
        settings.put("client.transport.ignore_cluster_name", true);
        settings.put("client.transport.sniff", true);
        
        TransportClient tc = TransportClient.builder().settings(settings).build();
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

	@Override
	protected void configure() {
		
	}
}
