/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.guardrail;

import java.io.IOException;

import org.conductoross.conductor.ai.models.guardrail.Guardrail;
import org.conductoross.conductor.ai.models.guardrail.HumanGuardrail;
import org.conductoross.conductor.ai.models.guardrail.LLMGuardrail;
import org.conductoross.conductor.ai.models.guardrail.ScriptGuardrail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies Jackson polymorphic (de)serialisation for {@link Guardrail} hierarchy and its use */
class GuardrailPolymorphicTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ---------------------------------------------------------------------
    //  Round‑trip tests for each concrete Guardrail subtype
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Round‑trip single guardrails")
    class SingleGuardrailRoundTrip {

        @Test
        void humanRoundTrip() throws IOException {
            HumanGuardrail original = new HumanGuardrail();
            original.setType(Guardrail.Type.HUMAN);
            original.setAssignee("alice");
            original.setMessage("review input");

            String json = mapper.writeValueAsString(original);
            Guardrail readBack = mapper.readValue(json, Guardrail.class);

            assertInstanceOf(HumanGuardrail.class, readBack);
            HumanGuardrail hg = (HumanGuardrail) readBack;
            assertEquals(original, hg);
        }

        @Test
        void llmRoundTrip() throws IOException {
            LLMGuardrail original = new LLMGuardrail();
            original.setType(Guardrail.Type.LLM);
            original.setModel("gpt‑4o");
            original.setInstructions("Keep answers short");

            String json = mapper.writeValueAsString(original);
            Guardrail readBack = mapper.readValue(json, Guardrail.class);

            assertInstanceOf(LLMGuardrail.class, readBack);
            assertEquals(original, readBack);
        }

        @Test
        void scriptRoundTrip() throws IOException {
            ScriptGuardrail original = new ScriptGuardrail();
            original.setType(Guardrail.Type.SCRIPT);
            original.setEvaluatorType("graaljs");
            original.setExpression("return true");

            String json = mapper.writeValueAsString(original);
            Guardrail readBack = mapper.readValue(json, Guardrail.class);

            assertInstanceOf(ScriptGuardrail.class, readBack);
            assertEquals(original, readBack);
        }
    }

    // ---------------------------------------------------------------------
    //  Error scenarios
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("Unknown type value")
        void unknownTypeFails() {
            String json =
                    """
                { "type": "ALIEN", "message": "???" }
                """;
            assertThrows(JsonMappingException.class, () -> mapper.readValue(json, Guardrail.class));
        }

        @Test
        @DisplayName("Missing type property")
        void missingTypeFails() {
            String json =
                    """
                { "assignee": "charlie", "message": "no type field" }
                """;
            assertThrows(JsonMappingException.class, () -> mapper.readValue(json, Guardrail.class));
        }
    }
}
