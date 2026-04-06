package com.example.webdienthoai.service;

import com.example.webdienthoai.dto.ExcelImportResultDto;
import com.example.webdienthoai.entity.Category;
import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.repository.CategoryRepository;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductExcelImportService {

    private static final DataFormatter DATA_FORMATTER = new DataFormatter();
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int MAX_EXCEL_COLORS = 12;
    private static final int MAX_EXCEL_STORAGE = 12;
    private static final int MAX_COLOR_NAME_LEN = 120;
    private static final int MAX_STORAGE_LABEL_LEN = 64;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryPathResolver categoryPathResolver;
    private final ProductPriceAuditService productPriceAuditService;

    public ExcelImportResultDto importFromStream(InputStream inputStream, UserPrincipal principal) {
        try (Workbook wb = WorkbookFactory.create(inputStream)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                return ExcelImportResultDto.builder()
                        .totalRows(0)
                        .successCount(0)
                        .errorCount(1)
                        .errors(java.util.List.of(ExcelImportResultDto.RowError.builder()
                                .row(1)
                                .message("Sheet trống")
                                .build()))
                        .build();
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
            if (!col.containsKey("name") || !col.containsKey("price") || !col.containsKey("categoryPath")) {
                return errorOnly(1, "Thiếu cột bắt buộc: Tên sản phẩm, Giá, Danh mục");
            }

            int success = 0;
            java.util.List<ExcelImportResultDto.RowError> errors = new java.util.ArrayList<>();
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
                    String name = cellString(row, col.get("name"));
                    if (name.isBlank()) {
                        throw new IllegalArgumentException("Tên sản phẩm trống");
                    }
                    BigDecimal price = parsePrice(row, col.get("price"));
                    if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("Giá không hợp lệ");
                    }
                    price = price.setScale(2, RoundingMode.HALF_UP);
                    String catPath = cellString(row, col.get("categoryPath"));
                    if (catPath.isBlank()) {
                        throw new IllegalArgumentException("Danh mục trống");
                    }
                    Long categoryId = categoryPathResolver.resolve(catPath);
                    Category category = categoryRepository.findById(categoryId)
                            .orElseThrow(() -> new IllegalArgumentException("Danh mục không tồn tại"));

                    Integer stock = parseIntOptional(row, col.get("stock")).orElse(0);
                    if (stock < 0) {
                        throw new IllegalArgumentException("Tồn kho không hợp lệ");
                    }
                    String description = ExcelTextUtil.trimToNull(cellString(row, col.get("description")));
                    String image = ExcelTextUtil.trimToNull(cellString(row, col.get("image")));
                    boolean featured = parseFeatured(row, col.get("featured"));

                    String slugCol = col.containsKey("slug") ? cellString(row, col.get("slug")) : "";
                    String finalSlug = resolveProductSlug(name, ExcelTextUtil.trimToNull(slugCol));

                    if (productRepository.existsBySlugIgnoreCase(finalSlug)) {
                        throw new IllegalArgumentException("Slug đã tồn tại: " + finalSlug);
                    }

                    String colorsJson = colorsJsonFromCell(row, col.get("colors"));
                    String storageJson = storageOptionsJsonFromCell(row, col.get("storageOptions"));

                    Product.ProductBuilder pb = Product.builder()
                            .name(name.trim())
                            .slug(finalSlug)
                            .description(description)
                            .image(image)
                            .price(price)
                            .category(category)
                            .categoryId(categoryId)
                            .stock(stock)
                            .reservedStock(0)
                            .featured(featured);
                    if (colorsJson != null) {
                        pb.colors(colorsJson);
                    }
                    if (storageJson != null) {
                        pb.storageOptions(storageJson);
                    }
                    Product product = pb.build();
                    product = productRepository.save(product);
                    String actor = ProductPriceAuditService.formatActor(principal);
                    productPriceAuditService.recordPriceChange(product.getId(), null, product.getPrice(), actor);
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
                .errors(java.util.List.of(ExcelImportResultDto.RowError.builder()
                        .row(row)
                        .message(msg)
                        .build()))
                .build();
    }

    private static boolean isRowEmpty(Row row, Map<String, Integer> col) {
        for (String key : java.util.List.of("name", "price", "categoryPath")) {
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
            String field = aliasProduct(key);
            if (field != null) {
                col.put(field, c);
            }
        }
        return Optional.of(col);
    }

    /** Header đã chuẩn hóa (không dấu, lower). */
    private static String aliasProduct(String normalizedHeader) {
        return switch (normalizedHeader) {
            case "ten san pham", "name", "ten sp", "tensanpham" -> "name";
            case "gia", "price" -> "price";
            case "danh muc", "duong dan danh muc", "category path", "categorypath" -> "categoryPath";
            case "ton kho", "stock", "so luong", "soluong" -> "stock";
            case "mo ta", "description" -> "description";
            case "anh", "anh url", "url anh", "image", "hinh anh", "url" -> "image";
            case "noi bat", "featured", "noibat" -> "featured";
            case "slug" -> "slug";
            case "mau sac", "mau", "colors", "color" -> "colors";
            case "dung luong", "dungluong", "storage", "storage options", "storageoptions", "bo nho", "bonho" -> "storageOptions";
            default -> null;
        };
    }

    /**
     * Nếu chuỗi có ký tự {@code |} thì tách theo {@code |}, không thì theo dấu phẩy.
     */
    private static List<String> splitDelimitedCell(String raw) {
        String t = raw.trim();
        if (t.isEmpty()) {
            return List.of();
        }
        String[] parts = t.contains("|") ? t.split("\\|") : t.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String s = p.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static String truncateLen(String s, int max) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String colorsJsonFromCell(Row row, Integer colIdx) throws JsonProcessingException {
        if (colIdx == null) {
            return null;
        }
        String raw = cellString(row, colIdx);
        if (raw.isBlank()) {
            return null;
        }
        List<String> parts = splitDelimitedCell(raw);
        if (parts.isEmpty()) {
            return null;
        }
        if (parts.size() > MAX_EXCEL_COLORS) {
            throw new IllegalArgumentException("Tối đa " + MAX_EXCEL_COLORS + " màu mỗi dòng");
        }
        ArrayNode arr = JSON.createArrayNode();
        for (String p : parts) {
            String n = truncateLen(p.trim(), MAX_COLOR_NAME_LEN);
            if (n.isEmpty()) {
                continue;
            }
            ObjectNode o = JSON.createObjectNode();
            o.put("name", n);
            arr.add(o);
        }
        if (arr.isEmpty()) {
            return null;
        }
        return JSON.writeValueAsString(arr);
    }

    private static String storageOptionsJsonFromCell(Row row, Integer colIdx) throws JsonProcessingException {
        if (colIdx == null) {
            return null;
        }
        String raw = cellString(row, colIdx);
        if (raw.isBlank()) {
            return null;
        }
        List<String> parts = splitDelimitedCell(raw);
        if (parts.isEmpty()) {
            return null;
        }
        if (parts.size() > MAX_EXCEL_STORAGE) {
            throw new IllegalArgumentException("Tối đa " + MAX_EXCEL_STORAGE + " dung lượng mỗi dòng");
        }
        ArrayNode arr = JSON.createArrayNode();
        for (String p : parts) {
            String label = truncateLen(p.trim(), MAX_STORAGE_LABEL_LEN);
            if (label.isEmpty()) {
                continue;
            }
            arr.add(label);
        }
        if (arr.isEmpty()) {
            return null;
        }
        return JSON.writeValueAsString(arr);
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

    private static BigDecimal parsePrice(Row row, Integer colIndex) {
        if (colIndex == null) {
            return null;
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        }
        String raw = DATA_FORMATTER.formatCellValue(cell).trim();
        if (raw.isEmpty()) {
            return null;
        }
        String t = raw.replace(".", "").replace(",", "").replaceAll("\\s+", "");
        if (t.isEmpty()) {
            return null;
        }
        return new BigDecimal(t).setScale(2, RoundingMode.HALF_UP);
    }

    private static Optional<Integer> parseIntOptional(Row row, Integer colIndex) {
        if (colIndex == null) {
            return Optional.empty();
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return Optional.empty();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return Optional.of((int) Math.round(cell.getNumericCellValue()));
        }
        String raw = DATA_FORMATTER.formatCellValue(cell).trim();
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        raw = raw.replace(".", "").replace(",", "").trim();
        return Optional.of(Integer.parseInt(raw));
    }

    private static boolean parseFeatured(Row row, Integer colIndex) {
        if (colIndex == null) {
            return false;
        }
        String s = cellString(row, colIndex);
        if (s.isBlank()) {
            return false;
        }
        String x = ExcelTextUtil.normalizeHeaderKey(s).replace(" ", "");
        if (x.equals("co") || x.equals("true") || x.equals("1") || x.equals("yes")) {
            return true;
        }
        if (x.equals("khong") || x.equals("false") || x.equals("0") || x.equals("no")) {
            return false;
        }
        throw new IllegalArgumentException("Nổi bật không hợp lệ (dùng: có / không / true / false / 1 / 0)");
    }

    private static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-");
        return normalized;
    }

    private static String resolveProductSlug(String name, String slugFromFile) {
        if (slugFromFile != null && !slugFromFile.isBlank()) {
            String s = slugify(slugFromFile);
            if (s.isEmpty()) {
                throw new IllegalArgumentException("Slug không hợp lệ");
            }
            return s;
        }
        String s = slugify(name);
        if (s.isEmpty()) {
            return "sp-" + Instant.now().toEpochMilli();
        }
        return s;
    }
}
