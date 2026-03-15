package com.example.webdienthoai.service.impl;

import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.service.PhoneSpecsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneSpecsServiceImpl implements PhoneSpecsService {

    private final ProductRepository productRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    /** Base URL của API (vd: https://mobile-phone-database-api.p.rapidapi.com). Để trống = tắt gọi API ngoài. */
    @Value("${app.phone-specs.api-url:}")
    private String apiBaseUrl;

    /** Tên header API key (vd: X-RapidAPI-Key). */
    @Value("${app.phone-specs.api-key-header:X-RapidAPI-Key}")
    private String apiKeyHeader;

    /** API key. Để trống = chỉ dùng dữ liệu đã lưu trong DB. */
    @Value("${app.phone-specs.api-key:}")
    private String apiKey;

    /** Path search (vd: /search). Một số API dùng /query hoặc path khác. */
    @Value("${app.phone-specs.search-path:/search}")
    private String searchPath;

    @Override
    public Optional<String> fetchSpecsForProduct(Product product) {
        if (StringUtils.hasText(product.getSpecifications())) {
            return Optional.of(product.getSpecifications());
        }
        if (!StringUtils.hasText(apiBaseUrl) || !StringUtils.hasText(apiKey)) {
            return Optional.empty();
        }
        try {
            String query = product.getName() != null ? product.getName().trim() : "";
            if (query.isEmpty()) return Optional.empty();
            String path = searchPath.startsWith("/") ? searchPath : "/" + searchPath;
            String url = apiBaseUrl.replaceAll("/$", "") + path + "?query=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.set(apiKeyHeader, apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(url),
                    HttpMethod.GET,
                    entity,
                    String.class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Could not fetch phone specs for product {}: {}", product.getId(), e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public boolean fetchAndSaveSpecs(Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) return false;
        Optional<String> specs = fetchSpecsForProduct(product);
        if (specs.isEmpty()) return false;
        product.setSpecifications(specs.get());
        productRepository.save(product);
        return true;
    }
}
