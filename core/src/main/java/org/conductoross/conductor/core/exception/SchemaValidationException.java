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
package org.conductoross.conductor.core.exception;

import jakarta.validation.ValidationException;

/**
 * A payload did not conform to the schema attached to its definition, or the schema itself could
 * not be enforced — it carries no type, carries a type this server does not validate, or names only
 * an external reference. A reference the registry does not hold is <em>not</em> one of these: that
 * leaves the payload unchecked and increments a counter, rather than failing anything.
 *
 * <p>The two are one exception because they have one consequence: the execution fails and the
 * message says why. Neither is fixed by retrying, which is why a task whose input fails validation
 * fails terminally.
 *
 * <p>Extends {@link ValidationException} rather than {@code NonTransientException} so that a
 * deployment catching the standard Bean Validation exception around a schema check keeps working —
 * Conductor's commercial build does exactly that in its executor. Nothing here classifies retries
 * off {@code NonTransientException}; that type only steers transaction retries in the persistence
 * DAOs, which this never reaches.
 *
 * <p>{@code ValidationExceptionMapper} takes {@link jakarta.validation.ValidationException} at
 * highest precedence and answers {@code 500} for anything that is not a constraint violation, so it
 * names this type explicitly to keep the {@code 400} a bad payload deserves.
 */
public class SchemaValidationException extends ValidationException {

    public SchemaValidationException(String message) {
        super(message);
    }

    public SchemaValidationException(String message, Object... args) {
        super(String.format(message, args));
    }
}
