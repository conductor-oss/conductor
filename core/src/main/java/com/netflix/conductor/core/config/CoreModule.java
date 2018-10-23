/*
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
import com.netflix.conductor.core.utils.JsonUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;

import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_DECISION;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_DYNAMIC;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_EVENT;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_FORK_JOIN;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_FORK_JOIN_DYNAMIC;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_JOIN;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_SIMPLE;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_SUB_WORKFLOW;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_USER_DEFINED;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_WAIT;
import static com.netflix.conductor.core.events.EventQueues.EVENT_QUEUE_PROVIDERS_QUALIFIER;

/**
 * @author Viren
 */
public class CoreModule extends AbstractModule {

    private static final String CONDUCTOR_QUALIFIER = "conductor";
    private static final String TASK_MAPPERS_QUALIFIER = "TaskMappers";

    @Override
    protected void configure() {
        install(MultibindingsScanner.asModule());
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

    @Provides
    @Singleton
    public JsonUtils getJsonUtils() {
        return new JsonUtils();
    }

    @ProvidesIntoMap
    @StringMapKey(CONDUCTOR_QUALIFIER)
    @Singleton
    @Named(EVENT_QUEUE_PROVIDERS_QUALIFIER)
    public EventQueueProvider getDynoEventQueueProvider(QueueDAO queueDAO, Configuration configuration) {
        return new DynoEventQueueProvider(queueDAO, configuration);
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_DECISION)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getDecisionTaskMapper() {
        return new DecisionTaskMapper();
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_DYNAMIC)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getDynamicTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        return new DynamicTaskMapper(parametersUtils, metadataDAO);
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_JOIN)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getJoinTaskMapper() {
        return new JoinTaskMapper();
    }


    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_FORK_JOIN_DYNAMIC)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getForkJoinDynamicTaskMapper(ParametersUtils parametersUtils, ObjectMapper objectMapper, MetadataDAO metadataDAO) {
        return new ForkJoinDynamicTaskMapper(parametersUtils, objectMapper, metadataDAO);
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_EVENT)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getEventTaskMapper(ParametersUtils parametersUtils) {
        return new EventTaskMapper(parametersUtils);
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_WAIT)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getWaitTaskMapper(ParametersUtils parametersUtils) {
        return new WaitTaskMapper(parametersUtils);
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_SUB_WORKFLOW)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getSubWorkflowTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        return new SubWorkflowTaskMapper(parametersUtils, metadataDAO);
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_FORK_JOIN)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getForkJoinTaskMapper() {
        return new ForkJoinTaskMapper();
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_USER_DEFINED)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getUserDefinedTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        return new UserDefinedTaskMapper(parametersUtils, metadataDAO);
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_SIMPLE)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getSimpleTaskMapper(ParametersUtils parametersUtils) {
        return new SimpleTaskMapper(parametersUtils);
    }
}
