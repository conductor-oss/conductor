package com.netflix.conductor.rest.controllers;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import com.netflix.conductor.dao.SecretsDAO;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class SecretResourceTest {

    private SecretsDAO dao;
    private SecretResource resource;

    @Before
    public void setUp() {
        dao = mock(SecretsDAO.class);
        resource = new SecretResource(dao);
    }

    @Test
    public void testListNamesOnly() {
        when(dao.listSecretNames()).thenReturn(List.of("DB_PASSWORD"));
        List<String> names = resource.listSecretNames();
        assertEquals(1, names.size());
        assertEquals("DB_PASSWORD", names.get(0));
    }
}
