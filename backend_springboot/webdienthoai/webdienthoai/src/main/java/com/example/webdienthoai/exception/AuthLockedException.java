package com.example.webdienthoai.exception;

public class AuthLockedException extends RuntimeException {
    private final long retryAfterSeconds;

    public AuthLockedException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

