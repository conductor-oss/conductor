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

import com.netflix.conductor.core.exception.NonTransientException;

/**
 * A payload did not conform to the schema attached to its definition, or the schema itself could
 * not be enforced — it names a version the registry does not hold, carries no type, or carries a
 * type this server does not validate.
 *
 * <p>The two are one exception because they have one consequence: the execution fails and the
 * message says why. Neither is fixed by retrying, which is why a task whose input fails validation
 * fails terminally.
 */
public class SchemaValidationException extends NonTransientException {

    public SchemaValidationException(String message) {
        super(message);
    }

    public SchemaValidationException(String message, Object... args) {
        super(String.format(message, args));
    }
}
