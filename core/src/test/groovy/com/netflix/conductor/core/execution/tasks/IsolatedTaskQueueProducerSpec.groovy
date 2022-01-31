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
package com.netflix.conductor.core.execution.tasks

import java.time.Duration

import org.junit.Test

import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.service.MetadataService

import spock.lang.Specification
import spock.lang.Subject

class IsolatedTaskQueueProducerSpec extends Specification {

    SystemTaskWorker systemTaskWorker
    MetadataService metadataService

    @Subject
    IsolatedTaskQueueProducer isolatedTaskQueueProducer

    def asyncSystemTask = new WorkflowSystemTask("asyncTask") {
        @Override
        boolean isAsync() {
            return true
        }
    }

    def setup() {
        systemTaskWorker = Mock(SystemTaskWorker.class)
        metadataService = Mock(MetadataService.class)

        isolatedTaskQueueProducer = new IsolatedTaskQueueProducer(metadataService, [asyncSystemTask] as Set, systemTaskWorker, false,
                Duration.ofSeconds(10))
    }

    @Test
    def "addTaskQueuesAddsElementToQueue"() {
        given:
        TaskDef taskDef = new TaskDef(isolationGroupId: "isolated")

        when:
        isolatedTaskQueueProducer.addTaskQueues()

        then:
        1 * systemTaskWorker.startPolling(asyncSystemTask, "${asyncSystemTask.taskType}-${taskDef.isolationGroupId}")
        1 * metadataService.getTaskDefs() >> Collections.singletonList(taskDef)
    }
}
