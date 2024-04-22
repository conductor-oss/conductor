/*
 * Copyright 2020 Conductor Authors.
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

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.netflix.conductor.common.validation.ErrorResponse;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.metrics.Monitors;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
@Order(ValidationExceptionMapper.ORDER + 1)
public class ApplicationExceptionMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationExceptionMapper.class);

    private final String host = Utils.getServerId();

    private static final Map<Class<? extends Throwable>, HttpStatus> EXCEPTION_STATUS_MAP =
            new HashMap<>();

    static {
        EXCEPTION_STATUS_MAP.put(NotFoundException.class, HttpStatus.NOT_FOUND);
        EXCEPTION_STATUS_MAP.put(ConflictException.class, HttpStatus.CONFLICT);
        EXCEPTION_STATUS_MAP.put(IllegalArgumentException.class, HttpStatus.BAD_REQUEST);
        EXCEPTION_STATUS_MAP.put(InvalidFormatException.class, HttpStatus.INTERNAL_SERVER_ERROR);
        EXCEPTION_STATUS_MAP.put(NoResourceFoundException.class, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ErrorResponse> handleAll(HttpServletRequest request, Throwable th) {
        logException(request, th);

        HttpStatus status =
                EXCEPTION_STATUS_MAP.getOrDefault(th.getClass(), HttpStatus.INTERNAL_SERVER_ERROR);

        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setInstance(host);
        errorResponse.setStatus(status.value());
        errorResponse.setMessage(th.getMessage());
        errorResponse.setRetryable(
                th instanceof TransientException); // set it to true for TransientException

        Monitors.error("error", String.valueOf(status.value()));

        return new ResponseEntity<>(errorResponse, status);
    }

    private void logException(HttpServletRequest request, Throwable exception) {
        LOGGER.error(
                "Error {} url: '{}'",
                exception.getClass().getSimpleName(),
                request.getRequestURI(),
                exception);
    }
}
