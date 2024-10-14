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

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import com.netflix.conductor.client.spring.ClientProperties;

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

@AutoConfiguration
@Slf4j
@EnableConfigurationProperties(ClientProperties.class)
@Import(OrkesClientProperties.class)
public class OrkesConductorClientAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public ApiClient orkesConductorClient(ClientProperties clientProperties,
                                          OrkesClientProperties orkesClientProperties) {
        var basePath = StringUtils.isBlank(clientProperties.getRootUri()) ? clientProperties.getBasePath() : clientProperties.getRootUri();
        if (basePath == null) {
            basePath = orkesClientProperties.getConductorServerUrl();
        }

        if (basePath == null) {
            return null;
        }

        String key = null;
        String secret = null;
        if (orkesClientProperties.getKeyId() != null) {
            key = orkesClientProperties.getKeyId();
            secret = orkesClientProperties.getSecret();
        } else if (orkesClientProperties.getSecurityKeyId() != null) {
            key = orkesClientProperties.getSecurityKeyId();
            secret = orkesClientProperties.getSecuritySecret();
        }

        return ApiClient.builder()
                .basePath(basePath)
                .credentials(key, secret)
                .connectTimeout(clientProperties.getTimeout().getConnect())
                .readTimeout(clientProperties.getTimeout().getRead())
                .writeTimeout(clientProperties.getTimeout().getWrite())
                .verifyingSsl(clientProperties.isVerifyingSsl())
                .build();
    }

    @Bean
    @ConditionalOnBean(ApiClient.class)
    @ConditionalOnMissingBean
    public OrkesClients orkesClients(ApiClient client) {
        return new OrkesClients(client);
    }

    @Bean
    @ConditionalOnBean(ApiClient.class)
    @ConditionalOnMissingBean
    public OrkesTaskClient orkesTaskClient(OrkesClients clients) {
        return clients.getTaskClient();
    }

    @Bean
    @ConditionalOnBean(ApiClient.class)
    @ConditionalOnMissingBean
    public OrkesMetadataClient orkesMetadataClient(OrkesClients clients) {
        return clients.getMetadataClient();
    }

    @Bean
    @ConditionalOnBean(ApiClient.class)
    @ConditionalOnMissingBean
    public OrkesWorkflowClient orkesWorkflowClient(OrkesClients clients) {
        return clients.getWorkflowClient();
    }

    @Bean
    @ConditionalOnBean(ApiClient.class)
    @ConditionalOnMissingBean
    public AuthorizationClient orkesAuthorizationClient(OrkesClients clients) {
        return clients.getAuthorizationClient();
    }

    @Bean
    @ConditionalOnBean(ApiClient.class)
    @ConditionalOnMissingBean
    public OrkesEventClient orkesEventClient(OrkesClients clients) {
        return clients.getEventClient();
    }

    @Bean
    @ConditionalOnBean(ApiClient.class)
    @ConditionalOnMissingBean
    public SchedulerClient orkesSchedulerClient(OrkesClients clients) {
        return clients.getSchedulerClient();
    }

    @Bean
    @ConditionalOnBean(ApiClient.class)
    @ConditionalOnMissingBean
    public SecretClient orkesSecretClient(OrkesClients clients) {
        return clients.getSecretClient();
    }

}
