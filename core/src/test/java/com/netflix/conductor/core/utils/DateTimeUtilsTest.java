/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.core.utils;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.netflix.conductor.core.utils.DateTimeUtils.parseDuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DateTimeUtilsTest {

    private static Stream<Arguments> validDurations() {
        return Stream.of(
                Arguments.of("", Duration.ofSeconds(0)),
                Arguments.of("5s", Duration.ofSeconds(5)),
                Arguments.of("5secs", Duration.ofSeconds(5)),
                Arguments.of("5seconds", Duration.ofSeconds(5)),
                Arguments.of("5m", Duration.ofMinutes(5)),
                Arguments.of("5mins", Duration.ofMinutes(5)),
                Arguments.of("5minutes", Duration.ofMinutes(5)),
                Arguments.of("5h", Duration.ofHours(5)),
                Arguments.of("5hrs", Duration.ofHours(5)),
                Arguments.of("5hours", Duration.ofHours(5)),
                Arguments.of("5d", Duration.ofDays(5)),
                Arguments.of("5days", Duration.ofDays(5)),
                Arguments.of("5m 5s", Duration.ofSeconds(5 * 60 + 5)),
                Arguments.of("5h 5m 5s", Duration.ofSeconds(5 * 60 * 60 + 5 * 60 + 5)),
                Arguments.of(
                        "5d 5h 5m 5s",
                        Duration.ofSeconds(5 * 24 * 60 * 60 + 5 * 60 * 60 + 5 * 60 + 5)),
                Arguments.of("5S", Duration.ofSeconds(5)),
                Arguments.of("5SECS", Duration.ofSeconds(5)),
                Arguments.of("5SECONDS", Duration.ofSeconds(5)),
                Arguments.of("5M", Duration.ofMinutes(5)),
                Arguments.of("5MINS", Duration.ofMinutes(5)),
                Arguments.of("5MINUTES", Duration.ofMinutes(5)),
                Arguments.of("5H", Duration.ofHours(5)),
                Arguments.of("5HRS", Duration.ofHours(5)),
                Arguments.of("5HOURS", Duration.ofHours(5)),
                Arguments.of("5D", Duration.ofDays(5)),
                Arguments.of("5DAYS", Duration.ofDays(5)),
                Arguments.of("5M 5S", Duration.ofSeconds(5 * 60 + 5)),
                Arguments.of("5H 5M 5S", Duration.ofSeconds(5 * 60 * 60 + 5 * 60 + 5)),
                Arguments.of(
                        "5D 5H 5M 5S",
                        Duration.ofSeconds(5 * 24 * 60 * 60 + 5 * 60 * 60 + 5 * 60 + 5)));
    }

    @ParameterizedTest(name = "[{0}] is valid duration")
    @MethodSource("validDurations")
    public void shouldParseDuration(String input, Duration expectedDuration) {
        assertThat(parseDuration(input)).isEqualTo(expectedDuration);
    }

    @ParameterizedTest(name = "[{0}] is invalid duration")
    @ValueSource(
            strings = {
                "5",
                "s",
                "secs",
                "seconds",
                "m",
                "mins",
                "minutes",
                "h",
                "hours",
                "d",
                "days",
                "5.0s",
                "5.0secs",
                "5.0seconds",
                "5.0m",
                "5.0mins",
                "5.0minutes",
                "5.0h",
                "5.0hrs",
                "5.0hours",
                "5.0d",
                "5.0days",
                "5.0m 5s",
                "5.0h 5m 5s",
                "5.0d 5h 5m 5s",
            })
    public void shouldValidateDuration(String input) {
        assertThatThrownBy(() -> parseDuration(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Not valid duration: " + input);
    }
}
