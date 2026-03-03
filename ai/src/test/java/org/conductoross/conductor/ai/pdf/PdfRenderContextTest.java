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
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PdfRenderContextTest {

    private PDDocument document;
    private PdfRenderContext ctx;

    @BeforeEach
    void setUp() {
        document = new PDDocument();
        ctx =
                new PdfRenderContext(
                        document,
                        PDRectangle.A4,
                        72f, // marginTop
                        72f, // marginRight
                        72f, // marginBottom
                        72f, // marginLeft
                        11f, // baseFontSize
                        false); // compact
    }

    @AfterEach
    void tearDown() throws IOException {
        if (ctx.getContentStream() != null) {
            ctx.getContentStream().close();
        }
        document.close();
    }

    // ========== Construction Tests ==========

    @Test
    void testDefaultConstructionSetsProperties() {
        assertEquals(PDRectangle.A4.getWidth(), ctx.getPageWidth(), 0.01f);
        assertEquals(PDRectangle.A4.getHeight(), ctx.getPageHeight(), 0.01f);
        assertEquals(72f, ctx.getMarginTop(), 0.01f);
        assertEquals(72f, ctx.getMarginRight(), 0.01f);
        assertEquals(72f, ctx.getMarginBottom(), 0.01f);
        assertEquals(72f, ctx.getMarginLeft(), 0.01f);
        assertEquals(11f, ctx.getBaseFontSize(), 0.01f);
        assertEquals(1.5f, ctx.getLineSpacing(), 0.01f);
        assertFalse(ctx.isCompact());
    }

    @Test
    void testCompactModeReducesLineSpacing() throws IOException {
        PdfRenderContext compactCtx =
                new PdfRenderContext(document, PDRectangle.A4, 72f, 72f, 72f, 72f, 11f, true);
        assertEquals(1.3f, compactCtx.getLineSpacing(), 0.01f);
        assertTrue(compactCtx.isCompact());
    }

    @Test
    void testFontsAreInitialized() {
        assertNotNull(ctx.getRegularFont());
        assertNotNull(ctx.getBoldFont());
        assertNotNull(ctx.getItalicFont());
        assertNotNull(ctx.getBoldItalicFont());
        assertNotNull(ctx.getMonoFont());
        assertNotNull(ctx.getMonoBoldFont());
    }

    // ========== Layout Calculation Tests ==========

    @Test
    void testContentWidthWithDefaultMargins() {
        // A4 width = 595.28, margins = 72 + 72 = 144
        float expected = PDRectangle.A4.getWidth() - 72f - 72f;
        assertEquals(expected, ctx.getContentWidth(), 0.01f);
    }

    @Test
    void testContentWidthWithListIndent() {
        ctx.setListIndentLevel(1);
        float expectedWithIndent = PDRectangle.A4.getWidth() - 72f - 72f - 20f;
        assertEquals(expectedWithIndent, ctx.getContentWidth(), 0.01f);
    }

    @Test
    void testContentWidthWithNestedListIndent() {
        ctx.setListIndentLevel(3);
        float expectedWithIndent = PDRectangle.A4.getWidth() - 72f - 72f - 60f;
        assertEquals(expectedWithIndent, ctx.getContentWidth(), 0.01f);
    }

    @Test
    void testContentWidthWithBlockquote() {
        ctx.setInBlockquote(true);
        float expectedWithBQ = PDRectangle.A4.getWidth() - 72f - 72f - 15f;
        assertEquals(expectedWithBQ, ctx.getContentWidth(), 0.01f);
    }

    @Test
    void testContentWidthWithBlockquoteAndListIndent() {
        ctx.setInBlockquote(true);
        ctx.setListIndentLevel(2);
        float expected = PDRectangle.A4.getWidth() - 72f - 72f - 40f - 15f;
        assertEquals(expected, ctx.getContentWidth(), 0.01f);
    }

    @Test
    void testLeftXWithDefaultMargins() {
        assertEquals(72f, ctx.getLeftX(), 0.01f);
    }

    @Test
    void testLeftXWithListIndent() {
        ctx.setListIndentLevel(2);
        assertEquals(72f + 40f, ctx.getLeftX(), 0.01f);
    }

    @Test
    void testRightX() {
        float expected = PDRectangle.A4.getWidth() - 72f;
        assertEquals(expected, ctx.getRightX(), 0.01f);
    }

    // ========== Line Height Tests ==========

    @Test
    void testLineHeightDefaultSpacing() {
        // lineSpacing = 1.5 (non-compact)
        assertEquals(11f * 1.5f, ctx.getLineHeight(11f), 0.01f);
    }

    @Test
    void testLineHeightCompactSpacing() throws IOException {
        PdfRenderContext compactCtx =
                new PdfRenderContext(document, PDRectangle.A4, 72f, 72f, 72f, 72f, 11f, true);
        assertEquals(11f * 1.3f, compactCtx.getLineHeight(11f), 0.01f);
    }

    @Test
    void testLineHeightWithDifferentFontSizes() {
        assertEquals(24f * 1.5f, ctx.getLineHeight(24f), 0.01f);
        assertEquals(8f * 1.5f, ctx.getLineHeight(8f), 0.01f);
    }

    // ========== Page Management Tests ==========

    @Test
    void testNewPageCreatesPageAndContentStream() throws IOException {
        ctx.newPage();

        assertNotNull(ctx.getContentStream());
        assertEquals(1, document.getNumberOfPages());
        float expectedCursorY = PDRectangle.A4.getHeight() - 72f;
        assertEquals(expectedCursorY, ctx.getCursorY(), 0.01f);
    }

    @Test
    void testMultipleNewPagesIncreasePageCount() throws IOException {
        ctx.newPage();
        ctx.newPage();
        ctx.newPage();

        assertEquals(3, document.getNumberOfPages());
    }

    @Test
    void testAdvanceCursorMovesDown() throws IOException {
        ctx.newPage();
        float initial = ctx.getCursorY();
        ctx.advanceCursor(50f);
        assertEquals(initial - 50f, ctx.getCursorY(), 0.01f);
    }

    @Test
    void testEnsureSpaceCreatesNewPageWhenNeeded() throws IOException {
        ctx.newPage();
        // Move cursor near the bottom
        ctx.setCursorY(ctx.getMarginBottom() + 5f);

        // Request more space than available
        ctx.ensureSpace(20f);

        // Should have created a new page
        assertEquals(2, document.getNumberOfPages());
        float expectedCursorY = PDRectangle.A4.getHeight() - 72f;
        assertEquals(expectedCursorY, ctx.getCursorY(), 0.01f);
    }

    @Test
    void testEnsureSpaceDoesNotCreatePageWhenEnoughRoom() throws IOException {
        ctx.newPage();
        float savedY = ctx.getCursorY();

        // Request small amount of space
        ctx.ensureSpace(10f);

        // Should stay on same page
        assertEquals(1, document.getNumberOfPages());
        assertEquals(savedY, ctx.getCursorY(), 0.01f);
    }

    // ========== Text Width Tests ==========

    @Test
    void testTextWidthReturnsPositiveValue() throws IOException {
        float width = ctx.getTextWidth("Hello World", ctx.getRegularFont(), 11f);
        assertTrue(width > 0f);
    }

    @Test
    void testTextWidthScalesWithFontSize() throws IOException {
        float width11 = ctx.getTextWidth("Test", ctx.getRegularFont(), 11f);
        float width22 = ctx.getTextWidth("Test", ctx.getRegularFont(), 22f);
        assertEquals(width11 * 2, width22, 0.01f);
    }

    @Test
    void testEmptyTextHasZeroWidth() throws IOException {
        float width = ctx.getTextWidth("", ctx.getRegularFont(), 11f);
        assertEquals(0f, width, 0.01f);
    }

    @Test
    void testMonoFontHasConsistentCharWidth() throws IOException {
        // Courier is monospaced, so all chars should have same width
        float widthI = ctx.getTextWidth("iii", ctx.getMonoFont(), 11f);
        float widthM = ctx.getTextWidth("mmm", ctx.getMonoFont(), 11f);
        assertEquals(widthI, widthM, 0.01f);
    }

    // ========== Different Page Sizes ==========

    @Test
    void testLetterPageSize() throws IOException {
        PdfRenderContext letterCtx =
                new PdfRenderContext(document, PDRectangle.LETTER, 72f, 72f, 72f, 72f, 11f, false);
        assertEquals(PDRectangle.LETTER.getWidth(), letterCtx.getPageWidth(), 0.01f);
        assertEquals(PDRectangle.LETTER.getHeight(), letterCtx.getPageHeight(), 0.01f);
    }

    @Test
    void testCustomMargins() throws IOException {
        PdfRenderContext customCtx =
                new PdfRenderContext(document, PDRectangle.A4, 36f, 50f, 36f, 50f, 12f, false);
        float expectedWidth = PDRectangle.A4.getWidth() - 50f - 50f;
        assertEquals(expectedWidth, customCtx.getContentWidth(), 0.01f);
        assertEquals(50f, customCtx.getLeftX(), 0.01f);
    }
}
