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
package com.netflix.conductor.mysql.dao;

import static com.netflix.conductor.core.exception.ApplicationException.Code.CONFLICT;
import static com.netflix.conductor.core.exception.ApplicationException.Code.NOT_FOUND;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.config.ObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.mysql.util.MySQLDAOTestUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@ContextConfiguration(classes = {ObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class MySQLMetadataDAOTest {

    private MySQLDAOTestUtil testUtil;
    private MySQLMetadataDAO metadataDAO;

    @Autowired
    private ObjectMapper objectMapper;

    @Rule
    public TestName name = new TestName();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup() {
        testUtil = new MySQLDAOTestUtil(objectMapper, name.getMethodName());
        metadataDAO = new MySQLMetadataDAO(testUtil.getObjectMapper(), testUtil.getDataSource(),
            testUtil.getTestProperties());
    }

    @After
    public void teardown() {
        testUtil.resetAllData();
        testUtil.getDataSource().close();
    }

    @Test
    public void testDuplicateWorkflowDef() {
        thrown.expect(ApplicationException.class);
        thrown.expectMessage("Workflow with testDuplicate.1 already exists!");
        thrown.expect(hasProperty("code", is(CONFLICT)));

        WorkflowDef def = new WorkflowDef();
        def.setName("testDuplicate");
        def.setVersion(1);

        metadataDAO.createWorkflowDef(def);
        metadataDAO.createWorkflowDef(def);
    }

    @Test
    public void testRemoveNotExistingWorkflowDef() {
        thrown.expect(ApplicationException.class);
        thrown.expectMessage("No such workflow definition: test version: 1");
        thrown.expect(hasProperty("code", is(NOT_FOUND)));

        metadataDAO.removeWorkflowDef("test", 1);
    }

    @Test
    public void testWorkflowDefOperations() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test");
        def.setVersion(1);
        def.setDescription("description");
        def.setCreatedBy("unit_test");
        def.setCreateTime(1L);
        def.setOwnerApp("ownerApp");
        def.setUpdatedBy("unit_test2");
        def.setUpdateTime(2L);

        metadataDAO.createWorkflowDef(def);

        List<WorkflowDef> all = metadataDAO.getAllWorkflowDefs();
        assertNotNull(all);
        assertEquals(1, all.size());
        assertEquals("test", all.get(0).getName());
        assertEquals(1, all.get(0).getVersion());

        WorkflowDef found = metadataDAO.getWorkflowDef("test", 1).get();
        assertTrue(EqualsBuilder.reflectionEquals(def, found));

        def.setVersion(3);
        metadataDAO.createWorkflowDef(def);

        all = metadataDAO.getAllWorkflowDefs();
        assertNotNull(all);
        assertEquals(2, all.size());
        assertEquals("test", all.get(0).getName());
        assertEquals(1, all.get(0).getVersion());

        found = metadataDAO.getLatestWorkflowDef(def.getName()).get();
        assertEquals(def.getName(), found.getName());
        assertEquals(def.getVersion(), found.getVersion());
        assertEquals(3, found.getVersion());

        all = metadataDAO.getAllLatest();
        assertNotNull(all);
        assertEquals(1, all.size());
        assertEquals("test", all.get(0).getName());
        assertEquals(3, all.get(0).getVersion());

        all = metadataDAO.getAllVersions(def.getName());
        assertNotNull(all);
        assertEquals(2, all.size());
        assertEquals("test", all.get(0).getName());
        assertEquals("test", all.get(1).getName());
        assertEquals(1, all.get(0).getVersion());
        assertEquals(3, all.get(1).getVersion());

        def.setDescription("updated");
        metadataDAO.updateWorkflowDef(def);
        found = metadataDAO.getWorkflowDef(def.getName(), def.getVersion()).get();
        assertEquals(def.getDescription(), found.getDescription());

        List<String> allnames = metadataDAO.findAll();
        assertNotNull(allnames);
        assertEquals(1, allnames.size());
        assertEquals(def.getName(), allnames.get(0));

        def.setVersion(2);
        metadataDAO.createWorkflowDef(def);

        found = metadataDAO.getLatestWorkflowDef(def.getName()).get();
        assertEquals(def.getName(), found.getName());
        assertEquals(3, found.getVersion());

        metadataDAO.removeWorkflowDef("test", 3);
        Optional<WorkflowDef> deleted = metadataDAO.getWorkflowDef("test", 3);
        assertFalse(deleted.isPresent());

        found = metadataDAO.getLatestWorkflowDef(def.getName()).get();
        assertEquals(def.getName(), found.getName());
        assertEquals(2, found.getVersion());

        metadataDAO.removeWorkflowDef("test", 1);
        deleted = metadataDAO.getWorkflowDef("test", 1);
        assertFalse(deleted.isPresent());

        found = metadataDAO.getLatestWorkflowDef(def.getName()).get();
        assertEquals(def.getName(), found.getName());
        assertEquals(2, found.getVersion());
    }

    @Test
    public void testTaskDefOperations() throws Exception {
        TaskDef def = new TaskDef("taskA");
        def.setDescription("description");
        def.setCreatedBy("unit_test");
        def.setCreateTime(1L);
        def.setInputKeys(Arrays.asList("a", "b", "c"));
        def.setOutputKeys(Arrays.asList("01", "o2"));
        def.setOwnerApp("ownerApp");
        def.setRetryCount(3);
        def.setRetryDelaySeconds(100);
        def.setRetryLogic(TaskDef.RetryLogic.FIXED);
        def.setTimeoutPolicy(TaskDef.TimeoutPolicy.ALERT_ONLY);
        def.setUpdatedBy("unit_test2");
        def.setUpdateTime(2L);

        metadataDAO.createTaskDef(def);

        TaskDef found = metadataDAO.getTaskDef(def.getName());
        assertTrue(EqualsBuilder.reflectionEquals(def, found));

        def.setDescription("updated description");
        metadataDAO.updateTaskDef(def);
        found = metadataDAO.getTaskDef(def.getName());
        assertTrue(EqualsBuilder.reflectionEquals(def, found));
        assertEquals("updated description", found.getDescription());

        for (int i = 0; i < 9; i++) {
            TaskDef tdf = new TaskDef("taskA" + i);
            metadataDAO.createTaskDef(tdf);
        }

        List<TaskDef> all = metadataDAO.getAllTaskDefs();
        assertNotNull(all);
        assertEquals(10, all.size());
        Set<String> allnames = all.stream().map(TaskDef::getName).collect(Collectors.toSet());
        assertEquals(10, allnames.size());
        List<String> sorted = allnames.stream().sorted().collect(Collectors.toList());
        assertEquals(def.getName(), sorted.get(0));

        for (int i = 0; i < 9; i++) {
            assertEquals(def.getName() + i, sorted.get(i + 1));
        }

        for (int i = 0; i < 9; i++) {
            metadataDAO.removeTaskDef(def.getName() + i);
        }
        all = metadataDAO.getAllTaskDefs();
        assertNotNull(all);
        assertEquals(1, all.size());
        assertEquals(def.getName(), all.get(0).getName());
    }

    @Test
    public void testRemoveNotExistingTaskDef() {
        thrown.expect(ApplicationException.class);
        thrown.expectMessage("No such task definition");
        thrown.expect(hasProperty("code", is(NOT_FOUND)));

        metadataDAO.removeTaskDef("test" + UUID.randomUUID().toString());
    }

    @Test
    public void testEventHandlers() {
        String event1 = "SQS::arn:account090:sqstest1";
        String event2 = "SQS::arn:account090:sqstest2";

        EventHandler eventHandler = new EventHandler();
        eventHandler.setName(UUID.randomUUID().toString());
        eventHandler.setActive(false);
        EventHandler.Action action = new EventHandler.Action();
        action.setAction(EventHandler.Action.Type.start_workflow);
        action.setStart_workflow(new EventHandler.StartWorkflow());
        action.getStart_workflow().setName("workflow_x");
        eventHandler.getActions().add(action);
        eventHandler.setEvent(event1);

        metadataDAO.addEventHandler(eventHandler);
        List<EventHandler> all = metadataDAO.getAllEventHandlers();
        assertNotNull(all);
        assertEquals(1, all.size());
        assertEquals(eventHandler.getName(), all.get(0).getName());
        assertEquals(eventHandler.getEvent(), all.get(0).getEvent());

        List<EventHandler> byEvents = metadataDAO.getEventHandlersForEvent(event1, true);
        assertNotNull(byEvents);
        assertEquals(0, byEvents.size());        //event is marked as in-active

        eventHandler.setActive(true);
        eventHandler.setEvent(event2);
        metadataDAO.updateEventHandler(eventHandler);

        all = metadataDAO.getAllEventHandlers();
        assertNotNull(all);
        assertEquals(1, all.size());

        byEvents = metadataDAO.getEventHandlersForEvent(event1, true);
        assertNotNull(byEvents);
        assertEquals(0, byEvents.size());

        byEvents = metadataDAO.getEventHandlersForEvent(event2, true);
        assertNotNull(byEvents);
        assertEquals(1, byEvents.size());
    }
}
