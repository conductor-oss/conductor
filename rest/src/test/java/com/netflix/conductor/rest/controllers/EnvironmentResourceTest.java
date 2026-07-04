package com.netflix.conductor.rest.controllers;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import com.netflix.conductor.common.metadata.EnvironmentVariable;
import com.netflix.conductor.dao.EnvironmentDAO;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class EnvironmentResourceTest {

    private EnvironmentDAO dao;
    private EnvironmentResource resource;

    @Before
    public void setUp() {
        dao = mock(EnvironmentDAO.class);
        resource = new EnvironmentResource(dao);
    }

    @Test
    public void testList() {
        when(dao.getAll()).thenReturn(List.of(EnvironmentVariable.of("REGION", "us-east-1")));
        List<EnvironmentVariable> all = resource.getAll();
        assertEquals(1, all.size());
        assertEquals("REGION", all.get(0).getName());
    }

    @Test
    public void testGet() {
        when(dao.getEnvVariable("REGION")).thenReturn("us-east-1");
        assertEquals("us-east-1", resource.get("REGION"));
    }
}
