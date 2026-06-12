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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.conductoross.conductor.ai.document.DocumentLoader;
import org.conductoross.conductor.ai.models.MarkdownToPdfRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for the MarkdownToPdfConverter. Tests 10+ different markdown document
 * structures and validates PDF output correctness, layout options, and edge cases.
 */
class MarkdownToPdfConverterTest {

    private MarkdownToPdfConverter converter;
    private PdfImageResolver imageResolver;

    @BeforeEach
    void setUp() {
        DocumentLoader httpLoader = mock(DocumentLoader.class);
        when(httpLoader.supports(argThat(s -> s != null && s.startsWith("http")))).thenReturn(true);

        imageResolver = new PdfImageResolver(List.of(httpLoader));
        converter = new MarkdownToPdfConverter(imageResolver);
    }

    /** Helper to convert markdown and return a valid PDDocument for assertions. */
    private PDDocument convertAndLoad(String markdown) throws IOException {
        return convertAndLoad(markdown, null);
    }

    private PDDocument convertAndLoad(String markdown, String pageSize) throws IOException {
        MarkdownToPdfRequest.MarkdownToPdfRequestBuilder builder =
                MarkdownToPdfRequest.builder().markdown(markdown);
        if (pageSize != null) {
            builder.pageSize(pageSize);
        }
        byte[] pdf = converter.convert(builder.build());
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        return Loader.loadPDF(pdf);
    }

    private String extractText(PDDocument doc) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        return stripper.getText(doc);
    }

    // ========================================================================
    // Document 1: Basic Headings & Paragraphs
    // ========================================================================

    @Nested
    @DisplayName("Document 1: Headings and Paragraphs")
    class HeadingsAndParagraphs {

        static final String MARKDOWN =
                """
                # Main Title

                This is the first paragraph with some introductory text.

                ## Section One

                Content under section one. This has multiple sentences. The paragraph should wrap properly across lines in the PDF.

                ### Subsection 1.1

                More detailed content here.

                #### Level 4 Heading

                Deep nested content.

                ##### Level 5 Heading

                Even deeper.

                ###### Level 6 Heading

                The deepest heading level.
                """;

        @Test
        void testProducesValidPdf() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                assertNotNull(doc);
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        }

        @Test
        void testContainsAllHeadingText() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                String text = extractText(doc);
                assertTrue(text.contains("Main Title"));
                assertTrue(text.contains("Section One"));
                assertTrue(text.contains("Subsection 1.1"));
                assertTrue(text.contains("Level 4 Heading"));
                assertTrue(text.contains("Level 5 Heading"));
                assertTrue(text.contains("Level 6 Heading"));
            }
        }

        @Test
        void testContainsParagraphText() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                String text = extractText(doc);
                assertTrue(text.contains("first paragraph"));
                assertTrue(text.contains("Content under section one"));
                assertTrue(text.contains("More detailed content"));
            }
        }
    }

    // ========================================================================
    // Document 2: Text Emphasis & Formatting
    // ========================================================================

    @Nested
    @DisplayName("Document 2: Text Emphasis and Inline Formatting")
    class EmphasisAndFormatting {

        static final String MARKDOWN =
                """
                # Formatting Test

                This paragraph has **bold text** and *italic text* and ***bold italic text***.

                Here is some ~~strikethrough text~~ mixed with regular text.

                Inline `code` appears within a sentence. Multiple `code spans` can appear.

                A paragraph with **bold at the start** and another with *italic at the end*.

                Mix of all: **bold**, *italic*, `code`, and ~~strikethrough~~ in one paragraph.
                """;

        @Test
        void testProducesValidPdf() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        }

        @Test
        void testContainsFormattedText() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                String text = extractText(doc);
                assertTrue(text.contains("bold text"));
                assertTrue(text.contains("italic text"));
                assertTrue(text.contains("strikethrough text"));
                assertTrue(text.contains("code"));
            }
        }
    }

    // ========================================================================
    // Document 3: Bullet Lists (Simple, Nested)
    // ========================================================================

    @Nested
    @DisplayName("Document 3: Bullet Lists")
    class BulletLists {

        static final String MARKDOWN =
                """
                # Bullet Lists

                Simple list:

                - First item
                - Second item
                - Third item

                Nested list:

                - Level 1 item A
                  - Level 2 item A1
                  - Level 2 item A2
                    - Level 3 item A2a
                - Level 1 item B
                  - Level 2 item B1

                Mixed content list:

                - Item with **bold** text
                - Item with `inline code`
                - Item with a [link](http://example.com)
                """;

        @Test
        void testProducesValidPdf() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        }

        @Test
        void testContainsListItems() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                String text = extractText(doc);
                assertTrue(text.contains("First item"));
                assertTrue(text.contains("Second item"));
                assertTrue(text.contains("Third item"));
                assertTrue(text.contains("Level 1 item A"));
                assertTrue(text.contains("Level 2 item A1"));
                assertTrue(text.contains("Level 3 item A2a"));
            }
        }
    }

    // ========================================================================
    // Document 4: Ordered Lists & Task Lists
    // ========================================================================

    @Nested
    @DisplayName("Document 4: Ordered Lists and Task Lists")
    class OrderedAndTaskLists {

        static final String MARKDOWN =
                """
                # Ordered Lists

                1. First step
                2. Second step
                3. Third step

                Nested ordered:

                1. Main step one
                   1. Sub-step 1a
                   2. Sub-step 1b
                2. Main step two

                ## Task List

                - [x] Completed task
                - [ ] Pending task
                - [x] Another done task
                - [ ] Still to do
                """;

        @Test
        void testProducesValidPdf() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        }

        @Test
        void testContainsOrderedItems() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                String text = extractText(doc);
                assertTrue(text.contains("First step"));
                assertTrue(text.contains("Second step"));
                assertTrue(text.contains("Main step one"));
                assertTrue(text.contains("Sub-step 1a"));
            }
        }

        @Test
        void testContainsTaskListItems() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                String text = extractText(doc);
                assertTrue(text.contains("Completed task"));
                assertTrue(text.contains("Pending task"));
            }
        }
    }

    // ========================================================================
    // Document 5: Code Blocks
    // ========================================================================

    @Nested
    @DisplayName("Document 5: Code Blocks")
    class CodeBlocks {

        static final String MARKDOWN =
                """
                # Code Examples

                A fenced code block with language:

                ```java
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("Hello, World!");
                    }
                }
                ```

                A fenced code block without language:

                ```
                plain code block
                with multiple lines
                ```

                An indented code block:

                    indented line 1
                    indented line 2
                    indented line 3

                Code with special characters:

                ```
                if (x < 10 && y > 5) {
                    result = x + y;
                }
                ```
                """;

        @Test
        void testProducesValidPdf() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        }

        @Test
        void testContainsCodeContent() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                String text = extractText(doc);
                assertTrue(text.contains("Hello"));
                assertTrue(text.contains("main"));
                assertTrue(text.contains("plain code block"));
            }
        }
    }

    // ========================================================================
    // Document 6: Tables
    // ========================================================================

    @Nested
    @DisplayName("Document 6: Tables")
    class Tables {

        static final String MARKDOWN =
                """
                # Table Examples

                Simple table:

                | Name | Age | City |
                |------|-----|------|
                | Alice | 30 | New York |
                | Bob | 25 | San Francisco |
                | Charlie | 35 | London |

                Table with alignment:

                | Left | Center | Right |
                |:-----|:------:|------:|
                | L1 | C1 | R1 |
                | L2 | C2 | R2 |

                Table with formatting:

                | Feature | Status | Notes |
                |---------|--------|-------|
                | **Bold** | `Active` | Works well |
                | *Italic* | `Pending` | In progress |
                """;

        @Test
        void testProducesValidPdf() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        }

        @Test
        void testContainsTableData() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                String text = extractText(doc);
                assertTrue(text.contains("Alice"));
                assertTrue(text.contains("30"));
                assertTrue(text.contains("New York"));
                assertTrue(text.contains("Bob"));
                assertTrue(text.contains("San Francisco"));
            }
        }
    }

    // ========================================================================
    // Document 7: Blockquotes
    // ========================================================================

    @Nested
    @DisplayName("Document 7: Blockquotes")
    class Blockquotes {

        static final String MARKDOWN =
                """
                # Blockquotes

                A simple blockquote:

                > This is a quoted paragraph. It should be indented with a vertical bar on the left.

                Nested blockquotes:

                > Outer quote
                > > Inner quote
                > > > Deeply nested quote

                Blockquote with formatting:

                > **Important:** This blockquote contains *formatted* text and `inline code`.
                >
                > It also spans multiple paragraphs within the same quote block.

                Regular text after the blockquote.
                """;

        @Test
        void testProducesValidPdf() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        }

        @Test
        void testContainsBlockquoteText() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                String text = extractText(doc);
                assertTrue(text.contains("quoted paragraph"));
                assertTrue(text.contains("Outer quote"));
                assertTrue(text.contains("Inner quote"));
                assertTrue(text.contains("Important"));
            }
        }
    }

    // ========================================================================
    // Document 8: Links & Horizontal Rules
    // ========================================================================

    @Nested
    @DisplayName("Document 8: Links and Horizontal Rules")
    class LinksAndRules {

        static final String MARKDOWN =
                """
                # Links and Rules

                A paragraph with an [inline link](https://example.com) in the middle.

                Another [link with title](https://example.com "Example Site") here.

                An auto-linked URL: https://www.conductor.community

                ---

                Content after the first horizontal rule.

                ***

                Content after the second horizontal rule.

                ___

                Final content.
                """;

        @Test
        void testProducesValidPdf() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        }

        @Test
        void testContainsLinkText() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                String text = extractText(doc);
                assertTrue(text.contains("inline link"));
                assertTrue(text.contains("link with title"));
                assertTrue(text.contains("Content after the first horizontal rule"));
                assertTrue(text.contains("Final content"));
            }
        }
    }

    // ========================================================================
    // Document 9: Images (with mocked resolver)
    // ========================================================================

    @Nested
    @DisplayName("Document 9: Images")
    class Images {

        static final String MARKDOWN =
                """
                # Document with Images

                Here is an image:

                ![Sample Image](http://example.com/sample.png)

                Text continues after the image.

                Another image below:

                ![Logo](http://example.com/logo.jpg)

                Final paragraph.
                """;

        @Test
        void testProducesValidPdfWithMissingImages() throws IOException {
            // Images will fail to load (mocked loader returns null for download)
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                assertTrue(doc.getNumberOfPages() >= 1);
                String text = extractText(doc);
                // Should show placeholder text for missing images
                assertTrue(text.contains("Sample Image") || text.contains("Image"));
                assertTrue(text.contains("Text continues after the image"));
            }
        }

        @Test
        void testImageWithDataUri() throws IOException {
            // Create a tiny 1x1 PNG
            // This is a minimal valid 1x1 red PNG (67 bytes)
            byte[] pngBytes = {
                (byte) 0x89,
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                0x00,
                0x00,
                0x00,
                0x0D,
                0x49,
                0x48,
                0x44,
                0x52,
                0x00,
                0x00,
                0x00,
                0x01,
                0x00,
                0x00,
                0x00,
                0x01,
                0x08,
                0x02,
                0x00,
                0x00,
                0x00,
                (byte) 0x90,
                0x77,
                0x53,
                (byte) 0xDE,
                0x00,
                0x00,
                0x00,
                0x0C,
                0x49,
                0x44,
                0x41,
                0x54,
                0x08,
                (byte) 0xD7,
                0x63,
                (byte) 0xF8,
                (byte) 0xCF,
                (byte) 0xC0,
                0x00,
                0x00,
                0x00,
                0x02,
                0x00,
                0x01,
                (byte) 0xE2,
                0x21,
                (byte) 0xBC,
                0x33,
                0x00,
                0x00,
                0x00,
                0x00,
                0x49,
                0x45,
                0x4E,
                0x44,
                (byte) 0xAE,
                0x42,
                0x60,
                (byte) 0x82
            };
            String base64 = java.util.Base64.getEncoder().encodeToString(pngBytes);
            String mdWithDataUri =
                    "# Image Test\n\n![Tiny](data:image/png;base64," + base64 + ")\n\nDone.";

            try (PDDocument doc = convertAndLoad(mdWithDataUri)) {
                assertTrue(doc.getNumberOfPages() >= 1);
                String text = extractText(doc);
                assertTrue(text.contains("Image Test"));
                assertTrue(text.contains("Done"));
            }
        }
    }

    // ========================================================================
    // Document 10: Complex Mixed Document
    // ========================================================================

    @Nested
    @DisplayName("Document 10: Complex Mixed Document")
    class ComplexMixed {

        static final String MARKDOWN =
                """
                # Project Status Report

                **Date:** January 15, 2026
                **Author:** Conductor Team

                ## Executive Summary

                This report covers the progress of the *Conductor* project over Q4 2025. Key highlights include:

                - Completed AI integration module
                - Launched vector database support
                - Released MCP tool calling

                ---

                ## Technical Progress

                ### Backend Services

                The backend team delivered several critical features:

                1. **Chat Completion API** - Full support for 11+ LLM providers
                2. **Media Generation** - Image, audio, and video generation
                3. **Vector Search** - MongoDB, PostgreSQL, and Pinecone

                > **Note:** All features are gated behind the `conductor.integrations.ai.enabled` flag.

                ### Code Sample

                Here is an example workflow definition:

                ```json
                {
                  "name": "ai-workflow",
                  "tasks": [
                    {
                      "type": "LLM_CHAT_COMPLETE",
                      "inputParameters": {
                        "llmProvider": "openai",
                        "model": "gpt-4"
                      }
                    }
                  ]
                }
                ```

                ### Performance Metrics

                | Metric | Q3 2025 | Q4 2025 | Change |
                |--------|---------|---------|--------|
                | Latency (p99) | 450ms | 320ms | -29% |
                | Throughput | 1200 rps | 1800 rps | +50% |
                | Error Rate | 0.5% | 0.2% | -60% |

                ## Action Items

                - [x] Deploy v4.0 to production
                - [x] Complete documentation update
                - [ ] Performance testing for v4.1
                - [ ] Security audit review

                ## Conclusion

                The team has made *excellent* progress. For more details, visit the [project wiki](https://wiki.example.com).

                ---

                *Report generated by Conductor Workflow Engine*
                """;

        @Test
        void testProducesValidPdf() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        }

        @Test
        void testContainsAllSections() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                String text = extractText(doc);
                assertTrue(text.contains("Project Status Report"));
                assertTrue(text.contains("Executive Summary"));
                assertTrue(text.contains("Technical Progress"));
                assertTrue(text.contains("Backend Services"));
                assertTrue(text.contains("Performance Metrics"));
                assertTrue(text.contains("Action Items"));
                assertTrue(text.contains("Conclusion"));
            }
        }

        @Test
        void testContainsTableData() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                String text = extractText(doc);
                assertTrue(text.contains("Latency"));
                assertTrue(text.contains("320ms"));
                assertTrue(text.contains("Throughput"));
            }
        }

        @Test
        void testContainsCodeBlock() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                String text = extractText(doc);
                assertTrue(text.contains("ai-workflow"));
                assertTrue(text.contains("LLM_CHAT_COMPLETE"));
            }
        }
    }

    // ========================================================================
    // Document 11: Long Document (Page Break Testing)
    // ========================================================================

    @Nested
    @DisplayName("Document 11: Long Document with Page Breaks")
    class LongDocument {

        @Test
        void testLongDocumentCreatesMultiplePages() throws IOException {
            StringBuilder md = new StringBuilder("# Long Document\n\n");
            for (int i = 1; i <= 100; i++) {
                md.append("## Section ").append(i).append("\n\n");
                md.append("This is paragraph ")
                        .append(i)
                        .append(". It contains enough text to take up some space on the page. ")
                        .append("We need to verify that page breaks happen correctly ")
                        .append("and content flows from one page to the next.\n\n");
            }

            try (PDDocument doc = convertAndLoad(md.toString())) {
                assertTrue(
                        doc.getNumberOfPages() > 1,
                        "Long document should span multiple pages, got " + doc.getNumberOfPages());
            }
        }

        @Test
        void testLongDocumentPreservesAllContent() throws IOException {
            StringBuilder md = new StringBuilder("# Content Preservation Test\n\n");
            for (int i = 1; i <= 50; i++) {
                md.append("- Item number ").append(i).append("\n");
            }

            try (PDDocument doc = convertAndLoad(md.toString())) {
                String text = extractText(doc);
                assertTrue(text.contains("Item number 1"));
                assertTrue(text.contains("Item number 25"));
                assertTrue(text.contains("Item number 50"));
            }
        }
    }

    // ========================================================================
    // Document 12: Edge Cases
    // ========================================================================

    @Nested
    @DisplayName("Document 12: Edge Cases")
    class EdgeCases {

        @Test
        void testMinimalMarkdown() throws IOException {
            try (PDDocument doc = convertAndLoad("Hello")) {
                assertTrue(doc.getNumberOfPages() >= 1);
                String text = extractText(doc);
                assertTrue(text.contains("Hello"));
            }
        }

        @Test
        void testOnlyHeading() throws IOException {
            try (PDDocument doc = convertAndLoad("# Just a Title")) {
                assertTrue(doc.getNumberOfPages() >= 1);
                String text = extractText(doc);
                assertTrue(text.contains("Just a Title"));
            }
        }

        @Test
        void testEmptyParagraphs() throws IOException {
            String md = "First\n\n\n\n\nSecond";
            try (PDDocument doc = convertAndLoad(md)) {
                assertTrue(doc.getNumberOfPages() >= 1);
                String text = extractText(doc);
                assertTrue(text.contains("First"));
                assertTrue(text.contains("Second"));
            }
        }

        @Test
        void testSpecialCharacters() throws IOException {
            String md = "# Special Characters\n\nAmpersan: & | Angle brackets: < > | Quotes: \" '";
            try (PDDocument doc = convertAndLoad(md)) {
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        }

        @Test
        void testVeryLongSingleLine() throws IOException {
            String longLine = "Word ".repeat(500);
            String md = "# Long Line Test\n\n" + longLine;
            try (PDDocument doc = convertAndLoad(md)) {
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        }

        @Test
        void testOnlyCodeBlock() throws IOException {
            String md = "```\nfunction hello() {\n  return 'world';\n}\n```";
            try (PDDocument doc = convertAndLoad(md)) {
                assertTrue(doc.getNumberOfPages() >= 1);
                String text = extractText(doc);
                assertTrue(text.contains("hello"));
            }
        }

        @Test
        void testOnlyTable() throws IOException {
            String md = "| A | B |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |";
            try (PDDocument doc = convertAndLoad(md)) {
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        }

        @Test
        void testOnlyList() throws IOException {
            String md = "- one\n- two\n- three";
            try (PDDocument doc = convertAndLoad(md)) {
                assertTrue(doc.getNumberOfPages() >= 1);
                String text = extractText(doc);
                assertTrue(text.contains("one"));
                assertTrue(text.contains("two"));
                assertTrue(text.contains("three"));
            }
        }

        @Test
        void testWhitespaceOnlyMarkdown() throws IOException {
            try (PDDocument doc = convertAndLoad("   \n\n   \n")) {
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        }
    }

    // ========================================================================
    // Layout Options Tests
    // ========================================================================

    @Nested
    @DisplayName("Layout Options")
    class LayoutOptions {

        @Test
        void testA4PageSize() throws IOException {
            MarkdownToPdfRequest request =
                    MarkdownToPdfRequest.builder()
                            .markdown("# A4 Test\n\nContent")
                            .pageSize("A4")
                            .build();
            byte[] pdf = converter.convert(request);
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                // A4 = 595.28 x 841.89 points
                float width = doc.getPage(0).getMediaBox().getWidth();
                float height = doc.getPage(0).getMediaBox().getHeight();
                assertEquals(595.28f, width, 1f);
                assertEquals(841.89f, height, 1f);
            }
        }

        @Test
        void testLetterPageSize() throws IOException {
            MarkdownToPdfRequest request =
                    MarkdownToPdfRequest.builder()
                            .markdown("# Letter Test\n\nContent")
                            .pageSize("LETTER")
                            .build();
            byte[] pdf = converter.convert(request);
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                // LETTER = 612 x 792 points
                float width = doc.getPage(0).getMediaBox().getWidth();
                float height = doc.getPage(0).getMediaBox().getHeight();
                assertEquals(612f, width, 1f);
                assertEquals(792f, height, 1f);
            }
        }

        @Test
        void testLegalPageSize() throws IOException {
            MarkdownToPdfRequest request =
                    MarkdownToPdfRequest.builder()
                            .markdown("# Legal Test\n\nContent")
                            .pageSize("LEGAL")
                            .build();
            byte[] pdf = converter.convert(request);
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                // LEGAL = 612 x 1008 points
                float width = doc.getPage(0).getMediaBox().getWidth();
                float height = doc.getPage(0).getMediaBox().getHeight();
                assertEquals(612f, width, 1f);
                assertEquals(1008f, height, 1f);
            }
        }

        @Test
        void testUnknownPageSizeDefaultsToA4() throws IOException {
            MarkdownToPdfRequest request =
                    MarkdownToPdfRequest.builder()
                            .markdown("# Unknown Size\n\nContent")
                            .pageSize("CUSTOM_UNKNOWN")
                            .build();
            byte[] pdf = converter.convert(request);
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                float width = doc.getPage(0).getMediaBox().getWidth();
                assertEquals(595.28f, width, 1f);
            }
        }

        @Test
        void testCaseInsensitivePageSize() throws IOException {
            MarkdownToPdfRequest request =
                    MarkdownToPdfRequest.builder()
                            .markdown("# Case Test\n\nContent")
                            .pageSize("letter")
                            .build();
            byte[] pdf = converter.convert(request);
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                float width = doc.getPage(0).getMediaBox().getWidth();
                assertEquals(612f, width, 1f);
            }
        }

        @Test
        void testCustomMargins() throws IOException {
            MarkdownToPdfRequest request =
                    MarkdownToPdfRequest.builder()
                            .markdown("# Margin Test\n\nContent")
                            .marginTop(36f)
                            .marginRight(36f)
                            .marginBottom(36f)
                            .marginLeft(36f)
                            .build();
            byte[] pdf = converter.convert(request);
            assertNotNull(pdf);
            assertTrue(pdf.length > 0);
        }

        @Test
        void testLargeMargins() throws IOException {
            MarkdownToPdfRequest request =
                    MarkdownToPdfRequest.builder()
                            .markdown("# Big Margins\n\nTight content area")
                            .marginTop(144f)
                            .marginRight(144f)
                            .marginBottom(144f)
                            .marginLeft(144f)
                            .build();
            byte[] pdf = converter.convert(request);
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                assertNotNull(doc);
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        }

        @Test
        void testCompactTheme() throws IOException {
            StringBuilder md = new StringBuilder("# Compact Theme Test\n\n");
            for (int i = 1; i <= 30; i++) {
                md.append("Paragraph ").append(i).append(". Some content here.\n\n");
            }
            MarkdownToPdfRequest defaultReq =
                    MarkdownToPdfRequest.builder().markdown(md.toString()).theme("default").build();
            MarkdownToPdfRequest compactReq =
                    MarkdownToPdfRequest.builder().markdown(md.toString()).theme("compact").build();

            byte[] defaultPdf = converter.convert(defaultReq);
            byte[] compactPdf = converter.convert(compactReq);

            // Compact should be smaller or equal (less spacing)
            try (PDDocument defaultDoc = Loader.loadPDF(defaultPdf);
                    PDDocument compactDoc = Loader.loadPDF(compactPdf)) {
                assertTrue(
                        compactDoc.getNumberOfPages() <= defaultDoc.getNumberOfPages(),
                        "Compact theme should use equal or fewer pages than default");
            }
        }

        @Test
        void testCustomFontSize() throws IOException {
            MarkdownToPdfRequest request =
                    MarkdownToPdfRequest.builder()
                            .markdown("# Font Size 14\n\nLarger text.")
                            .baseFontSize(14f)
                            .build();
            byte[] pdf = converter.convert(request);
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                assertNotNull(doc);
            }
        }

        @Test
        void testSmallFontSize() throws IOException {
            MarkdownToPdfRequest request =
                    MarkdownToPdfRequest.builder()
                            .markdown("# Font Size 8\n\nSmall text.")
                            .baseFontSize(8f)
                            .build();
            byte[] pdf = converter.convert(request);
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                assertNotNull(doc);
            }
        }
    }

    // ========================================================================
    // PDF Metadata Tests
    // ========================================================================

    @Nested
    @DisplayName("PDF Metadata")
    class PdfMetadata {

        @Test
        void testMetadataIsSet() throws IOException {
            MarkdownToPdfRequest request =
                    MarkdownToPdfRequest.builder()
                            .markdown("# Metadata Test")
                            .pdfMetadata(
                                    Map.of(
                                            "title", "Test Document",
                                            "author", "Unit Test",
                                            "subject", "Testing",
                                            "keywords", "test, pdf, markdown"))
                            .build();
            byte[] pdf = converter.convert(request);
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                assertEquals("Test Document", doc.getDocumentInformation().getTitle());
                assertEquals("Unit Test", doc.getDocumentInformation().getAuthor());
                assertEquals("Testing", doc.getDocumentInformation().getSubject());
                assertEquals("test, pdf, markdown", doc.getDocumentInformation().getKeywords());
            }
        }

        @Test
        void testPartialMetadata() throws IOException {
            MarkdownToPdfRequest request =
                    MarkdownToPdfRequest.builder()
                            .markdown("# Partial Metadata")
                            .pdfMetadata(Map.of("title", "Only Title"))
                            .build();
            byte[] pdf = converter.convert(request);
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                assertEquals("Only Title", doc.getDocumentInformation().getTitle());
                assertNull(doc.getDocumentInformation().getAuthor());
            }
        }

        @Test
        void testNoMetadata() throws IOException {
            MarkdownToPdfRequest request =
                    MarkdownToPdfRequest.builder().markdown("# No Metadata").build();
            byte[] pdf = converter.convert(request);
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                assertNotNull(doc);
                // Title should be null since no metadata was provided
                assertNull(doc.getDocumentInformation().getTitle());
            }
        }
    }

    // ========================================================================
    // Document 13: Deeply Nested Structures
    // ========================================================================

    @Nested
    @DisplayName("Document 13: Deeply Nested Structures")
    class DeeplyNested {

        static final String MARKDOWN =
                """
                # Nested Structures

                > Quote with a list:
                >
                > - Item in quote A
                > - Item in quote B
                >   - Nested in nested

                > Quote with code:
                >
                > ```
                > code inside quote
                > ```

                List with multiple paragraphs per item:

                - First item

                  Continuation paragraph for first item.

                - Second item

                  Continuation paragraph for second item.

                1. Ordered with nested bullets
                   - Bullet inside ordered
                   - Another bullet
                2. Second ordered item
                   1. Sub-ordered
                   2. Sub-ordered 2
                """;

        @Test
        void testProducesValidPdf() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        }

        @Test
        void testContainsNestedContent() throws IOException {
            try (PDDocument doc = convertAndLoad(MARKDOWN)) {
                String text = extractText(doc);
                assertTrue(text.contains("Item in quote A"));
                assertTrue(text.contains("code inside quote"));
                assertTrue(text.contains("Continuation paragraph"));
                assertTrue(text.contains("Bullet inside ordered"));
            }
        }
    }

    // ========================================================================
    // Conversion Error Handling
    // ========================================================================

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        void testNullMarkdownThrowsException() {
            MarkdownToPdfRequest request = MarkdownToPdfRequest.builder().markdown(null).build();
            assertThrows(Exception.class, () -> converter.convert(request));
        }
    }
}
