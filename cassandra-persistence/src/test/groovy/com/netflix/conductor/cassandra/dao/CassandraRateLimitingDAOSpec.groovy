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

import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.model.TaskModel

import spock.lang.Subject

class CassandraRateLimitingDAOSpec extends CassandraSpec {

    @Subject
    CassandraRateLimitingDAO rateLimitingDAO

    def setup() {
        rateLimitingDAO = new CassandraRateLimitingDAO(session, objectMapper, cassandraProperties, statements)
    }

    // Each feature uses its own task def name: rate limit buckets live in the shared keyspace for
    // the lifetime of the spec, so reusing a name would leak counts between features.
    private static TaskModel task(String taskDefName) {
        new TaskModel(taskId: UUID.randomUUID().toString(), taskDefName: taskDefName)
    }

    def "tasks are rate limited once the limit within the frequency window is reached"() {
        given:
        def taskDef = new TaskDef(name: 'limited_task', rateLimitPerFrequency: 2, rateLimitFrequencyInSeconds: 300)

        expect: 'the first two executions to be within the limit'
        !rateLimitingDAO.exceedsRateLimitPerFrequency(task(taskDef.name), taskDef)
        !rateLimitingDAO.exceedsRateLimitPerFrequency(task(taskDef.name), taskDef)

        and: 'every execution after that to be rate limited'
        rateLimitingDAO.exceedsRateLimitPerFrequency(task(taskDef.name), taskDef)
        rateLimitingDAO.exceedsRateLimitPerFrequency(task(taskDef.name), taskDef)
    }

    def "rate limit is not applied when it is not configured"() {
        given:
        def taskDef = new TaskDef(name: 'unlimited_task', rateLimitPerFrequency: 0, rateLimitFrequencyInSeconds: 0)

        expect:
        (1..5).every { !rateLimitingDAO.exceedsRateLimitPerFrequency(task(taskDef.name), taskDef) }
    }

    def "buckets outside the frequency window no longer count towards the limit"() {
        given:
        def taskDef = new TaskDef(name: 'sliding_window_task', rateLimitPerFrequency: 1, rateLimitFrequencyInSeconds: 1)

        expect: 'the first execution to be within the limit and the second to be rate limited'
        !rateLimitingDAO.exceedsRateLimitPerFrequency(task(taskDef.name), taskDef)
        rateLimitingDAO.exceedsRateLimitPerFrequency(task(taskDef.name), taskDef)

        when: 'the one second window elapses'
        Thread.sleep(1500)

        then: 'the earlier execution has dropped out of the window'
        !rateLimitingDAO.exceedsRateLimitPerFrequency(task(taskDef.name), taskDef)
    }

    def "rate limits are tracked per task def"() {
        given:
        def taskDef = new TaskDef(name: 'task_def_a', rateLimitPerFrequency: 1, rateLimitFrequencyInSeconds: 300)
        def otherTaskDef = new TaskDef(name: 'task_def_b', rateLimitPerFrequency: 1, rateLimitFrequencyInSeconds: 300)

        when: 'one task def exhausts its limit'
        rateLimitingDAO.exceedsRateLimitPerFrequency(task(taskDef.name), taskDef)

        then:
        rateLimitingDAO.exceedsRateLimitPerFrequency(task(taskDef.name), taskDef)

        and: 'another task def is unaffected'
        !rateLimitingDAO.exceedsRateLimitPerFrequency(task(otherTaskDef.name), otherTaskDef)
    }

    def "rate limit falls back to the values on the task when there is no task def"() {
        given:
        def task = task('task_without_def')
        task.rateLimitPerFrequency = 1
        task.rateLimitFrequencyInSeconds = 300

        expect:
        !rateLimitingDAO.exceedsRateLimitPerFrequency(task, null)

        and:
        rateLimitingDAO.exceedsRateLimitPerFrequency(task, null)
    }
}
