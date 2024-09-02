/*
 * Copyright 2022 Orkes, Inc.
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
package io.orkes.conductor.client.http;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.orkes.conductor.client.SchedulerClient;
import io.orkes.conductor.client.model.SaveScheduleRequest;
import io.orkes.conductor.client.model.TagObject;
import io.orkes.conductor.client.model.WorkflowSchedule;
import io.orkes.conductor.client.util.ClientTestUtil;
import io.orkes.conductor.client.util.Commons;

public class SchedulerClientTests  {
    private final String NAME = "test_sdk_java_scheduler_name";
    private final String CRON_EXPRESSION = "0 * * * * *";

    private final SchedulerClient schedulerClient = ClientTestUtil.getOrkesClients().getSchedulerClient();

    @Test
    void testMethods() {
        schedulerClient.deleteSchedule(NAME);
        Assertions.assertTrue(schedulerClient.getNextFewSchedules(CRON_EXPRESSION, 0L, 0L, 0).isEmpty());
        schedulerClient.saveSchedule(getSaveScheduleRequest());
        Assertions.assertTrue(schedulerClient.getAllSchedules(Commons.WORKFLOW_NAME).size() > 0);
        WorkflowSchedule workflowSchedule = schedulerClient.getSchedule(NAME);
        Assertions.assertEquals(NAME, workflowSchedule.getName());
        Assertions.assertEquals(CRON_EXPRESSION, workflowSchedule.getCronExpression());
        Assertions.assertFalse(schedulerClient.search(0, 10, "ASC", "*", "").getResults().isEmpty());
        schedulerClient.setSchedulerTags(getTagObject(), NAME);
        Assertions.assertEquals(getTagObject(), schedulerClient.getSchedulerTags(NAME));
        schedulerClient.deleteSchedulerTags(getTagObject(), NAME);
        Assertions.assertEquals(0, schedulerClient.getSchedulerTags(NAME).size());
        schedulerClient.pauseSchedule(NAME);
        workflowSchedule = schedulerClient.getSchedule(NAME);
        Assertions.assertTrue(workflowSchedule.isPaused());
        schedulerClient.resumeSchedule(NAME);
        workflowSchedule = schedulerClient.getSchedule(NAME);
        Assertions.assertFalse(workflowSchedule.isPaused());
        schedulerClient.deleteSchedule(NAME);
    }

    @Test
    void testDebugMethods() {
        schedulerClient.pauseAllSchedules();
        schedulerClient.resumeAllSchedules();
        schedulerClient.requeueAllExecutionRecords();
    }

    SaveScheduleRequest getSaveScheduleRequest() {
        return new SaveScheduleRequest()
                .name(NAME)
                .cronExpression(CRON_EXPRESSION)
                .startWorkflowRequest(Commons.getStartWorkflowRequest());
    }

    private List<TagObject> getTagObject() {
        TagObject tagObject = new TagObject();
        tagObject.setKey("department");
        tagObject.setValue("accounts");
        return List.of(tagObject);
    }
}
