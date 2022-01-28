/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.mysql.util;

import java.util.function.Supplier;

/** Functional class to support the lazy execution of a String result. */
public class LazyToString {

    private final Supplier<String> supplier;

    /** @param supplier Supplier to execute when {@link #toString()} is called. */
    public LazyToString(Supplier<String> supplier) {
        this.supplier = supplier;
    }

    @Override
    public String toString() {
        return supplier.get();
    }
}
