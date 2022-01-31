/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.redis.limit

import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.spock.Testcontainers

import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.model.TaskModel
import com.netflix.conductor.redis.limit.config.RedisConcurrentExecutionLimitProperties

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Testcontainers
class RedisConcurrentExecutionLimitDAOSpec extends Specification {

    GenericContainer redis = new GenericContainer("redis:5.0.3-alpine")
            .withExposedPorts(6379)

    @Subject
    RedisConcurrentExecutionLimitDAO dao

    StringRedisTemplate redisTemplate

    RedisConcurrentExecutionLimitProperties properties

    def setup() {
        properties = new RedisConcurrentExecutionLimitProperties(namespace: 'conductor')
        redisTemplate = new StringRedisTemplate(new JedisConnectionFactory(new RedisStandaloneConfiguration(redis.host, redis.firstMappedPort)))
        dao = new RedisConcurrentExecutionLimitDAO(redisTemplate, properties)
    }

    def "verify addTaskToLimit adds the taskId to the right set"() {
        given:
        def taskId = 'task1'
        def taskDefName = 'task_def_name1'
        def keyName = "${properties.namespace}:$taskDefName" as String

        TaskModel task = new TaskModel(taskId: taskId, taskDefName: taskDefName)

        when:
        dao.addTaskToLimit(task)

        then:
        redisTemplate.hasKey(keyName)
        redisTemplate.opsForSet().size(keyName) == 1
        redisTemplate.opsForSet().isMember(keyName, taskId)
    }

    def "verify removeTaskFromLimit removes the taskId from the right set"() {
        given:
        def taskId = 'task1'
        def taskDefName = 'task_def_name1'
        def keyName = "${properties.namespace}:$taskDefName" as String

        redisTemplate.opsForSet().add(keyName, taskId)

        TaskModel task = new TaskModel(taskId: taskId, taskDefName: taskDefName)

        when:
        dao.removeTaskFromLimit(task)

        then:
        !redisTemplate.hasKey(keyName) // since the only element in the set is removed, Redis removes the set
    }

    @Unroll
    def "verify exceedsLimit returns false for #testCase"() {
        given:
        def taskId = 'task1'
        def taskDefName = 'task_def_name1'

        TaskModel task = new TaskModel(taskId: taskId, taskDefName: taskDefName, workflowTask: workflowTask)

        when:
        def retVal = dao.exceedsLimit(task)

        then:
        !retVal

        where:
        workflowTask << [new WorkflowTask(taskDefinition: null), new WorkflowTask(taskDefinition: new TaskDef(concurrentExecLimit: -2))]
        testCase << ['a task with no TaskDefinition', 'TaskDefinition with concurrentExecLimit is less than 0']
    }

    def "verify exceedsLimit returns false for tasks less than concurrentExecLimit"() {
        given:
        def taskId = 'task1'
        def taskDefName = 'task_def_name1'
        def keyName = "${properties.namespace}:$taskDefName" as String

        TaskModel task = new TaskModel(taskId: taskId, taskDefName: taskDefName, workflowTask: new WorkflowTask(taskDefinition: new TaskDef(concurrentExecLimit: 2)))

        redisTemplate.opsForSet().add(keyName, taskId)

        when:
        def retVal = dao.exceedsLimit(task)

        then:
        !retVal
    }

    def "verify exceedsLimit returns false for taskId already in the set but more than concurrentExecLimit"() {
        given:
        def taskId = 'task1'
        def taskDefName = 'task_def_name1'
        def keyName = "${properties.namespace}:$taskDefName" as String

        TaskModel task = new TaskModel(taskId: taskId, taskDefName: taskDefName, workflowTask: new WorkflowTask(taskDefinition: new TaskDef(concurrentExecLimit: 2)))

        redisTemplate.opsForSet().add(keyName, taskId) // add the id of the task passed as argument to exceedsLimit
        redisTemplate.opsForSet().add(keyName, 'taskId2')

        when:
        def retVal = dao.exceedsLimit(task)

        then:
        !retVal
    }

    def "verify exceedsLimit returns true for a new taskId more than concurrentExecLimit"() {
        given:
        def taskId = 'task1'
        def taskDefName = 'task_def_name1'
        def keyName = "${properties.namespace}:$taskDefName" as String

        TaskModel task = new TaskModel(taskId: taskId, taskDefName: taskDefName, workflowTask: new WorkflowTask(taskDefinition: new TaskDef(concurrentExecLimit: 2)))

        // add task ids different from the id of the task passed to exceedsLimit
        redisTemplate.opsForSet().add(keyName, 'taskId2')
        redisTemplate.opsForSet().add(keyName, 'taskId3')

        when:
        def retVal = dao.exceedsLimit(task)

        then:
        retVal
    }

    def "verify createKeyName ignores namespace if its not present"() {
        given:
        def dao = new RedisConcurrentExecutionLimitDAO(null, conductorProperties)

        when:
        def keyName = dao.createKeyName('taskdefname')

        then:
        keyName == expectedKeyName

        where:
        conductorProperties << [new RedisConcurrentExecutionLimitProperties(), new RedisConcurrentExecutionLimitProperties(namespace: null), new RedisConcurrentExecutionLimitProperties(namespace: 'test')]
        expectedKeyName << ['conductor:taskdefname', 'taskdefname', 'test:taskdefname']
    }
}
