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
package org.conductoross.conductor.core.storage;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WorkflowFamilyResolverTest {

    private ExecutionDAO executionDAO;
    private WorkflowFamilyResolverImpl resolver;

    @Before
    public void setUp() {
        executionDAO = mock(ExecutionDAO.class);
        resolver = new WorkflowFamilyResolverImpl(executionDAO);
    }

    @Test
    public void testNullWorkflowIdReturnsEmptySet() {
        Set<String> family = resolver.getFamily(null);
        assertTrue(family.isEmpty());
    }

    @Test
    public void testWorkflowNotFoundReturnsSelfOnly() {
        when(executionDAO.getWorkflow(eq("missing"), anyBoolean())).thenReturn(null);

        Set<String> family = resolver.getFamily("missing");
        assertEquals(Set.of("missing"), family);
    }

    @Test
    public void testSingleWorkflowNoParentNoChildren() {
        stubWorkflow("wf-a", null);

        Set<String> family = resolver.getFamily("wf-a");
        assertEquals(Set.of("wf-a"), family);
    }

    @Test
    public void testWorkflowWithParent() {
        stubWorkflow("wf-child", "wf-parent");
        stubWorkflow("wf-parent", null);

        Set<String> family = resolver.getFamily("wf-child");
        assertEquals(Set.of("wf-child", "wf-parent"), family);
    }

    @Test
    public void testWorkflowWithGrandparent() {
        stubWorkflow("wf-gc", "wf-child");
        stubWorkflow("wf-child", "wf-parent");
        stubWorkflow("wf-parent", null);

        Set<String> family = resolver.getFamily("wf-gc");
        assertEquals(Set.of("wf-gc", "wf-child", "wf-parent"), family);
    }

    @Test
    public void testWorkflowWithTwoChildren() {
        stubWorkflowWithSubWorkflowTasks("wf-parent", null, "wf-c1", "wf-c2");
        stubWorkflow("wf-c1", "wf-parent");
        stubWorkflow("wf-c2", "wf-parent");

        Set<String> family = resolver.getFamily("wf-parent");
        assertEquals(Set.of("wf-parent", "wf-c1", "wf-c2"), family);
    }

    @Test
    public void testWorkflowWithGrandchildren() {
        stubWorkflowWithSubWorkflowTasks("wf-parent", null, "wf-child");
        stubWorkflowWithSubWorkflowTasks("wf-child", "wf-parent", "wf-gc");
        stubWorkflow("wf-gc", "wf-child");

        Set<String> family = resolver.getFamily("wf-parent");
        assertEquals(Set.of("wf-parent", "wf-child", "wf-gc"), family);
    }

    private void stubWorkflow(String id, String parentId) {
        stubWorkflowWithSubWorkflowTasks(id, parentId);
    }

    private void stubWorkflowWithSubWorkflowTasks(String id, String parentId, String... childIds) {
        WorkflowModel wf = new WorkflowModel();
        wf.setWorkflowId(id);
        wf.setParentWorkflowId(parentId);
        for (String childId : childIds) {
            TaskModel task = new TaskModel();
            task.setSubWorkflowId(childId);
            wf.getTasks().add(task);
        }
        when(executionDAO.getWorkflow(eq(id), anyBoolean())).thenReturn(wf);
    }
}
