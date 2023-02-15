/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.client.spring;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.sdk.workflow.executor.task.AnnotatedWorkerExecutor;
import com.netflix.discovery.EurekaClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClientProperties.class)
public class ConductorClientAutoConfiguration {

    @Autowired(required = false)
    private EurekaClient eurekaClient;

    @Autowired(required = false)
    private List<Worker> workers = new ArrayList<>();

    @ConditionalOnMissingBean
    @Bean
    public TaskClient taskClient(ClientProperties clientProperties) {
        TaskClient taskClient = new TaskClient();
        taskClient.setRootURI(clientProperties.getRootUri());
        return taskClient;
    }

    @ConditionalOnMissingBean
    @Bean
    public AnnotatedWorkerExecutor annotatedWorkerExecutor(TaskClient taskClient) {
        return new AnnotatedWorkerExecutor(taskClient);
    }

    @ConditionalOnMissingBean
    @Bean(initMethod = "init", destroyMethod = "shutdown")
    public TaskRunnerConfigurer taskRunnerConfigurer(
            TaskClient taskClient, ClientProperties clientProperties) {
        return new TaskRunnerConfigurer.Builder(taskClient, workers)
                .withTaskThreadCount(clientProperties.getTaskThreadCount())
                .withThreadCount(clientProperties.getThreadCount())
                .withSleepWhenRetry((int) clientProperties.getSleepWhenRetryDuration().toMillis())
                .withUpdateRetryCount(clientProperties.getUpdateRetryCount())
                .withTaskToDomain(clientProperties.getTaskToDomain())
                .withShutdownGracePeriodSeconds(clientProperties.getShutdownGracePeriodSeconds())
                .withEurekaClient(eurekaClient)
                .build();
    }
}
