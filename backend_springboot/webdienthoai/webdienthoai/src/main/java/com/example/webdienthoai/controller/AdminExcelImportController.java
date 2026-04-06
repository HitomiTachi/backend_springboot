package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.ExcelImportResultDto;
import com.example.webdienthoai.security.UserPrincipal;
import com.example.webdienthoai.service.ExcelTemplateService;
import com.example.webdienthoai.service.ProductExcelImportService;
import com.example.webdienthoai.service.UserExcelImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/import")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminExcelImportController {

    private static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExcelTemplateService excelTemplateService;
    private final ProductExcelImportService productExcelImportService;
    private final UserExcelImportService userExcelImportService;

    @GetMapping("/templates/products")
    public ResponseEntity<byte[]> downloadProductTemplate() {
        byte[] bytes = excelTemplateService.buildProductTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau_san_pham.xlsx\"")
                .contentType(MediaType.parseMediaType(XLSX_TYPE))
                .body(bytes);
    }

    @GetMapping("/templates/users")
    public ResponseEntity<byte[]> downloadUserTemplate() {
        byte[] bytes = excelTemplateService.buildUserTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau_nguoi_dung.xlsx\"")
                .contentType(MediaType.parseMediaType(XLSX_TYPE))
                .body(bytes);
    }

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importProducts(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Chọn file Excel (.xlsx)"));
        }
        if (!isXlsx(file)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Chỉ chấp nhận file .xlsx"));
        }
        try {
            ExcelImportResultDto result = productExcelImportService.importFromStream(file.getInputStream(), principal);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Lỗi đọc file: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/users", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importUsers(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Chọn file Excel (.xlsx)"));
        }
        if (!isXlsx(file)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Chỉ chấp nhận file .xlsx"));
        }
        try {
            ExcelImportResultDto result = userExcelImportService.importFromStream(file.getInputStream());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Lỗi đọc file: " + e.getMessage()));
        }
    }

    private static boolean isXlsx(MultipartFile file) {
        String n = file.getOriginalFilename();
        return n != null && n.toLowerCase().endsWith(".xlsx");
    }
}
