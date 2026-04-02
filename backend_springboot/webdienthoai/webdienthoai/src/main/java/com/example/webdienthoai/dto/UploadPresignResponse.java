package com.example.webdienthoai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class UploadPresignResponse {
    private String uploadUrl;
    private String publicUrl;
    private String method;
    private Map<String, String> headers;
}

