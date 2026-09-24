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
package org.conductoross.conductor.id;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Function;

import org.apache.logging.log4j.core.util.UuidUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.utils.IDGenerator;

import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(name = "conductor.id.generator", havingValue = "time_based")
@Slf4j
public class TimeBasedUUIDGenerator extends IDGenerator {

    private static Calendar uuidEpoch = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    private static final long epochMillis;

    private static final long TIME_DIVISOR = 10000L;

    static {
        uuidEpoch.clear();
        uuidEpoch.set(1582, 9, 15, 0, 0, 0); //
        epochMillis = uuidEpoch.getTime().getTime();
    }

    public TimeBasedUUIDGenerator() {
        log.info("Using TimeBasedUUIDGenerator to generate Ids");
    }

    @Override
    public String generate() {
        return UuidUtil.getTimeBasedUuid().toString();
    }

    public static long getDate(String idWithOrgMayBe) {
        return safelyExecute(
                id -> {
                    UUID uuid = UUID.fromString(id);
                    if (uuid.version() != 1) {
                        return 0L;
                    }
                    return (uuid.timestamp() / TIME_DIVISOR) + epochMillis;
                },
                idWithOrgMayBe);
    }

    private static <T, R> R safelyExecute(Function<T, R> function, T input) {
        try {
            return function.apply(input);
        } catch (Exception e) {
            switch (e.getClass().getSimpleName()) {
                case "IllegalArgumentException":
                    throw new NonTransientException("Invalid UUID Provided %s".formatted(input));
                case "NullPointerException":
                    throw new NonTransientException("Null UUID Provided %s".formatted(input));
                case "UnsupportedOperationException":
                    throw new NonTransientException("Unsupported UUID Version %s".formatted(input));
                default:
                    throw e;
            }
        }
    }
}
