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

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.netflix.conductor.common.validation.ErrorResponse;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.metrics.Monitors;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import static com.netflix.conductor.core.exception.ApplicationException.Code.INTERNAL_ERROR;
import static com.netflix.conductor.core.exception.ApplicationException.Code.INVALID_INPUT;

@RestControllerAdvice
@Order(ValidationExceptionMapper.ORDER + 1)
public class ApplicationExceptionMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationExceptionMapper.class);

    private final String host = Utils.getServerId();

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(
            HttpServletRequest request, ApplicationException ex) {
        logException(request, ex);

        Monitors.error("error", String.valueOf(ex.getHttpStatusCode()));

        return new ResponseEntity<>(
                toErrorResponse(ex), HttpStatus.valueOf(ex.getHttpStatusCode()));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ErrorResponse> handleAll(HttpServletRequest request, Throwable th) {
        logException(request, th);

        ApplicationException.Code code =
                (th instanceof IllegalArgumentException || th instanceof InvalidFormatException)
                        ? INVALID_INPUT
                        : INTERNAL_ERROR;

        ApplicationException ex = new ApplicationException(code, th.getMessage(), th);

        return handleApplicationException(request, ex);
    }

    private void logException(HttpServletRequest request, Throwable exception) {
        LOGGER.error(
                String.format(
                        "Error %s url: '%s'",
                        exception.getClass().getSimpleName(), request.getRequestURI()),
                exception);
    }

    private ErrorResponse toErrorResponse(ApplicationException ex) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setInstance(host);
        errorResponse.setStatus(ex.getHttpStatusCode());
        errorResponse.setMessage(ex.getMessage());
        errorResponse.setRetryable(ex.isRetryable());
        return errorResponse;
    }
}
