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
package com.netflix.conductor.rest.controllers;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.metrics.Monitors;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(ValidationExceptionMapper.ORDER + 1)
public class ApplicationExceptionMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationExceptionMapper.class);

    private final String host = Utils.getServerId();

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<Map<?, ?>> handleApplicationException(HttpServletRequest request, ApplicationException ex) {
        logException(request, ex);

        if (ex.getHttpStatusCode() == 500) {
            Monitors.error("error", "error");
        }

        Map<String, Object> entityMap = ex.toMap();
        entityMap.put("instance", host);

        return new ResponseEntity<>(entityMap, HttpStatus.valueOf(ex.getHttpStatusCode()));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<?, ?>> handleAll(HttpServletRequest request, Throwable exception) {
        logException(request, exception);

        Monitors.error("error", "error");

        ApplicationException ex;

        if (exception instanceof IllegalArgumentException || exception instanceof InvalidFormatException) {
            ex = new ApplicationException(ApplicationException.Code.INVALID_INPUT, exception.getMessage(), exception);
        } else {
            ex = new ApplicationException(ApplicationException.Code.INTERNAL_ERROR, exception.getMessage(), exception);
        }

        Map<String, Object> entityMap = ex.toMap();
        entityMap.put("instance", host);

        return new ResponseEntity<>(entityMap, HttpStatus.valueOf(ex.getHttpStatusCode()));
    }

    private void logException(HttpServletRequest request, Throwable exception) {
        LOGGER.error(String.format("Error %s url: '%s'", exception.getClass().getSimpleName(),
            request.getRequestURI()), exception);
    }
}
