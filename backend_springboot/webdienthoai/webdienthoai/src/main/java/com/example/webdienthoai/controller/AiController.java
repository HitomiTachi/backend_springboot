package com.example.webdienthoai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
public class AiController {

    @Value("${app.gemini.api-key:}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Model ưu tiên thử trước — chỉ dùng làm gợi ý sắp xếp
    private static final List<String> PREFERRED = List.of(
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-1.5-flash",
        "gemini-1.5-flash-8b",
        "gemini-1.5-pro"
    );

    private static final String SYSTEM_PROMPT =
        "Bạn là chuyên gia viết nội dung sản phẩm cho cửa hàng điện tử TechHome (Việt Nam). " +
        "Khi nhận được tên hoặc từ khóa sản phẩm, hãy trả về JSON với cấu trúc sau (KHÔNG markdown, CHỈ JSON thuần). " +
        "suggestedPrice là số (VNĐ, số nguyên). suggestedStock là số nguyên tồn kho gợi ý (0–1000000). " +
        "colors: mảng object { \"name\": \"Tên màu\", \"hex\": \"#RRGGBB\" } (hex tuỳ chọn, đúng 6 ký tự hex sau #). " +
        "storageOptions: mảng chuỗi ví dụ [\"128GB\",\"256GB\"]. " +
        "specifications: object lồng — mỗi key là nhóm tiếng Việt (Màn hình, Camera sau, Camera trước, Hiệu năng & bộ nhớ, Pin & sạc, Kết nối, Thiết kế…), " +
        "value là object { \"Nhãn thông số\": \"Giá trị\" }.\n" +
        "{\n" +
        "  \"name\": \"Tên đầy đủ và chuyên nghiệp\",\n" +
        "  \"description\": \"Mô tả 3-5 câu tiếng Việt nêu bật tính năng nổi bật\",\n" +
        "  \"suggestedPrice\": 29990000,\n" +
        "  \"categoryHint\": \"Điện thoại | Máy tính bảng | Phụ kiện | Âm thanh | Làm mát | Nhà thông minh | Khác\",\n" +
        "  \"suggestedStock\": 50,\n" +
        "  \"colors\": [{ \"name\": \"Đen không gian\", \"hex\": \"#1d1d1f\" }],\n" +
        "  \"storageOptions\": [\"128GB\", \"256GB\", \"512GB\"],\n" +
        "  \"specifications\": {\n" +
        "    \"Màn hình\": { \"Kích thước\": \"6.7 inch\", \"Công nghệ màn hình\": \"OLED\" },\n" +
        "    \"Hiệu năng & bộ nhớ\": { \"Chip xử lý\": \"Snapdragon 8 Gen 3\", \"RAM\": \"12GB\" }\n" +
        "  }\n" +
        "}";

    /** Liệt kê models khả dụng với API key hiện tại */
    @GetMapping("/list-models")
    public ResponseEntity<?> listModels() {
        if (!isKeyConfigured()) {
            return ResponseEntity.status(503).body(Map.of("message", "API key chưa cấu hình"));
        }
        try {
            List<String> names = fetchAvailableModels();
            return ResponseEntity.ok(Map.of("models", names, "count", names.size()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateProduct(@RequestBody Map<String, String> payload) {
        String keyword = payload.getOrDefault("keyword", "").trim();
        if (keyword.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "keyword không được để trống"));
        }
        if (!isKeyConfigured()) {
            return ResponseEntity.status(503).body(Map.of(
                "message", "Gemini API key chưa cấu hình. Thêm app.gemini.api-key vào application.properties"
            ));
        }

        // Lấy danh sách model thật từ API, sắp xếp theo thứ tự ưu tiên
        List<String> models;
        try {
            models = fetchAvailableModels();
            if (models.isEmpty()) {
                return ResponseEntity.status(503).body(Map.of("message", "Không tìm thấy model nào trong tài khoản"));
            }
            // Sắp xếp: model trong PREFERRED lên trước, theo thứ tự PREFERRED
            models.sort((a, b) -> {
                int ia = PREFERRED.indexOf(a);
                int ib = PREFERRED.indexOf(b);
                if (ia == -1) ia = PREFERRED.size();
                if (ib == -1) ib = PREFERRED.size();
                return ia - ib;
            });
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                "message", "Không thể lấy danh sách model: " + e.getMessage()
            ));
        }

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(Map.of(
                "parts", List.of(
                    Map.of("text", SYSTEM_PROMPT),
                    Map.of("text", "Tạo thông tin sản phẩm cho: \"" + keyword + "\"")
                )
            ))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        String lastError = "Không tìm được model khả dụng";

        for (String model : models) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent?key=" + geminiApiKey;
            try {
                ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class
                );

                JsonNode root = objectMapper.readTree(resp.getBody());
                String text = root.path("candidates").get(0)
                        .path("content").path("parts").get(0)
                        .path("text").asText("").trim();

                text = text.replaceAll("(?i)^```json\\s*", "")
                           .replaceAll("(?i)^```\\s*", "")
                           .replaceAll("\\s*```$", "")
                           .trim();

                JsonNode result = objectMapper.readTree(text);
                if (!result.has("name")) {
                    lastError = "AI trả về JSON không hợp lệ từ " + model;
                    continue;
                }

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("name", truncate(result.path("name").asText(""), 500));
                body.put("description", truncate(result.path("description").asText(""), 12000));
                body.put("suggestedPrice", parseSuggestedPrice(result.path("suggestedPrice")));
                body.put("categoryHint", truncate(result.path("categoryHint").asText(""), 200));
                body.put("model", model);
                Integer stock = parseSuggestedStock(result.path("suggestedStock"));
                if (stock != null) {
                    body.put("suggestedStock", stock);
                }
                List<Map<String, String>> colors = parseColors(result.path("colors"));
                if (colors != null) {
                    body.put("colors", colors);
                }
                List<String> storageOpts = parseStorageOptions(result.path("storageOptions"));
                if (storageOpts != null) {
                    body.put("storageOptions", storageOpts);
                }
                Map<String, Map<String, String>> specs = sanitizeSpecifications(result.path("specifications"));
                if (specs != null) {
                    body.put("specifications", specs);
                }

                return ResponseEntity.ok(body);

            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                if (status == 429) { lastError = "Model " + model + " vượt quota"; continue; }
                if (status == 404) { lastError = "Model " + model + " không hỗ trợ generateContent"; continue; }
                lastError = "Model " + model + " HTTP " + status + ": " + e.getResponseBodyAsString();
            } catch (Exception e) {
                lastError = "Model " + model + ": " + e.getMessage();
            }
        }

        return ResponseEntity.status(503).body(Map.of("message",
            "Tất cả " + models.size() + " model đều thất bại. Lỗi cuối: " + lastError));
    }

    /** Gọi ListModels API, trả về tên model (bỏ tiền tố "models/") */
    private List<String> fetchAvailableModels() throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + geminiApiKey;
        ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
        JsonNode root = objectMapper.readTree(resp.getBody());

        List<String> result = new ArrayList<>();
        root.path("models").forEach(m -> {
            // Kiểm tra model hỗ trợ generateContent
            boolean supportsGenerate = false;
            for (JsonNode method : m.path("supportedGenerationMethods")) {
                if ("generateContent".equals(method.asText())) {
                    supportsGenerate = true;
                    break;
                }
            }
            if (supportsGenerate) {
                String name = m.path("name").asText(); // "models/gemini-2.0-flash"
                result.add(name.replace("models/", ""));
            }
        });
        return result;
    }

    private boolean isKeyConfigured() {
        return geminiApiKey != null && !geminiApiKey.isBlank()
                && !geminiApiKey.equals("PASTE_YOUR_GEMINI_API_KEY_HERE")
                && !geminiApiKey.equals("YOUR_API_KEY");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }

    private static long parseSuggestedPrice(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return 0L;
        }
        if (n.isNumber()) {
            return Math.round(n.doubleValue());
        }
        if (n.isTextual()) {
            String t = n.asText("").replaceAll("[^0-9]", "");
            if (t.isEmpty()) return 0L;
            try {
                return Long.parseLong(t);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    /** null nếu không có hoặc không hợp lệ */
    private static Integer parseSuggestedStock(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull() || !n.isNumber()) {
            return null;
        }
        int v = n.intValue();
        if (v < 0) return null;
        return Math.min(v, 1_000_000);
    }

    private static final int MAX_COLORS = 12;
    private static final int MAX_STORAGE = 12;
    private static final int MAX_SPEC_SECTIONS = 12;
    private static final int MAX_SPEC_ROWS = 24;

    private static List<Map<String, String>> parseColors(JsonNode arr) {
        if (arr == null || !arr.isArray()) {
            return null;
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (JsonNode item : arr) {
            if (out.size() >= MAX_COLORS) break;
            if (item.isObject()) {
                String name = truncate(item.path("name").asText("").trim(), 120);
                if (name.isEmpty()) continue;
                String hex = item.path("hex").asText("").trim();
                Map<String, String> row = new LinkedHashMap<>();
                row.put("name", name);
                if (hex.matches("#[0-9A-Fa-f]{6}")) {
                    row.put("hex", hex);
                }
                out.add(row);
            } else if (item.isTextual()) {
                String name = truncate(item.asText("").trim(), 120);
                if (!name.isEmpty()) {
                    out.add(new LinkedHashMap<>(Map.of("name", name)));
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static List<String> parseStorageOptions(JsonNode arr) {
        if (arr == null || !arr.isArray()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : arr) {
            if (out.size() >= MAX_STORAGE) break;
            if (item.isTextual()) {
                String s = truncate(item.asText("").trim(), 48);
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static Map<String, Map<String, String>> sanitizeSpecifications(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> secIt = node.fields();
        int secCount = 0;
        while (secIt.hasNext() && secCount < MAX_SPEC_SECTIONS) {
            Map.Entry<String, JsonNode> e = secIt.next();
            String secTitle = truncate(e.getKey().trim(), 100);
            if (secTitle.isEmpty()) continue;
            JsonNode block = e.getValue();
            if (!block.isObject()) continue;
            Map<String, String> inner = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> rowIt = block.fields();
            int rowCount = 0;
            while (rowIt.hasNext() && rowCount < MAX_SPEC_ROWS) {
                Map.Entry<String, JsonNode> row = rowIt.next();
                String lab = truncate(row.getKey().trim(), 150);
                if (lab.isEmpty()) continue;
                String val = "";
                JsonNode vn = row.getValue();
                if (vn != null && vn.isValueNode() && !vn.isNull()) {
                    val = truncate(vn.asText(""), 4000);
                }
                inner.put(lab, val);
                rowCount++;
            }
            if (!inner.isEmpty()) {
                out.put(secTitle, inner);
                secCount++;
            }
        }
        return out.isEmpty() ? null : out;
    }
}
