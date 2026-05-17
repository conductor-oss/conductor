/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.netflix.conductor.benchmark;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scripts and inputs used by {@link ScriptEngineBenchmark}. All scripts use ES5/ES6 syntax that
 * works on Rhino, GraalJS, and V8 without modification. No async/await, no optional chaining,
 * no nullish coalescing.
 */
final class BenchmarkScripts {

    private BenchmarkScripts() {}

    static final List<Case> CASES = new ArrayList<>();

    /** ~80-line aggregation pipeline used by cases 10 and 11. Declared before static init. */
    private static final String COMPLEX_ORDERS_SCRIPT = buildComplexOrdersScript();

    static {
        // 1. Trivial boolean expression
        CASES.add(
                new Case(
                        "01_trivial_boolean",
                        "$.value > 0",
                        smallNumericInput()));

        // 2. Simple arithmetic on three fields
        CASES.add(
                new Case(
                        "02_simple_arith",
                        "($.a + $.b) * $.c - $.d / 2",
                        smallNumericInput()));

        // 3. Deep nested property access
        CASES.add(
                new Case(
                        "03_nested_property_access",
                        "$.user.profile.address.city + ', ' + $.user.profile.address.country",
                        userInput()));

        // 4. Conditional returning a small object
        CASES.add(
                new Case(
                        "04_conditional_object_return",
                        "(function() {\n"
                                + "  if ($.value > 100) {\n"
                                + "    return { tier: 'high', score: $.value * 2 };\n"
                                + "  } else if ($.value > 10) {\n"
                                + "    return { tier: 'mid', score: $.value };\n"
                                + "  } else {\n"
                                + "    return { tier: 'low', score: 0 };\n"
                                + "  }\n"
                                + "})()",
                        smallNumericInput()));

        // 5. Array filter + count over 50 items
        CASES.add(
                new Case(
                        "05_array_filter_count_50",
                        "$.items.filter(function(i) { return i.active && i.value > 50; }).length",
                        itemsInput(50)));

        // 6. Array map over 50 items
        CASES.add(
                new Case(
                        "06_array_map_50",
                        "$.items.map(function(i) { return { id: i.id, doubled: i.value * 2, label: i.name + '-x' }; })",
                        itemsInput(50)));

        // 7. Array reduce sum over 1000 items
        CASES.add(
                new Case(
                        "07_array_reduce_sum_1000",
                        "$.items.reduce(function(acc, i) { return acc + (i.active ? i.value : 0); }, 0)",
                        itemsInput(1000)));

        // 8. String manipulation
        CASES.add(
                new Case(
                        "08_string_manipulation",
                        "(function() {\n"
                                + "  var s = $.user.firstName + ' ' + $.user.lastName;\n"
                                + "  var upper = s.toUpperCase();\n"
                                + "  var parts = upper.split(' ');\n"
                                + "  return parts[0].slice(0, 3) + '_' + parts[1].slice(0, 3);\n"
                                + "})()",
                        userInput()));

        // 9. Nested loop / for-in aggregation
        CASES.add(
                new Case(
                        "09_nested_loop_aggregation",
                        "(function() {\n"
                                + "  var result = {};\n"
                                + "  for (var i = 0; i < $.items.length; i++) {\n"
                                + "    var it = $.items[i];\n"
                                + "    if (!result[it.category]) {\n"
                                + "      result[it.category] = { count: 0, sum: 0 };\n"
                                + "    }\n"
                                + "    result[it.category].count++;\n"
                                + "    result[it.category].sum += it.value;\n"
                                + "  }\n"
                                + "  return result;\n"
                                + "})()",
                        itemsInput(500)));

        // 10. Complex pipeline (~80 lines) processing 500 orders
        CASES.add(
                new Case(
                        "10_complex_orders_pipeline_500",
                        COMPLEX_ORDERS_SCRIPT,
                        ordersInput(500)));

        // 11. Same complex pipeline but on 5000 orders (stress test)
        CASES.add(
                new Case(
                        "11_complex_orders_pipeline_5000",
                        COMPLEX_ORDERS_SCRIPT,
                        ordersInput(5000)));

        // 12. JSON-like deep transform (immutability-friendly shape)
        CASES.add(
                new Case(
                        "12_deep_transform",
                        "(function() {\n"
                                + "  var out = [];\n"
                                + "  for (var i = 0; i < $.items.length; i++) {\n"
                                + "    var it = $.items[i];\n"
                                + "    out.push({\n"
                                + "      id: it.id,\n"
                                + "      name: it.name.toUpperCase(),\n"
                                + "      meta: { active: it.active, score: it.value * (it.active ? 1.1 : 0.5) },\n"
                                + "      tags: it.tags.filter(function(t) { return t.length > 2; })\n"
                                + "    });\n"
                                + "  }\n"
                                + "  return out;\n"
                                + "})()",
                        itemsInput(200)));
    }

    private static String buildComplexOrdersScript() {
        return "(function() {\n"
                    + "  var orders = $.orders;\n"
                    + "  var summary = {\n"
                    + "    total: 0,\n"
                    + "    count: 0,\n"
                    + "    byCategory: {},\n"
                    + "    byCustomer: {},\n"
                    + "    bySource: {},\n"
                    + "    flaggedOrders: [],\n"
                    + "    avgOrder: 0,\n"
                    + "    maxOrder: 0,\n"
                    + "    minOrder: Number.MAX_VALUE\n"
                    + "  };\n"
                    + "  for (var i = 0; i < orders.length; i++) {\n"
                    + "    var o = orders[i];\n"
                    + "    var amount = 0;\n"
                    + "    for (var j = 0; j < o.lineItems.length; j++) {\n"
                    + "      var li = o.lineItems[j];\n"
                    + "      amount += li.price * li.qty * (1 - (li.discount || 0));\n"
                    + "    }\n"
                    + "    o.computedAmount = amount;\n"
                    + "    summary.total += amount;\n"
                    + "    summary.count++;\n"
                    + "    if (amount > summary.maxOrder) summary.maxOrder = amount;\n"
                    + "    if (amount < summary.minOrder) summary.minOrder = amount;\n"
                    + "    if (!summary.byCategory[o.category]) {\n"
                    + "      summary.byCategory[o.category] = { count: 0, sum: 0, items: 0 };\n"
                    + "    }\n"
                    + "    var cat = summary.byCategory[o.category];\n"
                    + "    cat.count++;\n"
                    + "    cat.sum += amount;\n"
                    + "    cat.items += o.lineItems.length;\n"
                    + "    if (!summary.byCustomer[o.customerId]) {\n"
                    + "      summary.byCustomer[o.customerId] = { count: 0, sum: 0, orders: [] };\n"
                    + "    }\n"
                    + "    var cust = summary.byCustomer[o.customerId];\n"
                    + "    cust.count++;\n"
                    + "    cust.sum += amount;\n"
                    + "    cust.orders.push(o.id);\n"
                    + "    if (!summary.bySource[o.source]) {\n"
                    + "      summary.bySource[o.source] = 0;\n"
                    + "    }\n"
                    + "    summary.bySource[o.source]++;\n"
                    + "    if (amount > 1000 || (o.flags && o.flags.indexOf('priority') >= 0)) {\n"
                    + "      summary.flaggedOrders.push({\n"
                    + "        id: o.id,\n"
                    + "        amount: amount,\n"
                    + "        category: o.category,\n"
                    + "        customerId: o.customerId,\n"
                    + "        reasons: amount > 1000 ? ['high_value'] : ['priority']\n"
                    + "      });\n"
                    + "    }\n"
                    + "  }\n"
                    + "  summary.avgOrder = summary.count > 0 ? summary.total / summary.count : 0;\n"
                    + "  var categories = [];\n"
                    + "  for (var k in summary.byCategory) {\n"
                    + "    var c = summary.byCategory[k];\n"
                    + "    c.avg = c.sum / c.count;\n"
                    + "    categories.push({ name: k, avg: c.avg, sum: c.sum, count: c.count });\n"
                    + "  }\n"
                    + "  categories.sort(function(a, b) { return b.sum - a.sum; });\n"
                    + "  summary.topCategories = categories.slice(0, 5);\n"
                    + "  var customers = [];\n"
                    + "  for (var ck in summary.byCustomer) {\n"
                    + "    var cu = summary.byCustomer[ck];\n"
                    + "    customers.push({ id: ck, sum: cu.sum, count: cu.count });\n"
                    + "  }\n"
                    + "  customers.sort(function(a, b) { return b.sum - a.sum; });\n"
                    + "  summary.topCustomers = customers.slice(0, 10);\n"
                    + "  return summary;\n"
                    + "})()";
    }

    // ---------- Input builders ----------

    private static Map<String, Object> smallNumericInput() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", 42);
        m.put("a", 10);
        m.put("b", 20);
        m.put("c", 30);
        m.put("d", 8);
        return m;
    }

    private static Map<String, Object> userInput() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("street", "1 Market St");
        address.put("city", "San Francisco");
        address.put("country", "USA");
        address.put("zip", "94105");

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("age", 34);
        profile.put("address", address);
        profile.put("verified", true);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("firstName", "Jane");
        user.put("lastName", "Doe");
        user.put("profile", profile);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("user", user);
        return input;
    }

    private static Map<String, Object> itemsInput(int n) {
        List<Map<String, Object>> items = new ArrayList<>(n);
        String[] categories = {"alpha", "beta", "gamma", "delta", "epsilon"};
        String[] tagPool = {"hot", "new", "sale", "x", "a", "exclusive", "limited", "fresh"};
        for (int i = 0; i < n; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "item-" + i);
            item.put("name", "Product " + i);
            item.put("category", categories[i % categories.length]);
            item.put("value", (i * 7) % 200);
            item.put("active", i % 3 != 0);
            List<String> tags = new ArrayList<>(3);
            tags.add(tagPool[i % tagPool.length]);
            tags.add(tagPool[(i + 2) % tagPool.length]);
            tags.add(tagPool[(i + 5) % tagPool.length]);
            item.put("tags", tags);
            items.add(item);
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("items", items);
        return input;
    }

    private static Map<String, Object> ordersInput(int n) {
        List<Map<String, Object>> orders = new ArrayList<>(n);
        String[] categories = {"electronics", "books", "clothing", "home", "sports", "toys"};
        String[] sources = {"web", "mobile", "in-store", "phone", "api"};
        String[] flagPool = {"priority", "wholesale", "gift", "fragile"};
        for (int i = 0; i < n; i++) {
            Map<String, Object> order = new LinkedHashMap<>();
            order.put("id", "ord-" + i);
            order.put("customerId", "cust-" + (i % 200));
            order.put("category", categories[i % categories.length]);
            order.put("source", sources[i % sources.length]);
            int lineCount = 1 + (i % 8);
            List<Map<String, Object>> lines = new ArrayList<>(lineCount);
            for (int j = 0; j < lineCount; j++) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("sku", "sku-" + (i * 10 + j));
                line.put("price", 5.0 + ((i + j) % 100));
                line.put("qty", 1 + ((i + j) % 4));
                line.put("discount", ((i + j) % 5 == 0) ? 0.1 : 0.0);
                lines.add(line);
            }
            order.put("lineItems", lines);
            if (i % 10 == 0) {
                List<String> flags = new ArrayList<>();
                flags.add(flagPool[i % flagPool.length]);
                order.put("flags", flags);
            }
            orders.add(order);
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("orders", orders);
        return input;
    }

    /** Benchmark case: one script + one input. */
    static final class Case {
        final String name;
        final String script;
        final Map<String, Object> input;

        Case(String name, String script, Map<String, Object> input) {
            this.name = name;
            this.script = script;
            this.input = input;
        }
    }
}
