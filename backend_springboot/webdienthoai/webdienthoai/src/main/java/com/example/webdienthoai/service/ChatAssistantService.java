package com.example.webdienthoai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trợ lý chat cửa hàng (Gemini) — chỉ gọi được khi đã cấu hình API key.
 */
@Service
public class ChatAssistantService {

    private static final int MAX_USER_MESSAGE = 4000;
    private static final int MAX_REPLY_CHARS = 8000;

    private static final List<String> PREFERRED = List.of(
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash",
            "gemini-1.5-flash-8b",
            "gemini-1.5-pro"
    );

    private static final String SYSTEM_PROMPT =
            "Bạn là trợ lý hỗ trợ khách hàng cửa hàng điện tử TechHome (Việt Nam). "
                    + "Trả lời ngắn gọn, lịch sự, bằng tiếng Việt. "
                    + "Chỉ tư vấn về: sản phẩm điện thoại/điện tử, đặt hàng, thanh toán (COD, VNPay), vận chuyển, đổi trả, bảo hành chung. "
                    + "Không bịa mã đơn hàng hay thông tin tài khoản cụ thể; nếu cần tra cứu đơn, hướng dẫn khách vào mục «Đơn hàng» sau khi đăng nhập. "
                    + "Không dùng markdown phức tạp; có thể dùng xuống dòng đơn giản.";

    @Value("${app.gemini.api-key:}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isConfigured() {
        return geminiApiKey != null && !geminiApiKey.isBlank()
                && !geminiApiKey.equals("PASTE_YOUR_GEMINI_API_KEY_HERE")
                && !geminiApiKey.equals("YOUR_API_KEY");
    }

    /**
     * @param userMessage tin nhắn đã trim
     * @param userEmail   email đăng nhập (ghi log ngữ cảnh, không gửi nội dung nhạy cảm tới model ngoài câu hỏi)
     */
    public String reply(String userMessage, String userEmail) {
        if (!isConfigured()) {
            throw new IllegalStateException("Gemini API key chưa cấu hình");
        }
        if (userMessage == null || userMessage.isEmpty()) {
            throw new IllegalArgumentException("Tin nhắn trống");
        }
        if (userMessage.length() > MAX_USER_MESSAGE) {
            throw new IllegalArgumentException("Tin nhắn quá dài (tối đa " + MAX_USER_MESSAGE + " ký tự)");
        }

        List<String> models;
        try {
            models = fetchAvailableModels();
            if (models.isEmpty()) {
                throw new IllegalStateException("Không có model Gemini khả dụng");
            }
            models.sort((a, b) -> {
                int ia = PREFERRED.indexOf(a);
                int ib = PREFERRED.indexOf(b);
                if (ia == -1) {
                    ia = PREFERRED.size();
                }
                if (ib == -1) {
                    ib = PREFERRED.size();
                }
                return ia - ib;
            });
        } catch (Exception e) {
            throw new IllegalStateException("Không lấy được danh sách model: " + e.getMessage());
        }

        Map<String, Object> generationConfig = Map.of(
                "maxOutputTokens", 1024,
                "temperature", 0.65
        );

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))));
        requestBody.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of(
                        "text", "Khách đã đăng nhập (gợi ý danh tính: " + sanitizeEmailHint(userEmail) + "). Câu hỏi:\n" + userMessage
                ))
        )));
        requestBody.put("generationConfig", generationConfig);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        String lastError = "Không có phản hồi";
        for (String model : models) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent?key=" + geminiApiKey;
            try {
                ResponseEntity<String> resp = restTemplate.exchange(
                        url, HttpMethod.POST, request, String.class
                );
                JsonNode root = objectMapper.readTree(resp.getBody());
                JsonNode candidates = root.path("candidates");
                if (!candidates.isArray() || candidates.isEmpty()) {
                    lastError = "Model " + model + " không trả candidates";
                    continue;
                }
                String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText("").trim();
                if (text.isEmpty()) {
                    lastError = "Model " + model + " trả nội dung rỗng";
                    continue;
                }
                if (text.length() > MAX_REPLY_CHARS) {
                    text = text.substring(0, MAX_REPLY_CHARS) + "…";
                }
                return text;
            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                if (status == 429) {
                    lastError = "Model " + model + " vượt quota";
                } else if (status == 404) {
                    lastError = "Model " + model + " không hỗ trợ generateContent";
                } else {
                    lastError = "Model " + model + " HTTP " + status;
                }
            } catch (Exception e) {
                lastError = "Model " + model + ": " + e.getMessage();
            }
        }
        throw new IllegalStateException("Không nhận được phản hồi từ AI. " + lastError);
    }

    private static String sanitizeEmailHint(String email) {
        if (email == null || email.isBlank()) {
            return "(không rõ)";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***@" + email.substring(at + 1);
    }

    private List<String> fetchAvailableModels() throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + geminiApiKey;
        ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
        JsonNode root = objectMapper.readTree(resp.getBody());
        List<String> result = new ArrayList<>();
        root.path("models").forEach(m -> {
            boolean supportsGenerate = false;
            for (JsonNode method : m.path("supportedGenerationMethods")) {
                if ("generateContent".equals(method.asText())) {
                    supportsGenerate = true;
                    break;
                }
            }
            if (supportsGenerate) {
                String name = m.path("name").asText();
                result.add(name.replace("models/", ""));
            }
        });
        return result;
    }
}
