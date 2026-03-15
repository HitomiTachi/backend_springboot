package com.example.webdienthoai.config;

import com.example.webdienthoai.entity.Category;
import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.entity.User;
import com.example.webdienthoai.repository.CategoryRepository;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Tạo dữ liệu mẫu (categories, products) nếu DB trống — dùng cho dev.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Tài khoản mẫu để đăng nhập (chỉ tạo khi chưa có user nào)
        if (userRepository.count() == 0) {
            userRepository.save(User.builder()
                    .name("Người dùng mẫu")
                    .email("user@example.com")
                    .password(passwordEncoder.encode("123456"))
                    .build());
        }

        if (categoryRepository.count() > 0) return;

        Category phone = categoryRepository.save(Category.builder().name("Điện thoại").slug("dien-thoai").build());
        Category laptop = categoryRepository.save(Category.builder().name("Laptop").slug("laptop").build());
        Category tablet = categoryRepository.save(Category.builder().name("Tablet").slug("tablet").build());

        String iphone17ProMaxSpecs = loadSpecsFromResource("data/iphone-17-pro-max-specs.json");
        String iphone17ProSpecs = loadSpecsFromResource("data/iphone-17-pro-specs.json");
        String iphone17Specs = loadSpecsFromResource("data/iphone-17-specs.json");
        String iphone17ESpecs = loadSpecsFromResource("data/iphone-17-e-specs.json");
        String iphoneAirSpecs = loadSpecsFromResource("data/iphone-air-specs.json");

        List<Product> products = List.of(
                Product.builder().name("iPhone 15").slug("iphone-15").description("Apple iPhone 15").image("/images/iphone15.jpg")
                        .price(new BigDecimal("24990000")).category(phone).stock(50).featured(true).build(),
                Product.builder().name("Samsung Galaxy S24").slug("samsung-galaxy-s24").description("Samsung Galaxy S24")
                        .image("/images/s24.jpg").price(new BigDecimal("21990000")).category(phone).stock(30).featured(true).build(),
                Product.builder().name("iPhone 17 e").slug("iphone-17-e").description("Apple iPhone 17 e - Chip A19, màn hình 6.1\" Super Retina XDR, camera 48MP, 256GB")
                        .image("/images/iphone17e.jpg").price(new BigDecimal("25990000")).category(phone).stock(35).featured(true)
                        .specifications(iphone17ESpecs).build(),
                Product.builder().name("iPhone 17").slug("iphone-17").description("Apple iPhone 17 - Chip A19, màn hình 6.3\" Super Retina XDR OLED, camera 48MP Fusion Main, 256GB")
                        .image("/images/iphone17.jpg").price(new BigDecimal("29990000")).category(phone).stock(30).featured(true)
                        .specifications(iphone17Specs).build(),
                Product.builder().name("iPhone 17 Pro").slug("iphone-17-pro").description("Apple iPhone 17 Pro - Chip A19 Pro, màn hình 6.3\" Super Retina XDR, camera 48MP, 256GB")
                        .image("/images/iphone17pro.jpg").price(new BigDecimal("36990000")).category(phone).stock(25).featured(true)
                        .specifications(iphone17ProSpecs).build(),
                Product.builder().name("iPhone 17 Pro Max").slug("iphone-17-pro-max").description("Apple iPhone 17 Pro Max - Chip A19 Pro, màn hình 6.9\" Super Retina XDR, camera 48MP")
                        .image("/images/iphone17promax.jpg").price(new BigDecimal("42990000")).category(phone).stock(20).featured(true)
                        .specifications(iphone17ProMaxSpecs).build(),
                Product.builder().name("iPhone Air").slug("iphone-air").description("Apple iPhone Air - Chip A19 Pro, màn hình 6.5\" Super Retina XDR, khung Titanium, 5.6mm, 256GB")
                        .image("/images/iphoneair.jpg").price(new BigDecimal("32990000")).category(phone).stock(22).featured(true)
                        .specifications(iphoneAirSpecs).build(),
                Product.builder().name("MacBook Pro M3").slug("macbook-pro-m3").description("Apple MacBook Pro M3")
                        .image("/images/macbook.jpg").price(new BigDecimal("45990000")).category(laptop).stock(20).featured(true).build(),
                Product.builder().name("iPad Pro").slug("ipad-pro").description("Apple iPad Pro")
                        .image("/images/ipad.jpg").price(new BigDecimal("27990000")).category(tablet).stock(25).featured(false).build()
        );
        productRepository.saveAll(products);
    }

    private String loadSpecsFromResource(String path) {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
