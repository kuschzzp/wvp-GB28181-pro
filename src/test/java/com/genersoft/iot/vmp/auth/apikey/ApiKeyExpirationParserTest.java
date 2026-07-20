package com.genersoft.iot.vmp.auth.apikey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyExpirationParser;
import com.genersoft.iot.vmp.auth.apikey.dto.CreateApiKeyRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiKeyExpirationParserTest {

    @Test
    void acceptsIso8601EpochMillisAndLegacyQueryFormat() {
        Instant expected = Instant.parse("2027-01-01T00:00:00Z");

        assertEquals(expected, ApiKeyExpirationParser.parse("2027-01-01T00:00:00Z"));
        assertEquals(expected, ApiKeyExpirationParser.parse("1798761600000"));
        assertEquals(Instant.parse("2026-12-31T16:00:00Z"),
                ApiKeyExpirationParser.parse("2027-01-01 00:00:00"));
    }

    @Test
    void jsonRequestTreatsIntegerAsEpochMillis() throws Exception {
        CreateApiKeyRequest request = new ObjectMapper().readValue(
                "{\"ownerId\":12,\"expiresAt\":1798761600000,\"enabled\":true}",
                CreateApiKeyRequest.class);

        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), request.expiresAt());
    }

    @Test
    void jsonRequestAcceptsIso8601AndRejectsMalformedOrStructuredValues() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CreateApiKeyRequest request = objectMapper.readValue(
                "{\"expiresAt\":\"2027-01-01T00:00:00Z\",\"enabled\":true}",
                CreateApiKeyRequest.class);

        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), request.expiresAt());
        assertThrows(JsonMappingException.class, () -> objectMapper.readValue(
                "{\"expiresAt\":\"2027/01/01\",\"enabled\":true}",
                CreateApiKeyRequest.class));
        assertThrows(JsonMappingException.class, () -> objectMapper.readValue(
                "{\"expiresAt\":[],\"enabled\":true}",
                CreateApiKeyRequest.class));
    }

    @Test
    void rejectsAmbiguousOrMalformedExpiration() {
        assertThrows(IllegalArgumentException.class,
                () -> ApiKeyExpirationParser.parse("2027/01/01"));
    }
}
