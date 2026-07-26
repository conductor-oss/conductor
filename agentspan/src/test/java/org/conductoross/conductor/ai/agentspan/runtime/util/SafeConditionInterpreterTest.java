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
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeConditionInterpreterTest {

    private static boolean ev(String src, Map<String, Object> root) {
        return SafeConditionInterpreter.evaluate(src, root);
    }

    // ── Happy-path evaluation ──────────────────────────────────────

    @Test
    void evaluatesFieldEqualityWithStrictAndLoose() {
        Map<String, Object> r = Map.of("passed", true);
        assertThat(ev("$.passed === true", r)).isTrue();
        assertThat(ev("$.passed == true", r)).isTrue();
        assertThat(ev("$.passed === false", r)).isFalse();
    }

    @Test
    void evaluatesNumericComparisons() {
        Map<String, Object> r = Map.of("score", 0.85, "n", 42L);
        assertThat(ev("$.score > 0.5", r)).isTrue();
        assertThat(ev("$.score >= 0.85", r)).isTrue();
        assertThat(ev("$.score < 0.5", r)).isFalse();
        assertThat(ev("$.n == 42", r)).isTrue();
        assertThat(ev("$.n >= 42", r)).isTrue();
        assertThat(ev("$.n > 42", r)).isFalse();
    }

    @Test
    void evaluatesStringEqualityWithBothQuoteStyles() {
        Map<String, Object> r = Map.of("status", "ok");
        assertThat(ev("$.status === 'ok'", r)).isTrue();
        assertThat(ev("$.status === \"ok\"", r)).isTrue();
        assertThat(ev("$.status === 'fail'", r)).isFalse();
    }

    @Test
    void evaluatesBooleanCombinators() {
        Map<String, Object> r = Map.of("a", true, "b", false, "c", true);
        assertThat(ev("$.a && !$.b", r)).isTrue();
        assertThat(ev("$.a || $.b", r)).isTrue();
        assertThat(ev("$.b || (!$.b && $.a && $.c)", r)).isTrue();
        assertThat(ev("!($.a && $.c)", r)).isFalse();
    }

    @Test
    void evaluatesNestedFieldAccess() {
        Map<String, Object> r =
                Map.of("result", Map.of("report", Map.of("word_count", 1240L, "passed", true)));
        assertThat(ev("$.result.report.passed === true", r)).isTrue();
        assertThat(ev("$.result.report.word_count >= 1000", r)).isTrue();
        assertThat(ev("$.result.report.word_count > 5000", r)).isFalse();
    }

    @Test
    void evaluatesBracketSubscriptForNonIdentifierKeys() {
        Map<String, Object> r = Map.of("nested-key", 7L, "with space", "x");
        assertThat(ev("$['nested-key'] == 7", r)).isTrue();
        assertThat(ev("$[\"with space\"] === 'x'", r)).isTrue();
    }

    @Test
    void evaluatesParenthesisedPrecedence() {
        Map<String, Object> r = Map.of("a", true, "b", false, "c", false);
        // Without parens, && binds tighter than ||, so a||b&&c == a||(b&&c) == true.
        assertThat(ev("$.a || $.b && $.c", r)).isTrue();
        // With explicit grouping that forces b||c to be evaluated first, then &&'d with a.
        assertThat(ev("$.a && ($.b || $.c)", r)).isFalse();
    }

    @Test
    void rootEvaluatesToWholeMap() {
        Map<String, Object> r = Map.of("k", 1L);
        assertThat(SafeConditionInterpreter.parse("$").eval(r)).isEqualTo(r);
    }

    @Test
    void evaluatesNullChecks() {
        Map<String, Object> r = new java.util.HashMap<>();
        r.put("maybe", null);
        assertThat(ev("$.maybe === null", r)).isTrue();
        assertThat(ev("$.absent === null", r)).isTrue();
        assertThat(ev("$.absent === 'something'", r)).isFalse();
    }

    // ── Reject the JS-injection vectors the regex denylist was for ─

    @Test
    void constructorAndProtoAccessAreInertJavaMapLookups() {
        // In the GraalJS world ``$.x.constructor`` / ``$.x.__proto__`` were
        // the prototype-pollution attack vectors that needed denylisting.
        // In Java they're just ``Map.get("constructor")`` / ``Map.get("__proto__")``
        // — there is no live prototype chain to escape into. Parse + eval
        // both succeed; the result is null (key not present in the map),
        // and downstream comparisons treat null as falsy. Documenting this
        // explicitly so a future reader doesn't reintroduce a regex
        // denylist "just in case".
        Map<String, Object> r = Map.of("x", Map.of("k", 1L));
        assertThat(SafeConditionInterpreter.isSafe("$.x.constructor")).isTrue();
        assertThat(SafeConditionInterpreter.isSafe("$.x.__proto__")).isTrue();
        assertThat(ev("$.x.constructor === null", r)).isTrue();
        assertThat(ev("$.x.__proto__ === null", r)).isTrue();
    }

    @Test
    void rejectsComputedPropertyByExpression() {
        // $['c' + 'onstructor'] is the canonical regex-denylist-bypass —
        // subscript MUST be a string or number literal, not an expression.
        assertThatThrownBy(() -> SafeConditionInterpreter.parse("$['c' + 'onstructor']"))
                .isInstanceOf(SafeConditionParseException.class);
    }

    @Test
    void rejectsFunctionCalls() {
        assertThatThrownBy(() -> SafeConditionInterpreter.parse("eval('1')"))
                .isInstanceOf(SafeConditionParseException.class);
        assertThatThrownBy(() -> SafeConditionInterpreter.parse("Function('x')"))
                .isInstanceOf(SafeConditionParseException.class);
        assertThatThrownBy(() -> SafeConditionInterpreter.parse("$.x.toString()"))
                .isInstanceOf(SafeConditionParseException.class);
    }

    @Test
    void rejectsAssignment() {
        assertThatThrownBy(() -> SafeConditionInterpreter.parse("$.x = 1"))
                .isInstanceOf(SafeConditionParseException.class);
    }

    @Test
    void rejectsBareIdentifierThatIsntALiteral() {
        // Free identifiers (`bar`, `globalThis`) aren't grammar-legal — only
        // ``true``/``false``/``null`` plus ``$``-rooted field paths.
        assertThatThrownBy(() -> SafeConditionInterpreter.parse("globalThis"))
                .isInstanceOf(SafeConditionParseException.class);
    }

    @Test
    void rejectsRegexLiteral() {
        // /pattern/.test(x) would be how an attacker injects a regex DoS.
        // Grammar has no regex literal, so the slash is a parse error.
        assertThatThrownBy(() -> SafeConditionInterpreter.parse("$.x === /abc/"))
                .isInstanceOf(SafeConditionParseException.class);
    }

    @Test
    void rejectsTrailingContent() {
        // ``$.a; do_something_else()`` would let a multi-statement payload
        // sneak in past a parser that ignored trailing input. Our parser
        // calls ``expectEnd`` after parseExpr.
        assertThatThrownBy(() -> SafeConditionInterpreter.parse("$.a; bad"))
                .isInstanceOf(SafeConditionParseException.class);
    }

    @Test
    void rejectsOverlongExpression() {
        String big = "$.a" + "==1||".repeat(1000);
        assertThatThrownBy(() -> SafeConditionInterpreter.parse(big))
                .isInstanceOf(SafeConditionParseException.class)
                .hasMessageContaining("exceeds");
    }

    // ── Whitelist sanity — these are conditions plans actually use ─

    @Test
    void acceptsRealWorldValidationConditions() {
        // Conditions drawn from the spec doc + the binsearch/AML/portfolio
        // examples. They MUST all parse cleanly.
        for (String cond :
                List.of(
                        "$.passed === true",
                        "$.exit_code === 0",
                        "$.score > 0.5 && $.confidence >= 0.8",
                        "$.violations.length === 0 || $.allow_partial === true",
                        "$.status !== 'failed' && $.retries < 3",
                        "$.value >= 100 && $.value <= 1000")) {
            assertThat(SafeConditionInterpreter.isSafe(cond)).as("must accept: %s", cond).isTrue();
        }
    }

    // ── isSafe shortcut ────────────────────────────────────────────

    @Test
    void isSafeReportsBooleanWithoutThrowing() {
        assertThat(SafeConditionInterpreter.isSafe("$.x === 1")).isTrue();
        assertThat(SafeConditionInterpreter.isSafe("$['c' + 'onstructor']")).isFalse();
    }

    // ── Edge cases in field access ─────────────────────────────────

    @Test
    void fieldAccessReturnsNullForMissingPath() {
        Map<String, Object> r = Map.of("a", Map.of("b", 1L));
        // Walking into a non-existent key returns null; downstream === null check works.
        assertThat(ev("$.a.b.c === null", r)).isTrue();
        assertThat(ev("$.absent.x === null", r)).isTrue();
    }

    @Test
    void notEqualWorksAcrossTypes() {
        Map<String, Object> r = Map.of("x", "5");
        assertThat(ev("$.x !== 5", r)).isTrue(); // strict: string != number
        assertThat(ev("$.x != 5", r)).isFalse(); // loose: numeric coercion
    }

    @Test
    void numericComparisonReturnsFalseOnNonNumericOperands() {
        // /dg #8: cmpNumeric throws ArithmeticException on non-numeric ops
        // — used to abort the whole INLINE because evaluate() didn't catch.
        // Now matches JS semantics: NaN-comparison is always false. Pins
        // every relational operator individually so the regression catches
        // a one-arm slip.
        Map<String, Object> r = Map.of("a", "foo", "b", "bar", "n", 5L);
        assertThat(ev("$.a < $.b", r)).isFalse();
        assertThat(ev("$.a <= $.b", r)).isFalse();
        assertThat(ev("$.a > $.b", r)).isFalse();
        assertThat(ev("$.a >= $.b", r)).isFalse();
        // Mixed: one numeric, one not — also NaN, also false on both sides.
        assertThat(ev("$.a < $.n", r)).isFalse();
        assertThat(ev("$.n < $.a", r)).isFalse();
        // Sanity: real numeric comparisons still work after the fix.
        assertThat(ev("$.n > 3", r)).isTrue();
        assertThat(ev("$.n < 3", r)).isFalse();
    }
}
