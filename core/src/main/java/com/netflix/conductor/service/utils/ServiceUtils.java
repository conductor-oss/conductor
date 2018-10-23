/*
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.service.utils;

import com.google.common.base.Preconditions;
import com.netflix.conductor.core.execution.ApplicationException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ServiceUtils {

    /**
     * Split string with "|" as delimiter.
     * @param inputStr Input string
     * @return List of String
     */
    public static List<String> convertStringToList(String inputStr) {
        List<String> list = new ArrayList<String>();
        if (StringUtils.isNotBlank(inputStr)) {
            list = Arrays.asList(inputStr.split("\\|"));
        }
        return list;
    }

    /**
     * Ensures the truth of an condition involving one or more parameters to the calling method.
     *
     * @param condition a boolean expression
     * @param errorMessage The exception message use if the input condition is not valid
     * @throws com.netflix.conductor.core.execution.ApplicationException if input condition is not valid
     */
    public static void checkArgument(boolean condition, String errorMessage){
        if(!condition) {
            throw new ApplicationException(ApplicationException.Code.INVALID_INPUT, errorMessage);
        }
    }

    /*
     * This method checks if the collection is null or is empty.
     * @param collection input of type {@link Collection}
     * @param errorMessage The exception message use if the collection is empty or null
     * @throws com.netflix.conductor.core.execution.ApplicationException if input Collection is not valid
     */
    public static void checkNotNullOrEmpty(Collection<?> collection, String errorMessage){
        if(collection == null || collection.isEmpty()) {
                throw new ApplicationException(ApplicationException.Code.INVALID_INPUT, errorMessage);
            }
    }

    /**
     * This method checks if the input map is valid or not.
     *
     * @param map input of type {@link Map}
     * @param errorMessage The exception message use if the map is empty or null
     * @throws com.netflix.conductor.core.execution.ApplicationException if input map is not valid
     */
    public static void checkNotNullOrEmpty(Map<?, ?> map, String errorMessage) {
        if(map == null || map.isEmpty()) {
            throw new ApplicationException(ApplicationException.Code.INVALID_INPUT, errorMessage);
        }
    }

    /**
     * This method checks it the input string is null or empty.
     *
     * @param input        input of type {@link String}
     * @param errorMessage The exception message use if the string is empty or null
     * @throws com.netflix.conductor.core.execution.ApplicationException if input string is not valid
     */
    public static void checkNotNullOrEmpty(String input, String errorMessage) {
        try {
            Preconditions.checkArgument(StringUtils.isNotBlank(input), errorMessage);
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(ApplicationException.Code.INVALID_INPUT, errorMessage);
        }
    }

    /**
     * This method checks if the object is null or empty.
     * @param object    input of type {@link Object}
     * @param errorMessage The exception message use if the object is empty or null
     * @throws com.netflix.conductor.core.execution.ApplicationException if input object is not valid
     */
    public static void checkNotNull(Object object, String errorMessage){
        try {
            Preconditions.checkNotNull(object, errorMessage);
        } catch (NullPointerException exception) {
            throw new ApplicationException(ApplicationException.Code.INVALID_INPUT, errorMessage);
        }
    }
}
