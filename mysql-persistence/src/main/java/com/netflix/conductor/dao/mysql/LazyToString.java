package com.netflix.conductor.dao.mysql;


import java.util.function.Supplier;

/**
 * Functional class to support the lazy execution of a String result.
 */
class LazyToString {
    private final Supplier<String> supplier;

    /**
     * @param supplier Supplier to execute when {@link #toString()} is called.
     */
    LazyToString(Supplier<String> supplier) {
        this.supplier = supplier;
    }

    @Override
    public String toString() {
        return supplier.get();
    }
}
