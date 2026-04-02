package com.example.webdienthoai.service;

import com.example.webdienthoai.dto.UploadPresignResponse;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Presigned PUT to Cloudflare R2 (S3 API). Credentials and endpoint must come from env, never committed.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "r2")
public class R2PresignService implements DisposableBean {

    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;
    private final String keyPrefix;

    public R2PresignService(
            @Value("${app.r2.endpoint}") String endpoint,
            @Value("${app.r2.bucket}") String bucket,
            @Value("${app.r2.access-key-id}") String accessKeyId,
            @Value("${app.r2.secret-access-key}") String secretAccessKey,
            @Value("${app.r2.public-base-url}") String publicBaseUrl,
            @Value("${app.r2.key-prefix:}") String keyPrefix
    ) {
        if (endpoint == null || endpoint.isBlank()
                || bucket == null || bucket.isBlank()
                || accessKeyId == null || accessKeyId.isBlank()
                || secretAccessKey == null || secretAccessKey.isBlank()
                || publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "R2 requires app.r2.endpoint, app.r2.bucket, app.r2.access-key-id, app.r2.secret-access-key, app.r2.public-base-url");
        }
        this.bucket = bucket.trim();
        String base = publicBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.publicBaseUrl = base;
        String prefix = keyPrefix == null ? "" : keyPrefix.trim();
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        this.keyPrefix = prefix;

        String normalizedEndpoint = normalizeR2Endpoint(endpoint.trim());

        this.presigner = S3Presigner.builder()
                .region(Region.of("auto"))
                .endpointOverride(URI.create(normalizedEndpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    /**
     * R2 expects {@code https://&lt;account_id&gt;.r2.cloudflarestorage.com} without bucket path.
     */
    static String normalizeR2Endpoint(String endpoint) {
        URI u = URI.create(endpoint);
        String path = u.getPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            return u.getScheme() + "://" + u.getAuthority();
        }
        return endpoint;
    }

    public UploadPresignResponse presignPut(String objectKey, String contentType) {
        String ct = (contentType != null && !contentType.isBlank())
                ? contentType
                : "application/octet-stream";

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(ct)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(put)
                .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);

        Map<String, String> outHeaders = new HashMap<>();
        presigned.httpRequest().headers().forEach((name, values) -> {
            if (values != null && !values.isEmpty()) {
                outHeaders.put(name, values.get(0));
            }
        });

        String publicUrl = publicBaseUrl + "/" + objectKey;

        return UploadPresignResponse.builder()
                .uploadUrl(presigned.url().toString())
                .publicUrl(publicUrl)
                .method("PUT")
                .headers(outHeaders)
                .build();
    }

    public String buildObjectKey(String safeFileName) {
        return keyPrefix + safeFileName;
    }

    @Override
    public void destroy() {
        presigner.close();
    }
}
