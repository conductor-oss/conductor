/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.conductor.dyno;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.dyno.connectionpool.RetryPolicy.RetryPolicyFactory;
import com.netflix.dyno.connectionpool.impl.RetryNTimes;
import com.netflix.dyno.connectionpool.impl.RunOnce;

public interface DynomiteConfiguration extends Configuration {
    // FIXME Are cluster and cluster name really different things?
    String CLUSTER_PROPERTY_NAME = "workflow.dynomite.cluster";
    String CLUSTER_DEFAULT_VALUE = null;

    String CLUSTER_NAME_PROPERTY_NAME = "workflow.dynomite.cluster.name";
    String HOSTS_PROPERTY_NAME = "workflow.dynomite.cluster.hosts";

    String MAX_CONNECTIONS_PER_HOST_PROPERTY_NAME = "workflow.dynomite.connection.maxConnsPerHost";
    int MAX_CONNECTIONS_PER_HOST_DEFAULT_VALUE = 10;

    String ROOT_NAMESPACE_PROPERTY_NAME = "workflow.namespace.queue.prefix";
    String ROOT_NAMESPACE_DEFAULT_VALUE = null;

    String DOMAIN_PROPERTY_NAME = "workflow.dyno.keyspace.domain";
    String DOMAIN_DEFAULT_VALUE = null;

    String NON_QUORUM_PORT_PROPERTY_NAME = "queues.dynomite.nonQuorum.port";
    int NON_QUORUM_PORT_DEFAULT_VALUE = 22122;
    
    //If attempt is set to 2, it will try for 2 + 1 = 3 times
    String MAX_RETRY_ATTEMPT = "workflow.dynomite.connection.max.retry.attempt";    
    int MAX_RETRY_ATTEMPT_VALUE = 0;

	String MAX_TIMEOUT_WHEN_EXHASUTED = "workflow.dynomite.connection.max.timeout.exhausted.ms";
    int MAX_TIMEOUT_WHEN_EXHAUSTED_VALUE = 800;

    default String getCluster() {
        return getProperty(CLUSTER_PROPERTY_NAME, CLUSTER_DEFAULT_VALUE);
    }

    default String getClusterName() {
        return getProperty(CLUSTER_NAME_PROPERTY_NAME, "");
    }

    default String getHosts() {
        return getProperty(HOSTS_PROPERTY_NAME, null);
    }

    default String getRootNamespace() {
        return getProperty(ROOT_NAMESPACE_PROPERTY_NAME, ROOT_NAMESPACE_DEFAULT_VALUE);
    }

    default String getDomain() {
        return getProperty(DOMAIN_PROPERTY_NAME, DOMAIN_DEFAULT_VALUE);
    }

    default int getMaxConnectionsPerHost() {
        return getIntProperty(
                MAX_CONNECTIONS_PER_HOST_PROPERTY_NAME,
                MAX_CONNECTIONS_PER_HOST_DEFAULT_VALUE
        );
    }

    default int getNonQuorumPort() {
        return getIntProperty(NON_QUORUM_PORT_PROPERTY_NAME, NON_QUORUM_PORT_DEFAULT_VALUE);
    }

    default String getQueuePrefix() {
        String prefix = getRootNamespace() + "." + getStack();

        if (getDomain() != null) {
            prefix = prefix + "." + getDomain();
        }

        return prefix;
    }
    
    default int getMaxTimeoutWhenExhausted() {
        return getIntProperty(
        		MAX_TIMEOUT_WHEN_EXHASUTED, 
        		MAX_TIMEOUT_WHEN_EXHAUSTED_VALUE);
    }

    default RetryPolicyFactory getConnectionRetryPolicy() {
    	int maxRetryAttempt = getIntProperty(
    							MAX_RETRY_ATTEMPT, 
    							MAX_RETRY_ATTEMPT_VALUE);    	
    	if (maxRetryAttempt == 0) {
    		return () -> new RunOnce();  				
    	}else {
    		return () -> new RetryNTimes(maxRetryAttempt,false);    				
    	}    	
    }
}
