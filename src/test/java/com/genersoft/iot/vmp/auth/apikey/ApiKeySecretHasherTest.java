package com.genersoft.iot.vmp.auth.apikey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeySecretHasherTest {

    private static final String PEPPER = "0123456789abcdef0123456789abcdef";

    @Test
    void generatesHighEntropyOneTimeSecretAndDeterministicHmac() {
        ApiKeySecretHasher hasher = new ApiKeySecretHasher(PEPPER);

        GeneratedApiKey first = hasher.generate();
        GeneratedApiKey second = hasher.generate();

        assertTrue(first.raw().startsWith("wvp_"));
        assertEquals(47, first.raw().length());
        assertEquals(first.raw().substring(0, 12), first.prefix());
        assertEquals(first.raw().substring(first.raw().length() - 4), first.last4());
        assertEquals(hasher.hash(first.raw()), first.hash());
        assertTrue(hasher.matches(first.raw(), first.hash()));
        assertNotEquals(first.raw(), second.raw());
        assertNotEquals(first.hash(), second.hash());
    }

    @Test
    void differentPepperCannotValidateSameSecret() {
        ApiKeySecretHasher first = new ApiKeySecretHasher(PEPPER);
        ApiKeySecretHasher second = new ApiKeySecretHasher("abcdef0123456789abcdef0123456789");
        GeneratedApiKey generated = first.generate();

        assertFalse(second.matches(generated.raw(), generated.hash()));
    }
}
