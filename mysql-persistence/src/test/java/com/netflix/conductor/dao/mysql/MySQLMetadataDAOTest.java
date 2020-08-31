package com.netflix.conductor.dao.mysql;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.execution.ApplicationException;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("Duplicates")
@RunWith(JUnit4.class)
public class MySQLMetadataDAOTest {

    private MySQLDAOTestUtil testUtil;
    private MySQLMetadataDAO dao;

    @Rule
    public TestName name = new TestName();

    @Before
    public void setup() throws Exception {
        testUtil = new MySQLDAOTestUtil(name.getMethodName());
        dao = new MySQLMetadataDAO(testUtil.getObjectMapper(), testUtil.getDataSource(), testUtil.getTestConfiguration());
    }

    @After
    public void teardown() throws Exception {
        testUtil.resetAllData();
        testUtil.getDataSource().close();
    }

    @Test(expected=ApplicationException.class)
    public void testDuplicate() throws Exception {
        WorkflowDef def = new WorkflowDef();
        def.setName("testDuplicate");
        def.setVersion(1);

        dao.createWorkflowDef(def);
        dao.createWorkflowDef(def);
    }

    @Test
    public void testWorkflowDefOperations() throws Exception {
        WorkflowDef def = new WorkflowDef();
        def.setName("test");
        def.setVersion(1);
        def.setDescription("description");
        def.setCreatedBy("unit_test");
        def.setCreateTime(1L);
        def.setOwnerApp("ownerApp");
        def.setUpdatedBy("unit_test2");
        def.setUpdateTime(2L);

        dao.createWorkflowDef(def);

        List<WorkflowDef> all = dao.getAllWorkflowDefs();
        assertNotNull(all);
        assertEquals(1, all.size());
        assertEquals("test", all.get(0).getName());
        assertEquals(1, all.get(0).getVersion());

        WorkflowDef found = dao.getWorkflowDef("test", 1).get();
        assertTrue(EqualsBuilder.reflectionEquals(def, found));

        def.setVersion(3);
        dao.createWorkflowDef(def);

        all = dao.getAllWorkflowDefs();
        assertNotNull(all);
        assertEquals(2, all.size());
        assertEquals("test", all.get(0).getName());
        assertEquals(1, all.get(0).getVersion());

        found = dao.getLatestWorkflowDef(def.getName()).get();
        assertEquals(def.getName(), found.getName());
        assertEquals(def.getVersion(), found.getVersion());
        assertEquals(3, found.getVersion());

        all = dao.getAllLatest();
        assertNotNull(all);
        assertEquals(1, all.size());
        assertEquals("test", all.get(0).getName());
        assertEquals(3, all.get(0).getVersion());

        all = dao.getAllVersions(def.getName());
        assertNotNull(all);
        assertEquals(2, all.size());
        assertEquals("test", all.get(0).getName());
        assertEquals("test", all.get(1).getName());
        assertEquals(1, all.get(0).getVersion());
        assertEquals(3, all.get(1).getVersion());

        def.setDescription("updated");
        dao.updateWorkflowDef(def);
        found = dao.getWorkflowDef(def.getName(), def.getVersion()).get();
        assertEquals(def.getDescription(), found.getDescription());

        List<String> allnames = dao.findAll();
        assertNotNull(allnames);
        assertEquals(1, allnames.size());
        assertEquals(def.getName(), allnames.get(0));

        def.setVersion(2);
        dao.createWorkflowDef(def);

        found = dao.getLatestWorkflowDef(def.getName()).get();
        assertEquals(def.getName(), found.getName());
        assertEquals(3, found.getVersion());

        dao.removeWorkflowDef("test", 3);
        Optional<WorkflowDef> deleted = dao.getWorkflowDef("test", 3);
        assertFalse(deleted.isPresent());

        found = dao.getLatestWorkflowDef(def.getName()).get();
        assertEquals(def.getName(), found.getName());
        assertEquals(2, found.getVersion());

        dao.removeWorkflowDef("test", 1);
        deleted = dao.getWorkflowDef("test", 1);
        assertFalse(deleted.isPresent());

        found = dao.getLatestWorkflowDef(def.getName()).get();
        assertEquals(def.getName(), found.getName());
        assertEquals(2, found.getVersion());
    }

    @Test
    public void testTaskDefOperations() throws Exception {
        TaskDef def = new TaskDef("taskA");
        def.setDescription("description");
        def.setCreatedBy("unit_test");
        def.setCreateTime(1L);
        def.setInputKeys(Arrays.asList("a","b","c"));
        def.setOutputKeys(Arrays.asList("01","o2"));
        def.setOwnerApp("ownerApp");
        def.setRetryCount(3);
        def.setRetryDelaySeconds(100);
        def.setRetryLogic(TaskDef.RetryLogic.FIXED);
        def.setTimeoutPolicy(TaskDef.TimeoutPolicy.ALERT_ONLY);
        def.setUpdatedBy("unit_test2");
        def.setUpdateTime(2L);

        dao.createTaskDef(def);

        TaskDef found = dao.getTaskDef(def.getName());
        assertTrue(EqualsBuilder.reflectionEquals(def, found));

        def.setDescription("updated description");
        dao.updateTaskDef(def);
        found = dao.getTaskDef(def.getName());
        assertTrue(EqualsBuilder.reflectionEquals(def, found));
        assertEquals("updated description", found.getDescription());

        for(int i = 0; i < 9; i++) {
            TaskDef tdf = new TaskDef("taskA" + i);
            dao.createTaskDef(tdf);
        }

        List<TaskDef> all = dao.getAllTaskDefs();
        assertNotNull(all);
        assertEquals(10, all.size());
        Set<String> allnames = all.stream().map(TaskDef::getName).collect(Collectors.toSet());
        assertEquals(10, allnames.size());
        List<String> sorted = allnames.stream().sorted().collect(Collectors.toList());
        assertEquals(def.getName(), sorted.get(0));

        for(int i = 0; i < 9; i++) {
            assertEquals(def.getName() + i, sorted.get(i+1));
        }

        for(int i = 0; i < 9; i++) {
            dao.removeTaskDef(def.getName() + i);
        }
        all = dao.getAllTaskDefs();
        assertNotNull(all);
        assertEquals(1, all.size());
        assertEquals(def.getName(), all.get(0).getName());
    }

    @Test(expected=ApplicationException.class)
    public void testRemoveTaskDef() throws Exception {
        dao.removeTaskDef("test" + UUID.randomUUID().toString());
    }

    @Test
    public void testEventHandlers() {
        String event1 = "SQS::arn:account090:sqstest1";
        String event2 = "SQS::arn:account090:sqstest2";

        EventHandler eh = new EventHandler();
        eh.setName(UUID.randomUUID().toString());
        eh.setActive(false);
        EventHandler.Action action = new EventHandler.Action();
        action.setAction(EventHandler.Action.Type.start_workflow);
        action.setStart_workflow(new EventHandler.StartWorkflow());
        action.getStart_workflow().setName("workflow_x");
        eh.getActions().add(action);
        eh.setEvent(event1);

        dao.addEventHandler(eh);
        List<EventHandler> all = dao.getAllEventHandlers();
        assertNotNull(all);
        assertEquals(1, all.size());
        assertEquals(eh.getName(), all.get(0).getName());
        assertEquals(eh.getEvent(), all.get(0).getEvent());

        List<EventHandler> byEvents = dao.getEventHandlersForEvent(event1, true);
        assertNotNull(byEvents);
        assertEquals(0, byEvents.size());		//event is marked as in-active

        eh.setActive(true);
        eh.setEvent(event2);
        dao.updateEventHandler(eh);

        all = dao.getAllEventHandlers();
        assertNotNull(all);
        assertEquals(1, all.size());

        byEvents = dao.getEventHandlersForEvent(event1, true);
        assertNotNull(byEvents);
        assertEquals(0, byEvents.size());

        byEvents = dao.getEventHandlersForEvent(event2, true);
        assertNotNull(byEvents);
        assertEquals(1, byEvents.size());
    }
}
