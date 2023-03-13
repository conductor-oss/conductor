/*
 * Copyright 2023 Netflix, Inc.
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

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.sdk.workflow.executor.task.AnnotatedWorkerExecutor;
import com.netflix.conductor.sdk.workflow.executor.task.WorkerConfiguration;

@Component
public class ConductorWorkerAutoConfiguration
        implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired private TaskClient taskClient;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent refreshedEvent) {
        ApplicationContext applicationContext = refreshedEvent.getApplicationContext();
        Environment environment = applicationContext.getEnvironment();
        WorkerConfiguration configuration = new SpringWorkerConfiguration(environment);
        AnnotatedWorkerExecutor annotatedWorkerExecutor =
                new AnnotatedWorkerExecutor(taskClient, configuration);

        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Component.class);
        beans.values()
                .forEach(
                        bean -> {
                            annotatedWorkerExecutor.addBean(bean);
                        });
        annotatedWorkerExecutor.startPolling();
    }
}
