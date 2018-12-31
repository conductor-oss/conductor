package com.netflix.conductor.server.resources;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.common.validation.ErrorResponse;
import com.netflix.conductor.common.validation.ValidationError;
import com.netflix.conductor.metrics.Monitors;
import com.sun.jersey.api.core.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import javax.validation.ValidationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class converts Hibernate {@link ValidationException} into jersey
 * response.
 * @author fjhaveri
 *
 */
@Provider
@Singleton
public class ValidationExceptionMapper implements ExceptionMapper<ValidationException> {
    private static Logger LOGGER = LoggerFactory.getLogger(ApplicationExceptionMapper.class);

    @Context
    private HttpContext context;

    @Context
    private UriInfo uriInfo;

    @Context
    private javax.inject.Provider<Request> request;
    private String host;

    @Inject
    public ValidationExceptionMapper(Configuration config) {
        this.host = config.getServerId();
    }

    @Override
    public Response toResponse(ValidationException exception) {
        logException(exception);

        Response.ResponseBuilder responseBuilder;

        if (exception instanceof ConstraintViolationException) {
            responseBuilder = Response.status(Response.Status.BAD_REQUEST);
        } else {
            responseBuilder = Response.serverError();
            Monitors.error("error", "error");
        }

        Map<String, Object> entityMap = new HashMap<>();
        entityMap.put("instance", host);

        responseBuilder.type(MediaType.APPLICATION_JSON_TYPE);
        responseBuilder.entity(toErrorResponse(exception));

        return responseBuilder.build();
    }

    private static ErrorResponse toErrorResponse(ValidationException ve) {
        if ( ve instanceof ConstraintViolationException ) {
            return constraintViolationExceptionToErrorResponse((ConstraintViolationException) ve);
        } else {
            ErrorResponse result = new ErrorResponse();
            result.setStatus(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
            result.setMessage(ve.getMessage());
            return result;
        }
    }

    private static ErrorResponse constraintViolationExceptionToErrorResponse(ConstraintViolationException exception) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setStatus(Response.Status.BAD_REQUEST.getStatusCode());
        errorResponse.setMessage("Validation failed, check below errors for detail.");

        List<ValidationError> validationErrors = new ArrayList<>();

        exception.getConstraintViolations().forEach(e ->
        validationErrors.add(new ValidationError(getViolationPath(e), e.getMessage(), getViolationInvalidValue(e.getInvalidValue()))));

        errorResponse.setValidationErrors(validationErrors);
        return errorResponse;
    }

    private static String getViolationPath(final ConstraintViolation violation) {
        final String propertyPath = violation.getPropertyPath().toString();
        return !"".equals(propertyPath) ? propertyPath : "";
    }

    private static String getViolationInvalidValue(final Object invalidValue) {
        if (invalidValue == null) {
            return null;
        }

        if (invalidValue.getClass().isArray()) {
            if (invalidValue instanceof Object[]) {
                // not helpful to return object array, skip it.
                return null;
            } else if (invalidValue instanceof boolean[]) {
                return Arrays.toString((boolean[]) invalidValue);
            } else if (invalidValue instanceof byte[]) {
                return Arrays.toString((byte[]) invalidValue);
            } else if (invalidValue instanceof char[]) {
                return Arrays.toString((char[]) invalidValue);
            } else if (invalidValue instanceof double[]) {
                return Arrays.toString((double[]) invalidValue);
            } else if (invalidValue instanceof float[]) {
                return Arrays.toString((float[]) invalidValue);
            } else if (invalidValue instanceof int[]) {
                return Arrays.toString((int[]) invalidValue);
            } else if (invalidValue instanceof long[]) {
                return Arrays.toString((long[]) invalidValue);
            } else if (invalidValue instanceof short[]) {
                return Arrays.toString((short[]) invalidValue);
            }
        }

        // It is only helpful to return invalid value of primitive types
        if ( invalidValue.getClass().getName().startsWith("java.lang.") ) {
            return invalidValue.toString();
        }

        return null;
    }

    @VisibleForTesting
    UriInfo getUriInfo() {
        return uriInfo;
    }

    @VisibleForTesting
    Request getRequest() {
        return request.get();
    }

    private void logException(ValidationException exception) {
        LOGGER.error(String.format("Error %s url: '%s'", exception.getClass().getSimpleName(),
                getUriInfo().getPath()), exception);
    }

}