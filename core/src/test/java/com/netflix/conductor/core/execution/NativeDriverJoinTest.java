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
package com.netflix.conductor.core.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.NativeState;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What the native driver does after a decide for a JOIN that waits for lanes. */
public class NativeDriverJoinTest {

    private static final int PRIORITY = 7;

    private QueueDAO queueDAO;
    private NativeDriver driver;
    private WorkflowModel workflow;
    private TaskModel laneA;
    private TaskModel laneB;
    private TaskModel join;
    private String joinQueue;

    @Before
    public void setUp() {
        queueDAO = mock(QueueDAO.class);
        NativeContinuationService service = mock(NativeContinuationService.class);
        when(service.repairAndWake(any())).thenReturn(new NativeContinuationService.Wakeups());
        MetadataDAO metadataDAO = mock(MetadataDAO.class);
        ExecutionDAOFacade executionDAOFacade = mock(ExecutionDAOFacade.class);
        driver =
                new NativeDriver(
                        new StandardDriver(
                                mock(ParametersUtils.class),
                                mock(ExternalPayloadStorageUtils.class),
                                executionDAOFacade,
                                queueDAO),
                        service,
                        new NativeRerunPlanner(),
                        metadataDAO,
                        new MetadataMapperService(metadataDAO),
                        executionDAOFacade,
                        queueDAO,
                        mock(SystemTaskRegistry.class));

        // a fork with the lanes a and b, then a JOIN with no targets
        WorkflowTask fork = entry("fork", TaskType.TASK_TYPE_FORK_JOIN);
        fork.setForkTasks(
                List.of(
                        new ArrayList<>(List.of(entry("a", TaskType.TASK_TYPE_SIMPLE))),
                        new ArrayList<>(List.of(entry("b", TaskType.TASK_TYPE_SIMPLE)))));
        WorkflowTask joinEntry = entry("join", TaskType.TASK_TYPE_JOIN);
        WorkflowDef def = new WorkflowDef();
        def.setName("native");
        def.getMetadata().put("native", Map.of("sdk", "test"));
        def.setTasks(new ArrayList<>(List.of(fork, joinEntry)));
        workflow = new WorkflowModel();
        workflow.setWorkflowId("wf");
        workflow.setWorkflowDefinition(def);
        workflow.setNativeState(new NativeState());

        laneA = task(fork.getForkTasks().get(0).get(0), TaskModel.Status.COMPLETED);
        laneB = task(fork.getForkTasks().get(1).get(0), TaskModel.Status.IN_PROGRESS);
        join = task(joinEntry, TaskModel.Status.IN_PROGRESS);
        join.setInputData(new HashMap<>(Map.of("joinOn", List.of())));
        workflow.getTasks().addAll(List.of(laneA, laneB, join));
        joinQueue = QueueUtils.getQueueName(join);
    }

    private static WorkflowTask entry(String ref, String type) {
        WorkflowTask entry = new WorkflowTask();
        entry.setName(ref);
        entry.setTaskReferenceName(ref);
        entry.setType(type);
        entry.getMetadata().put("lane", "main");
        return entry;
    }

    private static TaskModel task(WorkflowTask entry, TaskModel.Status status) {
        TaskModel task = new TaskModel();
        task.setTaskId("id-" + entry.getTaskReferenceName());
        task.setReferenceTaskName(entry.getTaskReferenceName());
        task.setTaskType(entry.getType());
        task.setWorkflowTask(entry);
        task.setWorkflowPriority(PRIORITY);
        task.setStatus(status);
        return task;
    }

    @Test
    public void aJoinIsNotWokenWhileALaneRuns() {
        driver.continueCode(workflow);

        verify(queueDAO, never()).postpone(anyString(), anyString(), anyInt(), anyLong());
        verify(queueDAO, never()).push(anyString(), anyString(), anyInt(), anyLong());
    }

    @Test
    public void aJoinIsCheckedWithNoDelayWhenItsLastLaneEnded() {
        laneB.setStatus(TaskModel.Status.COMPLETED);
        when(queueDAO.containsMessage(joinQueue, join.getTaskId())).thenReturn(true);

        driver.continueCode(workflow);

        verify(queueDAO).postpone(joinQueue, join.getTaskId(), PRIORITY, 0);
        verify(queueDAO, never()).push(anyString(), anyString(), anyInt(), anyLong());
    }

    @Test
    public void aJoinWhoseMessageIsLostIsQueuedAgainWhenItsLastLaneEnded() {
        laneB.setStatus(TaskModel.Status.COMPLETED);
        when(queueDAO.containsMessage(joinQueue, join.getTaskId())).thenReturn(false);

        driver.continueCode(workflow);

        verify(queueDAO).push(joinQueue, join.getTaskId(), PRIORITY, 0);
    }

    @Test
    public void aJoinThatFinishedIsNotWoken() {
        laneB.setStatus(TaskModel.Status.COMPLETED);
        join.setStatus(TaskModel.Status.COMPLETED);

        driver.continueCode(workflow);

        verify(queueDAO, never()).postpone(anyString(), anyString(), anyInt(), anyLong());
        verify(queueDAO, never()).push(anyString(), anyString(), anyInt(), anyLong());
    }
}
