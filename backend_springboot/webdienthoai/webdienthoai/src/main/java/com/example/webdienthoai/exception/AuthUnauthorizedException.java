package com.example.webdienthoai.exception;

public class AuthUnauthorizedException extends RuntimeException {
    public AuthUnauthorizedException(String message) {
        super(message);
    }
}

