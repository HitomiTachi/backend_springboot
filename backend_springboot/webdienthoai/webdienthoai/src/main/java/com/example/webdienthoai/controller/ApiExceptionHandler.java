package com.example.webdienthoai.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Trả về lỗi JSON thống nhất để frontend parse (message, code).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, ?> details) {
        ApiErrorResponse response = ApiErrorResponse.builder()
                .code(code)
                .message(message)
                .path(request.getRequestURI())
                .traceId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .details(details)
                .build();
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException e, HttpServletRequest request) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                e.getMessage() != null ? e.getMessage() : "Email hoặc mật khẩu sai",
                request,
                Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError err : e.getBindingResult().getFieldErrors()) {
            errors.put(err.getField(), err.getDefaultMessage());
        }
        String firstMessage = errors.isEmpty() ? "Invalid request" : errors.values().iterator().next();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", firstMessage, request, Map.of("errors", errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParams(MissingServletRequestParameterException e, HttpServletRequest request) {
        String message = "Thiếu query parameter: " + e.getParameterName();
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", message, request, Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String message = "Sai kiểu dữ liệu cho tham số: " + e.getName();
        return build(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", message, request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
        String root = e.getRootCause() != null ? e.getRootCause().getMessage() : e.getMessage();
        String msg = "Dữ liệu không hợp lệ hoặc vi phạm ràng buộc.";
        if (root != null) {
            if (root.contains("Duplicate entry")) msg = "Dữ liệu đã tồn tại (bị trùng).";
            else if (root.contains("Data too long")) msg = "Giá trị quá dài cho phép trong database.";
            else if (root.contains("cannot be null")) msg = "Thiếu trường bắt buộc.";
        }
        return build(
                HttpStatus.BAD_REQUEST,
                "DATA_INTEGRITY_ERROR",
                msg,
                request,
                Map.of("detail", root != null ? root : ""));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception e, HttpServletRequest request) {
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Lỗi máy chủ. Vui lòng thử lại.",
                request,
                Map.of("exception", e.getClass().getSimpleName()));
    }
}
