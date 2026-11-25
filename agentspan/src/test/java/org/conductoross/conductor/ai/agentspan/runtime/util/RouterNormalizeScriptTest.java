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
package org.conductoross.conductor.ai.agentspan.runtime.util;

import java.util.List;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the coordinator/router decision normalizer. The critical contracts: an unrecognized
 * router output maps to DONE (graceful exit) instead of silently ghost-running the first agent;
 * wrapped/case-variant outputs resolve to the canonical agent name; names that are substrings of
 * other names cannot double-match; and the value the generated DO_WHILE term condition compares
 * against actually exits the loop on DONE.
 */
class RouterNormalizeScriptTest {

    private static final List<String> AGENTS = List.of("backend_dev", "frontend_dev");

    private Context graalCtx;

    @BeforeEach
    void setUp() {
        graalCtx = Context.newBuilder("js").allowAllAccess(true).build();
    }

    @AfterEach
    void tearDown() {
        graalCtx.close();
    }

    private String normalize(String rawJs, List<String> agents) {
        String script =
                "var $ = {raw: "
                        + rawJs
                        + "}; "
                        + JavaScriptBuilder.normalizeRouterDecisionScript(agents);
        return graalCtx.eval("js", script).asString();
    }

    private String normalize(String rawJs) {
        return normalize(rawJs, AGENTS);
    }

    // ── Canonicalization ────────────────────────────────────────────

    @Test
    void exactMatchPassesThrough() {
        assertThat(normalize("'backend_dev'")).isEqualTo("backend_dev");
        assertThat(normalize("'DONE'")).isEqualTo("DONE");
    }

    @Test
    void caseInsensitiveExactMatchReturnsCanonicalName() {
        assertThat(normalize("'Backend_Dev'")).isEqualTo("backend_dev");
        assertThat(normalize("'BACKEND_DEV'")).isEqualTo("backend_dev");
        assertThat(normalize("'done'")).isEqualTo("DONE");
        assertThat(normalize("'Done.'")).isEqualTo("DONE");
    }

    @Test
    void wrappingCharactersAreStrippedFromBothEnds() {
        assertThat(normalize("'**backend_dev**'")).isEqualTo("backend_dev");
        assertThat(normalize("'\"backend_dev\"'")).isEqualTo("backend_dev");
        assertThat(normalize("'`backend_dev`'")).isEqualTo("backend_dev");
        assertThat(normalize("'backend_dev.'")).isEqualTo("backend_dev");
        assertThat(normalize("'  backend_dev  '")).isEqualTo("backend_dev");
    }

    // ── Unknown / degenerate outputs → DONE ─────────────────────────

    @Test
    void unknownAgentNameMapsToDone() {
        // The live bug: router hallucinated 'marketing_team' → previously ghost-ran the
        // first agent via the SWITCH default case.
        assertThat(normalize("'marketing_team'")).isEqualTo("DONE");
    }

    @Test
    void nullEmptyAndPunctuationOnlyMapToDone() {
        assertThat(normalize("null")).isEqualTo("DONE");
        assertThat(normalize("''")).isEqualTo("DONE");
        assertThat(normalize("'---'")).isEqualTo("DONE");
    }

    @Test
    void nonStringOutputIsCoerced() {
        // A user router worker may return a non-string result
        assertThat(normalize("42")).isEqualTo("DONE");
        assertThat(normalize("['backend_dev']")).isEqualTo("backend_dev"); // String([x]) === 'x'
    }

    // ── Prose / substring handling ──────────────────────────────────

    @Test
    void singleAgentInProseIsExtracted() {
        assertThat(normalize("'route to backend_dev'")).isEqualTo("backend_dev");
        assertThat(normalize("'I think Backend_dev should handle this.'")).isEqualTo("backend_dev");
    }

    @Test
    void ambiguousProseWithTwoAgentsMapsToDone() {
        assertThat(normalize("'backend_dev or frontend_dev'")).isEqualTo("DONE");
    }

    @Test
    void substringAgentNamesDoNotDoubleMatch() {
        // 'dev' is a substring of 'backend_dev' — longest-first blanking must prevent
        // 'dev' from re-matching inside the 'backend_dev' span.
        List<String> agents = List.of("dev", "backend_dev");
        assertThat(normalize("'route to backend_dev'", agents)).isEqualTo("backend_dev");
        assertThat(normalize("'dev'", agents)).isEqualTo("dev");
        // Both genuinely present → ambiguous → DONE
        assertThat(normalize("'backend_dev then dev'", agents)).isEqualTo("DONE");
    }

    @Test
    void wordBoundariesPreventPartialWordMatches() {
        // 'backend_devops' must not match 'backend_dev'
        assertThat(normalize("'backend_devops'")).isEqualTo("DONE");
    }

    @Test
    void agentNamedDoneCheckerIsRoutableWhileDoneStillExits() {
        List<String> agents = List.of("done_checker", "backend_dev");
        assertThat(normalize("'done_checker'", agents)).isEqualTo("done_checker");
        assertThat(normalize("'DONE'", agents)).isEqualTo("DONE");
    }

    @Test
    void regexMetacharactersInAgentNamesAreSafe() {
        // Interior metacharacters match literally (indexOf, no regex compiled from names).
        List<String> agents = List.of("agent.plus", "backend_dev");
        assertThat(normalize("'agent.plus'", agents)).isEqualTo("agent.plus");
        assertThat(normalize("'use agent.plus for this'", agents)).isEqualTo("agent.plus");
        // A name with leading/trailing non-word chars can't crash the scan — it just
        // won't survive the wrapping-strip, degrading to DONE, never to ghost work.
        List<String> weird = List.of("agent+", "backend_dev");
        assertThat(normalize("'agent+'", weird)).isEqualTo("DONE");
    }

    // ── Rewiring proof: the GENERATED term condition exits on DONE ──

    @Test
    void generatedTermConditionExitsOnDoneAndContinuesOnAgent() {
        // Mirror of MultiAgentCompiler's termCondition format string, with the norm ref —
        // proves the compare target and the normalizer output shape line up.
        String termCondition =
                String.format(
                        "if ( $.%s['iteration'] < %d && $.%s['result'] != 'DONE' ) { true; } else { false; }",
                        "team_loop", 25, "team_route_norm");

        String continueCase =
                "var $ = {team_loop: {iteration: 3}, team_route_norm: {result: 'backend_dev'}}; "
                        + termCondition;
        assertThat(graalCtx.eval("js", continueCase).asBoolean()).isTrue();

        String doneCase =
                "var $ = {team_loop: {iteration: 3}, team_route_norm: {result: 'DONE'}}; "
                        + termCondition;
        assertThat(graalCtx.eval("js", doneCase).asBoolean()).isFalse();

        String maxTurnsCase =
                "var $ = {team_loop: {iteration: 25}, team_route_norm: {result: 'backend_dev'}}; "
                        + termCondition;
        assertThat(graalCtx.eval("js", maxTurnsCase).asBoolean()).isFalse();
    }

    @Test
    void normalizerOutputFeedsTermConditionEndToEnd() {
        // Full chain: raw router output → normalizer → term condition, in one JS context.
        // A hallucinated agent name must terminate the loop.
        String script =
                "var $ = {raw: '**marketing_team**'}; "
                        + "var norm = "
                        + JavaScriptBuilder.normalizeRouterDecisionScript(AGENTS)
                        + "; "
                        + "$ = {team_loop: {iteration: 2}, team_route_norm: {result: norm}}; "
                        + "if ( $.team_loop['iteration'] < 25 && $.team_route_norm['result'] != 'DONE' ) { true; } else { false; }";
        assertThat(graalCtx.eval("js", script).asBoolean()).isFalse();
    }
}
