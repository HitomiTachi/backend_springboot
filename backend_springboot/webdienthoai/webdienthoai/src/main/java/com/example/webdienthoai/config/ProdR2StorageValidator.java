package com.example.webdienthoai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Khi chạy profile production, bắt buộc R2 (object storage), không dùng local disk cho upload.
 * Cùng tinh thần {@link com.example.webdienthoai.security.SecuritySecretValidator}.
 */
@Component
public class ProdR2StorageValidator {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${app.storage.type:local}")
    private String storageType;

    @Value("${app.r2.endpoint:}")
    private String r2Endpoint;

    @Value("${app.r2.bucket:}")
    private String r2Bucket;

    @Value("${app.r2.access-key-id:}")
    private String r2AccessKeyId;

    @Value("${app.r2.secret-access-key:}")
    private String r2SecretAccessKey;

    @Value("${app.r2.public-base-url:}")
    private String r2PublicBaseUrl;

    @PostConstruct
    void validateProductionStorage() {
        boolean strictProfile = "prod".equalsIgnoreCase(activeProfile)
                || "production".equalsIgnoreCase(activeProfile);
        if (!strictProfile) {
            return;
        }
        if (!"r2".equalsIgnoreCase(storageType)) {
            throw new IllegalStateException(
                    "Profile production bắt buộc app.storage.type=r2 (Cloudflare R2). Không dùng lưu file local trên server production.");
        }
        if (!StringUtils.hasText(r2Endpoint)
                || !StringUtils.hasText(r2Bucket)
                || !StringUtils.hasText(r2AccessKeyId)
                || !StringUtils.hasText(r2SecretAccessKey)
                || !StringUtils.hasText(r2PublicBaseUrl)) {
            throw new IllegalStateException(
                    "Profile production bắt buộc cấu hình R2 qua env: S3_ENDPOINT, S3_BUCKET, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, PUBLIC_ASSET_BASE_URL.");
        }
    }
}
