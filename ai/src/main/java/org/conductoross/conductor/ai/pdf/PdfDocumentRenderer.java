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
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.ext.footnotes.Footnote;
import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListItem;
import com.vladsch.flexmark.ext.tables.*;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import lombok.extern.slf4j.Slf4j;

/**
 * Walks the flexmark markdown AST and renders each node type to PDF using Apache PDFBox. Handles
 * word wrapping, page breaks, inline formatting, tables, images, lists, code blocks, blockquotes,
 * and links.
 */
@Slf4j
public class PdfDocumentRenderer {

    private final PdfRenderContext ctx;
    private final PdfImageResolver imageResolver;
    private final String imageBaseUrl;

    public PdfDocumentRenderer(
            PdfRenderContext ctx, PdfImageResolver imageResolver, String imageBaseUrl) {
        this.ctx = ctx;
        this.imageResolver = imageResolver;
        this.imageBaseUrl = imageBaseUrl;
    }

    /** Renders the entire markdown document AST to PDF. */
    public void render(Document document) throws IOException {
        ctx.newPage();
        renderChildren(document);
    }

    private void renderChildren(Node parent) throws IOException {
        Node child = parent.getFirstChild();
        while (child != null) {
            renderNode(child);
            child = child.getNext();
        }
    }

    private void renderNode(Node node) throws IOException {
        if (node instanceof Heading) {
            renderHeading((Heading) node);
        } else if (node instanceof Paragraph) {
            renderParagraph((Paragraph) node);
        } else if (node instanceof BulletList) {
            renderBulletList((BulletList) node);
        } else if (node instanceof OrderedList) {
            renderOrderedList((OrderedList) node);
        } else if (node instanceof FencedCodeBlock) {
            renderFencedCodeBlock((FencedCodeBlock) node);
        } else if (node instanceof IndentedCodeBlock) {
            renderIndentedCodeBlock((IndentedCodeBlock) node);
        } else if (node instanceof BlockQuote) {
            renderBlockQuote((BlockQuote) node);
        } else if (node instanceof ThematicBreak) {
            renderThematicBreak();
        } else if (node instanceof TableBlock) {
            renderTable((TableBlock) node);
        } else if (node instanceof HtmlBlock) {
            renderHtmlBlock((HtmlBlock) node);
        } else {
            // Unknown block-level node: try rendering children
            renderChildren(node);
        }
    }

    // ========================================================================
    // Block-level rendering
    // ========================================================================

    private void renderHeading(Heading heading) throws IOException {
        float scale =
                switch (heading.getLevel()) {
                    case 1 -> 2.0f;
                    case 2 -> 1.6f;
                    case 3 -> 1.3f;
                    case 4 -> 1.15f;
                    case 5 -> 1.0f;
                    default -> 0.9f;
                };

        float fontSize = ctx.getBaseFontSize() * scale;
        float spacing = ctx.isCompact() ? fontSize * 0.5f : fontSize * 0.8f;

        ctx.ensureSpace(fontSize + spacing * 2);
        ctx.advanceCursor(spacing);

        List<TextRun> runs = collectTextRuns(heading);
        // Force bold for headings
        for (TextRun run : runs) {
            run.bold = true;
        }
        renderTextRuns(runs, fontSize);

        // Draw underline for h1 and h2
        if (heading.getLevel() <= 2) {
            ctx.advanceCursor(3f);
            PDPageContentStream cs = ctx.getContentStream();
            cs.setStrokingColor(0.85f, 0.85f, 0.85f);
            cs.setLineWidth(0.5f);
            cs.moveTo(ctx.getLeftX(), ctx.getCursorY());
            cs.lineTo(ctx.getRightX(), ctx.getCursorY());
            cs.stroke();
            ctx.advanceCursor(3f);
        }

        ctx.advanceCursor(spacing * 0.5f);
    }

    private void renderParagraph(Paragraph paragraph) throws IOException {
        // Check if paragraph contains only an image
        if (paragraph.getFirstChild() instanceof Image
                && paragraph.getFirstChild() == paragraph.getLastChild()) {
            renderImage((Image) paragraph.getFirstChild());
            return;
        }

        float fontSize = ctx.getBaseFontSize();
        float spacing = ctx.isCompact() ? fontSize * 0.3f : fontSize * 0.5f;

        ctx.ensureSpace(ctx.getLineHeight(fontSize) + spacing);
        ctx.advanceCursor(spacing);

        List<TextRun> runs = collectTextRuns(paragraph);
        renderTextRuns(runs, fontSize);

        ctx.advanceCursor(spacing);
    }

    private void renderBulletList(BulletList list) throws IOException {
        float spacing = ctx.isCompact() ? 2f : 4f;
        ctx.advanceCursor(spacing);
        ctx.setListIndentLevel(ctx.getListIndentLevel() + 1);

        Node item = list.getFirstChild();
        while (item != null) {
            if (item instanceof TaskListItem taskItem) {
                String marker = taskItem.isItemDoneMarker() ? "[x] " : "[ ] ";
                renderListItem(item, marker);
            } else if (item instanceof BulletListItem) {
                renderListItem(item, "- ");
            }
            item = item.getNext();
        }

        ctx.setListIndentLevel(ctx.getListIndentLevel() - 1);
        ctx.advanceCursor(spacing);
    }

    private void renderOrderedList(OrderedList list) throws IOException {
        float spacing = ctx.isCompact() ? 2f : 4f;
        ctx.advanceCursor(spacing);
        ctx.setListIndentLevel(ctx.getListIndentLevel() + 1);

        int number = list.getStartNumber();
        Node item = list.getFirstChild();
        while (item != null) {
            if (item instanceof OrderedListItem) {
                renderListItem(item, number + ". ");
                number++;
            }
            item = item.getNext();
        }

        ctx.setListIndentLevel(ctx.getListIndentLevel() - 1);
        ctx.advanceCursor(spacing);
    }

    private void renderListItem(Node item, String marker) throws IOException {
        float fontSize = ctx.getBaseFontSize();
        ctx.ensureSpace(ctx.getLineHeight(fontSize));

        // Render marker
        PDPageContentStream cs = ctx.getContentStream();
        cs.beginText();
        cs.setFont(ctx.getRegularFont(), fontSize);
        cs.newLineAtOffset(ctx.getLeftX(), ctx.getCursorY() - fontSize);
        cs.showText(sanitizeText(marker, ctx.getRegularFont()));
        cs.endText();

        // Render item content inline (offset by marker width)
        float markerWidth;
        try {
            markerWidth = ctx.getTextWidth(marker, ctx.getRegularFont(), fontSize);
        } catch (IOException e) {
            markerWidth = fontSize * marker.length() * 0.5f;
        }

        // Render paragraphs and nested lists within the item
        Node child = item.getFirstChild();
        boolean firstChild = true;
        while (child != null) {
            if (child instanceof Paragraph) {
                if (firstChild) {
                    // First paragraph renders on the same line as the marker
                    List<TextRun> runs = collectTextRuns(child);
                    renderTextRunsWithOffset(runs, fontSize, markerWidth);
                    firstChild = false;
                } else {
                    renderParagraph((Paragraph) child);
                }
            } else if (child instanceof BulletList) {
                renderBulletList((BulletList) child);
            } else if (child instanceof OrderedList) {
                renderOrderedList((OrderedList) child);
            } else {
                renderNode(child);
            }
            child = child.getNext();
        }

        if (firstChild) {
            // No paragraph children - item has inline content only
            ctx.advanceCursor(ctx.getLineHeight(fontSize));
        }
    }

    private void renderFencedCodeBlock(FencedCodeBlock codeBlock) throws IOException {
        renderCodeContent(codeBlock.getContentChars().toString());
    }

    private void renderIndentedCodeBlock(IndentedCodeBlock codeBlock) throws IOException {
        renderCodeContent(codeBlock.getContentChars().toString());
    }

    private void renderCodeContent(String code) throws IOException {
        float fontSize = ctx.getBaseFontSize() * 0.85f;
        float lineHeight = ctx.getLineHeight(fontSize);
        float padding = 8f;

        String[] lines = code.split("\n", -1);
        // Remove trailing empty line if present
        if (lines.length > 0 && lines[lines.length - 1].isBlank()) {
            String[] trimmed = new String[lines.length - 1];
            System.arraycopy(lines, 0, trimmed, 0, trimmed.length);
            lines = trimmed;
        }

        float blockHeight = lines.length * lineHeight + padding * 2;
        ctx.ensureSpace(Math.min(blockHeight, ctx.getLineHeight(fontSize) * 3));
        ctx.advanceCursor(ctx.isCompact() ? 4f : 8f);

        // Draw background rectangle
        float bgTop = ctx.getCursorY() + fontSize * 0.3f;
        float bgWidth = ctx.getContentWidth();
        PDPageContentStream cs = ctx.getContentStream();
        cs.setNonStrokingColor(0.96f, 0.96f, 0.96f);
        cs.addRect(ctx.getLeftX(), bgTop - blockHeight, bgWidth, blockHeight);
        cs.fill();
        cs.setNonStrokingColor(0f, 0f, 0f);

        // Render each line
        float codeX = ctx.getLeftX() + padding;
        for (String line : lines) {
            ctx.ensureSpace(lineHeight);
            cs = ctx.getContentStream();
            cs.beginText();
            cs.setFont(ctx.getMonoFont(), fontSize);
            cs.newLineAtOffset(codeX, ctx.getCursorY() - fontSize);
            // Truncate lines that are too wide
            String displayLine =
                    truncateToFit(line, ctx.getMonoFont(), fontSize, bgWidth - padding * 2);
            cs.showText(sanitizeText(displayLine, ctx.getMonoFont()));
            cs.endText();
            ctx.advanceCursor(lineHeight);
        }

        ctx.advanceCursor(ctx.isCompact() ? 4f : 8f);
    }

    private void renderBlockQuote(BlockQuote blockQuote) throws IOException {
        float spacing = ctx.isCompact() ? 3f : 6f;
        ctx.advanceCursor(spacing);

        boolean wasInBlockquote = ctx.isInBlockquote();
        ctx.setInBlockquote(true);

        // Record Y position before rendering children for the vertical bar
        float startY = ctx.getCursorY();

        renderChildren(blockQuote);

        float endY = ctx.getCursorY();

        // Draw vertical gray bar on the left
        float barX = ctx.getLeftX() - 10f;
        PDPageContentStream cs = ctx.getContentStream();
        cs.setStrokingColor(0.8f, 0.8f, 0.8f);
        cs.setLineWidth(3f);
        cs.moveTo(barX, startY);
        cs.lineTo(barX, endY);
        cs.stroke();
        cs.setStrokingColor(0f, 0f, 0f);

        ctx.setInBlockquote(wasInBlockquote);
        ctx.advanceCursor(spacing);
    }

    private void renderThematicBreak() throws IOException {
        float spacing = ctx.isCompact() ? 8f : 16f;
        ctx.ensureSpace(spacing * 2);
        ctx.advanceCursor(spacing);

        PDPageContentStream cs = ctx.getContentStream();
        cs.setStrokingColor(0.8f, 0.8f, 0.8f);
        cs.setLineWidth(0.5f);
        cs.moveTo(ctx.getLeftX(), ctx.getCursorY());
        cs.lineTo(ctx.getRightX(), ctx.getCursorY());
        cs.stroke();
        cs.setStrokingColor(0f, 0f, 0f);

        ctx.advanceCursor(spacing);
    }

    private void renderImage(Image image) throws IOException {
        String src = image.getUrl().toString();
        byte[] imageBytes = imageResolver.resolve(src, imageBaseUrl);
        if (imageBytes == null) {
            // Render alt text as placeholder
            log.warn("Could not load image: {}", src);
            renderPlainText("[Image: " + image.getText() + "]", ctx.getBaseFontSize());
            return;
        }

        try {
            PDImageXObject pdImage =
                    PDImageXObject.createFromByteArray(ctx.getDocument(), imageBytes, src);

            float maxWidth = ctx.getContentWidth();
            float maxHeight =
                    ctx.getPageHeight() - ctx.getMarginTop() - ctx.getMarginBottom() - 40f;

            // Scale to fit within content width and available height
            float imgWidth = pdImage.getWidth();
            float imgHeight = pdImage.getHeight();

            if (imgWidth > maxWidth) {
                float ratio = maxWidth / imgWidth;
                imgWidth = maxWidth;
                imgHeight *= ratio;
            }
            if (imgHeight > maxHeight) {
                float ratio = maxHeight / imgHeight;
                imgHeight = maxHeight;
                imgWidth *= ratio;
            }

            ctx.ensureSpace(imgHeight + 10f);
            ctx.advanceCursor(5f);

            float x = ctx.getLeftX();
            float y = ctx.getCursorY() - imgHeight;

            PDPageContentStream cs = ctx.getContentStream();
            cs.drawImage(pdImage, x, y, imgWidth, imgHeight);

            ctx.advanceCursor(imgHeight + 5f);
        } catch (Exception e) {
            log.warn("Failed to embed image '{}': {}", src, e.getMessage());
            renderPlainText("[Image: " + image.getText() + "]", ctx.getBaseFontSize());
        }
    }

    private void renderTable(TableBlock tableBlock) throws IOException {
        float fontSize = ctx.getBaseFontSize() * 0.9f;
        float cellPadding = 4f;
        float lineHeight = ctx.getLineHeight(fontSize);

        // Collect table data
        List<List<String>> headerRows = new ArrayList<>();
        List<List<String>> bodyRows = new ArrayList<>();

        Node child = tableBlock.getFirstChild();
        while (child != null) {
            if (child instanceof TableHead) {
                collectTableRows(child, headerRows);
            } else if (child instanceof TableBody) {
                collectTableRows(child, bodyRows);
            }
            child = child.getNext();
        }

        List<List<String>> allRows = new ArrayList<>();
        allRows.addAll(headerRows);
        allRows.addAll(bodyRows);

        if (allRows.isEmpty()) return;

        // Calculate column count and widths
        int colCount = allRows.stream().mapToInt(List::size).max().orElse(0);
        if (colCount == 0) return;

        float tableWidth = ctx.getContentWidth();
        float colWidth = tableWidth / colCount;

        ctx.advanceCursor(ctx.isCompact() ? 4f : 8f);

        // Render rows
        for (int rowIdx = 0; rowIdx < allRows.size(); rowIdx++) {
            List<String> row = allRows.get(rowIdx);
            boolean isHeader = rowIdx < headerRows.size();
            float rowHeight = lineHeight + cellPadding * 2;

            ctx.ensureSpace(rowHeight);

            float rowY = ctx.getCursorY();
            float cellX = ctx.getLeftX();

            PDPageContentStream cs = ctx.getContentStream();

            // Draw header background
            if (isHeader) {
                cs.setNonStrokingColor(0.96f, 0.96f, 0.96f);
                cs.addRect(cellX, rowY - rowHeight, tableWidth, rowHeight);
                cs.fill();
                cs.setNonStrokingColor(0f, 0f, 0f);
            }

            // Draw cell text
            for (int colIdx = 0; colIdx < colCount; colIdx++) {
                String cellText = colIdx < row.size() ? row.get(colIdx) : "";
                PDType1Font font = isHeader ? ctx.getBoldFont() : ctx.getRegularFont();

                // Truncate text to fit in cell
                String displayText =
                        truncateToFit(cellText, font, fontSize, colWidth - cellPadding * 2);

                cs.beginText();
                cs.setFont(font, fontSize);
                cs.newLineAtOffset(cellX + cellPadding, rowY - cellPadding - fontSize);
                cs.showText(sanitizeText(displayText, font));
                cs.endText();

                cellX += colWidth;
            }

            // Draw cell borders
            cs.setStrokingColor(0.85f, 0.85f, 0.85f);
            cs.setLineWidth(0.5f);
            // Top border
            cs.moveTo(ctx.getLeftX(), rowY);
            cs.lineTo(ctx.getLeftX() + tableWidth, rowY);
            cs.stroke();
            // Bottom border
            cs.moveTo(ctx.getLeftX(), rowY - rowHeight);
            cs.lineTo(ctx.getLeftX() + tableWidth, rowY - rowHeight);
            cs.stroke();
            // Vertical borders
            float borderX = ctx.getLeftX();
            for (int i = 0; i <= colCount; i++) {
                cs.moveTo(borderX, rowY);
                cs.lineTo(borderX, rowY - rowHeight);
                cs.stroke();
                borderX += colWidth;
            }
            cs.setStrokingColor(0f, 0f, 0f);

            ctx.advanceCursor(rowHeight);
        }

        ctx.advanceCursor(ctx.isCompact() ? 4f : 8f);
    }

    private void collectTableRows(Node section, List<List<String>> rows) {
        Node row = section.getFirstChild();
        while (row != null) {
            if (row instanceof TableRow) {
                List<String> cells = new ArrayList<>();
                Node cell = row.getFirstChild();
                while (cell != null) {
                    if (cell instanceof TableCell) {
                        cells.add(cell.getChars().toString().trim());
                    }
                    cell = cell.getNext();
                }
                rows.add(cells);
            }
            row = row.getNext();
        }
    }

    private void renderHtmlBlock(HtmlBlock htmlBlock) throws IOException {
        // Render raw HTML as plain text
        String text = htmlBlock.getChars().toString().trim();
        if (!text.isEmpty()) {
            renderPlainText(text, ctx.getBaseFontSize());
        }
    }

    // ========================================================================
    // Inline text rendering
    // ========================================================================

    /** A styled text run within a paragraph. */
    static class TextRun {
        String text;
        boolean bold;
        boolean italic;
        boolean code;
        boolean strikethrough;
        String linkUrl;

        TextRun(
                String text,
                boolean bold,
                boolean italic,
                boolean code,
                boolean strikethrough,
                String linkUrl) {
            this.text = text;
            this.bold = bold;
            this.italic = italic;
            this.code = code;
            this.strikethrough = strikethrough;
            this.linkUrl = linkUrl;
        }
    }

    /** Collects all inline text runs from a block node, preserving formatting. */
    private List<TextRun> collectTextRuns(Node block) {
        List<TextRun> runs = new ArrayList<>();
        collectInlineRuns(block, runs, false, false, false, false, null);
        return runs;
    }

    private void collectInlineRuns(
            Node node,
            List<TextRun> runs,
            boolean bold,
            boolean italic,
            boolean code,
            boolean strikethrough,
            String linkUrl) {
        Node child = node.getFirstChild();
        while (child != null) {
            if (child instanceof Text) {
                String text = child.getChars().toString();
                if (!text.isEmpty()) {
                    runs.add(new TextRun(text, bold, italic, code, strikethrough, linkUrl));
                }
            } else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                runs.add(new TextRun("\n", bold, italic, code, strikethrough, linkUrl));
            } else if (child instanceof Code) {
                String text = ((Code) child).getText().toString();
                runs.add(new TextRun(text, bold, italic, true, strikethrough, linkUrl));
            } else if (child instanceof StrongEmphasis) {
                collectInlineRuns(child, runs, true, italic, code, strikethrough, linkUrl);
            } else if (child instanceof Emphasis) {
                collectInlineRuns(child, runs, bold, true, code, strikethrough, linkUrl);
            } else if (child instanceof Strikethrough) {
                collectInlineRuns(child, runs, bold, italic, code, true, linkUrl);
            } else if (child instanceof Link link) {
                collectInlineRuns(
                        child, runs, bold, italic, code, strikethrough, link.getUrl().toString());
            } else if (child instanceof Image image) {
                // Inline image - add placeholder text
                runs.add(
                        new TextRun(
                                "[" + image.getText() + "]",
                                bold,
                                italic,
                                code,
                                strikethrough,
                                linkUrl));
            } else if (child instanceof Footnote) {
                runs.add(new TextRun("[*]", bold, italic, code, strikethrough, linkUrl));
            } else if (child instanceof HtmlInline) {
                // Skip inline HTML tags
            } else {
                // Recurse into unknown inline nodes
                collectInlineRuns(child, runs, bold, italic, code, strikethrough, linkUrl);
            }
            child = child.getNext();
        }
    }

    /** Renders text runs with word wrapping across the content area. */
    private void renderTextRuns(List<TextRun> runs, float fontSize) throws IOException {
        renderTextRunsWithOffset(runs, fontSize, 0f);
    }

    /**
     * Renders text runs with word wrapping, with an initial X offset (used for list items where the
     * marker occupies the start of the first line).
     */
    private void renderTextRunsWithOffset(List<TextRun> runs, float fontSize, float initialOffset)
            throws IOException {
        float x = ctx.getLeftX() + initialOffset;
        float maxX = ctx.getRightX();
        float lineHeight = ctx.getLineHeight(fontSize);
        boolean firstLine = true;

        for (TextRun run : runs) {
            PDType1Font font = resolveFont(run);
            float runFontSize = run.code ? fontSize * 0.85f : fontSize;

            if (run.text.equals("\n")) {
                // Explicit line break
                ctx.advanceCursor(lineHeight);
                ctx.ensureSpace(lineHeight);
                x = ctx.getLeftX();
                firstLine = false;
                continue;
            }

            // Split into words for wrapping
            String[] words = run.text.split("(?<=\\s)|(?=\\s)");
            for (String word : words) {
                if (word.isEmpty()) continue;

                float wordWidth = ctx.getTextWidth(word, font, runFontSize);

                // Wrap to next line if needed
                if (x + wordWidth > maxX && x > ctx.getLeftX() + 1f) {
                    ctx.advanceCursor(lineHeight);
                    ctx.ensureSpace(lineHeight);
                    x = ctx.getLeftX();
                    firstLine = false;
                    // Skip leading whitespace on new line
                    if (word.isBlank()) continue;
                }

                if (firstLine && x == ctx.getLeftX() + initialOffset) {
                    // First word on first line - position cursor
                } else if (x == ctx.getLeftX()) {
                    // First word on a new line - position cursor
                }

                PDPageContentStream cs = ctx.getContentStream();

                // Draw code background
                if (run.code) {
                    cs.setNonStrokingColor(0.94f, 0.94f, 0.94f);
                    cs.addRect(
                            x - 1f,
                            ctx.getCursorY() - runFontSize - 1f,
                            wordWidth + 2f,
                            runFontSize + 3f);
                    cs.fill();
                    cs.setNonStrokingColor(0f, 0f, 0f);
                }

                // Draw text
                if (run.linkUrl != null) {
                    cs.setNonStrokingColor(0.02f, 0.4f, 0.84f);
                }

                cs.beginText();
                cs.setFont(font, runFontSize);
                cs.newLineAtOffset(x, ctx.getCursorY() - fontSize);
                cs.showText(sanitizeText(word, font));
                cs.endText();

                // Draw strikethrough line
                if (run.strikethrough) {
                    float strikeY = ctx.getCursorY() - fontSize * 0.35f;
                    cs.setLineWidth(0.5f);
                    cs.moveTo(x, strikeY);
                    cs.lineTo(x + wordWidth, strikeY);
                    cs.stroke();
                }

                // Add link annotation
                if (run.linkUrl != null) {
                    cs.setNonStrokingColor(0f, 0f, 0f);
                    addLinkAnnotation(
                            x,
                            ctx.getCursorY() - fontSize - 2f,
                            wordWidth,
                            fontSize + 4f,
                            run.linkUrl);
                }

                x += wordWidth;
            }
        }

        // Advance past the last rendered line
        ctx.advanceCursor(lineHeight);
    }

    // ========================================================================
    // Utility methods
    // ========================================================================

    private PDType1Font resolveFont(TextRun run) {
        if (run.code) return ctx.getMonoFont();
        if (run.bold && run.italic) return ctx.getBoldItalicFont();
        if (run.bold) return ctx.getBoldFont();
        if (run.italic) return ctx.getItalicFont();
        return ctx.getRegularFont();
    }

    private void renderPlainText(String text, float fontSize) throws IOException {
        float lineHeight = ctx.getLineHeight(fontSize);
        ctx.ensureSpace(lineHeight);

        PDPageContentStream cs = ctx.getContentStream();
        cs.beginText();
        cs.setFont(ctx.getRegularFont(), fontSize);
        cs.newLineAtOffset(ctx.getLeftX(), ctx.getCursorY() - fontSize);
        String displayText =
                truncateToFit(text, ctx.getRegularFont(), fontSize, ctx.getContentWidth());
        cs.showText(sanitizeText(displayText, ctx.getRegularFont()));
        cs.endText();

        ctx.advanceCursor(lineHeight);
    }

    private String truncateToFit(String text, PDType1Font font, float fontSize, float maxWidth) {
        try {
            float width = ctx.getTextWidth(text, font, fontSize);
            if (width <= maxWidth) return text;

            // Binary search for truncation point
            int end = text.length();
            while (end > 0
                    && ctx.getTextWidth(text.substring(0, end) + "...", font, fontSize)
                            > maxWidth) {
                end = end - Math.max(1, end / 4);
            }
            return end > 0 ? text.substring(0, end) + "..." : "...";
        } catch (IOException e) {
            return text.length() > 50 ? text.substring(0, 50) + "..." : text;
        }
    }

    /**
     * Sanitizes text for PDFBox rendering by replacing characters that are not encodable in the
     * Standard 14 fonts (WinAnsiEncoding). Non-encodable characters are replaced with '?'.
     */
    private String sanitizeText(String text, PDType1Font font) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            try {
                font.encode(String.valueOf(c));
                sb.append(c);
            } catch (Exception e) {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private void addLinkAnnotation(float x, float y, float width, float height, String url) {
        try {
            PDAnnotationLink link = new PDAnnotationLink();
            PDActionURI action = new PDActionURI();
            action.setURI(url);
            link.setAction(action);
            link.setRectangle(new PDRectangle(x, y, width, height));
            link.setBorderStyle(null);

            ctx.getDocument()
                    .getPage(ctx.getDocument().getNumberOfPages() - 1)
                    .getAnnotations()
                    .add(link);
        } catch (Exception e) {
            log.debug("Failed to add link annotation for URL: {}", url);
        }
    }
}
