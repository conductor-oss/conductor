/*
 * Copyright 2026 Conductor Authors.
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
package com.netflix.conductor.cassandra.dao

import org.springframework.cache.CacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext

import com.netflix.conductor.cassandra.config.cache.CacheableEventHandlerDAO
import com.netflix.conductor.cassandra.config.cache.CacheableMetadataDAO
import com.netflix.conductor.cassandra.config.cache.CachingConfig
import com.netflix.conductor.common.metadata.events.EventHandler
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.dao.EventHandlerDAO
import com.netflix.conductor.dao.MetadataDAO

import static com.netflix.conductor.cassandra.config.cache.CachingConfig.EVENT_HANDLER_CACHE
import static com.netflix.conductor.cassandra.config.cache.CachingConfig.TASK_DEF_CACHE

/**
 * Exercises the caching wrappers through a real Spring context, using the production
 * {@link CachingConfig}. The @CachePut key expressions are evaluated by the cache interceptor, so a
 * broken expression cannot be caught by calling these DAOs directly - it only surfaces at runtime,
 * as a SpelEvaluationException on every write.
 */
class CacheableDAOSpec extends CassandraSpec {

    AnnotationConfigApplicationContext context
    CacheManager cacheManager
    MetadataDAO metadataDAO
    EventHandlerDAO eventHandlerDAO

    def setup() {
        context = new AnnotationConfigApplicationContext(CachingConfig)
        cacheManager = context.getBean(CacheManager)

        // initializeBean applies the caching BeanPostProcessor, so what comes back is the proxy the
        // server actually calls.
        metadataDAO = context.beanFactory.initializeBean(
                new CacheableMetadataDAO(
                        new CassandraMetadataDAO(session, objectMapper, cassandraProperties, statements),
                        cassandraProperties,
                        cacheManager),
                'cacheableMetadataDAO') as MetadataDAO

        eventHandlerDAO = context.beanFactory.initializeBean(
                new CacheableEventHandlerDAO(
                        new CassandraEventHandlerDAO(session, objectMapper, cassandraProperties, statements),
                        cassandraProperties,
                        cacheManager),
                'cacheableEventHandlerDAO') as EventHandlerDAO
    }

    def cleanup() {
        context?.close()
    }

    def "createTaskDef caches the task def under its name"() {
        given:
        def taskDef = new TaskDef(name: 'cached_task_def', description: 'cached', retryCount: 0)

        when:
        metadataDAO.createTaskDef(taskDef)

        then: 'the key resolves from the argument, with no SpelEvaluationException'
        noExceptionThrown()
        cacheManager.getCache(TASK_DEF_CACHE).get('cached_task_def').get().name == 'cached_task_def'
    }

    def "updateTaskDef refreshes the cached task def"() {
        given:
        metadataDAO.createTaskDef(new TaskDef(name: 'updated_task_def', description: 'first', retryCount: 0))

        when:
        metadataDAO.updateTaskDef(new TaskDef(name: 'updated_task_def', description: 'second', retryCount: 0))

        then:
        noExceptionThrown()
        cacheManager.getCache(TASK_DEF_CACHE).get('updated_task_def').get().description == 'second'
    }

    def "addEventHandler resolves its cache key from the event handler"() {
        given:
        def eventHandler = new EventHandler(name: 'cached_event_handler', event: 'conductor:test', active: false)

        when:
        eventHandlerDAO.addEventHandler(eventHandler)

        then: 'an entry exists under the resolved key, so no SpelEvaluationException was thrown'
        noExceptionThrown()
        cacheManager.getCache(EVENT_HANDLER_CACHE).get('cached_event_handler') != null

        and: 'the value is null because @CachePut is on a void method - the periodic refresh is what populates it'
        cacheManager.getCache(EVENT_HANDLER_CACHE).get('cached_event_handler').get() == null
    }
}
