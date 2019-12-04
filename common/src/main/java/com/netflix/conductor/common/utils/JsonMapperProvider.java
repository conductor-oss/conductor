package com.netflix.conductor.common.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;

import javax.inject.Provider;
import java.io.IOException;

public class JsonMapperProvider implements Provider<ObjectMapper> {

    public JsonMapperProvider() {}

    /**
     * JsonProtoModule can be registered into an {@link ObjectMapper}
     * to enable the serialization and deserialization of ProtoBuf objects
     * from/to JSON.
     *
     * Right now this module only provides (de)serialization for the {@link Any}
     * ProtoBuf type, as this is the only ProtoBuf object which we're currently
     * exposing through the REST API.
     *
     * {@see AnySerializer}, {@see AnyDeserializer}
     */
    private static class JsonProtoModule extends SimpleModule {
        private final static String JSON_TYPE = "@type";
        private final static String JSON_VALUE = "@value";

        /**
         * AnySerializer converts a ProtoBuf {@link Any} object into its JSON
         * representation.
         *
         * This is <b>not</b> a canonical ProtoBuf JSON representation. Let us
         * explain what we're trying to accomplish here:
         *
         * The {@link Any} ProtoBuf message is a type in the PB standard library that
         * can store any other arbitrary ProtoBuf message in a type-safe way, even
         * when the server has no knowledge of the schema of the stored message.
         *
         * It accomplishes this by storing a tuple of informtion: an URL-like type
         * declaration for the stored message, and the serialized binary encoding
         * of the stored message itself. Language specific implementations of ProtoBuf
         * provide helper methods to encode and decode arbitrary messages into an
         * {@link Any} object ({@link Any#pack(Message)} in Java).
         *
         * We want to expose these {@link Any} objects in the REST API because they've
         * been introduced as part of the new GRPC interface to Conductor, but unfortunately
         * we cannot encode them using their canonical ProtoBuf JSON encoding. According to
         * the docs:
         *
         *      The JSON representation of an `Any` value uses the regular
         *      representation of the deserialized, embedded message, with an
         *      additional field `@type` which contains the type URL. Example:
         *
         *      package google.profile;
         *      message Person {
         *          string first_name = 1;
         *          string last_name = 2;
         *      }
         *      {
         *          "@type": "type.googleapis.com/google.profile.Person",
         *          "firstName": <string>,
         *          "lastName": <string>
         *      }
         *
         * In order to accomplish this representation, the PB-JSON encoder needs to have
         * knowledge of all the ProtoBuf messages that could be serialized inside the
         * {@link Any} message. This is not possible to accomplish inside the Conductor server,
         * which is simply passing through arbitrary payloads from/to clients.
         *
         * Consequently, to actually expose the Message through the REST API, we must create
         * a custom encoding that contains the raw data of the serialized message, as we are
         * not able to deserialize it on the server. We simply return a dictionary with
         * '@type' and '@value' keys, where '@type' is identical to the canonical representation,
         * but '@value' contains a base64 encoded string with the binary data of the serialized
         * message.
         *
         * Since all the provided Conductor clients are required to know this encoding, it's always
         * possible to re-build the original {@link Any} message regardless of the client's language.
         *
         * {@see AnyDeserializer}
         */
        protected class AnySerializer extends JsonSerializer<Any> {
            @Override
            public void serialize(Any value, JsonGenerator jgen, SerializerProvider provider)
                    throws IOException, JsonProcessingException {
                jgen.writeStartObject();
                jgen.writeStringField(JSON_TYPE, value.getTypeUrl());
                jgen.writeBinaryField(JSON_VALUE, value.getValue().toByteArray());
                jgen.writeEndObject();
            }
        }

        /**
         * AnyDeserializer converts the custom JSON representation of an {@link Any} value
         * into its original form.
         *
         * {@see AnySerializer} for details on this representation.
         */
        protected class AnyDeserializer extends JsonDeserializer<Any> {
            @Override
            public Any deserialize(JsonParser p, DeserializationContext ctxt)
                    throws IOException, JsonProcessingException {
                JsonNode root = p.getCodec().readTree(p);
                JsonNode type = root.get(JSON_TYPE);
                JsonNode value = root.get(JSON_VALUE);

                if (type == null || !type.isTextual()) {
                    ctxt.reportMappingException("invalid '@type' field when deserializing ProtoBuf Any object");
                }

                if (value == null || !value.isTextual()) {
                    ctxt.reportMappingException("invalid '@value' field when deserializing ProtoBuf Any object");
                }

                return Any.newBuilder()
                        .setTypeUrl(type.textValue())
                        .setValue(ByteString.copyFrom(value.binaryValue()))
                        .build();
            }
        }

        public JsonProtoModule() {
            super("ConductorJsonProtoModule");
            addSerializer(Any.class, new AnySerializer());
            addDeserializer(Any.class, new AnyDeserializer());
        }
    }

    @Override
    public ObjectMapper get() {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS));
        objectMapper.registerModule(new JsonProtoModule());
        return objectMapper;
    }
}
