/*
 * Copyright 2020 Orkes, Inc.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import com.netflix.conductor.sdk.workflow.executor.task.AnnotatedWorkerExecutor;

import lombok.extern.slf4j.Slf4j;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClientProperties.class)
@Slf4j
public class ConductorClientAutoConfiguration {

    @ConditionalOnMissingBean
    @Bean
    public ConductorClient conductorClient(ClientProperties clientProperties) {
        // TODO allow configuration of other properties via application.properties
        return ConductorClient.builder()
                .basePath(clientProperties.getRootUri())
                .build();
    }

    @ConditionalOnMissingBean
    @Bean
    public TaskClient taskClient(ConductorClient client) {
        return new TaskClient(client);
    }

    @ConditionalOnMissingBean
    @Bean
    public AnnotatedWorkerExecutor annotatedWorkerExecutor(TaskClient taskClient) {
        return new AnnotatedWorkerExecutor(taskClient);
    }

    @ConditionalOnMissingBean
    @Bean(initMethod = "init", destroyMethod = "shutdown")
    public TaskRunnerConfigurer taskRunnerConfigurer(Environment env,
                                                     TaskClient taskClient,
                                                     ClientProperties clientProperties,
                                                     List<Worker> workers) {
        Map<String, Integer> taskThreadCount = new HashMap<>();
        for (Worker worker : workers) {
            String key = "conductor.worker." + worker.getTaskDefName() + ".threadCount";
            int threadCount = env.getProperty(key, Integer.class, 10);
            log.info("Using {} threads for {} worker", threadCount, worker.getTaskDefName());
            taskThreadCount.put(worker.getTaskDefName(), threadCount);
        }

        if (clientProperties.getTaskThreadCount() != null) {
            clientProperties.getTaskThreadCount().putAll(taskThreadCount);
        } else {
            clientProperties.setTaskThreadCount(taskThreadCount);
        }

        return new TaskRunnerConfigurer.Builder(taskClient, workers)
                .withTaskThreadCount(clientProperties.getTaskThreadCount())
                .withThreadCount(clientProperties.getThreadCount())
                .withSleepWhenRetry((int) clientProperties.getSleepWhenRetryDuration().toMillis())
                .withUpdateRetryCount(clientProperties.getUpdateRetryCount())
                .withTaskToDomain(clientProperties.getTaskToDomain())
                .withShutdownGracePeriodSeconds(clientProperties.getShutdownGracePeriodSeconds())
                .withTaskPollTimeout(clientProperties.getTaskPollTimeout())
                .build();
    }

    @Bean
    public WorkflowExecutor workflowExecutor(ConductorClient client, AnnotatedWorkerExecutor annotatedWorkerExecutor) {
        return new WorkflowExecutor(client, annotatedWorkerExecutor);
    }

    @ConditionalOnMissingBean
    @Bean
    public WorkflowClient workflowClient(ConductorClient client) {
        return new WorkflowClient(client);
    }
}
