package com.example.webdienthoai.dto;

import lombok.Data;

@Data
public class UploadPresignRequest {
    /** Tên file gốc (server sanitize). */
    private String fileName;

    /** MIME ảnh, ví dụ image/jpeg — dùng khi ký presigned PUT (R2). */
    private String contentType;
}

