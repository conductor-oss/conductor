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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java parser + interpreter for plan-supplied {@code success_condition} expressions.
 *
 * <p>Closes the dg-review F14 finding: the previous design ran user-supplied JS through a regex
 * denylist and then handed it to GraalJS. Two layers, one threat model, one component — not defence
 * in depth. This interpreter replaces both halves with a deterministic Java pipeline:
 *
 * <ol>
 *   <li>{@link #parse(String)} tokenises + recursive-descent-parses the expression into an AST
 *       whose node types are drawn from a fixed whitelist ({@link OrNode}, {@link AndNode}, {@link
 *       NotNode}, {@link CmpNode}, {@link FieldAccess}, {@link Literal}). The parser
 *       <em>cannot</em> emit a node type outside that whitelist — no function calls, no property
 *       assignment, no computed-property-by-string access, no eval-equivalents.
 *   <li>{@link #evaluate(String, Map)} walks the AST against a root map (the parsed tool output).
 *       Pure Java; GraalJS never touches the expression at runtime.
 * </ol>
 *
 * <h2>Grammar (Draft-style)</h2>
 *
 * <pre>{@code
 * expr      := orExpr
 * orExpr    := andExpr ('||' andExpr)*
 * andExpr   := notExpr ('&&' notExpr)*
 * notExpr   := '!' notExpr | cmp
 * cmp       := atom (cmpOp atom)?
 * cmpOp     := '==' | '!=' | '===' | '!==' | '<' | '<=' | '>' | '>='
 * atom      := field | literal | '(' expr ')'
 * field     := '$' ('.' ident | '[' literal ']')*
 * literal   := number | string | 'true' | 'false' | 'null'
 * ident     := [a-zA-Z_][a-zA-Z0-9_]*
 * number    := -?[0-9]+ ('.' [0-9]+)?
 * string    := '"' [^"]* '"' | "'" [^']* "'"
 * }</pre>
 *
 * <p>What's <em>not</em> in the grammar (by design):
 *
 * <ul>
 *   <li>Function calls, method calls, property writes.
 *   <li>Computed property access via a string expression ({@code $['c' + 'onstructor']}).
 *   <li>Template literals, regex literals.
 *   <li>{@code constructor}, {@code __proto__}, {@code Function}, {@code eval}.
 * </ul>
 *
 * <p>If those ever need to be supported, extending the grammar requires adding a new node type with
 * a {@link Node#eval(Map)} implementation — the addition is auditable in code review rather than
 * silently re-opened by widening a regex.
 */
public final class SafeConditionInterpreter {

    private SafeConditionInterpreter() {}

    /** Maximum source length accepted by the parser. */
    public static final int MAX_LENGTH = 1024;

    // ── Public API ─────────────────────────────────────────────────

    /** Parse a condition into an AST. Throws on syntax errors. */
    public static Node parse(String src) {
        if (src == null) throw new SafeConditionParseException("null condition");
        if (src.length() > MAX_LENGTH) {
            throw new SafeConditionParseException(
                    "condition exceeds " + MAX_LENGTH + " characters");
        }
        Parser p = new Parser(src);
        Node ast = p.parseExpr();
        p.expectEnd();
        return ast;
    }

    /** Evaluate the parsed condition against a root map. */
    public static boolean evaluate(String src, Map<String, Object> root) {
        return truthy(parse(src).eval(root != null ? root : Map.of()));
    }

    /** True if and only if {@link #parse(String)} would accept this string. */
    public static boolean isSafe(String src) {
        try {
            parse(src);
            return true;
        } catch (SafeConditionParseException e) {
            return false;
        }
    }

    // ── AST ────────────────────────────────────────────────────────

    public sealed interface Node permits OrNode, AndNode, NotNode, CmpNode, FieldAccess, Literal {
        Object eval(Map<String, Object> root);
    }

    public record OrNode(List<Node> parts) implements Node {
        @Override
        public Object eval(Map<String, Object> root) {
            for (Node n : parts) {
                if (truthy(n.eval(root))) return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
    }

    public record AndNode(List<Node> parts) implements Node {
        @Override
        public Object eval(Map<String, Object> root) {
            for (Node n : parts) {
                if (!truthy(n.eval(root))) return Boolean.FALSE;
            }
            return Boolean.TRUE;
        }
    }

    public record NotNode(Node inner) implements Node {
        @Override
        public Object eval(Map<String, Object> root) {
            return !truthy(inner.eval(root));
        }
    }

    public record CmpNode(Node lhs, String op, Node rhs) implements Node {
        @Override
        public Object eval(Map<String, Object> root) {
            Object l = lhs.eval(root);
            Object r = rhs.eval(root);
            switch (op) {
                case "==":
                case "===":
                    return looseEquals(l, r, "===".equals(op));
                case "!=":
                case "!==":
                    return !looseEquals(l, r, "!==".equals(op));
                case "<":
                case "<=":
                case ">":
                case ">=":
                    // /dg #8: cmpNumeric throws ArithmeticException on non-
                    // numeric operands; the comment at its throw site says the
                    // intent is to let ``evaluate()`` default to false. But
                    // ``evaluate()`` didn't catch, so a single non-numeric
                    // comparison aborted the whole INLINE. Match JS semantics:
                    // NaN-comparison is always false.
                    try {
                        int c = cmpNumeric(l, r);
                        switch (op) {
                            case "<":
                                return c < 0;
                            case "<=":
                                return c <= 0;
                            case ">":
                                return c > 0;
                            default:
                                return c >= 0;
                        }
                    } catch (ArithmeticException ignored) {
                        return Boolean.FALSE;
                    }
                default:
                    throw new SafeConditionParseException("unknown comparator: " + op);
            }
        }
    }

    /**
     * ``$`` (returns whole root map) or ``$.foo.bar`` or ``$['key']``. Walks down a fixed sequence
     * of accessors only — no computed property access from a runtime string expression, so
     * prototype-pollution paths like ``$['c' + 'onstructor']`` are syntactically impossible.
     */
    public record FieldAccess(List<String> path) implements Node {
        @Override
        public Object eval(Map<String, Object> root) {
            Object cur = root;
            for (String key : path) {
                if (cur == null) return null;
                if (cur instanceof Map<?, ?> m) {
                    cur = m.get(key);
                } else {
                    return null;
                }
            }
            return cur;
        }
    }

    public record Literal(Object value) implements Node {
        @Override
        public Object eval(Map<String, Object> root) {
            return value;
        }
    }

    // ── Truthiness + comparison helpers ────────────────────────────

    static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0.0;
        if (v instanceof String s) return !s.isEmpty();
        if (v instanceof Map<?, ?> m) return !m.isEmpty();
        if (v instanceof List<?> l) return !l.isEmpty();
        return true;
    }

    private static boolean looseEquals(Object a, Object b, boolean strict) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number an && b instanceof Number bn) {
            return Double.compare(an.doubleValue(), bn.doubleValue()) == 0;
        }
        if (a instanceof Boolean && b instanceof Boolean) return a.equals(b);
        if (a instanceof String && b instanceof String) return a.equals(b);
        if (strict) {
            // Strict equality requires same type; primitive cases handled above.
            return false;
        }
        // Loose: coerce strings and numbers.
        if (a instanceof Number an && b instanceof String bs) {
            try {
                return Double.compare(an.doubleValue(), Double.parseDouble(bs)) == 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        if (a instanceof String as && b instanceof Number bn) {
            try {
                return Double.compare(Double.parseDouble(as), bn.doubleValue()) == 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return a.equals(b);
    }

    private static int cmpNumeric(Object a, Object b) {
        double da = numericOrNaN(a);
        double db = numericOrNaN(b);
        if (Double.isNaN(da) || Double.isNaN(db)) {
            // JS-style NaN comparisons always yield false. Encode that by
            // returning a sentinel that's neither <, =, nor > 0 in the
            // caller's checks. Since we use < / <= / > / >=, returning a
            // strictly non-zero number that the caller's particular
            // comparison treats as false is what we want — easier: throw
            // and let evaluate() default to false.
            throw new ArithmeticException("non-numeric comparison: " + a + " vs " + b);
        }
        return Double.compare(da, db);
    }

    private static double numericOrNaN(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return Double.NaN;
            }
        }
        if (v instanceof Boolean b) return b ? 1.0 : 0.0;
        return Double.NaN;
    }

    // ── Parser ─────────────────────────────────────────────────────

    static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
            this.pos = 0;
        }

        Node parseExpr() {
            return parseOr();
        }

        private Node parseOr() {
            Node first = parseAnd();
            List<Node> parts = null;
            while (peek("||")) {
                if (parts == null) {
                    parts = new ArrayList<>();
                    parts.add(first);
                }
                consume("||");
                parts.add(parseAnd());
            }
            return parts == null ? first : new OrNode(parts);
        }

        private Node parseAnd() {
            Node first = parseNot();
            List<Node> parts = null;
            while (peek("&&")) {
                if (parts == null) {
                    parts = new ArrayList<>();
                    parts.add(first);
                }
                consume("&&");
                parts.add(parseNot());
            }
            return parts == null ? first : new AndNode(parts);
        }

        private Node parseNot() {
            skipWs();
            if (peek("!") && !peek("!=") && !peek("!==")) {
                consume("!");
                return new NotNode(parseNot());
            }
            return parseCmp();
        }

        private Node parseCmp() {
            Node lhs = parseAtom();
            skipWs();
            // longest-match: 3-char ops before 2-char before 1-char.
            for (String op : new String[] {"===", "!==", "==", "!=", "<=", ">=", "<", ">"}) {
                if (peek(op)) {
                    consume(op);
                    Node rhs = parseAtom();
                    return new CmpNode(lhs, op, rhs);
                }
            }
            return lhs;
        }

        private Node parseAtom() {
            skipWs();
            if (pos >= src.length()) throw err("unexpected end of expression");
            char c = src.charAt(pos);
            if (c == '(') {
                consume("(");
                Node inner = parseExpr();
                skipWs();
                if (!peek(")")) throw err("missing ')'");
                consume(")");
                return inner;
            }
            if (c == '$') {
                return parseField();
            }
            if (c == '"' || c == '\'') {
                return parseString();
            }
            if (c == '-' || isDigit(c)) {
                return parseNumber();
            }
            if (isIdentStart(c)) {
                return parseKeywordOrIdent();
            }
            throw err("unexpected character '" + c + "'");
        }

        private Node parseField() {
            consume("$");
            List<String> path = new ArrayList<>();
            while (true) {
                skipWs();
                if (peek(".")) {
                    consume(".");
                    String name = readIdent();
                    if (name.isEmpty()) throw err("expected identifier after '.'");
                    path.add(name);
                } else if (peek("[")) {
                    consume("[");
                    skipWs();
                    String key;
                    if (pos < src.length() && (src.charAt(pos) == '"' || src.charAt(pos) == '\'')) {
                        Node lit = parseString();
                        key = String.valueOf(((Literal) lit).value());
                    } else if (pos < src.length() && isDigit(src.charAt(pos))) {
                        Node lit = parseNumber();
                        key = String.valueOf(((Literal) lit).value());
                    } else {
                        throw err("subscript must be a string or number literal");
                    }
                    skipWs();
                    if (!peek("]")) throw err("missing ']'");
                    consume("]");
                    path.add(key);
                } else {
                    break;
                }
            }
            return new FieldAccess(path);
        }

        private Node parseString() {
            char quote = src.charAt(pos);
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < src.length() && src.charAt(pos) != quote) {
                char c = src.charAt(pos);
                if (c == '\\' && pos + 1 < src.length()) {
                    char nxt = src.charAt(pos + 1);
                    if (nxt == quote || nxt == '\\') {
                        sb.append(nxt);
                        pos += 2;
                        continue;
                    }
                    if (nxt == 'n') {
                        sb.append('\n');
                        pos += 2;
                        continue;
                    }
                    if (nxt == 't') {
                        sb.append('\t');
                        pos += 2;
                        continue;
                    }
                }
                sb.append(c);
                pos++;
            }
            if (pos >= src.length()) throw err("unterminated string literal");
            pos++; // closing quote
            return new Literal(sb.toString());
        }

        private Node parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') pos++;
            while (pos < src.length() && isDigit(src.charAt(pos))) pos++;
            if (pos < src.length() && src.charAt(pos) == '.') {
                pos++;
                while (pos < src.length() && isDigit(src.charAt(pos))) pos++;
            }
            String s = src.substring(start, pos);
            if (s.isEmpty() || "-".equals(s)) throw err("bad numeric literal");
            // Prefer integer when there's no decimal part.
            if (s.indexOf('.') >= 0) return new Literal(Double.parseDouble(s));
            try {
                return new Literal(Long.parseLong(s));
            } catch (NumberFormatException nfe) {
                return new Literal(Double.parseDouble(s));
            }
        }

        private Node parseKeywordOrIdent() {
            String id = readIdent();
            switch (id) {
                case "true":
                    return new Literal(Boolean.TRUE);
                case "false":
                    return new Literal(Boolean.FALSE);
                case "null":
                    return new Literal(null);
                default:
                    throw err(
                            "unknown identifier: '"
                                    + id
                                    + "' — only $.field paths and "
                                    + "true/false/null literals are allowed");
            }
        }

        private String readIdent() {
            int start = pos;
            if (pos < src.length() && isIdentStart(src.charAt(pos))) {
                pos++;
                while (pos < src.length() && isIdentPart(src.charAt(pos))) {
                    pos++;
                }
            }
            return src.substring(start, pos);
        }

        private void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        private boolean peek(String token) {
            skipWs();
            int len = token.length();
            return pos + len <= src.length() && src.regionMatches(pos, token, 0, len);
        }

        private void consume(String token) {
            skipWs();
            int len = token.length();
            if (pos + len > src.length() || !src.regionMatches(pos, token, 0, len)) {
                throw err("expected '" + token + "'");
            }
            pos += len;
        }

        void expectEnd() {
            skipWs();
            if (pos != src.length()) {
                throw err("unexpected trailing input: '" + src.substring(pos) + "'");
            }
        }

        private SafeConditionParseException err(String msg) {
            return new SafeConditionParseException(
                    msg + " at position " + pos + " in: " + summarise(src));
        }

        private static String summarise(String s) {
            if (s.length() <= 80) return s;
            return s.substring(0, 77) + "…";
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || isDigit(c);
    }

    /** Friendly wrapper around the field-access map root. */
    public static Map<String, Object> root(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                r.put(String.valueOf(e.getKey()), e.getValue());
            }
            return r;
        }
        return Map.of();
    }
}
