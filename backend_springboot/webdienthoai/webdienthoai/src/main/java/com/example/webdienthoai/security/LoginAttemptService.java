package com.example.webdienthoai.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private record AttemptState(int failures, Instant lockUntil) {
    }

    private final int maxFailures;
    private final Duration lockDuration;
    private final Map<String, AttemptState> stateByKey = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.auth.max-failed-attempts:5}") int maxFailures,
            @Value("${app.auth.lock-minutes:15}") long lockMinutes) {
        this.maxFailures = Math.max(1, maxFailures);
        this.lockDuration = Duration.ofMinutes(Math.max(1, lockMinutes));
    }

    public boolean isLocked(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        AttemptState state = stateByKey.get(normalize(key));
        if (state == null || state.lockUntil == null) {
            return false;
        }
        if (Instant.now().isAfter(state.lockUntil)) {
            stateByKey.remove(normalize(key));
            return false;
        }
        return true;
    }

    public long remainingLockSeconds(String key) {
        AttemptState state = stateByKey.get(normalize(key));
        if (state == null || state.lockUntil == null) {
            return 0;
        }
        long seconds = Duration.between(Instant.now(), state.lockUntil).getSeconds();
        return Math.max(0, seconds);
    }

    public void onSuccess(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        stateByKey.remove(normalize(key));
    }

    public void onFailure(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        String normalized = normalize(key);
        AttemptState current = stateByKey.get(normalized);
        if (current != null && current.lockUntil != null && Instant.now().isBefore(current.lockUntil)) {
            return;
        }

        int failures = (current == null ? 0 : current.failures) + 1;
        if (failures >= maxFailures) {
            stateByKey.put(normalized, new AttemptState(failures, Instant.now().plus(lockDuration)));
            return;
        }
        stateByKey.put(normalized, new AttemptState(failures, null));
    }

    private String normalize(String key) {
        return key.trim().toLowerCase();
    }
}
