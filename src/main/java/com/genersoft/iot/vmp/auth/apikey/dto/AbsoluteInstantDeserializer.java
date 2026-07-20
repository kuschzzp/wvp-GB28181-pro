package com.genersoft.iot.vmp.auth.apikey.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;
import java.time.Instant;

public class AbsoluteInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return parse(parser, Long.toString(parser.getLongValue()));
        }
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            return parse(parser, parser.getValueAsString());
        }
        if (parser.hasToken(JsonToken.VALUE_NULL)) {
            return null;
        }
        return (Instant) context.handleUnexpectedToken(Instant.class, parser);
    }

    private Instant parse(JsonParser parser, String value) throws InvalidFormatException {
        try {
            return ApiKeyExpirationParser.parse(value);
        } catch (IllegalArgumentException exception) {
            throw InvalidFormatException.from(parser, exception.getMessage(), value, Instant.class);
        }
    }
}
