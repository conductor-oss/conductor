/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.core.execution.tasks;

import static org.junit.Assert.assertFalse;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.service.MetadataService;
import java.time.Duration;
import java.util.Collections;
import org.junit.Test;
import org.mockito.Mockito;

public class TestIsolatedTaskQueueProducer {

    @Test
    public void addTaskQueuesAddsElementToQueue() {

        SystemTaskWorkerCoordinator.taskNameWorkflowTaskMapping.put("HTTP", Mockito.mock(WorkflowSystemTask.class));
        MetadataService metadataService = Mockito.mock(MetadataService.class);
        IsolatedTaskQueueProducer isolatedTaskQueueProducer = new IsolatedTaskQueueProducer(metadataService, false,
            Duration.ofSeconds(10));
        TaskDef taskDef = new TaskDef();
        taskDef.setIsolationGroupId("isolated");
        Mockito.when(metadataService.getTaskDefs()).thenReturn(Collections.singletonList(taskDef));
        isolatedTaskQueueProducer.addTaskQueues();

        assertFalse(SystemTaskWorkerCoordinator.queue.isEmpty());
    }
}
