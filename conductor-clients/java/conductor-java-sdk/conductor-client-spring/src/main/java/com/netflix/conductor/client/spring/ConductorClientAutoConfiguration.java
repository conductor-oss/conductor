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
package com.netflix.conductor.client.spring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.client.metrics.MetricsCollector;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import com.netflix.conductor.sdk.workflow.executor.task.AnnotatedWorkerExecutor;

import lombok.extern.slf4j.Slf4j;

@AutoConfiguration
@Slf4j
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@EnableConfigurationProperties(ClientProperties.class)
public class ConductorClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConductorClient conductorClient(ClientProperties properties) {
        var basePath = StringUtils.isBlank(properties.getRootUri()) ? properties.getBasePath() : properties.getRootUri();
        if (basePath == null) {
            return null;
        }

        var builder = ConductorClient.builder()
                .basePath(basePath)
                .connectTimeout(properties.getTimeout().getConnect())
                .readTimeout(properties.getTimeout().getRead())
                .writeTimeout(properties.getTimeout().getWrite())
                .verifyingSsl(properties.isVerifyingSsl());
        return builder.build();
    }

    @Bean
    @ConditionalOnBean(ConductorClient.class)
    @ConditionalOnMissingBean
    public TaskClient taskClient(ConductorClient client) {
        return new TaskClient(client);
    }

    @Bean
    @ConditionalOnBean(ConductorClient.class)
    @ConditionalOnMissingBean
    public AnnotatedWorkerExecutor annotatedWorkerExecutor(TaskClient taskClient) {
        return new AnnotatedWorkerExecutor(taskClient);
    }

    @Bean(initMethod = "init", destroyMethod = "shutdown")
    @ConditionalOnBean(ConductorClient.class)
    @ConditionalOnMissingBean
    public TaskRunnerConfigurer taskRunnerConfigurer(Environment env,
                                                     TaskClient taskClient,
                                                     ClientProperties clientProperties,
                                                     List<Worker> workers,
                                                     Optional<MetricsCollector> metricsCollector) {
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

        TaskRunnerConfigurer.Builder builder = new TaskRunnerConfigurer.Builder(taskClient, workers)
                .withTaskThreadCount(clientProperties.getTaskThreadCount())
                .withThreadCount(clientProperties.getThreadCount())
                .withSleepWhenRetry((int) clientProperties.getSleepWhenRetryDuration().toMillis())
                .withUpdateRetryCount(clientProperties.getUpdateRetryCount())
                .withTaskToDomain(clientProperties.getTaskToDomain())
                .withShutdownGracePeriodSeconds(clientProperties.getShutdownGracePeriodSeconds())
                .withTaskPollTimeout(clientProperties.getTaskPollTimeout());
        metricsCollector.ifPresent(builder::withMetricsCollector);
        return builder.build();
    }

    @Bean
    @ConditionalOnBean(ConductorClient.class)
    @ConditionalOnMissingBean
    public WorkflowExecutor workflowExecutor(ConductorClient client, AnnotatedWorkerExecutor annotatedWorkerExecutor) {
        return new WorkflowExecutor(client, annotatedWorkerExecutor);
    }

    @Bean
    @ConditionalOnBean(ConductorClient.class)
    @ConditionalOnMissingBean
    public WorkflowClient workflowClient(ConductorClient client) {
        return new WorkflowClient(client);
    }
}
