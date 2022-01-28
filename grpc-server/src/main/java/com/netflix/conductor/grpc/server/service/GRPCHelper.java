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
package com.netflix.conductor.grpc.server.service;

import java.util.Arrays;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;

import com.google.rpc.DebugInfo;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.protobuf.lite.ProtoLiteUtils;
import io.grpc.stub.StreamObserver;

public class GRPCHelper {

    private final Logger logger;

    private static final Metadata.Key<DebugInfo> STATUS_DETAILS_KEY =
            Metadata.Key.of(
                    "grpc-status-details-bin",
                    ProtoLiteUtils.metadataMarshaller(DebugInfo.getDefaultInstance()));

    public GRPCHelper(Logger log) {
        this.logger = log;
    }

    /**
     * Converts an internal exception thrown by Conductor into an StatusException that uses modern
     * "Status" metadata for GRPC.
     *
     * <p>Note that this is trickier than it ought to be because the GRPC APIs have not been
     * upgraded yet. Here's a quick breakdown of how this works in practice:
     *
     * <p>Reporting a "status" result back to a client with GRPC is pretty straightforward. GRPC
     * implementations simply serialize the status into several HTTP/2 trailer headers that are sent
     * back to the client before shutting down the HTTP/2 stream.
     *
     * <p>- 'grpc-status', which is a string representation of a {@link com.google.rpc.Code} -
     * 'grpc-message', which is the description of the returned status - 'grpc-status-details-bin'
     * (optional), which is an arbitrary payload with a serialized ProtoBuf object, containing an
     * accurate description of the error in case the status is not successful.
     *
     * <p>By convention, Google provides a default set of ProtoBuf messages for the most common
     * error cases. Here, we'll be using {@link DebugInfo}, as we're reporting an internal Java
     * exception which we couldn't properly handle.
     *
     * <p>Now, how do we go about sending all those headers _and_ the {@link DebugInfo} payload
     * using the Java GRPC API?
     *
     * <p>The only way we can return an error with the Java API is by passing an instance of {@link
     * io.grpc.StatusException} or {@link io.grpc.StatusRuntimeException} to {@link
     * StreamObserver#onError(Throwable)}. The easiest way to create either of these exceptions is
     * by using the {@link Status} class and one of its predefined code identifiers (in this case,
     * {@link Status#INTERNAL} because we're reporting an internal exception). The {@link Status}
     * class has setters to set its most relevant attributes, namely those that will be
     * automatically serialized into the 'grpc-status' and 'grpc-message' trailers in the response.
     * There is, however, no setter to pass an arbitrary ProtoBuf message to be serialized into a
     * `grpc-status-details-bin` trailer. This feature exists in the other language implementations
     * but it hasn't been brought to Java yet.
     *
     * <p>Fortunately, {@link Status#asException(Metadata)} exists, allowing us to pass any amount
     * of arbitrary trailers before we close the response. So we're using this API to manually craft
     * the 'grpc-status-detail-bin' trailer, in the same way that the GRPC server implementations
     * for Go and C++ craft and serialize the header. This will allow us to access the metadata
     * cleanly from Go and C++ clients by using the 'details' method which _has_ been implemented in
     * those two clients.
     *
     * @param t The exception to convert
     * @return an instance of {@link StatusException} which will properly serialize all its headers
     *     into the response.
     */
    private StatusException throwableToStatusException(Throwable t) {
        String[] frames = ExceptionUtils.getStackFrames(t);
        Metadata metadata = new Metadata();
        metadata.put(
                STATUS_DETAILS_KEY,
                DebugInfo.newBuilder()
                        .addAllStackEntries(Arrays.asList(frames))
                        .setDetail(ExceptionUtils.getMessage(t))
                        .build());

        return Status.INTERNAL.withDescription(t.getMessage()).withCause(t).asException(metadata);
    }

    void onError(StreamObserver<?> response, Throwable t) {
        logger.error("internal exception during GRPC request", t);
        response.onError(throwableToStatusException(t));
    }

    /**
     * Convert a non-null String instance to a possibly null String instance based on ProtoBuf's
     * rules for optional arguments.
     *
     * <p>This helper converts an String instance from a ProtoBuf object into a possibly null
     * String. In ProtoBuf objects, String fields are not nullable, but an empty String field is
     * considered to be "missing".
     *
     * <p>The internal Conductor APIs expect missing arguments to be passed as null values, so this
     * helper performs such conversion.
     *
     * @param str a string from a ProtoBuf object
     * @return the original string, or null
     */
    String optional(@Nonnull String str) {
        return str.isEmpty() ? null : str;
    }

    /**
     * Check if a given non-null String instance is "missing" according to ProtoBuf's missing field
     * rules. If the String is missing, the given default value will be returned. Otherwise, the
     * string itself will be returned.
     *
     * @param str the input String
     * @param defaults the default value for the string
     * @return 'str' if it is not empty according to ProtoBuf rules; 'defaults' otherwise
     */
    String optionalOr(@Nonnull String str, String defaults) {
        return str.isEmpty() ? defaults : str;
    }

    /**
     * Convert a non-null Integer instance to a possibly null Integer instance based on ProtoBuf's
     * rules for optional arguments.
     *
     * <p>This helper converts an Integer instance from a ProtoBuf object into a possibly null
     * Integer. In ProtoBuf objects, Integer fields are not nullable, but a zero-value Integer field
     * is considered to be "missing".
     *
     * <p>The internal Conductor APIs expect missing arguments to be passed as null values, so this
     * helper performs such conversion.
     *
     * @param i an Integer from a ProtoBuf object
     * @return the original Integer, or null
     */
    Integer optional(@Nonnull Integer i) {
        return i == 0 ? null : i;
    }

    /**
     * Check if a given non-null Integer instance is "missing" according to ProtoBuf's missing field
     * rules. If the Integer is missing (i.e. if it has a zero-value), the given default value will
     * be returned. Otherwise, the Integer itself will be returned.
     *
     * @param i the input Integer
     * @param defaults the default value for the Integer
     * @return 'i' if it is not a zero-value according to ProtoBuf rules; 'defaults' otherwise
     */
    Integer optionalOr(@Nonnull Integer i, int defaults) {
        return i == 0 ? defaults : i;
    }
}
