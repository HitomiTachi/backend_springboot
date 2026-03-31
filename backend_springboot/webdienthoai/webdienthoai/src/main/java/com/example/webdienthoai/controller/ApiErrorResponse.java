package com.example.webdienthoai.controller;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
public class ApiErrorResponse {
    private String code;
    private String message;
    private String path;
    private String traceId;
    private Instant timestamp;
    private Map<String, ?> details;
}
