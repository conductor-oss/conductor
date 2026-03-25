/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.tasks.webhook;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WebhooksConfigResource}. Verifies that the resource delegates correctly to
 * {@link WebhookConfigService} and that no orgId is passed through any method signature (OSS is
 * single-tenant; orgId isolation is the DAO implementation's concern).
 */
public class WebhooksConfigResourceTest {

    private WebhookConfigService service;
    private WebhooksConfigResource resource;

    @Before
    public void setUp() {
        service = mock(WebhookConfigService.class);
        resource = new WebhooksConfigResource(service);
    }

    // -------------------------------------------------------------------------
    // POST / — createWebhook
    // -------------------------------------------------------------------------

    @Test
    public void testCreateWebhook_delegatesToService() {
        WebhookConfig config = headerBasedConfig("my-hook");

        WebhookConfig result = resource.createWebhook(config);

        verify(service).createWebhook(config);
        assertSame(config, result);
    }

    @Test
    public void testCreateWebhook_conflictPropagates() {
        WebhookConfig config = headerBasedConfig("dup");
        doThrow(new ConflictException("already exists")).when(service).createWebhook(any());

        try {
            resource.createWebhook(config);
            fail("Expected ConflictException");
        } catch (ConflictException e) {
            assertTrue(e.getMessage().contains("already exists"));
        }
    }

    @Test
    public void testCreateWebhook_validation_noTargetThrows() {
        WebhookConfig config = new WebhookConfig();
        config.setVerifier(WebhookConfig.Verifier.HEADER_BASED);
        config.setHeaders(Map.of("X-Secret", "abc"));
        // no expression, no receiverWorkflows, no workflowsToStart

        try {
            resource.createWebhook(config);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("expression"));
            verify(service, never()).createWebhook(any());
        }
    }

    @Test
    public void testCreateWebhook_validation_headerBasedWithNoHeadersThrows() {
        WebhookConfig config = new WebhookConfig();
        config.setVerifier(WebhookConfig.Verifier.HEADER_BASED);
        config.setReceiverWorkflowNamesToVersions(Map.of("wf", 1));
        // no headers

        try {
            resource.createWebhook(config);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("header"));
            verify(service, never()).createWebhook(any());
        }
    }

    @Test
    public void testCreateWebhook_validation_nullVerifier_noHeaderCheck() {
        // If verifier is null the HEADER_BASED check is skipped (null != HEADER_BASED)
        WebhookConfig config = new WebhookConfig();
        config.setReceiverWorkflowNamesToVersions(Map.of("wf", 1));
        // verifier is null, headers are null — validation should pass

        resource.createWebhook(config);

        verify(service).createWebhook(config);
    }

    @Test
    public void testCreateWebhook_validation_expressionAloneIsValid() {
        WebhookConfig config = new WebhookConfig();
        config.setExpression("payload.type == 'payment'");
        config.setEvaluatorType("javascript");
        // no verifier, no receiver workflows — expression alone is sufficient

        resource.createWebhook(config);

        verify(service).createWebhook(config);
    }

    @Test
    public void testCreateWebhook_validation_workflowsToStartAloneIsValid() {
        WebhookConfig config = new WebhookConfig();
        config.setWorkflowsToStart(Map.of("myWorkflow", Map.of("version", 1)));

        resource.createWebhook(config);

        verify(service).createWebhook(config);
    }

    // -------------------------------------------------------------------------
    // PUT /{id} — updateWebhook
    // -------------------------------------------------------------------------

    @Test
    public void testUpdateWebhook_setsIdFromPath_delegatesToService() {
        WebhookConfig config = headerBasedConfig("updated");

        WebhookConfig result = resource.updateWebhook("path-id-123", config);

        assertEquals("path-id-123", config.getId());
        verify(service).updateWebhook(config);
        assertSame(config, result);
    }

    @Test
    public void testUpdateWebhook_notFoundPropagates() {
        WebhookConfig config = headerBasedConfig("ghost");
        doThrow(new NotFoundException("not found")).when(service).updateWebhook(any());

        try {
            resource.updateWebhook("ghost-id", config);
            fail("Expected NotFoundException");
        } catch (NotFoundException e) {
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    @Test
    public void testUpdateWebhook_validation_failsBeforeCallingService() {
        WebhookConfig config = new WebhookConfig();
        config.setVerifier(WebhookConfig.Verifier.HEADER_BASED);
        config.setReceiverWorkflowNamesToVersions(Map.of("wf", 1));
        // no headers — should fail validation

        try {
            resource.updateWebhook("some-id", config);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            verify(service, never()).updateWebhook(any());
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /{id} — deleteWebhook
    // -------------------------------------------------------------------------

    @Test
    public void testDeleteWebhook_delegatesToService() {
        doNothing().when(service).deleteWebhook("id-to-delete");

        resource.deleteWebhook("id-to-delete");

        verify(service).deleteWebhook("id-to-delete");
    }

    @Test
    public void testDeleteWebhook_notFoundPropagates() {
        doThrow(new NotFoundException("not found")).when(service).deleteWebhook("ghost");

        try {
            resource.deleteWebhook("ghost");
            fail("Expected NotFoundException");
        } catch (NotFoundException e) {
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    // -------------------------------------------------------------------------
    // GET /{id} — getWebhook
    // -------------------------------------------------------------------------

    @Test
    public void testGetWebhook_returnsConfig() {
        WebhookConfig config = headerBasedConfig("found");
        config.setId("abc-123");
        when(service.getWebhook("abc-123")).thenReturn(config);

        WebhookConfig result = resource.getWebhook("abc-123");

        assertNotNull(result);
        assertEquals("found", result.getName());
        verify(service).getWebhook("abc-123");
    }

    @Test
    public void testGetWebhook_notFound_throwsNotFoundException() {
        when(service.getWebhook("ghost")).thenReturn(null);

        try {
            resource.getWebhook("ghost");
            fail("Expected NotFoundException");
        } catch (NotFoundException e) {
            assertTrue(e.getMessage().contains("ghost"));
        }
    }

    // -------------------------------------------------------------------------
    // GET / — getAllWebhooks
    // -------------------------------------------------------------------------

    @Test
    public void testGetAllWebhooks_delegatesToService() {
        List<WebhookConfig> configs = List.of(headerBasedConfig("a"), headerBasedConfig("b"));
        when(service.getAllWebhooks()).thenReturn(configs);

        List<WebhookConfig> result = resource.getAllWebhooks();

        assertEquals(2, result.size());
        verify(service).getAllWebhooks();
    }

    @Test
    public void testGetAllWebhooks_emptyList() {
        when(service.getAllWebhooks()).thenReturn(List.of());

        List<WebhookConfig> result = resource.getAllWebhooks();

        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // orgId isolation: resource methods must never pass orgId
    // -------------------------------------------------------------------------

    @Test
    public void testOrgIdNeverPassedThroughAnyMethod() {
        // All five resource methods delegate to service methods that have NO orgId parameter.
        // This test documents that the OSS resource is single-tenant clean — orgId isolation
        // is handled inside the DAO implementation, not in the service/resource layer.
        WebhookConfig config = headerBasedConfig("org-check");
        when(service.getWebhook(anyString())).thenReturn(config);
        when(service.getAllWebhooks()).thenReturn(List.of(config));

        resource.createWebhook(config);
        resource.updateWebhook("id-1", config);
        resource.deleteWebhook("id-1");
        resource.getWebhook("id-1");
        resource.getAllWebhooks();

        // Verify that only the single-tenant service methods (no orgId overloads) were called
        verify(service).createWebhook(config);
        verify(service).updateWebhook(config);
        verify(service).deleteWebhook("id-1");
        verify(service).getWebhook("id-1");
        verify(service).getAllWebhooks();
        verifyNoMoreInteractions(service);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private WebhookConfig headerBasedConfig(String name) {
        WebhookConfig config = new WebhookConfig();
        config.setName(name);
        config.setVerifier(WebhookConfig.Verifier.HEADER_BASED);
        config.setHeaders(Map.of("X-Webhook-Secret", "my-secret"));
        config.setReceiverWorkflowNamesToVersions(Map.of("order_workflow", 1));
        return config;
    }
}
