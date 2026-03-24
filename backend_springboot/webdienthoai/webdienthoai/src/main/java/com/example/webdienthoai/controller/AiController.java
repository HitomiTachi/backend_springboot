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
        "Khi nhận được tên hoặc từ khóa sản phẩm, hãy trả về JSON với cấu trúc sau (KHÔNG markdown, CHỈ JSON thuần):\n" +
        "{\n" +
        "  \"name\": \"Tên đầy đủ và chuyên nghiệp\",\n" +
        "  \"description\": \"Mô tả 3-5 câu tiếng Việt nêu bật tính năng nổi bật\",\n" +
        "  \"suggestedPrice\": 29990000,\n" +
        "  \"categoryHint\": \"Điện thoại | Máy tính bảng | Phụ kiện | Âm thanh | Làm mát | Nhà thông minh | Khác\"\n" +
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
    public ResponseEntity<?> generateProduct(@RequestBody Map<String, String> body) {
        String keyword = body.getOrDefault("keyword", "").trim();
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

                return ResponseEntity.ok(Map.of(
                    "name",           result.path("name").asText(""),
                    "description",    result.path("description").asText(""),
                    "suggestedPrice", result.path("suggestedPrice").asLong(0),
                    "categoryHint",   result.path("categoryHint").asText(""),
                    "model",          model
                ));

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
}
