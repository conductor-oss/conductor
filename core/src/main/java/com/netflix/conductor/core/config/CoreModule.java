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
import com.netflix.conductor.core.events.SimpleActionProcessor;
import com.netflix.conductor.core.events.SimpleEventProcessor;
import com.netflix.conductor.core.events.queue.EventPollSchedulerProvider;
import com.netflix.conductor.core.events.queue.dyno.DynoEventQueueProvider;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.mapper.DecisionTaskMapper;
import com.netflix.conductor.core.execution.mapper.DynamicTaskMapper;
import com.netflix.conductor.core.execution.mapper.EventTaskMapper;
import com.netflix.conductor.core.execution.mapper.ExclusiveJoinTaskMapper;
import com.netflix.conductor.core.execution.mapper.ForkJoinDynamicTaskMapper;
import com.netflix.conductor.core.execution.mapper.ForkJoinTaskMapper;
import com.netflix.conductor.core.execution.mapper.HTTPTaskMapper;
import com.netflix.conductor.core.execution.mapper.JoinTaskMapper;
import com.netflix.conductor.core.execution.mapper.KafkaPublishTaskMapper;
import com.netflix.conductor.core.execution.mapper.LambdaTaskMapper;
import com.netflix.conductor.core.execution.mapper.SimpleTaskMapper;
import com.netflix.conductor.core.execution.mapper.SubWorkflowTaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.mapper.TerminateTaskMapper;
import com.netflix.conductor.core.execution.mapper.UserDefinedTaskMapper;
import com.netflix.conductor.core.execution.mapper.WaitTaskMapper;
import com.netflix.conductor.core.execution.mapper.DoWhileTaskMapper;
import com.netflix.conductor.core.execution.mapper.JsonJQTransformTaskMapper;
import com.netflix.conductor.core.execution.mapper.SetVariableTaskMapper;
import com.netflix.conductor.core.execution.tasks.Event;
import com.netflix.conductor.core.execution.tasks.IsolatedTaskQueueProducer;
import com.netflix.conductor.core.execution.tasks.Lambda;
import com.netflix.conductor.core.execution.tasks.SetVariable;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.core.execution.tasks.SystemTaskWorkerCoordinator;
import com.netflix.conductor.core.execution.tasks.Terminate;
import com.netflix.conductor.core.execution.tasks.Wait;
import com.netflix.conductor.core.utils.JsonUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import rx.Scheduler;

import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_DECISION;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_DYNAMIC;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_EVENT;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_EXCLUSIVE_JOIN;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_FORK_JOIN;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_FORK_JOIN_DYNAMIC;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_HTTP;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_JOIN;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_KAFKA_PUBLISH;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_LAMBDA;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_SIMPLE;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_SUB_WORKFLOW;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_TERMINATE;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_USER_DEFINED;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_WAIT;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_DO_WHILE;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_JSON_JQ_TRANSFORM;
import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_SET_VARIABLE;
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
        bind(SystemTaskWorkerCoordinator.class).asEagerSingleton();
        bind(SubWorkflow.class).asEagerSingleton();
        bind(Wait.class).asEagerSingleton();
        bind(Event.class).asEagerSingleton();
        bind(Lambda.class).asEagerSingleton();
        bind(Terminate.class).asEagerSingleton();
        bind(IsolatedTaskQueueProducer.class).asEagerSingleton();
        bind(SetVariable.class).asEagerSingleton();
        // start processing events when instance starts
        bind(ActionProcessor.class).to(SimpleActionProcessor.class);
        bind(EventProcessor.class).to(SimpleEventProcessor.class).asEagerSingleton();
        bind(Scheduler.class).toProvider(EventPollSchedulerProvider.class).asEagerSingleton();
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
    public EventQueueProvider getDynoEventQueueProvider(QueueDAO queueDAO, Configuration configuration, Scheduler eventScheduler) {
        return new DynoEventQueueProvider(queueDAO, configuration, eventScheduler);
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_DECISION)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getDecisionTaskMapper() {
        return new DecisionTaskMapper();
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_DO_WHILE)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getDoWhileTaskMapper(MetadataDAO metadataDAO) {
        return new DoWhileTaskMapper(metadataDAO);
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

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_HTTP)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getHTTPTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        return new HTTPTaskMapper(parametersUtils, metadataDAO);
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_LAMBDA)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getLambdaTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        return new LambdaTaskMapper(parametersUtils, metadataDAO);
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_EXCLUSIVE_JOIN)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getExclusiveJoinTaskMapper() {
        return new ExclusiveJoinTaskMapper();
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_TERMINATE)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getTerminateTaskMapper(ParametersUtils parametersUtils) {
        return new TerminateTaskMapper(parametersUtils);
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_KAFKA_PUBLISH)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getKafkaPublishTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        return new KafkaPublishTaskMapper(parametersUtils, metadataDAO);
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_JSON_JQ_TRANSFORM)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getJsonJQTransformTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        return new JsonJQTransformTaskMapper(parametersUtils, metadataDAO);
    }

    @ProvidesIntoMap
    @StringMapKey(TASK_TYPE_SET_VARIABLE)
    @Singleton
    @Named(TASK_MAPPERS_QUALIFIER)
    public TaskMapper getSetVariableTaskMapper() {
        return new SetVariableTaskMapper();
    }
}
