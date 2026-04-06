package com.example.webdienthoai.service;

import com.example.webdienthoai.dto.ExcelImportResultDto;
import com.example.webdienthoai.entity.User;
import com.example.webdienthoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserExcelImportService {

    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ExcelImportResultDto importFromStream(InputStream inputStream) {
        try (Workbook wb = WorkbookFactory.create(inputStream)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                return errorOnly(1, "Sheet trống");
            }
            Row header = sheet.getRow(0);
            if (header == null) {
                return errorOnly(1, "Thiếu dòng tiêu đề");
            }
            Optional<Map<String, Integer>> colOpt = mapHeaderColumns(header);
            if (colOpt.isEmpty()) {
                return errorOnly(1, "Không map được cột từ dòng tiêu đề");
            }
            Map<String, Integer> col = colOpt.get();
            if (!col.containsKey("email") || !col.containsKey("password") || !col.containsKey("name")
                    || !col.containsKey("role")) {
                return errorOnly(1, "Thiếu cột bắt buộc: Email, Mật khẩu, Họ tên, Vai trò");
            }

            int success = 0;
            List<ExcelImportResultDto.RowError> errors = new ArrayList<>();
            int attempted = 0;
            int lastRow = sheet.getLastRowNum();

            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                int excelRow = r + 1;
                if (isRowEmpty(row, col)) {
                    continue;
                }
                attempted++;
                try {
                    String email = cellString(row, col.get("email")).trim().toLowerCase();
                    if (email.isBlank()) {
                        throw new IllegalArgumentException("Email trống");
                    }
                    if (!email.contains("@")) {
                        throw new IllegalArgumentException("Email không hợp lệ");
                    }
                    String password = cellString(row, col.get("password"));
                    if (password.isBlank()) {
                        throw new IllegalArgumentException("Mật khẩu trống");
                    }
                    if (password.length() < 6) {
                        throw new IllegalArgumentException("Mật khẩu tối thiểu 6 ký tự");
                    }
                    String name = cellString(row, col.get("name")).trim();
                    if (name.isBlank()) {
                        throw new IllegalArgumentException("Họ tên trống");
                    }
                    String role = normalizeRole(cellString(row, col.get("role")));
                    if (role == null) {
                        throw new IllegalArgumentException("Vai trò phải là admin hoặc customer");
                    }
                    if (userRepository.existsByEmail(email)) {
                        throw new IllegalArgumentException("Email đã tồn tại: " + email);
                    }

                    String phone = ExcelTextUtil.trimToNull(cellString(row, col.get("phone")));
                    String gender = ExcelTextUtil.trimToNull(cellString(row, col.get("gender")));
                    LocalDate dob = parseDate(row, col.get("dateOfBirth"));

                    User user = User.builder()
                            .name(name)
                            .email(email)
                            .password(passwordEncoder.encode(password))
                            .role(role)
                            .phone(phone)
                            .gender(gender)
                            .dateOfBirth(dob)
                            .passwordChangedAt(Instant.now())
                            .build();
                    userRepository.save(user);
                    success++;
                } catch (Exception e) {
                    errors.add(ExcelImportResultDto.RowError.builder()
                            .row(excelRow)
                            .message(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                            .build());
                }
            }

            return ExcelImportResultDto.builder()
                    .totalRows(attempted)
                    .successCount(success)
                    .errorCount(errors.size())
                    .errors(errors)
                    .build();
        } catch (Exception e) {
            return errorOnly(0, "Không đọc được file Excel: " + e.getMessage());
        }
    }

    private static ExcelImportResultDto errorOnly(int row, String msg) {
        return ExcelImportResultDto.builder()
                .totalRows(0)
                .successCount(0)
                .errorCount(1)
                .errors(List.of(ExcelImportResultDto.RowError.builder()
                        .row(row)
                        .message(msg)
                        .build()))
                .build();
    }

    private static boolean isRowEmpty(Row row, Map<String, Integer> col) {
        for (String key : List.of("email", "password", "name", "role")) {
            Integer c = col.get(key);
            if (c == null) {
                continue;
            }
            if (!cellString(row, c).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static Optional<Map<String, Integer>> mapHeaderColumns(Row header) {
        Map<String, Integer> col = new HashMap<>();
        short last = header.getLastCellNum();
        if (last <= 0) {
            return Optional.empty();
        }
        for (int c = 0; c < last; c++) {
            Cell cell = header.getCell(c);
            String raw = DATA_FORMATTER.formatCellValue(cell).trim();
            if (raw.isEmpty()) {
                continue;
            }
            String key = ExcelTextUtil.normalizeHeaderKey(raw);
            String field = aliasUser(key);
            if (field != null) {
                col.put(field, c);
            }
        }
        return Optional.of(col);
    }

    private static String aliasUser(String normalizedHeader) {
        return switch (normalizedHeader) {
            case "email" -> "email";
            case "mat khau", "password", "matkhau" -> "password";
            case "ho ten", "name", "hoten", "ten" -> "name";
            case "vai tro", "role", "vaitro" -> "role";
            case "sdt", "phone", "so dien thoai", "dien thoai" -> "phone";
            case "gioi tinh", "gender", "gioitinh" -> "gender";
            case "ngay sinh", "date of birth", "ngaysinh", "sinh nhat" -> "dateOfBirth";
            default -> null;
        };
    }

    private static String cellString(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return "";
        }
        return DATA_FORMATTER.formatCellValue(cell).trim();
    }

    private static String cellString(Row row, Integer colIndex) {
        if (colIndex == null) {
            return "";
        }
        return cellString(row, colIndex.intValue());
    }

    private static String normalizeRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String r = ExcelTextUtil.normalizeHeaderKey(raw).replace(" ", "");
        if (r.equals("admin")) {
            return "admin";
        }
        if (r.equals("customer")) {
            return "customer";
        }
        return null;
    }

    private static LocalDate parseDate(Row row, Integer colIndex) {
        if (colIndex == null) {
            return null;
        }
        String s = cellString(row, colIndex);
        if (s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Ngày sinh không hợp lệ (dùng yyyy-MM-dd)");
        }
    }
}
