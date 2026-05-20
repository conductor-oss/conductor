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

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import lombok.Getter;
import lombok.Setter;

/**
 * Mutable state object that tracks the current rendering position, page, fonts, and layout
 * configuration while walking the markdown AST and rendering to PDF via PDFBox.
 */
@Getter
public class PdfRenderContext {

    private final PDDocument document;
    private final float pageWidth;
    private final float pageHeight;
    private final float marginTop;
    private final float marginRight;
    private final float marginBottom;
    private final float marginLeft;

    // Fonts
    private final PDType1Font regularFont;
    private final PDType1Font boldFont;
    private final PDType1Font italicFont;
    private final PDType1Font boldItalicFont;
    private final PDType1Font monoFont;
    private final PDType1Font monoBoldFont;

    private final float baseFontSize;
    private final float lineSpacing;

    // Mutable rendering state
    @Setter private PDPageContentStream contentStream;
    @Setter private float cursorY;
    @Setter private int listIndentLevel;
    @Setter private boolean inBlockquote;
    @Setter private boolean compact;

    public PdfRenderContext(
            PDDocument document,
            PDRectangle pageSize,
            float marginTop,
            float marginRight,
            float marginBottom,
            float marginLeft,
            float baseFontSize,
            boolean compact) {
        this.document = document;
        this.pageWidth = pageSize.getWidth();
        this.pageHeight = pageSize.getHeight();
        this.marginTop = marginTop;
        this.marginRight = marginRight;
        this.marginBottom = marginBottom;
        this.marginLeft = marginLeft;
        this.baseFontSize = baseFontSize;
        this.lineSpacing = compact ? 1.3f : 1.5f;
        this.compact = compact;

        // Standard 14 fonts - always available, no embedding needed
        this.regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        this.boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        this.italicFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        this.boldItalicFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE);
        this.monoFont = new PDType1Font(Standard14Fonts.FontName.COURIER);
        this.monoBoldFont = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);

        this.listIndentLevel = 0;
        this.inBlockquote = false;
    }

    /** Returns the usable content width (page width minus left and right margins and indents). */
    public float getContentWidth() {
        return pageWidth - marginLeft - marginRight - getIndentOffset();
    }

    /** Returns the left X coordinate accounting for margins, indentation, and blockquote. */
    public float getLeftX() {
        return marginLeft + getIndentOffset();
    }

    /** Returns the right X boundary. */
    public float getRightX() {
        return pageWidth - marginRight;
    }

    /** Calculates total indent offset from list nesting and blockquote state. */
    private float getIndentOffset() {
        float indent = listIndentLevel * 20f;
        if (inBlockquote) {
            indent += 15f;
        }
        return indent;
    }

    /** Returns the line height for the given font size. */
    public float getLineHeight(float fontSize) {
        return fontSize * lineSpacing;
    }

    /**
     * Ensures there is enough vertical space on the current page for the given height. If not,
     * creates a new page.
     *
     * @param height the required vertical space in points
     */
    public void ensureSpace(float height) throws IOException {
        if (cursorY - height < marginBottom) {
            newPage();
        }
    }

    /** Closes the current content stream, adds a new page, and resets the cursor. */
    public void newPage() throws IOException {
        if (contentStream != null) {
            contentStream.close();
        }
        PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
        document.addPage(page);
        contentStream = new PDPageContentStream(document, page);
        cursorY = pageHeight - marginTop;
    }

    /** Advances the cursor down by the specified amount. */
    public void advanceCursor(float amount) {
        cursorY -= amount;
    }

    /**
     * Calculates the width of a text string in the given font and size.
     *
     * @param text the text to measure
     * @param font the font to use
     * @param fontSize the font size in points
     * @return the width in points
     */
    public float getTextWidth(String text, PDType1Font font, float fontSize) throws IOException {
        return font.getStringWidth(text) / 1000f * fontSize;
    }
}
