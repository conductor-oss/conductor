package org.conductoross.conductor.common.utils;

import java.util.LinkedList;
import java.util.List;

public final class ErrorList {
    private final List<String> errors = new LinkedList<>();

    public ErrorList add(String error) {
        if (error != null && !error.isBlank()) {
            error = error.trim();

            while (error.endsWith(".")) {
                error = error.substring(0, error.length() - 1);
            }

            errors.add(error);
        }

        return this;
    }

    public ErrorList add(String message, Exception e) {
        if (e != null) {
            return this.add(message + ": " + e.getMessage());
        } else {
            return this.add(message);
        }
    }

    public String getMessage(String delim, String suffix) {
        if (errors.isEmpty()) {
            return null;
        }

        return String.join(delim, this.errors) + suffix;
    }

    public String getMessage() {
        return getMessage(", ", ".");
    }

    public boolean isEmpty() {
        return this.errors.isEmpty();
    }

    public boolean arePresent() {
        return !this.errors.isEmpty();
    }

    public static ErrorList empty() {
        return new ErrorList();
    }

    public static ErrorList singleton(String error) {
        return new ErrorList().add(error);
    }

    public static ErrorList singleton(String message, Exception e) {
        return new ErrorList().add(message, e);
    }
}
