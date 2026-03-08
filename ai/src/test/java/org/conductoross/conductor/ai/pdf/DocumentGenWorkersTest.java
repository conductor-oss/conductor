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
package org.conductoross.conductor.ai.pdf;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.document.DocumentLoader;
import org.conductoross.conductor.ai.models.LLMResponse;
import org.conductoross.conductor.ai.models.MarkdownToPdfRequest;
import org.conductoross.conductor.ai.tasks.worker.DocumentGenWorkers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.core.env.Environment;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentGenWorkersTest {

    private DocumentGenWorkers workers;
    private MarkdownToPdfConverter mockConverter;
    private DocumentLoader mockLoader;

    @BeforeEach
    void setUp() {
        mockConverter = mock(MarkdownToPdfConverter.class);
        mockLoader = mock(DocumentLoader.class);
        when(mockLoader.supports(argThat(s -> s != null && !s.startsWith("s3://"))))
                .thenReturn(true);
        when(mockLoader.upload(anyMap(), eq("application/pdf"), any(byte[].class), anyString()))
                .thenAnswer(
                        invocation -> {
                            String uri = invocation.getArgument(3);
                            return uri;
                        });

        Environment env = mock(Environment.class);
        when(env.getProperty(eq("conductor.file-storage.parentDir"), anyString()))
                .thenReturn("/tmp/test-payload/");

        workers = new DocumentGenWorkers(mockConverter, List.of(mockLoader), env);
    }

    private void withMockedTaskContext(Runnable action) {
        Task task = mock(Task.class);
        when(task.getWorkflowInstanceId()).thenReturn("wf-123");
        when(task.getTaskId()).thenReturn("task-456");

        TaskContext taskContext = mock(TaskContext.class);
        when(taskContext.getTask()).thenReturn(task);

        try (MockedStatic<TaskContext> mockedStatic = mockStatic(TaskContext.class)) {
            mockedStatic.when(TaskContext::get).thenReturn(taskContext);
            action.run();
        }
    }

    @Test
    void testGeneratePdfWithValidInput() {
        byte[] pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46}; // %PDF
        when(mockConverter.convert(any(MarkdownToPdfRequest.class))).thenReturn(pdfBytes);

        withMockedTaskContext(
                () -> {
                    MarkdownToPdfRequest request =
                            MarkdownToPdfRequest.builder().markdown("# Test\n\nHello").build();

                    LLMResponse response = workers.generatePdf(request);

                    assertNotNull(response);
                    assertEquals("COMPLETED", response.getFinishReason());
                    assertNotNull(response.getMedia());
                    assertEquals(1, response.getMedia().size());
                    assertEquals("application/pdf", response.getMedia().get(0).getMimeType());
                    assertNotNull(response.getMedia().get(0).getLocation());

                    // Verify result map
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = (Map<String, Object>) response.getResult();
                    assertNotNull(result.get("location"));
                    assertEquals(4, result.get("sizeBytes"));
                });
    }

    @Test
    void testGeneratePdfWithBlankMarkdownThrowsException() {
        assertThrows(
                NonRetryableException.class,
                () -> {
                    withMockedTaskContext(
                            () -> {
                                MarkdownToPdfRequest request =
                                        MarkdownToPdfRequest.builder().markdown("").build();
                                workers.generatePdf(request);
                            });
                });
    }

    @Test
    void testGeneratePdfWithNullMarkdownThrowsException() {
        assertThrows(
                NonRetryableException.class,
                () -> {
                    withMockedTaskContext(
                            () -> {
                                MarkdownToPdfRequest request =
                                        MarkdownToPdfRequest.builder().markdown(null).build();
                                workers.generatePdf(request);
                            });
                });
    }

    @Test
    void testGeneratePdfWithCustomOutputLocation() {
        byte[] pdfBytes = new byte[] {1, 2, 3};
        when(mockConverter.convert(any(MarkdownToPdfRequest.class))).thenReturn(pdfBytes);

        withMockedTaskContext(
                () -> {
                    MarkdownToPdfRequest request =
                            MarkdownToPdfRequest.builder()
                                    .markdown("# Custom Location")
                                    .outputLocation("file:///custom/path/output.pdf")
                                    .build();

                    LLMResponse response = workers.generatePdf(request);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = (Map<String, Object>) response.getResult();
                    assertEquals("file:///custom/path/output.pdf", result.get("location"));

                    verify(mockLoader)
                            .upload(
                                    anyMap(),
                                    eq("application/pdf"),
                                    eq(pdfBytes),
                                    eq("file:///custom/path/output.pdf"));
                });
    }

    @Test
    void testGeneratePdfWithDefaultOutputLocation() {
        byte[] pdfBytes = new byte[] {1, 2, 3};
        when(mockConverter.convert(any(MarkdownToPdfRequest.class))).thenReturn(pdfBytes);

        withMockedTaskContext(
                () -> {
                    MarkdownToPdfRequest request =
                            MarkdownToPdfRequest.builder().markdown("# Default Location").build();

                    LLMResponse response = workers.generatePdf(request);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = (Map<String, Object>) response.getResult();
                    String location = (String) result.get("location");
                    assertTrue(location.startsWith("/tmp/test-payload/"));
                    assertTrue(location.contains("wf-123"));
                    assertTrue(location.contains("task-456"));
                    assertTrue(location.endsWith(".pdf"));
                });
    }

    @Test
    void testGeneratePdfWithUnsupportedOutputLocation() {
        byte[] pdfBytes = new byte[] {1, 2, 3};
        when(mockConverter.convert(any(MarkdownToPdfRequest.class))).thenReturn(pdfBytes);

        assertThrows(
                NonRetryableException.class,
                () -> {
                    withMockedTaskContext(
                            () -> {
                                MarkdownToPdfRequest request =
                                        MarkdownToPdfRequest.builder()
                                                .markdown("# Unsupported")
                                                .outputLocation("s3://bucket/key.pdf")
                                                .build();
                                workers.generatePdf(request);
                            });
                });
    }

    @Test
    void testConverterIsCalledWithRequest() {
        byte[] pdfBytes = new byte[] {1};
        when(mockConverter.convert(any(MarkdownToPdfRequest.class))).thenReturn(pdfBytes);

        withMockedTaskContext(
                () -> {
                    MarkdownToPdfRequest request =
                            MarkdownToPdfRequest.builder()
                                    .markdown("# Verify Call")
                                    .pageSize("LETTER")
                                    .theme("compact")
                                    .build();

                    workers.generatePdf(request);

                    verify(mockConverter).convert(request);
                });
    }
}
