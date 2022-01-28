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
package com.netflix.conductor;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.ConstraintViolation;

public class TestUtils {

    public static Set<String> getConstraintViolationMessages(
            Set<ConstraintViolation<?>> constraintViolations) {
        Set<String> messages = new HashSet<>(constraintViolations.size());
        messages.addAll(
                constraintViolations.stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.toList()));
        return messages;
    }
}
