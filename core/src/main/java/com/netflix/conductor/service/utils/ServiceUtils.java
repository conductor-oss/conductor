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

    public static List<String> convertToSortedList(String sortStr) {
        List<String> list = new ArrayList<String>();
        if (sortStr != null && sortStr.length() != 0) {
            list = Arrays.asList(sortStr.split("\\|"));
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
    public static void isValid(boolean condition, String errorMessage){
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
    public static void isValid( Collection<?> collection, String errorMessage){
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
    public static void isValid(Map<?, ?> map, String errorMessage) {
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
    public static void isValid(String input, String errorMessage) {
        try {
            Preconditions.checkArgument(StringUtils.isNotBlank(input), errorMessage);
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(ApplicationException.Code.INVALID_INPUT, errorMessage);
        }
    }

    /**
     * This method checks if the object is null or empty.
     * @param object    input of type {@link Object}
     *  @throws com.netflix.conductor.core.execution.ApplicationException if input object is not valid
     */
    public static void isValid(Object object, String errorMessage){
        try {
            Preconditions.checkNotNull(object, errorMessage);
        } catch (NullPointerException exception) {
            throw new ApplicationException(ApplicationException.Code.INVALID_INPUT, errorMessage);
        }
    }
}
