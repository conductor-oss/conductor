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
