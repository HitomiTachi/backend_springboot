package com.example.webdienthoai.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTests {

    @Test
    void shouldLockAfterConfiguredFailures() {
        LoginAttemptService service = new LoginAttemptService(3, 5);
        String key = "user@example.com";

        service.onFailure(key);
        assertFalse(service.isLocked(key));

        service.onFailure(key);
        assertFalse(service.isLocked(key));

        service.onFailure(key);
        assertTrue(service.isLocked(key));
    }

    @Test
    void shouldClearLockAfterSuccess() {
        LoginAttemptService service = new LoginAttemptService(2, 5);
        String key = "user@example.com";

        service.onFailure(key);
        service.onFailure(key);
        assertTrue(service.isLocked(key));

        service.onSuccess(key);
        assertFalse(service.isLocked(key));
    }
}
