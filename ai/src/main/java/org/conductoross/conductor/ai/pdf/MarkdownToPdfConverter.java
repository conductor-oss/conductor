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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.conductoross.conductor.ai.models.MarkdownToPdfRequest;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.definition.DefinitionExtension;
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.data.MutableDataSet;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the conversion of Markdown text to PDF. Parses markdown using flexmark-java, then
 * renders the AST to PDF using Apache PDFBox via {@link PdfDocumentRenderer}.
 */
@Component
@Conditional(AIIntegrationEnabledCondition.class)
@Slf4j
public class MarkdownToPdfConverter {

    private final PdfImageResolver imageResolver;

    public MarkdownToPdfConverter(PdfImageResolver imageResolver) {
        this.imageResolver = imageResolver;
    }

    /**
     * Converts markdown text to a PDF byte array.
     *
     * @param request the conversion request containing markdown and layout options
     * @return the generated PDF as a byte array
     */
    public byte[] convert(MarkdownToPdfRequest request) {
        if (request.getMarkdown() == null) {
            throw new IllegalArgumentException("markdown content must not be null");
        }

        // Step 1: Parse markdown to AST
        Document markdownAst = parseMarkdown(request.getMarkdown());

        // Step 2: Create PDF document
        try (PDDocument document = new PDDocument()) {
            // Step 3: Configure page size
            PDRectangle pageSize = resolvePageSize(request.getPageSize());

            // Step 4: Set PDF metadata
            setMetadata(document, request.getPdfMetadata());

            // Step 5: Create render context
            boolean compact = "compact".equalsIgnoreCase(request.getTheme());
            PdfRenderContext ctx =
                    new PdfRenderContext(
                            document,
                            pageSize,
                            request.getMarginTop(),
                            request.getMarginRight(),
                            request.getMarginBottom(),
                            request.getMarginLeft(),
                            request.getBaseFontSize(),
                            compact);

            // Step 6: Render AST to PDF
            PdfDocumentRenderer renderer =
                    new PdfDocumentRenderer(ctx, imageResolver, request.getImageBaseUrl());
            renderer.render(markdownAst);

            // Step 7: Close the last content stream
            if (ctx.getContentStream() != null) {
                ctx.getContentStream().close();
            }

            // Step 8: Save to bytes
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate PDF from markdown", e);
        }
    }

    private Document parseMarkdown(String markdown) {
        MutableDataSet options = new MutableDataSet();
        options.set(
                Parser.EXTENSIONS,
                List.of(
                        TablesExtension.create(),
                        StrikethroughExtension.create(),
                        TaskListExtension.create(),
                        FootnoteExtension.create(),
                        AutolinkExtension.create(),
                        DefinitionExtension.create()));

        Parser parser = Parser.builder(options).build();
        return parser.parse(markdown);
    }

    private PDRectangle resolvePageSize(String pageSize) {
        if (pageSize == null) return PDRectangle.A4;
        return switch (pageSize.toUpperCase()) {
            case "LETTER" -> PDRectangle.LETTER;
            case "LEGAL" -> PDRectangle.LEGAL;
            case "A3" -> new PDRectangle(841.89f, 1190.55f);
            case "A5" -> new PDRectangle(419.53f, 595.28f);
            default -> PDRectangle.A4;
        };
    }

    private void setMetadata(PDDocument document, Map<String, String> pdfMetadata) {
        if (pdfMetadata == null || pdfMetadata.isEmpty()) return;

        PDDocumentInformation info = document.getDocumentInformation();
        if (pdfMetadata.containsKey("title")) {
            info.setTitle(pdfMetadata.get("title"));
        }
        if (pdfMetadata.containsKey("author")) {
            info.setAuthor(pdfMetadata.get("author"));
        }
        if (pdfMetadata.containsKey("subject")) {
            info.setSubject(pdfMetadata.get("subject"));
        }
        if (pdfMetadata.containsKey("keywords")) {
            info.setKeywords(pdfMetadata.get("keywords"));
        }
    }
}
