package com.netflix.conductor.dao.dynomite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dyno.DynoProxy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class BaseDynoDAOTest {

    @Mock
    private DynoProxy dynoClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Configuration config;

    private BaseDynoDAO baseDynoDAO;

    @Before
    public void setUp() {
        this.baseDynoDAO = new BaseDynoDAO(dynoClient, objectMapper, config);
    }

    @Test
    public void testNsKey() {
        assertEquals("", baseDynoDAO.nsKey());

        String[] keys = {"key1", "key2"};
        assertEquals("key1.key2", baseDynoDAO.nsKey(keys));

        Mockito.when(config.getProperty("workflow.namespace.prefix", null)).thenReturn("test");
        assertEquals("test", baseDynoDAO.nsKey());

        assertEquals("test.key1.key2", baseDynoDAO.nsKey(keys));

        Mockito.when(config.getStack()).thenReturn("stack");
        assertEquals("test.stack.key1.key2", baseDynoDAO.nsKey(keys));
    }
}
