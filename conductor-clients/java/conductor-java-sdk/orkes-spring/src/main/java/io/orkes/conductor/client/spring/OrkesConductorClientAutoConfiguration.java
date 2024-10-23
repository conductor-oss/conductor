/*
 * Copyright 2020 Conductor Authors.
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
package io.orkes.conductor.client.spring;


import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import io.orkes.conductor.client.ApiClient;
import io.orkes.conductor.client.AuthorizationClient;
import io.orkes.conductor.client.OrkesClients;
import io.orkes.conductor.client.SchedulerClient;
import io.orkes.conductor.client.SecretClient;
import io.orkes.conductor.client.http.OrkesEventClient;
import io.orkes.conductor.client.http.OrkesMetadataClient;
import io.orkes.conductor.client.http.OrkesTaskClient;
import io.orkes.conductor.client.http.OrkesWorkflowClient;

import lombok.extern.slf4j.Slf4j;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class OrkesConductorClientAutoConfiguration {

    // Keeping these for backwards compatibility
    public static final String CONDUCTOR_SERVER_URL ="conductor.server.url";
    public static final String CONDUCTOR_SECURITY_CLIENT_KEY_ID ="conductor.security.client.key-id";
    public static final String CONDUCTOR_SECURITY_CLIENT_SECRET ="conductor.security.client.secret";

    // Properties should be placed under "conductor.client"
    public static final String CONDUCTOR_CLIENT_BASE_PATH = "conductor.client.basepath";
    public static final String CONDUCTOR_CLIENT_KEY_ID = "conductor.client.key-id";
    public static final String CONDUCTOR_CLIENT_SECRET = "conductor.client.secret";
    public static final String CONDUCTOR_CLIENT_CONNECT_TIMEOUT = "conductor.client.timeout.connect";
    public static final String CONDUCTOR_CLIENT_READ_TIMEOUT = "conductor.client.timeout.read";
    public static final String CONDUCTOR_CLIENT_WRITE_TIMEOUT = "conductor.client.timeout.write";
    public static final String CONDUCTOR_CLIENT_VERIFYING_SSL = "conductor.client.verifying-ssl";

    @Bean
    @ConditionalOnMissingBean
    public ApiClient orkesConductorClient(Environment env) {
        ApiClient.ApiClientBuilder builder = ApiClient.builder();

        String basePath = env.getProperty(CONDUCTOR_CLIENT_BASE_PATH);
        if (basePath == null) {
            basePath = env.getProperty(CONDUCTOR_SERVER_URL);
        }

        String keyId = env.getProperty(CONDUCTOR_CLIENT_KEY_ID);
        if (keyId == null) {
            keyId = env.getProperty(CONDUCTOR_SECURITY_CLIENT_KEY_ID);
        }

        String secret = env.getProperty(CONDUCTOR_CLIENT_SECRET);
        if (secret == null) {
            secret = env.getProperty(CONDUCTOR_SECURITY_CLIENT_SECRET);
        }

        Long connectTimeout = env.getProperty(CONDUCTOR_CLIENT_CONNECT_TIMEOUT, Long.class);
        if (connectTimeout != null) {
            builder.connectTimeout(connectTimeout);
        }

        Long readTimeout = env.getProperty(CONDUCTOR_CLIENT_READ_TIMEOUT, Long.class);
        if (readTimeout != null) {
            builder.readTimeout(readTimeout);
        }

        Long writeTimeout = env.getProperty(CONDUCTOR_CLIENT_WRITE_TIMEOUT, Long.class);
        if (writeTimeout != null) {
            builder.writeTimeout(writeTimeout);
        }

        Boolean verifyingSsl = env.getProperty(CONDUCTOR_CLIENT_VERIFYING_SSL, Boolean.class);
        if (verifyingSsl != null) {
            builder.verifyingSsl(verifyingSsl);
        }

        return builder
                .basePath(basePath)
                .credentials(keyId, secret)
                .build();
    }

    @Bean
    public OrkesClients orkesClients(ApiClient client) {
        return new OrkesClients(client);
    }

    @Bean
    public OrkesTaskClient orkesTaskClient(OrkesClients clients) {
        return clients.getTaskClient();
    }

    @Bean
    public OrkesMetadataClient orkesMetadataClient(OrkesClients clients) {
        return clients.getMetadataClient();
    }

    @Bean
    public OrkesWorkflowClient orkesWorkflowClient(OrkesClients clients) {
        return clients.getWorkflowClient();
    }

    @Bean
    public AuthorizationClient orkesAuthorizationClient(OrkesClients clients) {
        return clients.getAuthorizationClient();
    }

    @Bean
    public OrkesEventClient orkesEventClient(OrkesClients clients) {
        return clients.getEventClient();
    }

    @Bean
    public SchedulerClient orkesSchedulerClient(OrkesClients clients) {
        return clients.getSchedulerClient();
    }

    @Bean
    public SecretClient orkesSecretClient(OrkesClients clients) {
        return clients.getSecretClient();
    }

}
