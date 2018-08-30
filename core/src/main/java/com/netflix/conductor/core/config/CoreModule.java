/**
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MultibindingsScanner;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.google.inject.name.Named;
import com.netflix.conductor.core.events.ActionProcessor;
import com.netflix.conductor.core.events.EventProcessor;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.core.events.queue.dyno.DynoEventQueueProvider;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.mapper.DecisionTaskMapper;
import com.netflix.conductor.core.execution.mapper.DynamicTaskMapper;
import com.netflix.conductor.core.execution.mapper.EventTaskMapper;
import com.netflix.conductor.core.execution.mapper.ForkJoinDynamicTaskMapper;
import com.netflix.conductor.core.execution.mapper.ForkJoinTaskMapper;
import com.netflix.conductor.core.execution.mapper.JoinTaskMapper;
import com.netflix.conductor.core.execution.mapper.SimpleTaskMapper;
import com.netflix.conductor.core.execution.mapper.SubWorkflowTaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.mapper.UserDefinedTaskMapper;
import com.netflix.conductor.core.execution.mapper.WaitTaskMapper;
import com.netflix.conductor.core.execution.tasks.Event;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.core.execution.tasks.SystemTaskWorkerCoordinator;
import com.netflix.conductor.core.execution.tasks.Wait;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;


/**
 * @author Viren
 */
public class CoreModule extends AbstractModule {

    @Override
    protected void configure() {
        install(MultibindingsScanner.asModule());
        requestStaticInjection(EventQueues.class);
        bind(ActionProcessor.class).asEagerSingleton();
        bind(EventProcessor.class).asEagerSingleton();
        bind(SystemTaskWorkerCoordinator.class).asEagerSingleton();
        bind(SubWorkflow.class).asEagerSingleton();
        bind(Wait.class).asEagerSingleton();
        bind(Event.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    public ParametersUtils getParameterUtils() {
        return new ParametersUtils();
    }


    @ProvidesIntoMap
    @StringMapKey("conductor")
    @Singleton
    @Named("EventQueueProviders")
    public EventQueueProvider getDynoEventQueueProvider(QueueDAO queueDAO, Configuration configuration) {
        return new DynoEventQueueProvider(queueDAO, configuration);
    }

    @ProvidesIntoMap
    @StringMapKey("DECISION")
    @Singleton
    @Named("TaskMappers")
    public TaskMapper getDecisionTaskMapper() {
        return new DecisionTaskMapper();
    }

    @ProvidesIntoMap
    @StringMapKey("DYNAMIC")
    @Singleton
    @Named("TaskMappers")
    public TaskMapper getDynamicTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        return new DynamicTaskMapper(parametersUtils, metadataDAO);
    }

    @ProvidesIntoMap
    @StringMapKey("JOIN")
    @Singleton
    @Named("TaskMappers")
    public TaskMapper getJoinTaskMapper() {
        return new JoinTaskMapper();
    }



    @ProvidesIntoMap
    @StringMapKey("FORK_JOIN_DYNAMIC")
    @Singleton
    @Named("TaskMappers")
    public TaskMapper getForkJoinDynamicTaskMapper(ParametersUtils parametersUtils, ObjectMapper objectMapper) {
        return new ForkJoinDynamicTaskMapper(parametersUtils, objectMapper);
    }

    @ProvidesIntoMap
    @StringMapKey("EVENT")
    @Singleton
    @Named("TaskMappers")
    public TaskMapper getEventTaskMapper(ParametersUtils parametersUtils) {
        return new EventTaskMapper(parametersUtils);
    }

    @ProvidesIntoMap
    @StringMapKey("WAIT")
    @Singleton
    @Named("TaskMappers")
    public TaskMapper getWaitTaskMapper(ParametersUtils parametersUtils) {
        return new WaitTaskMapper(parametersUtils);
    }

    @ProvidesIntoMap
    @Singleton
    @StringMapKey("SUB_WORKFLOW")
    @Named("TaskMappers")
    public TaskMapper getSubWorkflowTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        return new SubWorkflowTaskMapper(parametersUtils, metadataDAO);
    }

    @ProvidesIntoMap
    @Singleton
    @StringMapKey("FORK_JOIN")
    @Named("TaskMappers")
    public TaskMapper getForkJoinTaskMapper() {
        return new ForkJoinTaskMapper();
    }

    @ProvidesIntoMap
    @StringMapKey("USER_DEFINED")
    @Singleton
    @Named("TaskMappers")
    public TaskMapper getUserDefinedTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        return new UserDefinedTaskMapper(parametersUtils, metadataDAO);
    }

    @ProvidesIntoMap
    @StringMapKey("SIMPLE")
    @Singleton
    @Named("TaskMappers")
    public TaskMapper getSimpleTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        return new SimpleTaskMapper(parametersUtils, metadataDAO);
    }


}
