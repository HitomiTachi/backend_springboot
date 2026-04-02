package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.UploadPresignRequest;
import com.example.webdienthoai.dto.UploadPresignResponse;
import com.example.webdienthoai.service.R2PresignService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    @Value("${app.storage.type:local}")
    private String storageType;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Autowired(required = false)
    private R2PresignService r2PresignService;

    @PostConstruct
    public void init() throws IOException {
        if (isR2()) {
            return;
        }
        Path dir = Paths.get(uploadDir);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    private boolean isR2() {
        return "r2".equalsIgnoreCase(storageType);
    }

    /**
     * POST /api/upload — chỉ dùng khi {@code app.storage.type=local}.
     */
    @PostMapping
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Upload-Filename", required = false) String xUploadFilename,
            HttpServletRequest request) {

        if (isR2()) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                    .body(Map.of("message", "Upload trực tiếp đã tắt. Dùng POST /api/upload/presign rồi PUT file lên URL trả về."));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File không được rỗng"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Chỉ chấp nhận file ảnh"));
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image";
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0) {
            ext = originalName.substring(dot);
        }

        String safeFilename;
        if (xUploadFilename != null && !xUploadFilename.isBlank()) {
            String raw = xUploadFilename.trim().replace("\\", "").replace("/", "");
            raw = raw.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (!raw.contains(".") && !ext.isBlank()) {
                raw = raw + ext;
            }
            safeFilename = raw;
        } else {
            safeFilename = UUID.randomUUID().toString().replace("-", "") + ext;
        }

        try {
            Path dest = Paths.get(uploadDir).resolve(safeFilename);
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Lỗi lưu file: " + e.getMessage()));
        }

        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + ":" + request.getServerPort();
        String url = baseUrl + "/uploads/" + safeFilename;

        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * POST /api/upload/presign
     * - local: trả POST tới /api/upload + header X-Upload-Filename
     * - r2: trả presigned PUT tới R2 + publicUrl (PUBLIC_ASSET_BASE_URL + key)
     */
    @PostMapping("/presign")
    public ResponseEntity<?> presign(
            HttpServletRequest request,
            @Valid @RequestBody UploadPresignRequest body) {
        String fileName = body.getFileName() != null ? body.getFileName().trim() : "";
        if (fileName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String contentType = body.getContentType();
        if (isR2()) {
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(Map.of("message", "Chỉ chấp nhận ảnh (contentType image/*)"));
            }
        } else if (contentType != null && !contentType.isBlank() && !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Chỉ chấp nhận ảnh (contentType image/*)"));
        }

        String safe = sanitizeFileName(fileName);

        if (isR2()) {
            if (r2PresignService == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("message", "R2 chưa cấu hình đúng (thiếu bean R2PresignService)."));
            }
            String key = r2PresignService.buildObjectKey(safe);
            return ResponseEntity.ok(r2PresignService.presignPut(key, contentType));
        }

        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + ":" + request.getServerPort();
        String uploadUrl = baseUrl + "/api/upload";
        String publicUrl = baseUrl + "/uploads/" + safe;

        return ResponseEntity.ok(UploadPresignResponse.builder()
                .uploadUrl(uploadUrl)
                .publicUrl(publicUrl)
                .method("POST")
                .headers(Map.of("X-Upload-Filename", safe))
                .build());
    }

    private static String sanitizeFileName(String fileName) {
        String safe = fileName.replace("\\", "").replace("/", "");
        return safe.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
