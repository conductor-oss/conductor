/*
 * Copyright 2022 Netflix, Inc.
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

import java.text.ParseException;
import java.time.Duration;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.time.DateUtils;

public class DateTimeUtils {

    private static final String[] patterns =
            new String[] {"yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm z", "yyyy-MM-dd"};

    public static Duration parseDuration(String text) {
        Matcher m =
                Pattern.compile(
                                "\\s*(?:(\\d+)\\s*(?:days?|d))?"
                                        + "\\s*(?:(\\d+)\\s*(?:hours?|hrs?|h))?"
                                        + "\\s*(?:(\\d+)\\s*(?:minutes?|mins?|m))?"
                                        + "\\s*(?:(\\d+)\\s*(?:seconds?|secs?|s))?"
                                        + "\\s*",
                                Pattern.CASE_INSENSITIVE)
                        .matcher(text);
        if (!m.matches()) throw new IllegalArgumentException("Not valid duration: " + text);

        int days = (m.start(1) == -1 ? 0 : Integer.parseInt(m.group(1)));
        int hours = (m.start(2) == -1 ? 0 : Integer.parseInt(m.group(2)));
        int mins = (m.start(3) == -1 ? 0 : Integer.parseInt(m.group(3)));
        int secs = (m.start(4) == -1 ? 0 : Integer.parseInt(m.group(4)));
        return Duration.ofSeconds((days * 86400) + (hours * 60L + mins) * 60L + secs);
    }

    public static Date parseDate(String date) throws ParseException {
        return DateUtils.parseDate(date, patterns);
    }
}
