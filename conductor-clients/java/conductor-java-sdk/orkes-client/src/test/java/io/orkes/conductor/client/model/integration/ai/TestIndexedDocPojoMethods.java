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
package io.orkes.conductor.client.model.integration.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


public class TestIndexedDocPojoMethods {

    @Test
    public void testDefaultConstructor() {
        IndexedDoc doc = new IndexedDoc();

        assertNull(doc.getDocId());
        assertNull(doc.getParentDocId());
        assertNull(doc.getText());
        assertEquals(0.0, doc.getScore());
        assertNotNull(doc.getMetadata());
        assertTrue(doc.getMetadata().isEmpty());
    }

    @Test
    public void testParameterizedConstructor() {
        String docId = "doc123";
        String parentDocId = "parent456";
        String text = "Sample text";
        double score = 0.95;

        IndexedDoc doc = new IndexedDoc(docId, parentDocId, text, score);

        assertEquals(docId, doc.getDocId());
        assertEquals(parentDocId, doc.getParentDocId());
        assertEquals(text, doc.getText());
        assertEquals(score, doc.getScore());
        assertNotNull(doc.getMetadata());
        assertTrue(doc.getMetadata().isEmpty());
    }
}