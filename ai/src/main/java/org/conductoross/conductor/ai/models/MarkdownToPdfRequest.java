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
package org.conductoross.conductor.ai.models;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Request model for the GENERATE_PDF system task that converts markdown text to PDF. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class MarkdownToPdfRequest extends LLMWorkerInput {

    /** The markdown text to convert to PDF. Required. */
    private String markdown;

    /** Page size: A4, LETTER, LEGAL. Default: A4. */
    @Builder.Default private String pageSize = "A4";

    /** Top margin in points (72pt = 1 inch). Default: 72. */
    @Builder.Default private float marginTop = 72f;

    /** Right margin in points. Default: 72. */
    @Builder.Default private float marginRight = 72f;

    /** Bottom margin in points. Default: 72. */
    @Builder.Default private float marginBottom = 72f;

    /** Left margin in points. Default: 72. */
    @Builder.Default private float marginLeft = 72f;

    /** Built-in style preset: "default", "compact". Default: "default". */
    @Builder.Default private String theme = "default";

    /** Base font size in points. Default: 11. */
    @Builder.Default private float baseFontSize = 11f;

    /**
     * Output location URI for the generated PDF. e.g., "file:///tmp/output.pdf". If null, uses the
     * default payload store location.
     */
    private String outputLocation;

    /** Optional metadata to embed in the PDF (title, author, subject, keywords). */
    private Map<String, String> pdfMetadata;

    /** Base URL for resolving relative image paths in the markdown. */
    private String imageBaseUrl;
}
